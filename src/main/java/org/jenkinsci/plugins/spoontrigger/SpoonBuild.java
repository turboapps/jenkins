package org.jenkinsci.plugins.spoontrigger;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.io.Closeables;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Result;
import lombok.Getter;
import lombok.Setter;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ConfigCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.jira.JiraApi;
import org.jenkinsci.plugins.spoontrigger.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Pattern;

public class SpoonBuild extends Build<SpoonProject, SpoonBuild> {

    private static final Pattern INVALID_CHARACTERS_PATTERN = Pattern.compile("\\W+");

    @Getter
    @Setter
    private boolean allowOverwrite = false;

    @Getter
    private Optional<StandardUsernamePasswordCredentials> credentials = Optional.absent();
    @Getter
    private Optional<Image> outputImage = Optional.absent();
    @Getter
    private Optional<Image> remoteImage = Optional.absent();
    @Getter
    private Optional<FilePath> script = Optional.absent();
    @Getter
    private Optional<EnvVars> env = Optional.absent();
    @Getter
    private Optional<String> hubUrl = Optional.absent();

    public SpoonBuild(SpoonProject project) throws IOException {
        super(project);
    }

    public SpoonBuild(SpoonProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public void run() {
        this.execute(new SpoonBuildExecution());
    }

    protected class SpoonBuildExecution extends BuildExecution {
        @Override
        protected Result doRun(BuildListener listener) throws Exception {
            Result buildResult = super.doRun(listener);

            try {
                if (buildResult != null && buildResult.isWorseOrEqualTo(Result.FAILURE)) {
                    TurboTool turboTool = TurboTool.getDefaultInstallation();
                    TurboTool.BugTrackerSettings bugTrackerSettings = turboTool.getBugTrackerSettings();
                    AbstractBuild<?, ?> rootBuild = getRootBuild();
                    if (bugTrackerSettings != null && rootBuild instanceof SpoonBuild) {
                        SpoonBuild build = (SpoonBuild) rootBuild;
                        SpoonProject project = build.getProject();
                        String projectName = project.getName();
                        JiraApi jiraApi = new JiraApi(bugTrackerSettings);
                        try {
                            jiraApi.createOrReopenIssue(projectName);
                        } finally {
                            final boolean swallowException = true;
                            Closeables.close(jiraApi, swallowException);
                        }
                    }
                }
            } catch (Throwable th) {
                LogUtils.log(listener, "Failed to report the build failure in JIRA", th);
            }

            return buildResult;
        }

        @Override
        public void cleanUp(BuildListener listener) throws Exception {
            try {
                super.cleanUp(listener);
            } finally {
                // cleanup is done outside the build scope, because it was cleaned up now
                ConfigCommand command = ConfigCommand.builder().reset(true).build();
                Launcher launcher = getLauncher();
                try {
                    int errorCode = launcher.launch().cmds(command.getArgumentList().toList()).join();
                    if (errorCode != 0) {
                        LogUtils.log(listener, String.format("Failed to reset default configuration. Process returned non-zero error code: %s.", errorCode));
                    }
                } catch (Throwable th) {
                    // no sense to change the build status, publishers and triggers were dispatched
                    LogUtils.log(listener, "Failed to reset default configuration", th);
                }
            }
        }
    }

    public Date getStartDate() {
        long startTime = this.getStartTimeInMillis();
        return new Date(startTime);
    }

    /**
     * @deprecated kept for backwards compatibility with Twitter Plugin
     */
    @Deprecated
    public Optional<String> getBuiltImage() {
        if (outputImage.isPresent()) {
            return Optional.of(outputImage.get().printIdentifier());
        }
        return Optional.absent();
    }

    public String getSanitizedProjectName() {
        return INVALID_CHARACTERS_PATTERN.matcher(getProject().getName()).replaceAll("");
    }

    void setCredentials(StandardUsernamePasswordCredentials credentials) {
        this.credentials = Optional.of(credentials);
    }

    void setOutputImage(Image outputImage) {
        this.outputImage = Optional.of(outputImage);
    }

    void setRemoteImage(Image remoteImage) {
        this.remoteImage = Optional.of(remoteImage);
    }

    void setScript(FilePath script) {
        this.script = Optional.of(script);
    }

    void setEnv(EnvVars env) {
        this.env = Optional.of(env);
    }

    void setHubUrl(String hubUrl) {
        this.hubUrl = Optional.of(hubUrl);
    }
}
