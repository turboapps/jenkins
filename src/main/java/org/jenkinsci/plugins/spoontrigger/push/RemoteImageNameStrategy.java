package org.jenkinsci.plugins.spoontrigger.push;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import hudson.model.BuildListener;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.spoontrigger.SpoonBuild;
import org.jenkinsci.plugins.spoontrigger.git.PushCause;
import org.jenkinsci.plugins.spoontrigger.git.RemoteImageGenerator;
import org.jenkinsci.plugins.spoontrigger.hub.HubApi;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.hub.Version;
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
        public Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build, BuildListener buildListener) {
            return Optional.absent();
        }
    },
    GENERATE_GIT {
        @Override
        public Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build, BuildListener buildListener) {
            Optional<String> organization = Optional.absent();
            if (pushConfig.overwriteOrganization) {
                organization = Optional.fromNullable(pushConfig.organization);
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

            if (pushConfig.overwriteOrganization) {
                checkState(Patterns.isNullOrSingleWord(pushConfig.organization), REQUIRE_SINGLE_WORD_OR_NULL_SP,
                        "Organization", pushConfig.organization);
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
        public Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build, BuildListener buildListener) {
            String remoteImageName = pushConfig.remoteImageName;
            String suffix = getSuffix(pushConfig, build, buildListener);
            return Optional.of(Image.parse(remoteImageName + ":" +  suffix));
        }

        @Override
        public void validate(PushConfig pushConfig, SpoonBuild build) {
            super.validate(pushConfig, build);

            checkState(Patterns.isNullOrSingleWord(pushConfig.remoteImageName), REQUIRE_SINGLE_WORD_OR_NULL_SP,
                    "Remote image name", pushConfig.remoteImageName);

            switch (pushConfig.tagGenerationStrategy) {
                case AppendDate: {
                    String dateFormat = pushConfig.dateFormat;
                    checkState(Patterns.isNullOrSingleWord(dateFormat), REQUIRE_SINGLE_WORD_OR_NULL_SP, "Date format", dateFormat);
                    checkState(StringValidators.Predicates.IS_DATE_FORMAT.apply(dateFormat), REQUIRE_VALID_FORMAT_SP, "Date", dateFormat);
                    break;
                }
                default:
            }
        }

        private String getSuffix(PushConfig pushConfig, SpoonBuild build, BuildListener buildListener) {
            switch (pushConfig.tagGenerationStrategy) {
                case AppendDate: {
                    SimpleDateFormat dateFormat = new SimpleDateFormat(pushConfig.dateFormat);
                    Date startDate = build.getStartDate();
                    return dateFormat.format(startDate);
                }
                case IncrementVersion: {
                    Version latestVersion = getLatestVersion(pushConfig.getRemoteImage(), build, buildListener);
                    return String.format("%d.0", latestVersion.getMajor() + 1);
                }
                default:
                    return "";
            }
        }

        private Version getLatestVersion(Image image, SpoonBuild build, BuildListener buildListener) {
            HubApi hubApi = HubApi.create(build, buildListener);
            Image latestImage = hubApi.getLatestVersion(image);
            String rawTag = latestImage.tag;
            if (rawTag != null) {
                Optional<Version> parsedVersion = Version.tryParse(rawTag);
                if (parsedVersion.isPresent()) {
                    return parsedVersion.get();
                }
            }
            return Version.EMPTY;
        }
    };

    public Image getRemoteImage(PushConfig config, SpoonBuild build, BuildListener buildListener) {
        validate(config, build);

        Optional<Image> remoteImageName = tryGetRemoteImage(config, build, buildListener);
        final Image imageToUse = remoteImageName.or(config.localImage);
        if (imageToUse.namespace == null) {
            Optional<StandardUsernamePasswordCredentials> credentials = build.getCredentials();
            if (credentials.isPresent()) {
                return new Image(credentials.get().getUsername(), imageToUse.repo, imageToUse.tag);
            }
        }
        return imageToUse;
    }

    protected void validate(PushConfig pushConfig, SpoonBuild build) {
    }

    protected abstract Optional<Image> tryGetRemoteImage(PushConfig pushConfig, SpoonBuild build, BuildListener buildListener);
}