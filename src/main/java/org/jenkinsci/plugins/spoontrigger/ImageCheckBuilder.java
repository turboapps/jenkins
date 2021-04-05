package org.jenkinsci.plugins.spoontrigger;

import com.google.common.io.Closeables;
import com.google.common.io.Resources;
import com.google.common.reflect.TypeToken;
import hudson.*;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public class ImageCheckBuilder extends BaseBuilder {

    private String exitCode;
    private String bootstrapTime;
    private String launchTime;
    private boolean isConsoleApp;
    private boolean hasChildProcesses;

    public String getExitCode() {
        return exitCode;
    }

    public String getBootstrapTime() {
        return bootstrapTime;
    }

    public String getLaunchTime() {
        return launchTime;
    }

    public boolean isConsoleApp() {
        return isConsoleApp;
    }

    public boolean isHasChildProcesses() {
        return hasChildProcesses;
    }

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
        Image outputImage = build.getOutputImage().orNull();
        checkState(outputImage != null, REQUIRE_OUTPUT_IMAGE);

        // run `turbo check`
        File commandScript = File.createTempFile("turbocheck", ".ps1", null);
        File initScript = File.createTempFile("turbocheck", ".ps1", null);
        try {
            // write init script (executed on jenkins system)
            String workspacePath = Paths.get(build.getWorkspace().getRemote(), "commandWorkspace").toString();
            String imageToTestPath = Paths.get(workspacePath, "image.svm").toString();
            FileWriter scriptWriter = new FileWriter(initScript);
            scriptWriter.write("& turbo export " + outputImage.printIdentifier() + " \"" + imageToTestPath + "\""); // export image to test to the workspace
            scriptWriter.close();

            // write test script (executed on vm system)
            CheckCommand command = createCheckCommand(outputImage);
            scriptWriter = new FileWriter(commandScript);
            scriptWriter.write("& turbo import svm x:\\image.svm -n=" + outputImage.printIdentifier() + " \n"); // import image
            scriptWriter.write("& " + command.argumentList.toString() + "\n"); // execute script to run test
            scriptWriter.write("$exitCode = $LastExitCode \n"); // save off the command's exit code so that we can return it rather than whatever subsequent commands may have
            scriptWriter.write("Copy-Item *.png output\\ \n"); // copy screenshots to workspace output directory
            scriptWriter.write("Copy-Item $env:LOCALAPPDATA\\turbo\\logs\\* output\\ \n"); // copy screenshots to workspace output directory
            scriptWriter.write("Exit $exitCode \n");
            scriptWriter.close();

            // build command to execute command script
            String commandExecutionScript = copyResourceToWorkspace(build.getWorkspace().getRemote(), "executeVboxCommand.ps1");
            String commandRunnerScript = copyResourceToWorkspace(build.getWorkspace().getRemote(), "executeVboxCommandRunner.ps1");

            ArgumentListBuilder argList = new ArgumentListBuilder();
            argList.addTokenized("Powershell -File ");
            argList.add(commandExecutionScript,
                    commandScript.getPath(),
                    commandRunnerScript,
                    initScript.getPath(),
                    workspacePath,
                    "Turbo10x64" /* vm name which contains "executeCommand" snapshot */ );
            ArgumentListBuilder winCommand = argList.toWindowsCommand();

            Launcher.ProcStarter procStarter = launcher.new ProcStarter();
            procStarter = procStarter.cmds(winCommand).stdout(listener.getLogger());
            procStarter = procStarter.pwd(build.getWorkspace()).envs(build.getEnvironment(listener));
            Proc proc = launcher.launch(procStarter);
            int exitCode = proc.join();

            listener.getLogger().println("Exit code: " + exitCode);

            return (exitCode == 0);
        }
        finally {
            commandScript.delete();
            initScript.delete();
        }
    }

    private String copyResourceToWorkspace(String workspacePath, String fileName) throws IOException {
        Path resourceOutputPath = Paths.get(workspacePath, fileName);
        URL resourceId = Resources.getResource(getClass(), fileName);
        FileOutputStream fileOutputStream = new FileOutputStream(resourceOutputPath.toFile());
        try {
            Resources.copy(resourceId, fileOutputStream);
        } finally {
            final boolean swallowIoException = true;
            Closeables.close(fileOutputStream, swallowIoException);
        }

        return resourceOutputPath.toString();
    }

    private CheckCommand createCheckCommand(Image image) {
        String screenshotDir = TurboTool.getDefaultInstallation().screenshotDir;
        checkState(screenshotDir != null, REQUIRE_SCREENSHOT_DIR);

        CheckCommand.CommandBuilder cmdBuilder = CheckCommand.builder()
                //.screenshotPath(screenshotDir)
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
