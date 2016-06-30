package org.jenkinsci.plugins.spoontrigger;

import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.CheckCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.scheduledtasks.ScheduledTasksApi;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_OUTPUT_IMAGE;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SCREENSHOT_DIR;

public class ImageCheckBuilder extends BaseBuilder {

    @DataBoundConstructor
    public ImageCheckBuilder() {
    }

    @Override
    protected boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ScheduledTasksApi scheduledTasksApi = new ScheduledTasksApi(build.getEnv().get(), build.getWorkspace(), build.getCharset(), launcher, listener, false);

        Image outputImage = build.getOutputImage().orNull();
        checkState(outputImage != null, REQUIRE_OUTPUT_IMAGE);

        CheckCommand command = createCheckCommand(outputImage);
        scheduledTasksApi.run("test_" + outputImage.getNamespace() + "_" + outputImage.getRepo(), command.getArgumentList().toString());

        return true;
    }

    private CheckCommand createCheckCommand(Image image) {
        String screenshotDir = TurboTool.getDefaultInstallation().getScreenshotDir();
        checkState(screenshotDir != null, REQUIRE_SCREENSHOT_DIR);

        CheckCommand.CommandBuilder cmdBuilder = CheckCommand.builder()
                .screenshotPath(screenshotDir)
                .image(image);

        return cmdBuilder.build();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Execute Validation Checks";
        }
    }
}
