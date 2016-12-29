package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ExportCommand;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.utils.AutoCompletion;
import org.jenkinsci.plugins.spoontrigger.utils.FileResolver;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public class ExportPublisher extends SpoonBasePublisher {


    private final String outputFile;

    private transient FilePath runtimeOutputFile;

    @DataBoundConstructor
    public ExportPublisher(String outputFile) {
        this.outputFile = Util.fixEmptyAndTrim(outputFile);
    }

    private static IllegalStateException onFailedResolveOutputFile(String filePath, Exception ex) {
        String msg = String.format(FAILED_RESOLVE_SP, "output file", filePath);
        return new IllegalStateException(msg, ex);
    }

    @Override
    public void beforePublish(SpoonBuild build, BuildListener listener) throws IllegalStateException {
        super.beforePublish(build, listener);

        this.runtimeOutputFile = this.resolveOutputFile(build, listener);
    }

    @Override
    public void publish(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener listener) throws IllegalStateException {
        CommandDriver client = super.createClient(abstractBuild, launcher, listener);
        ExportCommand exportCmd = this.createExportCommand();
        exportCmd.run(client);
    }

    private FilePath resolveOutputFile(SpoonBuild build, TaskListener listener) throws IllegalStateException {
        checkState(this.outputFile != null, REQUIRE_NOT_NULL_OR_EMPTY_S, "output file");

        Optional<EnvVars> env = build.getEnv();

        checkState(env.isPresent(), REQUIRE_PRESENT_S, "build environment variables");

        Optional<FilePath> outputFilePath = FileResolver.create().env(env.get()).build(build).listener(listener).resolve(this.outputFile);

        if (outputFilePath.isPresent()) {
            try {
                checkState(!outputFilePath.isPresent() || !outputFilePath.get().isDirectory(), PATH_NOT_POINT_TO_ITEM_SPS, "output file", this.outputFile, "a file");
            } catch (IOException ex) {
                throw onFailedResolveOutputFile(this.outputFile, ex);
            } catch (InterruptedException ex) {
                throw onFailedResolveOutputFile(this.outputFile, ex);
            }

            return outputFilePath.get();
        }

        String expandedFilepath = env.get().expand(this.outputFile);
        File outputFile = new File(expandedFilepath);
        return new FilePath(outputFile);
    }

    private ExportCommand createExportCommand() {
        return ExportCommand.builder().outputFile(this.runtimeOutputFile).image(this.getImage().get().printIdentifier()).build();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private static final Validator<File> OUTPUT_FILE_VALIDATOR;
        private static final Validator<String> OUTPUT_FILE_STRING_VALIDATOR;

        static {
            OUTPUT_FILE_VALIDATOR = Validators.chain(
                    FileValidators.isNotDirectory(String.format(PATH_NOT_POINT_TO_ITEM_S, "a file")),
                    FileValidators.notExist(String.format(EXIST_S, "File"), Level.WARNING),
                    FileValidators.isPathAbsolute(PATH_SHOULD_BE_ABSOLUTE, Level.WARNING));

            OUTPUT_FILE_STRING_VALIDATOR = StringValidators.isNotNull(REQUIRED_PARAMETER, Level.ERROR);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        public FormValidation doCheckOutputFile(@QueryParameter String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            try {
                OUTPUT_FILE_STRING_VALIDATOR.validate(filePath);
                File outputFile = new File(filePath);
                OUTPUT_FILE_VALIDATOR.validate(outputFile);
                return FormValidation.ok();
            } catch (ValidationException ex) {
                return ex.failureMessage;
            }
        }

        public AutoCompletionCandidates doAutoCompleteOutputFile(@QueryParameter String value) {
            return AutoCompletion.suggestFiles(value);
        }

        @Override
        public String getDisplayName() {
            return "Export Turbo image";
        }
    }
}
