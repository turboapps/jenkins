package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.spoontrigger.commands.BaseCommand;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ModelCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.PushModelCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.scheduledtasks.ScheduledTasksApi;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.IGNORE_PARAMETER;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_OUTPUT_IMAGE;
import static org.jenkinsci.plugins.spoontrigger.utils.FileUtils.deleteDirectoryTree;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class ModelBuilder extends BaseBuilder {

    private static final String TRANSCRIPT_DIR = "transcripts";
    private static final String MODEL_DIR = "model";

    @Nullable
    private final PushGuardSettings pushGuardSettings;
    private final String hubUrls;

    @DataBoundConstructor
    public ModelBuilder(@Nullable PushGuardSettings pushGuardSettings, @Nullable String hubUrls) {
        this.pushGuardSettings = pushGuardSettings;
        this.hubUrls = hubUrls;
    }
    public String getHubUrls() {
        return hubUrls;
    }

    @Nullable
    public PushGuardSettings getPushGuardSettings() {
        return pushGuardSettings;
    }

    public String getMinBufferSize() {
        if (pushGuardSettings == null) {
            return null;
        }

        Double minBufferSizeMB = this.pushGuardSettings.minBufferSizeMB.orNull();
        return minBufferSizeMB != null ? minBufferSizeMB.toString() : null;
    }

    @Override
    public boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        Image outputImage = build.getOutputImage().orNull();
        checkState(outputImage != null, REQUIRE_OUTPUT_IMAGE);

        Path tempDir = Files.createTempDirectory(Paths.get("C:/CI/Temp"),"jenkins-model-" + build.getSanitizedProjectName() + "-build-");
        try {
            ModelWorker worker = new ModelWorker(tempDir, build, launcher, listener, hubUrlsAsList());
            worker.buildModel();
        }
        catch (Exception e) {
            Result currentResult = build.getResult();
            if (currentResult == null || currentResult.isBetterThan(Result.UNSTABLE)) {
                build.setResult(Result.UNSTABLE);
                log(listener, "[ModelBuilder]"+e.getMessage());
            }
        }
        finally {
            deleteDirectoryTree(tempDir);
        }
        return true;
    }

    private List<String> hubUrlsAsList() {
        List<String> result = new ArrayList<String>();
        if(this.hubUrls != null) {
            for(String url: this.hubUrls.split(",")) {
                result.add(url.trim());
            }
        }
        return result;
    }

    private class ModelWorker {
        private final Path workingDir;
        private final Path transcriptDir;
        private final Path modelDir;

        private final SpoonBuild build;
        private final Image image;
        private final BuildListener listener;
        private final CommandDriver driver;
        private final ScheduledTasksApi tasksApi;
        private final List<String> hubUrls;

        public ModelWorker(Path workingDirectory, SpoonBuild build, Launcher launcher, BuildListener listener, List<String> hubUrls) {
            this.workingDir = workingDirectory;
            this.transcriptDir = Paths.get(workingDirectory.toString(), TRANSCRIPT_DIR).toAbsolutePath();
            this.modelDir = Paths.get(workingDirectory.toString(), MODEL_DIR).toAbsolutePath();

            this.build = build;
            this.image = build.getOutputImage().get();
            this.listener = listener;
            this.hubUrls = hubUrls;

            this.driver = CommandDriver.builder(build).launcher(launcher).listener(listener).build();
            final boolean quiet = true;
            this.tasksApi = new ScheduledTasksApi(
                    build.getEnv().get(),
                    new FilePath(workingDir.toFile()),
                    build.getCharset(),
                    launcher,
                    listener,
                    quiet);
        }

        public void buildModel() throws IOException, InterruptedException {
            profile();
            model();
            if (shouldPush()) {
                push();
            } else {
                Result currentResult = build.getResult();
                if (currentResult == null || currentResult.isBetterThan(Result.UNSTABLE)) {
                    build.setResult(Result.UNSTABLE);
                }
            }
        }

        private void push() {
            PushModelCommand.CommandBuilder builder = PushModelCommand.builder()
                    .localImage(image.printIdentifier())
                    .modelDirectory(modelDir.toString());

            Optional<Image> remoteImage = build.getRemoteImage();
            if (remoteImage.isPresent()) {
                builder.remoteImage(remoteImage.get().printIdentifier());
            }

            if(hubUrls.isEmpty()) {
                PushModelCommand pushModelCommand = builder.build();
                pushModelCommand.run(driver);
            } else {
                for(String hubUrl : hubUrlsAsList()) {
                    switchHub(driver, hubUrl, build);

                    PushModelCommand pushModelCommand = builder.build();
                    pushModelCommand.run(driver);
                }
            }
        }

        private void model() {
            ModelCommand modelCommand = ModelCommand.builder().image(image.printIdentifier())
                    .transcriptDirectory(transcriptDir.toString())
                    .modelDirectory(modelDir.toString())
                    .build();
            modelCommand.run(driver);
        }

        private long getBufferSize() {
            File prefetchFile = Paths.get(modelDir.toString(), "p.xs").toFile();
            if (prefetchFile.exists()) {
                return prefetchFile.length();
            }
            return 0L;
        }

        private boolean shouldPush() {
            if (pushGuardSettings == null) {
                return true;
            }

            Optional<Double> minBufferSizeMB = pushGuardSettings.minBufferSizeMB;
            if (minBufferSizeMB.isPresent()) {
                final long minBufferSize = Double.valueOf(minBufferSizeMB.get() * 1024 * 1024).longValue();
                final long actualBufferSize = getBufferSize();
                if (actualBufferSize < minBufferSize) {
                    double actualBufferSizeInMB = (double) actualBufferSize / (1024.0 * 1024.0);
                    String msg = String.format("Buffer size of %.2f MB is below the specified limit", actualBufferSizeInMB);
                    log(listener, msg);
                    return false;
                }
            }
            return true;
        }

        /**
         * Profiling is implemented using scheduled tasks, because Jenkins build agent is running as a service without access to user interface.
         */
        private void profile() throws IOException, InterruptedException {
            String taskName = getProjectId(build);
            if (tasksApi.isDefined(taskName)) {
                tasksApi.delete(taskName);
            }

            ArgumentListBuilder profileCommand = new ArgumentListBuilder(BaseCommand.SPOON_CLIENT)
                    .add("profile")
                    .add("--mode=quiet")
                    .add("--isolate=full")
                    .add(image.printIdentifier())
                    .add(transcriptDir);

            log(listener, workingDir, profileCommand);
            tasksApi.create(taskName, profileCommand.toString());
            try {
                final int MaxAttempts = 60;
                final int SleepTime = 5000;

                tasksApi.run(taskName);

                boolean isProfileRunning = true;
                for (int attempt = 1; attempt <= MaxAttempts && isProfileRunning; ++attempt) {
                    Thread.sleep(SleepTime);
                    isProfileRunning = tasksApi.isRunning(taskName);
                }

                checkState(!isProfileRunning, "Profiling is running too long");
                checkState(isTranscriptSaved(), "Transcript files not found in directory %s", transcriptDir);
            } finally {
                try {
                    tasksApi.delete(taskName);
                } catch (Exception ex) {
                    String errMsg = String.format("Failed to delete scheduled task %s: %s", taskName, ex.getMessage());
                    log(listener, errMsg);
                }
            }
        }

        private boolean isTranscriptSaved() {
            String files[] = transcriptDir.toFile().list();
            if (files == null) {
                return false;
            }

            for (String filename : files) {
                String extension = com.google.common.io.Files.getFileExtension(filename);
                if ("xt".equals(extension)) {
                    return true;
                }
            }
            return false;
        }
    }

    private String getProjectId(SpoonBuild build) {
        SpoonProject project = build.getParent();
        String projectName = project.getName();
        return projectName.toLowerCase(Locale.ROOT);
    }

    public static final class PushGuardSettings {
        public final Optional<Double> minBufferSizeMB;

        @DataBoundConstructor
        public PushGuardSettings(String minBufferSize) {
            this.minBufferSizeMB = parseDouble(minBufferSize);
        }

        private Optional<Double> parseDouble(String value) {
            final String valueToParse = Util.fixEmptyAndTrim(value);
            if (valueToParse == null) {
                return Optional.absent();
            }

            try {
                return Optional.of(Double.valueOf(valueToParse));
            } catch (NumberFormatException ex) {
                return Optional.absent();
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private static final Validator<String> NULL_OR_POSITIVE_FLOATING_POINT_NUMBER;
        private static final Validator<String> HUB_URLS_VALIDATOR =
                Validators.chain(StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK));

        static {
            NULL_OR_POSITIVE_FLOATING_POINT_NUMBER = Validators.chain(
                    StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK),
                    new PositiveFloatingPointNumberValidator()
            );
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Build Turbo model";
        }

        public FormValidation doCheckMinBufferSize(@QueryParameter String value) {
            String minBufferSize = Util.fixEmptyAndTrim(value);
            return Validators.validate(NULL_OR_POSITIVE_FLOATING_POINT_NUMBER, minBufferSize);
        }

        public FormValidation doCheckHubUrls(@QueryParameter String value) {
            String hubUrls = Util.fixEmptyAndTrim(value);
            return Validators.validate(HUB_URLS_VALIDATOR, hubUrls);
        }

        private static class PositiveFloatingPointNumberValidator implements Validator<String> {

            @Override
            public void validate(String value) throws ValidationException {
                try {
                    double parsedValue = Double.parseDouble(value);
                    if (parsedValue <= 0.0) {
                        FormValidation formValidation = FormValidation.error("Negative values or zero are not allowed");
                        throw new ValidationException(formValidation);
                    }
                } catch (NumberFormatException ex) {
                    FormValidation formValidation = FormValidation.error("Value is not a valid number");
                    throw new ValidationException(formValidation);
                }
            }
        }
    }
}
