package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.utils.JsonOption;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public class SnapshotBuilder extends Builder {

    private final String xStudioPath;
    private final String xStudioLicensePath;
    private final String vagrantBox;

    @DataBoundConstructor
    public SnapshotBuilder(String xStudioPath, String xStudioLicensePath, String vagrantBox) {
        this.xStudioPath = Util.fixEmptyAndTrim(xStudioPath);
        this.xStudioLicensePath = Util.fixEmptyAndTrim(xStudioLicensePath);
        this.vagrantBox = Util.fixEmptyAndTrim(vagrantBox);
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

        private static final String DEFAULT_VAGRANT_BOX = "opentable/win-2012r2-standard-amd64-nocm";
        private static final String DEFAULT_XSTUDIO_PATH = "xstudio";
        private static final Validator<File> SCRIPT_FILE_PATH_FILE_VALIDATOR;
        private static final Validator<String> VAGRANT_DEFAULT_BOX_VALIDATOR;
        private static final Validator<String> VAGRANT_BOX_VALIDATOR;

        static {
            SCRIPT_FILE_PATH_FILE_VALIDATOR = Validators.chain(
                    FileValidators.exists(String.format(DOES_NOT_EXIST_S, "File")),
                    FileValidators.isFile(String.format(PATH_NOT_POINT_TO_ITEM_S, "a file")),
                    FileValidators.isPathAbsolute(PATH_SHOULD_BE_ABSOLUTE, Level.WARNING)
            );
            VAGRANT_DEFAULT_BOX_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(String.format(REQUIRE_NON_EMPTY_STRING_S, "Parameter"), Level.WARNING),
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Parameter")));
            VAGRANT_BOX_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK),
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Parameter")));
        }

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
            xStudioPath = jsonWrapper.getString("xStudioPath").or(DEFAULT_XSTUDIO_PATH);
            xStudioLicensePath = jsonWrapper.getString("xStudioLicensePath").orNull();
            vagrantBox = jsonWrapper.getString("vagrantBox").or(DEFAULT_VAGRANT_BOX);

            save();

            return super.configure(req, json);
        }

        @Override
        public SnapshotBuilder newInstance(StaplerRequest req, JSONObject json)
                throws FormException {
            JsonOption.ObjectWrapper jsonWrapper = JsonOption.wrap(json);
            String vagrantBoxToUse = jsonWrapper.getString("vagrantBox").or(getVagrantBox());
            return new SnapshotBuilder(getXStudioPath(), getXStudioLicensePath(), vagrantBoxToUse);
        }

        public FormValidation doCheckXStudioLicensePath(@QueryParameter String value) {
            return validateOptionalFilePath(value);
        }

        public FormValidation doCheckXStudioPath(@QueryParameter String value) {
            String xStudioPath = Util.fixEmptyAndTrim(value);
            if (xStudioPath != null && xStudioPath.toLowerCase(Locale.ROOT).equals(DEFAULT_XSTUDIO_PATH)) {
                return FormValidation.ok();
            }
            return validateOptionalFilePath(value);
        }

        public FormValidation doCheckDefaultVagrantBox(@QueryParameter String value) {
            String vagrantBox = Util.fixEmptyAndTrim(value);
            return Validators.validate(VAGRANT_DEFAULT_BOX_VALIDATOR, vagrantBox);
        }

        public FormValidation doCheckVagrantBox(@QueryParameter String value) {
            String vagrantBox = Util.fixEmptyAndTrim(value);
            return Validators.validate(VAGRANT_BOX_VALIDATOR, vagrantBox);
        }

        public String getXStudioPath() {
            if (Strings.isNullOrEmpty(xStudioPath)) {
                return DEFAULT_XSTUDIO_PATH;
            }
            return xStudioPath;
        }

        public String getVagrantBox() {
            if (Strings.isNullOrEmpty(vagrantBox)) {
                return DEFAULT_VAGRANT_BOX;
            }
            return vagrantBox;
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
