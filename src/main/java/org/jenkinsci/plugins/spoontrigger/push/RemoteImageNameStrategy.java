package org.jenkinsci.plugins.spoontrigger.push;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.spoontrigger.SpoonBuild;
import org.jenkinsci.plugins.spoontrigger.git.PushCause;
import org.jenkinsci.plugins.spoontrigger.git.RemoteImageGenerator;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;
import org.jenkinsci.plugins.spoontrigger.validation.StringValidators;

import java.text.SimpleDateFormat;
import java.util.Date;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SINGLE_WORD_OR_NULL_SP;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_VALID_FORMAT_SP;

public enum RemoteImageNameStrategy {
    DO_NOT_USE {
        @Override
        public Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build) {
            return Optional.absent();
        }
    },
    GENERATE_GIT {
        @Override
        public Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build) {
            Optional<String> organization = Optional.absent();
            if (pushConfig.isOverwriteOrganization()) {
                organization = Optional.fromNullable(pushConfig.getOrganization());
            }

            PushCause cause = build.getCause(PushCause.class);
            if (cause != null) {
                return Optional.of(Image.parse(RemoteImageGenerator.fromPush(cause, organization)));
            }

            BuildData buildData = build.getAction(BuildData.class);
            if (buildData != null) {
                return Optional.of(Image.parse(RemoteImageGenerator.fromPull(buildData, organization)));
            }

            return Optional.absent();
        }

        @Override
        public void validate(PushConfig pushConfig, SpoonBuild build) {
            super.validate(pushConfig, build);

            if (pushConfig.isOverwriteOrganization()) {
                checkState(Patterns.isNullOrSingleWord(pushConfig.getOrganization()), REQUIRE_SINGLE_WORD_OR_NULL_SP,
                        "Organization", pushConfig.getOrganization());
            }

            PushCause webHookCause = build.getCause(PushCause.class);
            if (webHookCause != null) {
                return;
            }

            BuildData pullGitCause = build.getAction(BuildData.class);
            if (pullGitCause != null) {
                return;
            }

            throw new IllegalStateException("Build has not been caused by a web hook event or pulling SCM");
        }
    },
    FIXED {
        @Override
        public Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build) {
            String remoteImageName = pushConfig.getRemoteImageName();

            String rawDateFormat = pushConfig.getDateFormat();
            if (pushConfig.isAppendDate() && !Strings.isNullOrEmpty(rawDateFormat)) {
                SimpleDateFormat dateFormat = new SimpleDateFormat(rawDateFormat);
                Date startDate = build.getStartDate();
                remoteImageName += dateFormat.format(startDate);
            }

            return Optional.of(Image.parse(remoteImageName));
        }

        @Override
        public void validate(PushConfig pushConfig, SpoonBuild build) {
            super.validate(pushConfig, build);

            checkState(Patterns.isNullOrSingleWord(pushConfig.getRemoteImageName()), REQUIRE_SINGLE_WORD_OR_NULL_SP,
                    "Remote image name", pushConfig.getRemoteImageName());

            if (pushConfig.isAppendDate()) {
                String dateFormat = pushConfig.getDateFormat();
                checkState(Patterns.isNullOrSingleWord(dateFormat), REQUIRE_SINGLE_WORD_OR_NULL_SP, "Date format", dateFormat);
                checkState(StringValidators.Predicates.IS_DATE_FORMAT.apply(dateFormat), REQUIRE_VALID_FORMAT_SP, "Date", dateFormat);
            }
        }
    };

    public Image getRemoteImage(PushConfig config, SpoonBuild build) {
        validate(config, build);

        Optional<Image> remoteImageName = tryGetRemoteImage(config, build);
        final Image imageToUse = remoteImageName.or(config.getLocalImage());
        if (imageToUse.getNamespace() == null) {
            Optional<StandardUsernamePasswordCredentials> credentials = build.getCredentials();
            if (credentials.isPresent()) {
                return new Image(credentials.get().getUsername(), imageToUse.getRepo(), imageToUse.getTag());
            }
        }
        return imageToUse;
    }

    protected void validate(PushConfig pushConfig, SpoonBuild build) {
    }

    protected abstract Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build);
}