package org.jenkinsci.plugins.spoontrigger;

import hudson.Extension;
import hudson.Util;
import hudson.init.Initializer;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.utils.JsonOption;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.util.Collections;

import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;
import static org.jenkinsci.plugins.spoontrigger.Messages.PATH_NOT_POINT_TO_ITEM_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.PATH_SHOULD_BE_ABSOLUTE;

public class TurboTool extends ToolInstallation {

    public static transient final String DEFAULT = "Default";


    public final String hubApiKey;

    public final String screenshotDir;

    @DataBoundConstructor
    public TurboTool(String name, String hubApiKey, String screenshotDir) {
        super(name, null, Collections.<ToolProperty<?>>emptyList());

        this.hubApiKey = Util.fixEmptyAndTrim(hubApiKey);
        this.screenshotDir = Util.fixEmptyAndTrim(screenshotDir);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            throw new AssertionError("No Jenkins instance");
        }
        return (DescriptorImpl) jenkinsInstance.getDescriptorOrDie(getClass());
    }

    public static TurboTool getDefaultInstallation() {
        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            throw new IllegalStateException("Jenkins instance is null");
        }
        DescriptorImpl descriptor = jenkinsInstance.getDescriptorByType(TurboTool.DescriptorImpl.class);
        TurboTool tool = descriptor.getInstallation(TurboTool.DEFAULT);
        if (tool != null) {
            return tool;
        } else {
            TurboTool[] installations = descriptor.getInstallations();
            if (installations.length > 0) {
                return installations[0];
            } else {
                DescriptorImpl.onLoaded();
                return descriptor.getInstallations()[0];
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<TurboTool> {

        private static final Validator<String> IGNORE_NULL_VALIDATOR = StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK);
        private static final Validator<String> PATH_STRING_VALIDATOR = StringValidators.isNotNull(REQUIRED_PARAMETER, Level.ERROR);
        private static final Validator<java.io.File> DIR_PATH_VALIDATOR = Validators.chain(
                FileValidators.exists(String.format(DOES_NOT_EXIST_S, "Directory")),
                FileValidators.isDirectory(String.format(PATH_NOT_POINT_TO_ITEM_S, "a directory")),
                FileValidators.isPathAbsolute(PATH_SHOULD_BE_ABSOLUTE, Level.WARNING)
        );


        private String hubApiKey;

        private String screenshotDir;

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Turbo";
        }

        public String getHubApiKey() {
            return hubApiKey;
        }

        public String getScreenshotDir() {
            return screenshotDir;
        }

        public TurboTool getInstallation(String name) {
            for (TurboTool i : getInstallations()) {
                if (i.getName().equals(name)) {
                    return i;
                }
            }
            return null;
        }

        @Initializer(after = EXTENSIONS_AUGMENTED)
        public static void onLoaded() {
            Jenkins jenkinsInstance = Jenkins.getInstance();
            if (jenkinsInstance == null) {
                return;
            }
            DescriptorImpl descriptor = (DescriptorImpl) jenkinsInstance.getDescriptor(TurboTool.class);
            TurboTool[] installations = getInstallations(descriptor);

            if (installations != null && installations.length > 0) {
                //No need to initialize if there's already something
                return;
            }

            TurboTool tool = new TurboTool(DEFAULT, null, null);
            descriptor.setInstallations(tool);
            descriptor.save();
        }

        public FormValidation doCheckHubApiKey(@QueryParameter String value) {
            String hubApiKey = Util.fixEmptyAndTrim(value);
            return Validators.validate(IGNORE_NULL_VALIDATOR, hubApiKey);
        }

        public FormValidation doCheckScreenshotDir(@QueryParameter String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            try {
                PATH_STRING_VALIDATOR.validate(filePath);
                File outputFile = new File(filePath);
                DIR_PATH_VALIDATOR.validate(outputFile);
                return FormValidation.ok();
            } catch (ValidationException ex) {
                return ex.failureMessage;
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JsonOption.ObjectWrapper jsonWrapper = JsonOption.wrap(json);

            hubApiKey = jsonWrapper.getString("hubApiKey").orNull();
            screenshotDir = jsonWrapper.getString("screenshotDir").orNull();

            setInstallations(new TurboTool(DEFAULT, hubApiKey, screenshotDir));
            save();

            return true;
        }

        private static TurboTool[] getInstallations(DescriptorImpl descriptor) {
            try {
                return descriptor.getInstallations();
            } catch (NullPointerException e) {
                return new TurboTool[0];
            }
        }
    }
}
