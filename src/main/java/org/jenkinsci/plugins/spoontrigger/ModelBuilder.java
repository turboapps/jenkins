package org.jenkinsci.plugins.spoontrigger;

import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.client.BaseCommand;
import org.jenkinsci.plugins.spoontrigger.client.ModelCommand;
import org.jenkinsci.plugins.spoontrigger.client.SpoonClient;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.schtasks.ScheduledTasksApi;
import org.jenkinsci.plugins.spoontrigger.utils.TaskListeners;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.requireInstanceOf;
import static org.jenkinsci.plugins.spoontrigger.utils.FileUtils.deleteDirectoryTree;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class ModelBuilder extends Builder {

    private static final String TRANSCRIPT_DIR = "transcripts";
    private static final String MODEL_DIR = "model";

    @DataBoundConstructor
    public ModelBuilder() {

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
            Image outputImage = build.getBuiltImage().orNull();
            checkState(outputImage != null, "Output image is not available in build information");

            Path tempDir = createTempDir(build);
            Path transcriptDir = Paths.get(tempDir.toString(), TRANSCRIPT_DIR).toAbsolutePath();
            Path modelDir = Paths.get(tempDir.toString(), MODEL_DIR).toAbsolutePath();
            try {
                profile(outputImage, transcriptDir, build, launcher, listener);

                SpoonClient client = SpoonClient.builder(build).launcher(launcher).listener(listener).build();
                model(client, outputImage, transcriptDir, modelDir);
            } finally {
                deleteDirectoryTree(tempDir);
            }

            return true;
        } catch (IllegalStateException ex) {
            TaskListeners.logFatalError(listener, ex);
            return false;
        }
    }

    private void model(SpoonClient client, Image image, Path transcriptDir, Path modelDir) {
        ModelCommand modelCommand = ModelCommand.builder().image(image.printIdentifier())
                .transcriptDirectory(transcriptDir.toString())
                .modelDirectory(modelDir.toString())
                .build();
        modelCommand.run(client);
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

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Build Turbo model";
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                return new ModelBuilder();
            } catch (JSONException ex) {
                throw new IllegalStateException("Error while parsing data form", ex);
            }
        }
    }
}
