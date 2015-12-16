package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import lombok.Data;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.commands.BaseCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ModelCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.PushModelCommand;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.schtasks.ScheduledTasksApi;
import org.jenkinsci.plugins.spoontrigger.utils.TaskListeners;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.IGNORE_PARAMETER;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_OUTPUT_IMAGE;
import static org.jenkinsci.plugins.spoontrigger.Messages.requireInstanceOf;
import static org.jenkinsci.plugins.spoontrigger.utils.FileUtils.deleteDirectoryTree;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class ModelBuilder extends Builder {

    private static final String TRANSCRIPT_DIR = "transcripts";
    private static final String MODEL_DIR = "model";

    @Nullable
    @Getter
    private final PushGuardSettings pushGuardSettings;

    @DataBoundConstructor
    public ModelBuilder(@Nullable PushGuardSettings pushGuardSettings) {
        this.pushGuardSettings = pushGuardSettings;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        checkArgument(build instanceof SpoonBuild, requireInstanceOf("build", SpoonBuild.class));

        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        try {
            SpoonBuild build = (SpoonBuild) abstractBuild;
            Image outputImage = build.getOutputImage().orNull();
            checkState(outputImage != null, REQUIRE_OUTPUT_IMAGE);

            Path tempDir = createTempDir(build);
            Path transcriptDir = Paths.get(tempDir.toString(), TRANSCRIPT_DIR).toAbsolutePath();
            Path modelDir = Paths.get(tempDir.toString(), MODEL_DIR).toAbsolutePath();
            try {
                profile(outputImage, transcriptDir, build, launcher, listener);
                CommandDriver client = CommandDriver.builder(build).launcher(launcher).listener(listener).build();
                model(client, outputImage, transcriptDir, modelDir);
                if (shouldPushModel(modelDir, listener)) {
                    pushModel(client, build, outputImage, modelDir);
                } else {
                    build.setResult(Result.UNSTABLE);
                }
            } finally {
                deleteDirectoryTree(tempDir);
            }

            return true;
        } catch (IllegalStateException ex) {
            TaskListeners.logFatalError(listener, ex);
            return false;
        }
    }

    private void pushModel(CommandDriver client, SpoonBuild build, Image localImage, Path modelDir) {
        PushModelCommand.CommandBuilder builder = PushModelCommand.builder()
                .localImage(localImage.printIdentifier())
                .modelDirectory(modelDir.toString());

        Optional<Image> remoteImage = build.getRemoteImage();
        if (remoteImage.isPresent()) {
            builder.remoteImage(remoteImage.get().printIdentifier());
        }

        PushModelCommand pushModelCommand = builder.build();
        pushModelCommand.run(client);
    }

    private void model(CommandDriver client, Image image, Path transcriptDir, Path modelDir) {
        ModelCommand modelCommand = ModelCommand.builder().image(image.printIdentifier())
                .transcriptDirectory(transcriptDir.toString())
                .modelDirectory(modelDir.toString())
                .build();
        modelCommand.run(client);
    }

    private long getBufferSize(Path modelDir) {
        File prefetchFile = Paths.get(modelDir.toString(), "p.xs").toFile();
        if (prefetchFile.exists()) {
            return prefetchFile.length();
        }
        return 0L;
    }

    private boolean shouldPushModel(Path modelDir, BuildListener listener) {
        if (pushGuardSettings == null) {
            return true;
        }

        Optional<Double> minBufferSizeMB = pushGuardSettings.getMinBufferSizeMB();
        if (minBufferSizeMB.isPresent()) {
            final long minBufferSize = Double.valueOf(minBufferSizeMB.get() * 1024 * 1024).longValue();
            final long actualBufferSize = getBufferSize(modelDir);
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
    private void profile(Image image, Path transcriptDir, SpoonBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        ScheduledTasksApi taskApi = ScheduledTasksApi.create(build, launcher);

        String taskName = getProjectId(build);
        if (taskApi.isDefined(taskName)) {
            taskApi.delete(taskName);
        }

        String command = BaseCommand.SPOON_CLIENT + " profile --mode=quiet --isolate=full "
                + image.printIdentifier() + " \\\"" + transcriptDir.toString() + "\\\"";
        taskApi.create(taskName, command);
        try {
            final int MaxAttempts = 60;
            final int SleepTime = 5000;

            taskApi.run(taskName);

            boolean isProfileRunning = true;
            for (int attempt = 1; attempt <= MaxAttempts && isProfileRunning; ++attempt) {
                Thread.sleep(SleepTime);
                isProfileRunning = taskApi.isRunning(taskName);
            }

            if (isProfileRunning) {
                throw new IllegalStateException("Profiling is running too long");
            }

            boolean isTranscriptSaved = false;
            for (String filename : transcriptDir.toFile().list()) {
                String extension = com.google.common.io.Files.getFileExtension(filename);
                if ("xt".equals(extension)) {
                    isTranscriptSaved = true;
                    break;
                }
            }

            checkState(isTranscriptSaved, "Transcript files not found in directory %s", transcriptDir);
        } finally {
            try {
                taskApi.delete(taskName);
            } catch (Exception ex) {
                String errMsg = String.format("Failed to delete scheduled task %s: %s", taskName, ex.getMessage());
                log(listener, errMsg);
            }
        }
    }

    private Path createTempDir(SpoonBuild build) throws IOException {
        final String prefix = "model-build-";

        String workspaceLocation = build.getWorkspace().getRemote();
        Path tempDir = (workspaceLocation == null) ? Files.createTempDirectory(prefix)
                : Files.createTempDirectory(Paths.get(workspaceLocation), prefix);
        return tempDir.toAbsolutePath();
    }

    private String getProjectId(SpoonBuild build) {
        SpoonProject project = build.getParent();
        String projectName = project.getName();
        return projectName.toLowerCase(Locale.ROOT);
    }

    @Data
    public static final class PushGuardSettings {
        @Getter
        private Optional<Double> minBufferSizeMB;

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
