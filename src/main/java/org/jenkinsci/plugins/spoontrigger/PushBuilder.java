package org.jenkinsci.plugins.spoontrigger;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import lombok.Getter;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.client.SpoonClient;
import org.jenkinsci.plugins.spoontrigger.hub.HubApi;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.push.PushConfig;
import org.jenkinsci.plugins.spoontrigger.push.Pusher;
import org.jenkinsci.plugins.spoontrigger.push.RemoteImageNameStrategy;
import org.jenkinsci.plugins.spoontrigger.utils.TaskListeners;
import org.jenkinsci.plugins.spoontrigger.validation.Level;
import org.jenkinsci.plugins.spoontrigger.validation.StringValidators;
import org.jenkinsci.plugins.spoontrigger.validation.Validator;
import org.jenkinsci.plugins.spoontrigger.validation.Validators;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class PushBuilder extends Builder {
    @Nullable
    @Getter
    private final String remoteImageName;
    @Nullable
    @Getter
    private final String dateFormat;
    @Nullable
    @Getter
    private final String organization;

    @Getter
    private final RemoteImageNameStrategy remoteImageStrategy;
    @Getter
    private final boolean appendDate;
    @Getter
    private final boolean overwriteOrganization;

    private transient PushConfig pushConfig;

    @DataBoundConstructor
    public PushBuilder(@Nullable RemoteImageNameStrategy remoteImageStrategy,
                       @Nullable String organization, boolean overwriteOrganization,
                       @Nullable String remoteImageName, @Nullable String dateFormat, boolean appendDate) {
        this.remoteImageStrategy = (remoteImageStrategy == null) ? RemoteImageNameStrategy.DO_NOT_USE : remoteImageStrategy;
        this.organization = Util.fixEmptyAndTrim(organization);
        this.overwriteOrganization = overwriteOrganization;
        this.remoteImageName = Util.fixEmptyAndTrim(remoteImageName);
        this.dateFormat = Util.fixEmptyAndTrim(dateFormat);
        this.appendDate = appendDate;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> abstractBuild, BuildListener listener) {
        checkArgument(abstractBuild instanceof SpoonBuild, requireInstanceOf("build", SpoonBuild.class));

        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        try {
            SpoonBuild build = (SpoonBuild) abstractBuild;
            Image localImage = build.getBuiltImage().orNull();
            checkState(localImage != null, REQUIRE_OUTPUT_IMAGE);

            PushConfig pushConfig = cratePushConfig(localImage);

            Image remoteImage = remoteImageStrategy.getRemoteImage(pushConfig, build);
            if (shouldAbort(remoteImage, build, listener)) {
                build.setResult(Result.ABORTED);
                return false;
            }

            if (!localImage.equals(remoteImage)) {
                build.setRemoteImage(remoteImage);
            }

            SpoonClient client = SpoonClient.builder(build).launcher(launcher).listener(listener).build();
            Pusher pusher = new Pusher(client);
            pusher.push(build);
            return true;
        } catch (IllegalStateException ex) {
            TaskListeners.logFatalError(listener, ex);
            return false;
        }
    }

    private boolean shouldAbort(Image remoteImage, SpoonBuild build, BuildListener listener) {
        if (build.isAllowOverwrite()) {
            return false;
        }

        Result currentResult = build.getResult();
        if (currentResult != null && currentResult.isWorseThan(Result.ABORTED)) {
            return false;
        }

        if (remoteImage.getNamespace() == null) {
            String msg = "Check if image " + remoteImage.printIdentifier() + " is available remotely is skipped," +
                    " because the image name does not specify namespace and it can't be extracted" +
                    " from Jenkins credentials";
            log(listener, msg);

            return false;
        }

        HubApi hubApi = new HubApi(listener);
        try {
            boolean result = hubApi.isAvailableRemotely(remoteImage);

            if (result) {
                String msg = String.format("Image %s is available remotely", remoteImage.printIdentifier());
                log(listener, msg);
            }

            return result;
        } catch (Exception ex) {
            String msg = String.format("Failed to check if image %s is available remotely: %s",
                    remoteImage.printIdentifier(),
                    ex.getMessage());
            log(listener, msg, ex);
            return false;
        }
    }

    private PushConfig cratePushConfig(Image localImage) {
        return new PushConfig(
                localImage,
                remoteImageName,
                dateFormat,
                appendDate,
                organization,
                overwriteOrganization);
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final Validator<String> REMOTE_IMAGE_NAME_VALIDATOR;
        private static final Validator<String> ORGANIZATION_VALIDATOR;
        private static final Validator<String> DATE_FORMAT_VALIDATOR;

        static {
            REMOTE_IMAGE_NAME_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(REQUIRED_PARAMETER, Level.ERROR),
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Parameter")));

            ORGANIZATION_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK),
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Organization")));

            DATE_FORMAT_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK),
                    StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Date format")),
                    StringValidators.isDateFormat(INVALID_DATE_FORMAT));
        }

        private static String getKeyOrDefault(JSONObject json, String key) {
            return json.containsKey(key) ? json.getString(key) : null;
        }

        private static boolean getBoolOrDefault(JSONObject json, String key) {
            return json.containsKey(key) && json.getBoolean(key);
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                JSONObject pushJSON = formData.getJSONObject("remoteImageStrategy");

                RemoteImageNameStrategy remoteImageStrategy = RemoteImageNameStrategy.DO_NOT_USE;
                String remoteImageName = null;
                String dateFormat = null;
                boolean appendDate = false;
                String organization = null;
                boolean overwriteOrganization = false;

                if (pushJSON != null && !pushJSON.isNullObject()) {
                    String remoteImageStrategyName = pushJSON.getString("value");
                    remoteImageStrategy = RemoteImageNameStrategy.valueOf(remoteImageStrategyName);
                    organization = getKeyOrDefault(pushJSON, "organization");
                    overwriteOrganization = getBoolOrDefault(pushJSON, "overwriteOrganization");
                    remoteImageName = getKeyOrDefault(pushJSON, "remoteImageName");
                    dateFormat = getKeyOrDefault(pushJSON, "dateFormat");
                    appendDate = getBoolOrDefault(pushJSON, "appendDate");
                }

                return new PushBuilder(remoteImageStrategy, organization, overwriteOrganization, remoteImageName, dateFormat, appendDate);
            } catch (JSONException ex) {
                throw new IllegalStateException("Error while parsing data form", ex);
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        public FormValidation doCheckRemoteImageName(@QueryParameter String value) {
            String imageName = Util.fixEmptyAndTrim(value);
            return Validators.validate(REMOTE_IMAGE_NAME_VALIDATOR, imageName);
        }

        public FormValidation doCheckDateFormat(@QueryParameter String value) {
            String dateFormat = Util.fixEmptyAndTrim(value);
            return Validators.validate(DATE_FORMAT_VALIDATOR, dateFormat);
        }

        public FormValidation doCheckOrganization(@QueryParameter String value) {
            String organization = Util.fixEmptyAndTrim(value);
            return Validators.validate(ORGANIZATION_VALIDATOR, organization);
        }

        @Override
        public String getDisplayName() {
            return "Push Turbo image";
        }
    }
}
