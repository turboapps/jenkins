package org.jenkinsci.plugins.spoontrigger;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ConfigCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Pattern;

public class SpoonBuild extends Build<SpoonProject, SpoonBuild> {

    private static final Pattern INVALID_CHARACTERS_PATTERN = Pattern.compile("\\W+");


    public boolean allowOverwrite = false;


    private Optional<StandardUsernamePasswordCredentials> credentials = Optional.absent();

    private Optional<Image> outputImage = Optional.absent();

    private Optional<Image> remoteImage = Optional.absent();

    private Optional<FilePath> script = Optional.absent();

    private Optional<EnvVars> env = Optional.absent();

    private Optional<String> hubUrl = Optional.absent();

    private boolean buildExe = false;

    public SpoonBuild(SpoonProject project) throws IOException {
        super(project);
    }

    public SpoonBuild(SpoonProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public Optional<Image> getRemoteImage() {
        return remoteImage;
    }

    public Optional<EnvVars> getEnv() {
        return env;
    }

    public Optional<Image> getOutputImage() {
        return outputImage;
    }

    public Optional<FilePath> getScript() {
        return script;
    }

    public Optional<String> getHubUrl() {
        return hubUrl;
    }

    public Optional<StandardUsernamePasswordCredentials> getCredentials() {
        return credentials;
    }

    public boolean isAllowOverwrite() {
        return allowOverwrite;
    }

    public boolean isBuildExe() {
        return buildExe;
    }

    @Override
    public void run() {
        this.execute(new SpoonBuildExecution());
    }

    protected class SpoonBuildExecution extends BuildExecution {
        @Override
        public void cleanUp(BuildListener listener) throws Exception {
            try {
                super.cleanUp(listener);
            } finally {
                // cleanup is done outside the build scope, because it was cleaned up now
                ConfigCommand command = ConfigCommand.builder().reset(true).build();
                Launcher launcher = getLauncher();
                try {
                    int errorCode = launcher.launch().cmds(command.argumentList.toList()).join();
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
