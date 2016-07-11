package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Optional;
import hudson.Extension;
import hudson.Util;
import hudson.init.Initializer;
import hudson.model.Item;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import lombok.Data;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.utils.Credentials;
import org.jenkinsci.plugins.spoontrigger.utils.JsonOption;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkArgument;
import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;
import static org.jenkinsci.plugins.spoontrigger.Messages.PATH_NOT_POINT_TO_ITEM_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.PATH_SHOULD_BE_ABSOLUTE;

public class TurboTool extends ToolInstallation {

    public static transient final String DEFAULT = "Default";

    @Getter
    private final String hubApiKey;

    @Getter
    private final String screenshotDir;

    @Getter
    private final BugTrackerSettings bugTrackerSettings;

    @DataBoundConstructor
    public TurboTool(String name, String hubApiKey, String screenshotDir, BugTrackerSettings bugTrackerSettings) {
        super(name, null, Collections.<ToolProperty<?>>emptyList());

        this.hubApiKey = Util.fixEmptyAndTrim(hubApiKey);
        this.screenshotDir = Util.fixEmptyAndTrim(screenshotDir);
        this.bugTrackerSettings = bugTrackerSettings;
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

    @Data
    public static final class BugTrackerSettings {

        private final URL endpoint;
        private final String host;
        private final String firstSegment;
        private final int port;
        private final String projectKey;
        private final String credentialsId;
        private final String issueType;
        private final String issueLabel;
        private final String transitionName;
        private final String issueTitleFormat;

        @DataBoundConstructor
        public BugTrackerSettings(URL endpoint, String projectKey, String issueTitleFormat, String transitionName, String issueLabel, String issueType, String credentialsId) {
            this.endpoint = endpoint;
            this.host = endpoint.getHost();
            int portToUse = endpoint.getPort();
            if (portToUse == -1) {
                portToUse = endpoint.getDefaultPort();
            }
            this.port = portToUse;
            this.firstSegment = endpoint.getPath();

            this.projectKey = Util.fixEmptyAndTrim(projectKey);
            this.issueType = Util.fixEmptyAndTrim(issueType);
            this.issueLabel = Util.fixEmptyAndTrim(issueLabel);
            this.issueTitleFormat = Util.fixEmptyAndTrim(issueTitleFormat);
            this.transitionName = Util.fixEmptyAndTrim(transitionName);
            this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
        }

        public static BugTrackerSettings parse(JsonOption.ObjectWrapper json) throws MalformedURLException {
            String endpoint = json.getString("endpoint").orNull();
            String projectKey = json.getString("projectKey").orNull();
            String issueTitleFormat = json.getString("issueTitleFormat").orNull();
            String issueLabel = json.getString("issueLabel").orNull();
            String issueType = json.getString("issueType").orNull();
            String transitionName = json.getString("transitionName").orNull();
            String credentialsId = json.getString("credentialsId").orNull();

            checkArgument(endpoint != null, REQUIRE_NON_EMPTY_STRING_S, "endpoint");

            return new BugTrackerSettings(new URL(endpoint), projectKey, issueTitleFormat, transitionName, issueLabel, issueType, credentialsId);
        }

        public String getIssueTitle(String projectName) {
            return String.format(issueTitleFormat, projectName);
        }
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<TurboTool> {

        private static final String DEFAULT_ISSUE_LABEL = "JenkinsBuildFailure";
        private static final String DEFAULT_TRANSITION_NAME = "Reopen";
        private static final String DEFAULT_TITLE_FORMAT = "%s build is faling";
        private static final String DEFAULT_ISSUE_TYPE = "Bug";

        private static final Validator<String> IGNORE_NULL_VALIDATOR = StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK);
        private static final Validator<String> REQUIRED_STRING_VALIDATOR = StringValidators.isNotNull(REQUIRED_PARAMETER, Level.ERROR);
        private static final Validator<java.io.File> DIR_PATH_VALIDATOR = Validators.chain(
                FileValidators.exists(String.format(DOES_NOT_EXIST_S, "Directory")),
                FileValidators.isDirectory(String.format(PATH_NOT_POINT_TO_ITEM_S, "a directory")),
                FileValidators.isPathAbsolute(PATH_SHOULD_BE_ABSOLUTE, Level.WARNING)
        );

        @Getter
        private String hubApiKey;

        @Getter
        private String screenshotDir;

        @Getter
        private BugTrackerSettings bugTrackerSettings;

        public String getEndpoint() {
            if (bugTrackerSettings != null && bugTrackerSettings.endpoint != null) {
                return bugTrackerSettings.endpoint.toString();
            }
            return null;
        }

        public String getProjectKey() {
            if (bugTrackerSettings != null) {
                return bugTrackerSettings.projectKey;
            }
            return null;
        }

        public String getIssueLabel() {
            if (bugTrackerSettings != null) {
                return bugTrackerSettings.issueLabel;
            }
            return DEFAULT_ISSUE_LABEL;
        }

        public String getIssueType() {
            if (bugTrackerSettings != null) {
                return bugTrackerSettings.issueType;
            }
            return DEFAULT_ISSUE_TYPE;
        }

        public String getTransitionName() {
            if (bugTrackerSettings != null) {
                return bugTrackerSettings.transitionName;
            }
            return DEFAULT_TRANSITION_NAME;
        }

        public String getIssueTitleFormat() {
            if (bugTrackerSettings != null) {
                return bugTrackerSettings.issueTitleFormat;
            }
            return DEFAULT_TITLE_FORMAT;
        }

        public String getCredentialsId() {
            if (bugTrackerSettings != null) {
                return bugTrackerSettings.credentialsId;
            }
            return null;
        }

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
                // No need to initialize if there's already something
                return;
            }

            TurboTool tool = new TurboTool(DEFAULT, null, null, null);
            descriptor.setInstallations(tool);
            descriptor.save();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
            if (project == null) {
                return Credentials.fillCredentialsIdItems();
            }
            return Credentials.fillCredentialsIdItems(project);
        }

        public FormValidation doCheckHubApiKey(@QueryParameter String value) {
            String hubApiKey = Util.fixEmptyAndTrim(value);
            return Validators.validate(IGNORE_NULL_VALIDATOR, hubApiKey);
        }

        public FormValidation doCheckScreenshotDir(@QueryParameter String value) {
            String filePath = Util.fixEmptyAndTrim(value);
            try {
                REQUIRED_STRING_VALIDATOR.validate(filePath);
                File outputFile = new File(filePath);
                DIR_PATH_VALIDATOR.validate(outputFile);
                return FormValidation.ok();
            } catch (ValidationException ex) {
                return ex.getFailureMessage();
            }
        }

        public FormValidation doCheckRequiredParameter(@QueryParameter String value) {
            String valueToUse = Util.fixEmptyAndTrim(value);
            return Validators.validate(REQUIRED_STRING_VALIDATOR, valueToUse);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JsonOption.ObjectWrapper jsonWrapper = JsonOption.wrap(json);

            hubApiKey = jsonWrapper.getString("hubApiKey").orNull();
            screenshotDir = jsonWrapper.getString("screenshotDir").orNull();

            bugTrackerSettings = null;
            Optional<JsonOption.ObjectWrapper> bugTrackerSettingsOpt = jsonWrapper.getObject("bugTrackerSettings");
            if (bugTrackerSettingsOpt.isPresent()) {
                try {
                    bugTrackerSettings = BugTrackerSettings.parse(bugTrackerSettingsOpt.get());
                } catch (MalformedURLException ex) {
                    throw new FormException(ex, "endpoint");
                }
            }

            setInstallations(new TurboTool(DEFAULT, hubApiKey, screenshotDir, bugTrackerSettings));
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
