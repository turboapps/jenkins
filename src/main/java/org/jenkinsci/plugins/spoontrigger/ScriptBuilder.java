package org.jenkinsci.plugins.spoontrigger;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import lombok.Data;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.BuildCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ConfigCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.LoginCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.VersionCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.utils.AutoCompletion;
import org.jenkinsci.plugins.spoontrigger.utils.Credentials;
import org.jenkinsci.plugins.spoontrigger.utils.FileResolver;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class ScriptBuilder extends BaseBuilder {

    @Nullable
    @Getter
    private final String scriptFilePath;
    @Nullable
    @Getter
    private final String credentialsId;
    @Nullable
    @Getter
    private final String hubUrl;
    @Nullable
    @Getter
    private final String imageName;
    @Nullable
    @Getter
    private final String vmVersion;
    @Nullable
    @Getter
    private final String containerWorkingDir;

    @Nullable
    @Getter
    private final MountSettings mountSettings;

    @Getter
    private final boolean diagnostic;
    @Getter
    private final boolean noBase;
    @Getter
    private final boolean overwrite;

    @DataBoundConstructor
    public ScriptBuilder(String scriptFilePath, String credentialsId, String hubUrl, String imageName,
                         String vmVersion, String containerWorkingDir, @Nullable MountSettings mountSettings,
                         boolean noBase, boolean overwrite, boolean diagnostic) {
        this.scriptFilePath = Util.fixEmptyAndTrim(scriptFilePath);
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
        this.hubUrl = Util.fixEmptyAndTrim(hubUrl);
        this.imageName = Util.fixEmptyAndTrim(imageName);
        this.vmVersion = Util.fixEmptyAndTrim(vmVersion);
        this.containerWorkingDir = Util.fixEmptyAndTrim(containerWorkingDir);
        this.mountSettings = mountSettings;
        this.noBase = noBase;
        this.overwrite = overwrite;
        this.diagnostic = diagnostic;
    }

    private static Optional<String> toString(FilePath filePath) {
        try {
            return Optional.of(filePath.getRemote());
        } catch (Exception ex) {
            return Optional.absent();
        }
    }

    public String getSourceContainer() {
        return (this.mountSettings != null) ? this.mountSettings.getSourceContainer() : null;
    }

    public String getTargetFolder() {
        return (this.mountSettings != null) ? this.mountSettings.getTargetFolder() : null;
    }

    public String getSourceFolder() {
        return (this.mountSettings != null) ? this.mountSettings.getSourceFolder() : null;
    }

    @Override
    public void prebuild(SpoonBuild build, BuildListener listener) {
        checkState(build.getEnv().isPresent(), "Env is not defined");

        Optional<StandardUsernamePasswordCredentials> credentials = this.getCredentials();
        if (credentials.isPresent()) {
            build.setCredentials(credentials.get());
        }
        build.setAllowOverwrite(this.overwrite);

        FilePath scriptPath = this.resolveScriptFilePath(build, build.getEnv().get(), listener);
        build.setScript(scriptPath);

        this.checkMountSettings();
    }

    @Override
    public boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        CommandDriver client = CommandDriver.scriptBuilder(build).launcher(launcher).listener(listener).ignoreErrorCode(true).build();

        checkSpoonPluginIsRunning(client);

        switchHub(client);

        Optional<StandardUsernamePasswordCredentials> credentials = build.getCredentials();
        if (credentials.isPresent()) {
            login(client, credentials.get());
        }

        BuildCommand command = createBuildCommand(build.getScript().get());
        command.run(client);

        Optional<Image> outputImage = command.getOutputImage();
        if (outputImage.isPresent()) {
            build.setOutputImage(outputImage.get());
            return true;
        }

        log(listener, "Failed to find the output image in the build process output");
        if (shouldAbort(build, command)) {
            build.setResult(Result.ABORTED);
        }
        return false;
    }

    private boolean shouldAbort(SpoonBuild build, BuildCommand command) {
        Result currentResult = build.getResult();
        BuildCommand.BuildFailure buildFailure = command.getError();
        return (currentResult == null || currentResult.isBetterThan(Result.ABORTED))
                && BuildCommand.BuildFailure.ImageAlreadyExists.equals(buildFailure);
    }

    private void checkMountSettings() {
        if (this.mountSettings == null) {
            return;
        }

        this.mountSettings.checkMissing();
    }

    private void checkSpoonPluginIsRunning(CommandDriver client) {
        VersionCommand versionCmd = VersionCommand.builder().build();
        versionCmd.run(client);
    }

    private void switchHub(CommandDriver client) {
        ConfigCommand.CommandBuilder cmdBuilder = ConfigCommand.builder();
        if (Strings.isNullOrEmpty(this.hubUrl)) {
            cmdBuilder.reset(true);
        } else {
            cmdBuilder.hub(this.hubUrl);
        }

        ConfigCommand configCommand = cmdBuilder.build();
        configCommand.run(client);
    }

    private void login(CommandDriver client, StandardUsernamePasswordCredentials credentials) {
        LoginCommand loginCmd = LoginCommand.builder().login(credentials.getUsername()).password(credentials.getPassword()).build();
        loginCmd.run(client);
    }

    private BuildCommand createBuildCommand(FilePath scriptPath) {
        BuildCommand.CommandBuilder cmdBuilder = BuildCommand.builder().script(scriptPath);
        if (this.imageName != null) {
            cmdBuilder.image(this.imageName);
        }

        if (this.vmVersion != null) {
            cmdBuilder.vmVersion(this.vmVersion);
        }

        if (this.containerWorkingDir != null) {
            cmdBuilder.containerWorkingDir(this.containerWorkingDir);
        }

        if (this.mountSettings != null) {
            this.mountSettings.fill(cmdBuilder);
        }

        cmdBuilder.diagnostic(this.diagnostic);
        cmdBuilder.overwrite(this.overwrite);
        cmdBuilder.noBase(this.noBase);

        return cmdBuilder.build();
    }

    private Optional<StandardUsernamePasswordCredentials> getCredentials() throws IllegalStateException {
        if (Strings.isNullOrEmpty(this.credentialsId)) {
            return Optional.absent();
        }

        Optional<StandardUsernamePasswordCredentials> credentials = Credentials.lookupById(StandardUsernamePasswordCredentials.class, this.credentialsId);

        checkState(credentials.isPresent(), "Cannot find any credentials with id (%s)", this.credentialsId);

        return credentials;
    }

    private FilePath resolveScriptFilePath(AbstractBuild build, EnvVars environment, BuildListener listener) throws IllegalStateException {
        checkState(Util.fixEmptyAndTrim(this.scriptFilePath) != null, REQUIRE_NON_EMPTY_STRING_S, "script file path");

        Optional<FilePath> scriptFile = FileResolver.create()
                .env(environment).build(build).listener(listener)
                .probingStrategy(FileResolver.Probe.WORKING_DIR, FileResolver.Probe.MODULE, FileResolver.Probe.WORKSPACE)
                .resolve(this.scriptFilePath);

        if (scriptFile.isPresent()) {
            return scriptFile.get();
        }

        String msg = String.format("Failed to find the script file in build workspace (%s) and root module (%s)."
                        + " If the script file path (%s) is correct check build logs why it was not found.",
                toString(build.getWorkspace()).or(FAILED_RESOLVE_PLACEHOLDER),
                toString(build.getModuleRoot()).or(FAILED_RESOLVE_PLACEHOLDER),
                this.scriptFilePath);
        throw new IllegalStateException(msg);
    }

    @Data
    public static final class MountSettings {

        private final String sourceContainer;
        private final String sourceFolder;
        private final String targetFolder;

        @DataBoundConstructor
        public MountSettings(String sourceContainer, String sourceFolder, String targetFolder) {
            this.sourceContainer = Util.fixEmptyAndTrim(sourceContainer);
            this.sourceFolder = Util.fixEmptyAndTrim(sourceFolder);
            this.targetFolder = Util.fixEmptyAndTrim(targetFolder);
        }

        private static IllegalStateException onMountFolderMissing(String folderName) {
            String errMsg = String.format(REQUIRE_NON_EMPTY_STRING_S, folderName);
            return new IllegalStateException(errMsg);
        }

        public void checkMissing() {
            if (this.sourceFolder == null) {
                throw onMountFolderMissing("Source folder");
            }

            if (this.targetFolder == null) {
                throw onMountFolderMissing("Target folder");
            }
        }

        public void fill(BuildCommand.CommandBuilder cmdBuilder) {
            if (this.sourceContainer != null) {
                cmdBuilder.mount(this.sourceContainer, this.sourceFolder, this.targetFolder);
            } else {
                cmdBuilder.mount(this.sourceFolder, this.targetFolder);
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final Validator<String> IGNORE_NULL_VALIDATOR;
        private static final Validator<String> SCRIPT_FILE_PATH_STRING_VALIDATOR;
        private static final Validator<File> SCRIPT_FILE_PATH_FILE_VALIDATOR;
        private static final Validator<String> VERSION_NUMBER_VALIDATOR;
        private static final Validator<String> CREDENTIALS_ID_VALIDATOR;
        private static final Validator<String> NULL_OR_SINGLE_WORD_VALIDATOR;

        static {
            IGNORE_NULL_VALIDATOR = StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK);
            SCRIPT_FILE_PATH_STRING_VALIDATOR = StringValidators.isNotNull(REQUIRED_PARAMETER, Level.ERROR);
            SCRIPT_FILE_PATH_FILE_VALIDATOR = Validators.chain(
                    FileValidators.isPathAbsolute(EMPTY, Level.OK),
                    FileValidators.exists(String.format(DOES_NOT_EXIST_S, "File")),
                    FileValidators.isFile(String.format(PATH_NOT_POINT_TO_ITEM_S, "a file"))
            );
            VERSION_NUMBER_VALIDATOR = Validators.chain(
                    IGNORE_NULL_VALIDATOR,
                    StringValidators.isVersionNumber());
            CREDENTIALS_ID_VALIDATOR = StringValidators.isNotNull("Credentials are required to login to a Turbo account", Level.WARNING);
            NULL_OR_SINGLE_WORD_VALIDATOR = Validators.chain(
                    IGNORE_NULL_VALIDATOR,
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Parameter")));
        }

        private static boolean doNotHasPermissions(Item project) {
            return project == null || !project.hasPermission(Item.CONFIGURE);
        }

        public FormValidation doCheckHubUrl(@QueryParameter String value) {
            String hubUrl = Util.fixEmptyAndTrim(value);
            return Validators.validate(NULL_OR_SINGLE_WORD_VALIDATOR, hubUrl);
        }

        public FormValidation doCheckScriptFilePath(@QueryParameter String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            try {
                SCRIPT_FILE_PATH_STRING_VALIDATOR.validate(filePath);
                File scriptFile = new File(filePath);
                SCRIPT_FILE_PATH_FILE_VALIDATOR.validate(scriptFile);
                return FormValidation.ok();
            } catch (ValidationException ex) {
                return ex.getFailureMessage();
            }
        }

        public AutoCompletionCandidates doAutoCompleteScriptFilePath(@QueryParameter String value) {
            return AutoCompletion.suggestFiles(value);
        }

        public FormValidation doCheckVmVersion(@QueryParameter String value) {
            String versionNumber = Util.fixEmptyAndTrim(value);
            return Validators.validate(VERSION_NUMBER_VALIDATOR, versionNumber);
        }

        public FormValidation doCheckContainerWorkingDir(@QueryParameter String value) {
            String workingDirectory = Util.fixEmptyAndTrim(value);
            return Validators.validate(IGNORE_NULL_VALIDATOR, workingDirectory);
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Item project, @QueryParameter String value) {
            if (doNotHasPermissions(project)) {
                return FormValidation.ok();
            }

            String credentialsId = Util.fixEmptyAndTrim(value);
            Validator<String> validator = Validators.chain(CREDENTIALS_ID_VALIDATOR, new CredentialValidator(project));
            return Validators.validate(validator, credentialsId);
        }

        public FormValidation doCheckImageName(@QueryParameter String value) {
            String imageName = Util.fixEmptyAndTrim(value);
            return Validators.validate(NULL_OR_SINGLE_WORD_VALIDATOR, imageName);
        }

        public FormValidation doCheckSourceContainer(@QueryParameter String value) {
            String sourceContainer = Util.fixEmptyAndTrim(value);
            return Validators.validate(NULL_OR_SINGLE_WORD_VALIDATOR, sourceContainer);
        }

        public FormValidation doCheckMountFolder(@QueryParameter String value) {
            String sourceFolder = Util.fixEmptyAndTrim(value);
            return Validators.validate(SCRIPT_FILE_PATH_STRING_VALIDATOR, sourceFolder);
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
            if (doNotHasPermissions(project)) {
                return new StandardListBoxModel();
            }

            List<StandardUsernamePasswordCredentials> projectCredentials = Credentials.lookupByItem(StandardUsernamePasswordCredentials.class, project);
            return new StandardListBoxModel().withEmptySelection().withAll(projectCredentials);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Execute TurboScript";
        }

        private static final class CredentialValidator implements Validator<String> {

            private final Item project;

            public CredentialValidator(Item project) {
                this.project = project;
            }

            @Override
            public void validate(String credentialsId) throws ValidationException {
                Optional<StandardUsernamePasswordCredentials> credentials = Credentials.lookupById(StandardUsernamePasswordCredentials.class, project, credentialsId);

                if (credentials.isPresent()) {
                    return;
                }

                String errMsg = String.format("Cannot find any credentials with id (%s)", credentialsId);
                FormValidation formValidation = FormValidation.warning(errMsg);
                throw new ValidationException(formValidation);
            }
        }
    }
}
