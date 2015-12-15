package org.jenkinsci.plugins.spoontrigger;

import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Mailer;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.validation.FileValidators;
import org.jenkinsci.plugins.spoontrigger.validation.Level;
import org.jenkinsci.plugins.spoontrigger.validation.Validator;
import org.jenkinsci.plugins.spoontrigger.validation.Validators;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public class SnapshotBuilder extends Builder {

    private final String xStudioPath;
    private final String xStudioLicensePath;

    @DataBoundConstructor
    public SnapshotBuilder(String xStudioPath, String xStudioLicensePath) {
        this.xStudioPath = Util.fixEmptyAndTrim(xStudioPath);
        this.xStudioLicensePath = Util.fixEmptyAndTrim(xStudioLicensePath);
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        checkArgument(build instanceof SpoonBuild, requireInstanceOf("build", SpoonBuild.class));

        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return super.perform(build, launcher, listener);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final Validator<File> SCRIPT_FILE_PATH_FILE_VALIDATOR;

        static {
            SCRIPT_FILE_PATH_FILE_VALIDATOR = Validators.chain(
                    FileValidators.exists(String.format(DOES_NOT_EXIST_S, "File")),
                    FileValidators.isFile(String.format(PATH_NOT_POINT_TO_ITEM_S, "a file")),
                    FileValidators.isPathAbsolute(PATH_SHOULD_BE_ABSOLUTE, Level.WARNING)
            );
        }

        @Getter
        private String xStudioPath;

        @Getter
        private String xStudioLicensePath;

        public DescriptorImpl() {
            super(SnapshotBuilder.class);

            this.load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            if (json != null && !json.isNullObject()) {
                xStudioPath = json.getString("xStudioPath");
                xStudioLicensePath = json.getString("xStudioLicensePath");
            }
            
            save();

            return super.configure(req, json);
        }

        @Override
        public SnapshotBuilder newInstance(StaplerRequest req, JSONObject json)
                throws FormException {
            String xStudioPath = null;
            String xStudioLicensePath = null;
            if (json != null && !json.isNullObject()) {
                xStudioPath = json.getString("xStudioPath");
                xStudioLicensePath = json.getString("xStudioLicensePath");
            }
            return new SnapshotBuilder(xStudioPath, xStudioLicensePath);
        }

        public FormValidation doCheckXStudioLicensePath(@QueryParameter String value) {
            return validateOptionalFilePath(value);
        }

        public FormValidation doCheckXStudioPath(@QueryParameter String value) {
            return validateOptionalFilePath(value);
        }

        private FormValidation validateOptionalFilePath(String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            if (filePath == null) {
                return FormValidation.ok(IGNORE_PARAMETER);
            }

            return Validators.validate(SCRIPT_FILE_PATH_FILE_VALIDATOR, new File(filePath));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Take Studio snapshot";
        }
    }
}
