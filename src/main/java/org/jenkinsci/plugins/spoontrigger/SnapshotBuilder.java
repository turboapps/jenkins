package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.Closeables;
import com.google.common.reflect.TypeToken;
import hudson.*;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import lombok.Data;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.OutputStreamCollector;
import org.jenkinsci.plugins.spoontrigger.commands.powershell.PowerShellCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ImportCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.PullCommand;
import org.jenkinsci.plugins.spoontrigger.commands.xstudio.BuildCommand;
import org.jenkinsci.plugins.spoontrigger.hub.HubApi;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.scheduledtasks.ScheduledTasksApi;
import org.jenkinsci.plugins.spoontrigger.snapshot.InstallScriptStrategy;
import org.jenkinsci.plugins.spoontrigger.snapshot.StartupFileStrategy;
import org.jenkinsci.plugins.spoontrigger.snapshot.XapplEditor;
import org.jenkinsci.plugins.spoontrigger.utils.FileUtils;
import org.jenkinsci.plugins.spoontrigger.utils.JsonOption;
import org.jenkinsci.plugins.spoontrigger.vagrant.VagrantEnvironment;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;
import static org.jenkinsci.plugins.spoontrigger.utils.AutoCompletion.suggestDirectories;
import static org.jenkinsci.plugins.spoontrigger.utils.AutoCompletion.suggestFiles;
import static org.jenkinsci.plugins.spoontrigger.utils.FileUtils.deleteDirectoryTreeRetryOnFailure;
import static org.jenkinsci.plugins.spoontrigger.utils.FileUtils.quietDeleteChildren;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class SnapshotBuilder extends BaseBuilder {

    private static final String IMAGE_NAME_FILE = "image.txt";

    @Getter
    private final InstallScriptSettings installScriptSettings;

    @Getter
    private final StartupFileSettings startupFileSettings;

    private final String xStudioPath;
    private final Optional<String> xStudioLicensePath;
    private final ArrayList<String> dependencies;
    private final ArrayList<String> snapshotPathsToDelete;

    @Getter
    private final String preInstallScriptPath;

    @Getter
    private final String postSnapshotScriptPath;

    @Getter
    private final String resourceDirectoryPath;

    @Getter
    private final boolean overwrite;

    @Getter
    private final String vagrantBox;

    private Optional<Image> importAsImage = Optional.absent();

    @DataBoundConstructor
    public SnapshotBuilder(
            String xStudioPath,
            String xStudioLicensePath,
            String vagrantBox,
            boolean overwrite,
            String preInstallScriptPath,
            String postSnapshotScriptPath,
            String resourceDirectoryPath,
            Collection<String> dependencies,
            Collection<String> snapshotFilesToDelete,
            InstallScriptSettings installScriptSettings,
            StartupFileSettings startupFileSettings) {
        this.xStudioPath = Util.fixEmptyAndTrim(xStudioPath);
        this.xStudioLicensePath = Optional.fromNullable(Util.fixEmptyAndTrim(xStudioLicensePath));
        this.vagrantBox = Util.fixEmptyAndTrim(vagrantBox);
        this.overwrite = overwrite;
        this.preInstallScriptPath = Util.fixEmptyAndTrim(preInstallScriptPath);
        this.postSnapshotScriptPath = Util.fixEmptyAndTrim(postSnapshotScriptPath);
        this.resourceDirectoryPath = Util.fixEmpty(resourceDirectoryPath);
        this.dependencies = new ArrayList<String>(dependencies);
        this.snapshotPathsToDelete = new ArrayList<String>(snapshotFilesToDelete);
        this.installScriptSettings = installScriptSettings;
        this.startupFileSettings = startupFileSettings;
    }

    public InstallScriptStrategy getInstallScriptStrategy() {
        return this.installScriptSettings.getStrategy();
    }

    public boolean getIgnoreExitCode() {
        return this.installScriptSettings.isIgnoreExitCode();
    }

    public String getSilentInstallArgs() {
        return this.installScriptSettings.getSilentInstallArgs();
    }

    public String getInstallScriptPath() {
        return this.installScriptSettings.getInstallScriptPath();
    }

    public StartupFileStrategy getStartupFileStrategy() {
        return this.startupFileSettings.getStrategy();
    }

    public String getStartupFilePath() {
        return this.startupFileSettings.getStartupFilePath();
    }

    public String getSnapshotPathsToDelete() {
        return Joiner.on(System.lineSeparator()).join(snapshotPathsToDelete);
    }

    @Override
    protected void prebuild(SpoonBuild build, BuildListener listener) {
        checkState(xStudioPath != null, String.format(REQUIRE_NOT_NULL_OR_EMPTY_S, "xStudioPath"));
        checkState(vagrantBox != null, String.format(REQUIRE_NOT_NULL_OR_EMPTY_S, "vagrantBox"));

        installScriptSettings.validate();
        startupFileSettings.validate();

        build.setAllowOverwrite(overwrite);
    }

    @Override
    public boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        String workspace = Paths.get(build.getWorkspace().getRemote()).toString();
        try {
            importAsImage = loadImportImageName(workspace);

            if (shouldAbort(build, listener)) {
                build.setResult(Result.ABORTED);
                return false;
            }

            takeSnapshot(workspace, build, launcher, listener);
            return true;
        } finally {
            quietDeleteChildren(Paths.get(workspace));
        }
    }

    public String getDependencies() {
        if (dependencies.isEmpty()) {
            return null;
        }
        return Joiner.on(", ").join(dependencies);
    }

    private boolean shouldAbort(SpoonBuild build, BuildListener listener) {
        if (build.isAllowOverwrite()) {
            return false;
        }

        Result currentResult = build.getResult();
        if (currentResult != null && currentResult.isWorseThan(Result.ABORTED)) {
            return false;
        }

        return importAsImage.isPresent() && isAvailableRemotely(importAsImage.get(), build, listener);
    }

    private void takeSnapshot(String workspace, SpoonBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        VagrantEnvironment vagrantEnv = createVagrantEnvironment(build, workspace);
        try {
            SnapshotTaker snapshotTaker = new SnapshotTaker(build, vagrantEnv, launcher, listener);
            snapshotTaker.takeSnapshot();
        } finally {
            // Vagrant working dir was moved to temp, because the Vagrant process running as a scheduled task
            // does not have write access to the build workspace in Program Files
            deleteDirectoryTreeRetryOnFailure(vagrantEnv.getWorkingDir(), listener);
        }
    }

    private VagrantEnvironment createVagrantEnvironment(SpoonBuild build, String buildWorkspace) throws IOException {
        Path workingDir = Files.createTempDirectory("jenkins-" + build.getSanitizedProjectName() + "-build-");
        VagrantEnvironment.EnvironmentBuilder environmentBuilder = VagrantEnvironment.builder(workingDir)
                .box(vagrantBox)
                .xStudioPath(xStudioPath);

        List<Path> installerPaths = getInstallerPaths(buildWorkspace);
        if (!installerPaths.isEmpty()) {
            environmentBuilder.installerPaths(installerPaths);
        }

        if (preInstallScriptPath != null) {
            environmentBuilder.preInstallScriptPath(preInstallScriptPath);
        }

        if (postSnapshotScriptPath != null) {
            environmentBuilder.postSnapshotScriptPath(postSnapshotScriptPath);
        }

        if (resourceDirectoryPath != null) {
            environmentBuilder.resourceDirectoryPath(resourceDirectoryPath);
        }

        installScriptSettings.configure(environmentBuilder);

        return environmentBuilder.build();
    }

    private List<Path> getInstallerPaths(String buildWorkspace) {
        File workspace = new File(buildWorkspace);
        File[] workspaceFiles = workspace.listFiles();
        checkState(workspaceFiles != null, "Failed to list files in %s directory", buildWorkspace);

        List<Path> installerPaths = new ArrayList<Path>();
        for (File workspaceFile : workspaceFiles) {
            final String extension = FileUtils.getExtension(workspaceFile.toPath());
            if ("exe".equals(extension) || "msi".equals(extension)) {
                installerPaths.add(workspaceFile.toPath());
            }
        }

        return installerPaths;
    }

    private Optional<Image> loadImportImageName(String workspacePath) throws IOException {
        Path imageFilePath = Paths.get(workspacePath, IMAGE_NAME_FILE);
        if (imageFilePath.toFile().exists()) {
            BufferedReader reader = Files.newBufferedReader(imageFilePath, Charset.defaultCharset());
            try {
                String imageName = reader.readLine();
                return Optional.of(Image.parse(imageName));
            } finally {
                final boolean swallowException = true;
                Closeables.close(reader, swallowException);
            }
        }
        return Optional.absent();
    }

    private class SnapshotTaker {
        private final SpoonBuild build;
        private final VagrantEnvironment vagrantEnv;
        private final BuildListener listener;
        private final ScheduledTasksApi scheduledTasksApi;
        private final CommandDriver commandDriver;

        public SnapshotTaker(SpoonBuild build, VagrantEnvironment vagrantEnv, Launcher launcher, BuildListener listener) {
            checkArgument(build.getEnv().isPresent(), "build");

            this.build = build;
            this.vagrantEnv = vagrantEnv;
            this.listener = listener;

            EnvVars env = this.build.getEnv().get();
            FilePath vagrantDir = new FilePath(vagrantEnv.getWorkingDir().toFile());
            this.commandDriver = CommandDriver.builder()
                    .charset(this.build.getCharset())
                    .env(env)
                    .pwd(vagrantDir)
                    .launcher(launcher)
                    .listener(this.listener)
                    .build();
            final boolean quiet = false;
            this.scheduledTasksApi = new ScheduledTasksApi(env, vagrantDir, build.getCharset(), launcher, this.listener, quiet);
        }

        private void takeSnapshot() {
            try {
                provisionVagrantVm();
                executePostSnapshotScript();
                removeFilesFromSnapshot();
                buildImage();
                importImage();
            } catch (Throwable buildError) {
                // do not swallow the initial build error
                destroyVagrantVm(true);
                throw new IllegalStateException("`vagrant up` failed with exception", buildError);
            }
            destroyVagrantVm(false);
        }

        private void pull(Image image) throws InterruptedException {
            PullCommand command = PullCommand.builder().image(image.printIdentifier()).build();
            command.run(commandDriver);
        }

        private void executePostSnapshotScript() throws InterruptedException {
            Path postSnapshotScriptPath = vagrantEnv.getPostSnapshotScriptPath();
            if (!postSnapshotScriptPath.toFile().exists()) {
                return;
            }

            PowerShellCommand command = PowerShellCommand.builder().scriptPath(postSnapshotScriptPath).build();
            command.run(commandDriver);
        }

        private void removeFilesFromSnapshot() throws Exception {
            if (snapshotPathsToDelete.isEmpty()) {
                return;
            }

            log(listener, "Removing redundant files from snapshot...");

            XapplEditor editor = new XapplEditor();
            Path xapplPath = vagrantEnv.getXapplPath();
            editor.load(xapplPath);

            for (String path : snapshotPathsToDelete) {
                if (editor.fileExists(path)) {
                    editor.removeFile(path);
                } else {
                    log(listener, String.format("File %s was not found in the snapshot", path));
                }
            }

            editor.save(xapplPath);

            String snapshotDir = vagrantEnv.getSnapshotPath().toString();
            for (String relativePath : snapshotPathsToDelete) {
                Path pathToRemove = Paths.get(snapshotDir, relativePath);
                try {
                    File fileToRemove = pathToRemove.toFile();
                    if (!fileToRemove.exists()) {
                        continue;
                    }

                    if (fileToRemove.isDirectory()) {
                        FileUtils.deleteDirectoryTree(pathToRemove);
                    } else {
                        Files.delete(pathToRemove);
                    }
                } catch (Throwable th) {
                    String errorMsg = String.format("Failed to remove %s from snapshot", pathToRemove);
                    throw new IllegalStateException(errorMsg, th);
                }
            }
        }

        private void buildImage() throws InterruptedException {
            BuildCommand.CommandBuilder commandBuilder = BuildCommand.builder(xStudioPath)
                    .xapplPath(vagrantEnv.getXapplPath().toString())
                    .imagePath(vagrantEnv.getImagePath().toString());

            if (xStudioLicensePath.isPresent()) {
                commandBuilder.licensePath(xStudioLicensePath.get());
            }

            Optional<String> startupFile = startupFileSettings.getStartupFile();
            if (startupFile.isPresent()) {
                commandBuilder.startupFilePath(startupFile.get());
            }

            HubApi hubApi = HubApi.create(build, listener);
            for (String dependency : dependencies) {
                Image buildDependency = Image.parse(dependency);
                Image dependencyToUse = buildDependency.getTag() == null ?
                        hubApi.getLatestVersion(buildDependency) : buildDependency;

                pull(dependencyToUse);

                commandBuilder.dependency(dependencyToUse.printIdentifier());
            }

            BuildCommand command = commandBuilder.build();
            command.run(commandDriver);
        }

        private void importImage() throws InterruptedException {
            ImportCommand.CommandBuilder commandBuilder = ImportCommand.builder()
                    .type("svm")
                    .path(vagrantEnv.getImagePath().toString())
                    .overwrite(overwrite);

            Optional<Image> imageToUse = getOutputImage();
            if (imageToUse.isPresent()) {
                commandBuilder.name(imageToUse.get().printIdentifier());
            }

            ImportCommand command = commandBuilder.build();
            command.run(commandDriver);

            Optional<Image> outputImage = command.getOutputImage();
            checkState(outputImage.isPresent(), "Failed to find imported image in command output");

            build.setOutputImage(outputImage.get());
        }

        private Optional<Image> getOutputImage() {
            // load image name from Vagrant working directory, because some snapshot projects extract product version after installation completed
            // otherwise use image name specified during build setup
            String vagrantWorkingDirPath = vagrantEnv.getWorkingDir().toString();
            try {
                Optional<Image> imageNameOpt = loadImportImageName(vagrantWorkingDirPath);
                return imageNameOpt.or(importAsImage);
            } catch (Throwable th) {
                String errMsg = String.format("Failed to load image name from %s", Paths.get(vagrantWorkingDirPath, IMAGE_NAME_FILE).toString());
                log(listener, errMsg, th);
                return importAsImage;
            }
        }

        private void provisionVagrantVm() throws IOException, InterruptedException {
            scheduledTasksApi.run(build.getProject().getName() + " - vagrant up", "vagrant up", new OutputStreamCollector());
        }

        private void destroyVagrantVm(boolean swallowException) {
            try {
                scheduledTasksApi.run(build.getProject().getName() + " - vagrant destroy", "vagrant destroy --force", new OutputStreamCollector());
            } catch (Throwable th) {
                final String errorMsg = "`vagrant destroy` failed with exception. The virtual machine may have to be removed from VirtualBox manually.";
                if (swallowException) {
                    log(listener, errorMsg, th);
                } else {
                    throw new IllegalStateException(errorMsg, th);
                }
            }
        }
    }

    @Data
    public static class InstallScriptSettings implements Serializable {
        private InstallScriptStrategy strategy;
        private String silentInstallArgs;
        private boolean ignoreExitCode;
        private String installScriptPath;

        public InstallScriptSettings(InstallScriptStrategy strategy, String silentInstallArgs, boolean ignoreExitCode, String installScriptPath) {
            this.strategy = strategy;
            this.silentInstallArgs = silentInstallArgs;
            this.ignoreExitCode = ignoreExitCode;
            this.installScriptPath = installScriptPath;
        }

        public static InstallScriptSettings fromJson(JsonOption.ObjectWrapper json) {
            String installStrategyName = json.getString("value").orNull();
            InstallScriptStrategy installScriptStrategy = InstallScriptStrategy.valueOf(installStrategyName);
            String silentInstallArgs = json.getString("silentInstallArgs").orNull();
            boolean ignoreExitCode = json.getBoolean("ignoreExitCode").or(Boolean.FALSE);
            String installScriptPath = json.getString("installScriptPath").orNull();

            return new InstallScriptSettings(installScriptStrategy, silentInstallArgs, ignoreExitCode, installScriptPath);
        }

        public void validate() {
            switch (strategy) {
                case FIXED:
                    checkState(!Strings.isNullOrEmpty(installScriptPath), String.format(REQUIRE_NOT_NULL_OR_EMPTY_S, "installScriptPath"));
                    break;
                case TEMPLATE:
                default:
                    break;
            }
        }

        public void configure(VagrantEnvironment.EnvironmentBuilder environmentBuilder) {
            switch (strategy) {
                case FIXED:
                    environmentBuilder.installScriptPath(installScriptPath);
                    break;
                case TEMPLATE:
                    environmentBuilder.generateInstallScript(silentInstallArgs, ignoreExitCode);
                    break;
                default:
                    throw new IllegalStateException("Unknown install script strategy: " + String.valueOf(strategy));
            }
        }
    }

    @Data
    public static class StartupFileSettings implements Serializable {
        private StartupFileStrategy strategy;
        private String startupFilePath;

        public StartupFileSettings(StartupFileStrategy strategy, String startupFilePath) {
            this.strategy = strategy;
            this.startupFilePath = startupFilePath;
        }

        public static StartupFileSettings fromJson(JsonOption.ObjectWrapper json) {
            String startupFileStrategyName = json.getString("value").orNull();
            StartupFileStrategy startupFileStrategy = StartupFileStrategy.valueOf(startupFileStrategyName);
            String startupFilePath = json.getString("startupFilePath").orNull();

            return new StartupFileSettings(startupFileStrategy, startupFilePath);
        }

        public void validate() {
            switch (strategy) {
                case FIXED:
                    checkState(!Strings.isNullOrEmpty(startupFilePath), String.format(REQUIRE_NOT_NULL_OR_EMPTY_S, "startupFilePath"));
                    break;
                case STUDIO:
                default:
                    break;
            }
        }

        public Optional<String> getStartupFile() {
            switch (strategy) {
                case FIXED:
                    return Optional.of(startupFilePath);
                case STUDIO:
                    return Optional.absent();
                default:
                    throw new IllegalStateException("Unknown startup file strategy: " + String.valueOf(strategy));
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public static final String DEFAULT_VAGRANT_BOX = "opentable/win-2012r2-standard-amd64-nocm";
        private static final Validator<File> HOST_FILE_PATH_VALIDATOR;
        private static final Validator<File> HOST_DIR_PATH_VALIDATOR;
        private static final Validator<String> VAGRANT_DEFAULT_BOX_VALIDATOR;
        private static final Validator<String> VAGRANT_BOX_VALIDATOR;
        private static final Validator<String> VIRTUAL_FILE_PATH_VALIDATOR;
        private static final Validator<String> SILENT_INSTALL_ARGS_VALIDATOR;
        private static final Validator<String> VIRTUAL_PATHS_TO_DELETE_VALIDATOR;
        private static final Validator<String> DEPENDENCY_VALIDATOR;

        private static final Pattern SPLIT_DEPENDENCIES_PATTERN = Pattern.compile("/s+|,|;");

        static {
            HOST_FILE_PATH_VALIDATOR = Validators.chain(
                    FileValidators.exists(String.format(DOES_NOT_EXIST_S, "File")),
                    FileValidators.isFile(String.format(PATH_NOT_POINT_TO_ITEM_S, "a file")),
                    FileValidators.isPathAbsolute(PATH_SHOULD_BE_ABSOLUTE, Level.WARNING)
            );
            HOST_DIR_PATH_VALIDATOR = Validators.chain(
                    FileValidators.exists(String.format(DOES_NOT_EXIST_S, "Directory")),
                    FileValidators.isDirectory(String.format(PATH_NOT_POINT_TO_ITEM_S, "a directory")),
                    FileValidators.isPathAbsolute(PATH_SHOULD_BE_ABSOLUTE, Level.WARNING)
            );
            VIRTUAL_FILE_PATH_VALIDATOR =
                    StringValidators.isNotNull(String.format(REQUIRE_NON_EMPTY_STRING_S, "Parameter"), Level.ERROR);
            VAGRANT_DEFAULT_BOX_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(String.format(REQUIRE_NON_EMPTY_STRING_S, "Parameter"), Level.WARNING),
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Parameter")));
            VAGRANT_BOX_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(String.format("Empty value will be replaced by a default box from global configuration: %s", DEFAULT_VAGRANT_BOX), Level.OK),
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Parameter")));
            SILENT_INSTALL_ARGS_VALIDATOR = StringValidators.isNotNull(String.format(IGNORE_PARAMETER, "Parameter"), Level.OK);
            VIRTUAL_PATHS_TO_DELETE_VALIDATOR = new VirtualPathsToDeleteValidator();
            DEPENDENCY_VALIDATOR = StringValidators.isNotNull(String.format(IGNORE_PARAMETER, "Parameter"), Level.OK);
        }

        @Getter
        private String xStudioPath;

        @Getter
        private String xStudioLicensePath;

        private String vagrantBox;

        public DescriptorImpl() {
            super(SnapshotBuilder.class);

            this.load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JsonOption.ObjectWrapper jsonWrapper = JsonOption.wrap(json);
            xStudioPath = jsonWrapper.getString("xStudioPath").orNull();
            xStudioLicensePath = jsonWrapper.getString("xStudioLicensePath").orNull();
            vagrantBox = jsonWrapper.getString("vagrantBox").or(DEFAULT_VAGRANT_BOX);

            save();

            return super.configure(req, json);
        }

        @Override
        public SnapshotBuilder newInstance(StaplerRequest req, JSONObject json)
                throws FormException {
            JsonOption.ObjectWrapper jsonWrapper = JsonOption.wrap(json);
            String vagrantBoxToUse = jsonWrapper.getString("vagrantBox").orNull();
            if (Strings.isNullOrEmpty(vagrantBoxToUse)) {
                vagrantBoxToUse = getVagrantBox();
            }
            String preInstallScriptPath = jsonWrapper.getString("preInstallScriptPath").orNull();
            String postSnapshotScriptPath = jsonWrapper.getString("postSnapshotScriptPath").orNull();
            String resourceDirectoryPath = jsonWrapper.getString("resourceDirectoryPath").orNull();
            boolean overwrite = jsonWrapper.getBoolean("overwrite").or(Boolean.FALSE);
            Collection<String> dependencies = extractDependencies(jsonWrapper.getString("dependencies").orNull());
            Collection<String> snapshotPathsToDelete = extractVirtualFilePaths(jsonWrapper.getString("snapshotPathsToDelete").orNull());
            InstallScriptSettings installSettings = InstallScriptSettings.fromJson(jsonWrapper.getObject("installScriptStrategy").orNull());
            StartupFileSettings startupFileSettings = StartupFileSettings.fromJson(jsonWrapper.getObject("startupFileStrategy").orNull());
            return new SnapshotBuilder(
                    getXStudioPath(),
                    getXStudioLicensePath(),
                    vagrantBoxToUse,
                    overwrite,
                    preInstallScriptPath,
                    postSnapshotScriptPath,
                    resourceDirectoryPath,
                    dependencies,
                    snapshotPathsToDelete,
                    installSettings,
                    startupFileSettings);
        }

        public FormValidation doCheckRequiredFilePath(@QueryParameter String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            if (filePath == null) {
                return FormValidation.error(String.format(REQUIRE_NON_EMPTY_STRING_S, "Parameter"));
            }
            return Validators.validate(HOST_FILE_PATH_VALIDATOR, new File(filePath));
        }

        public FormValidation doCheckOptionalFilePath(@QueryParameter String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            if (filePath == null) {
                return FormValidation.ok(String.format(IGNORE_PARAMETER, "Parameter"));
            }
            return Validators.validate(HOST_FILE_PATH_VALIDATOR, new File(filePath));
        }

        public FormValidation doCheckOptionalDirectoryPath(@QueryParameter String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            if (filePath == null) {
                return FormValidation.ok(String.format(IGNORE_PARAMETER, "Parameter"));
            }
            return Validators.validate(HOST_DIR_PATH_VALIDATOR, new File(filePath));
        }

        public AutoCompletionCandidates doAutoCompleteXStudioPath(@QueryParameter String value) {
            return suggestFiles(value);
        }

        public AutoCompletionCandidates doAutoCompleteXStudioLicensePath(@QueryParameter String value) {
            return suggestFiles(value);
        }

        public AutoCompletionCandidates doAutoCompleteInstallScriptPath(@QueryParameter String value) {
            return suggestFiles(value);
        }

        public AutoCompletionCandidates doAutoCompletePostSnapshotScriptPath(@QueryParameter String value) {
            return suggestFiles(value);
        }

        public AutoCompletionCandidates doAutoCompletePreInstallScriptPath(@QueryParameter String value) {
            return suggestFiles(value);
        }

        public AutoCompletionCandidates doAutoCompleteResourceDirectoryPath(@QueryParameter String value) {
            return suggestDirectories(value);
        }

        public FormValidation doCheckVirtualFilePath(@QueryParameter String value) {
            String virtualFilePath = Util.fixEmptyAndTrim(value);
            return Validators.validate(VIRTUAL_FILE_PATH_VALIDATOR, virtualFilePath);
        }

        public FormValidation doCheckSnapshotPathsToDelete(@QueryParameter String value) {
            String filePathsToDelete = Util.fixEmptyAndTrim(value);
            return Validators.validate(VIRTUAL_PATHS_TO_DELETE_VALIDATOR, filePathsToDelete);
        }

        public FormValidation doCheckDefaultVagrantBox(@QueryParameter String value) {
            String vagrantBox = Util.fixEmptyAndTrim(value);
            return Validators.validate(VAGRANT_DEFAULT_BOX_VALIDATOR, vagrantBox);
        }

        public FormValidation doCheckVagrantBox(@QueryParameter String value) {
            String vagrantBox = Util.fixEmptyAndTrim(value);
            return Validators.validate(VAGRANT_BOX_VALIDATOR, vagrantBox);
        }

        public FormValidation doCheckSilentInstallArgs(@QueryParameter String value) {
            String silentInstallArgs = Util.fixEmptyAndTrim(value);
            return Validators.validate(SILENT_INSTALL_ARGS_VALIDATOR, silentInstallArgs);
        }

        public FormValidation doCheckDependencies(@QueryParameter String value) {
            String dependencies = Util.fixEmptyAndTrim(value);
            return Validators.validate(DEPENDENCY_VALIDATOR, dependencies);
        }

        public String defaultSilentInstallArgs() {
            return "/S";
        }

        public String defaultSnapshotPathsToDelete() {
            return "@SYSDRIVE@\\tmp" + System.lineSeparator() + "@SYSDRIVE@\\vagrant";
        }

        public String getVagrantBox() {
            if (Strings.isNullOrEmpty(vagrantBox)) {
                return DEFAULT_VAGRANT_BOX;
            }
            return vagrantBox;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Take Studio snapshot";
        }

        private Collection<String> extractDependencies(@Nullable String dependencies) {
            String dependenciesToUse = Util.fixEmptyAndTrim(dependencies);

            if (dependenciesToUse == null) {
                return Collections.emptyList();
            }

            String[] segments = SPLIT_DEPENDENCIES_PATTERN.split(dependenciesToUse);
            ArrayList<String> result = new ArrayList<String>(segments.length);
            for (String segment : segments) {
                String segmentToUse = segment.trim();
                if (segmentToUse.length() > 0) {
                    result.add(segmentToUse);
                }
            }

            return result;
        }

        private static Collection<String> extractVirtualFilePaths(@Nullable String filePathList) {
            if (filePathList == null) {
                return Collections.emptyList();
            }

            String[] virtualFilePaths = filePathList.split("\r?\n");
            ArrayList<String> filePaths = new ArrayList<String>(virtualFilePaths.length);
            for (String filePath : virtualFilePaths) {
                String filePathToUse = Util.fixEmptyAndTrim(filePath);
                if (filePathToUse != null) {
                    filePaths.add(filePathToUse);
                }
            }
            return filePaths;
        }

        private static class VirtualPathsToDeleteValidator implements Validator<String> {
            @Override
            public void validate(String value) throws ValidationException {
                if (value == null) {
                    return;
                }

                Collection<String> filePaths = extractVirtualFilePaths(value);
                for (String filePath : filePaths) {
                    if (Paths.get(filePath).isAbsolute()) {
                        FormValidation formValidation = FormValidation.error("Absolute paths are forbidden to avid collisions with file system on host machine." +
                                " Use Sandbox Locations like @SYSDRIVE@, @APPDATA@, @PROGRAMFILES@ or @PROGRAMFILESX86@.");
                        throw new ValidationException(formValidation);
                    }
                }
            }
        }
    }
}
