package org.jenkinsci.plugins.spoontrigger;

import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.CheckCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.scheduledtasks.ScheduledTasksApi;
import org.jenkinsci.plugins.spoontrigger.validation.Level;
import org.jenkinsci.plugins.spoontrigger.validation.StringValidators;
import org.jenkinsci.plugins.spoontrigger.validation.Validator;
import org.jenkinsci.plugins.spoontrigger.validation.Validators;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public class ImageCheckBuilder extends BaseBuilder {

    private String exitCode;
    private String bootstrapTime;
    private String launchTime;
    private boolean isConsoleApp;
    private boolean hasChildProcesses;

    @DataBoundConstructor
    public ImageCheckBuilder(String exitCode, String bootstrapTime, String launchTime, boolean isConsoleApp, boolean hasChildProcesses) {
        this.exitCode = Util.fixEmptyAndTrim(exitCode);
        this.bootstrapTime = Util.fixEmptyAndTrim(bootstrapTime);
        this.launchTime = Util.fixEmptyAndTrim(launchTime);
        this.isConsoleApp = isConsoleApp;
        this.hasChildProcesses = hasChildProcesses;
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
                .image(image)
                .exitCode(exitCode)
                .consoleApp(isConsoleApp)
                .launchDuration(launchTime)
                .bootstrapDuration(bootstrapTime)
                .hasChildProcess(hasChildProcesses);

        return cmdBuilder.build();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private static final Validator<String> IGNORE_NULL_VALIDATOR = StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK);
        private static final Validator<String> TIME_SPAN_VALIDATOR = Validators.chain(IGNORE_NULL_VALIDATOR, StringValidators.isTimeSpanFormat(String.format(REQUIRE_VALID_FORMAT_S, "Time span")));
        private static final Validator<String> EXIT_CODE_VALIDATOR = Validators.chain(IGNORE_NULL_VALIDATOR, StringValidators.isInteger(String.format(REQUIRE_VALID_FORMAT_S, "Exit code")));

        public FormValidation doCheckTimeSpan(@QueryParameter String value) {
            String value_to_use = Util.fixEmptyAndTrim(value);
            return Validators.validate(TIME_SPAN_VALIDATOR, value_to_use);
        }

        public FormValidation doCheckExitCode(@QueryParameter String value) {
            String value_to_use = Util.fixEmptyAndTrim(value);
            return Validators.validate(EXIT_CODE_VALIDATOR, value_to_use);
        }

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
