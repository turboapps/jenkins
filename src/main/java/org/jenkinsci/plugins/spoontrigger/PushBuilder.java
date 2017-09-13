package org.jenkinsci.plugins.spoontrigger;

import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.push.PushConfig;
import org.jenkinsci.plugins.spoontrigger.push.Pusher;
import org.jenkinsci.plugins.spoontrigger.push.RemoteImageNameStrategy;
import org.jenkinsci.plugins.spoontrigger.push.TagGenerationStrategy;
import org.jenkinsci.plugins.spoontrigger.validation.Level;
import org.jenkinsci.plugins.spoontrigger.validation.StringValidators;
import org.jenkinsci.plugins.spoontrigger.validation.Validator;
import org.jenkinsci.plugins.spoontrigger.validation.Validators;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public class PushBuilder extends BaseBuilder {
    @Nullable
    private final String remoteImageName;
    @Nullable
    private final String dateFormat;
    @Nullable
    private final String organization;

    private final RemoteImageNameStrategy remoteImageStrategy;
    private final boolean appendDate;
    private final boolean incrementVersion;
    private final boolean overwriteOrganization;
    private final String hubUrls;
    private final boolean buildExe;
    private final boolean forcePush;


    @DataBoundConstructor
    public PushBuilder(@Nullable RemoteImageNameStrategy remoteImageStrategy, @Nullable String hubUrls,
                       @Nullable String organization,
                       boolean overwriteOrganization,
                       @Nullable String remoteImageName,
                       @Nullable String dateFormat,
                       boolean appendDate,
                       boolean incrementVersion,
                       boolean buildExe,
                       boolean forcePush) {
        this.remoteImageStrategy = (remoteImageStrategy == null) ? RemoteImageNameStrategy.DO_NOT_USE : remoteImageStrategy;
        this.hubUrls = Util.fixEmptyAndTrim(hubUrls);
        this.organization = Util.fixEmptyAndTrim(organization);
        this.overwriteOrganization = overwriteOrganization;
        this.remoteImageName = Util.fixEmptyAndTrim(remoteImageName);
        this.dateFormat = Util.fixEmptyAndTrim(dateFormat);
        this.appendDate = appendDate;
        this.incrementVersion = incrementVersion;
        this.buildExe = buildExe;
        this.forcePush = forcePush;
    }

    @Override
    public boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        Image localImage = build.getOutputImage().orNull();
        checkState(localImage != null, REQUIRE_OUTPUT_IMAGE);

        PushConfig pushConfig = cratePushConfig(localImage);

        Image remoteImage = remoteImageStrategy.getRemoteImage(pushConfig, build, listener);
        if (shouldAbort(remoteImage, build, listener)) {
            build.setResult(Result.ABORTED);
            return false;
        }

        if (!localImage.equals(remoteImage)) {
            build.setRemoteImage(remoteImage);
        }

        CommandDriver client = CommandDriver.builder(build).launcher(launcher).listener(listener).build();

        if (this.hubUrls != null) {
            // if multiple hubs specified, push to each one of them
            for (String hubUrl : hubUrlsAsList()) {
                switchHub(client, hubUrl, build);

                Pusher pusher = new Pusher(client);
                pusher.push(build, buildExe);
            }
        } else {
            // push without changing current hub
            Pusher pusher = new Pusher(client);
            pusher.push(build, buildExe);
        }

        return true;
    }

    private List<String> hubUrlsAsList() {
        List<String> result = new ArrayList<String>();
        if (this.hubUrls != null) {
            for (String url : this.hubUrls.split(",")) {
                result.add(url.trim());
            }
        }
        return result;
    }

    private boolean shouldAbort(Image remoteImage, SpoonBuild build, BuildListener listener) {
        if (build.allowOverwrite || forcePush) {
            return false;
        }

        Result currentResult = build.getResult();
        if (currentResult != null && currentResult.isWorseThan(Result.ABORTED)) {
            return false;
        }

        return isAvailableRemotely(remoteImage, build, listener);
    }

    private PushConfig cratePushConfig(Image localImage) {
        final TagGenerationStrategy tagGenerationStrategy = getTagGenerationStrategy();
        return new PushConfig(
                localImage,
                remoteImageName,
                dateFormat,
                tagGenerationStrategy,
                organization,
                overwriteOrganization,
                hubUrls,
                buildExe);
    }

    private TagGenerationStrategy getTagGenerationStrategy() {
        if (incrementVersion) {
            return TagGenerationStrategy.IncrementVersion;
        }
        if (appendDate) {
            return TagGenerationStrategy.AppendDate;
        }
        return TagGenerationStrategy.Identity;
    }


    @Nullable
    public String getRemoteImageName() {
        return remoteImageName;
    }

    @Nullable
    public String getDateFormat() {
        return dateFormat;
    }

    @Nullable
    public String getOrganization() {
        return organization;
    }

    public RemoteImageNameStrategy getRemoteImageStrategy() {
        return remoteImageStrategy;
    }

    public boolean isAppendDate() {
        return appendDate;
    }

    public boolean isIncrementVersion() {
        return incrementVersion;
    }

    public boolean isOverwriteOrganization() {
        return overwriteOrganization;
    }

    public String getHubUrls() {
        return hubUrls;
    }

    public boolean isBuildExe() {
        return buildExe;
    }

    public boolean getForcePush() { return forcePush; }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final Validator<String> REMOTE_IMAGE_NAME_VALIDATOR;
        private static final Validator<String> ORGANIZATION_VALIDATOR;
        private static final Validator<String> DATE_FORMAT_VALIDATOR;
        private static final Validator<String> HUB_URLS_VALIDATOR;

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

            HUB_URLS_VALIDATOR = Validators.chain(
                    StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK));
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
                String hubUrls = null;
                String dateFormat = null;
                boolean appendDate = false;
                String organization = null;
                boolean overwriteOrganization = false;
                boolean incrementVersion = false;
                boolean buildExe = false;
                boolean forcePush = false;

                if (pushJSON != null && !pushJSON.isNullObject()) {
                    String remoteImageStrategyName = pushJSON.getString("value");
                    remoteImageStrategy = RemoteImageNameStrategy.valueOf(remoteImageStrategyName);
                    hubUrls = getKeyOrDefault(formData, "hubUrls");
                    buildExe = getBoolOrDefault(formData, "buildExe");
                    forcePush = getBoolOrDefault(formData, "forcePush");
                    organization = getKeyOrDefault(pushJSON, "organization");
                    overwriteOrganization = getBoolOrDefault(pushJSON, "overwriteOrganization");
                    remoteImageName = getKeyOrDefault(pushJSON, "remoteImageName");
                    dateFormat = getKeyOrDefault(pushJSON, "dateFormat");
                    appendDate = getBoolOrDefault(pushJSON, "appendDate");
                    incrementVersion = getBoolOrDefault(pushJSON, "incrementVersion");
                }

                return new PushBuilder(
                        remoteImageStrategy,
                        hubUrls,
                        organization,
                        overwriteOrganization,
                        remoteImageName,
                        dateFormat,
                        appendDate,
                        incrementVersion,
                        buildExe,
                        forcePush);
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

        public FormValidation doCheckHubUrls(@QueryParameter String value) {
            String hubUrls = Util.fixEmptyAndTrim(value);
            return Validators.validate(HUB_URLS_VALIDATOR, hubUrls);
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
