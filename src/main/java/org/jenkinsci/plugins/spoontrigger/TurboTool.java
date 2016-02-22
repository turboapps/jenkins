package org.jenkinsci.plugins.spoontrigger;

import hudson.Extension;
import hudson.Util;
import hudson.init.Initializer;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.utils.JsonOption;
import org.jenkinsci.plugins.spoontrigger.validation.Level;
import org.jenkinsci.plugins.spoontrigger.validation.StringValidators;
import org.jenkinsci.plugins.spoontrigger.validation.Validator;
import org.jenkinsci.plugins.spoontrigger.validation.Validators;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;

import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;
import static org.jenkinsci.plugins.spoontrigger.Messages.IGNORE_PARAMETER;

public class TurboTool extends ToolInstallation {

    public static transient final String DEFAULT = "Default";

    @Getter
    private final String hubApiKey;

    @DataBoundConstructor
    public TurboTool(String name, String hubApiKey) {
        super(name, null, Collections.<ToolProperty<?>>emptyList());

        this.hubApiKey = Util.fixEmptyAndTrim(hubApiKey);
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

        private static final Validator<String> HUB_API_KEY =
                StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK);

        @Getter
        private String hubApiKey;

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Turbo";
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

            TurboTool tool = new TurboTool(DEFAULT, null);
            descriptor.setInstallations(tool);
            descriptor.save();
        }

        public FormValidation doCheckHubApiKey(@QueryParameter String value) {
            String hubApiKey = Util.fixEmptyAndTrim(value);
            return Validators.validate(HUB_API_KEY, hubApiKey);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JsonOption.ObjectWrapper jsonWrapper = JsonOption.wrap(json);

            hubApiKey = jsonWrapper.getString("hubApiKey").orNull();

            setInstallations(new TurboTool(DEFAULT, hubApiKey));
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
