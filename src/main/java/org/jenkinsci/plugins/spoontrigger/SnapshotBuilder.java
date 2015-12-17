package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.vagrant.DestroyCommand;
import org.jenkinsci.plugins.spoontrigger.commands.vagrant.UpCommand;
import org.jenkinsci.plugins.spoontrigger.utils.JsonOption;
import org.jenkinsci.plugins.spoontrigger.vagrant.VagrantEnvironment;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class SnapshotBuilder extends BaseBuilder {

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
    protected void prebuild(SpoonBuild build, BuildListener listener) {
        checkState(xStudioPath != null, String.format(REQUIRE_NOT_NULL_OR_EMPTY_S, "xStudioPath"));
        checkState(vagrantBox != null, String.format(REQUIRE_NOT_NULL_OR_EMPTY_S, "vagrantBox"));
    }

    @Override
    public boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        VagrantEnvironment environment = createVagrantEnvironment(build);
        try {
            CommandDriver commandDriver = CommandDriver.builder(build).launcher(launcher).listener(listener).build();
            try {
                new UpCommand().run(commandDriver);
            } catch (Throwable th) {
                log(listener, "Vagrant up failed with exception", th);
            } finally {
                try {
                    new DestroyCommand().run(commandDriver);
                } catch (Throwable th) {
                    log(listener, "Vagrant destroy failed with exception. The virtual machine may have to be removed from VirtualBox manually.", th);
                }
            }
        } finally {
            environment.close();
        }
        return true;
    }

    private VagrantEnvironment createVagrantEnvironment(SpoonBuild build) {
        Path workspace = Paths.get(build.getWorkspace().getRemote());
        VagrantEnvironment.EnvironmentBuilder environmentBuilder = VagrantEnvironment.builder(workspace)
                .box(vagrantBox)
                .xStudioPath(xStudioPath)
                .generateInstallScript("/S", false);
        if (xStudioLicensePath != null) {
            environmentBuilder.xStudioLicensePath(xStudioLicensePath);
        }
        return environmentBuilder.build();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public static final String DEFAULT_VAGRANT_BOX = "opentable/win-2012r2-standard-amd64-nocm";
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

        @Getter
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
            xStudioPath = jsonWrapper.getString("xStudioPath").orNull();
            xStudioLicensePath = jsonWrapper.getString("xStudioLicensePath").orNull();
            vagrantBox = jsonWrapper.getString("vagrantBox").or(DEFAULT_VAGRANT_BOX);

            save();

            return super.configure(req, json);
        }

        @Override
        public SnapshotBuilder newInstance(StaplerRequest req, JSONObject json)
                throws FormException {
            JsonOption.ObjectWrapper jsonWrapper = JsonOption.wrap(json);
            String vagrantBoxToUse = jsonWrapper.getString("vagrantBox").orNull();
            if (Strings.isNullOrEmpty(vagrantBoxToUse)) {
                vagrantBoxToUse = getVagrantBox();
            }
            return new SnapshotBuilder(getXStudioPath(), getXStudioLicensePath(), vagrantBoxToUse);
        }

        public FormValidation doCheckXStudioLicensePath(@QueryParameter String value) {
            return validateOptionalFilePath(value);
        }

        public FormValidation doCheckXStudioPath(@QueryParameter String value) {
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

        public String getVagrantBox() {
            if (Strings.isNullOrEmpty(vagrantBox)) {
                return DEFAULT_VAGRANT_BOX;
            }
            return vagrantBox;
        }

        private FormValidation validateOptionalFilePath(String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            if (filePath == null) {
                return FormValidation.error(String.format(REQUIRE_NON_EMPTY_STRING_S, "Parameter"));
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
