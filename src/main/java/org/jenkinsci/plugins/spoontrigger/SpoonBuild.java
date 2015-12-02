package org.jenkinsci.plugins.spoontrigger;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Build;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class SpoonBuild extends Build<SpoonProject, SpoonBuild> {

    @Getter
    private Optional<StandardUsernamePasswordCredentials> credentials = Optional.absent();
    @Getter
    private Optional<String> builtImage = Optional.absent();
    @Getter
    private Optional<FilePath> script = Optional.absent();
    @Getter
    private Optional<EnvVars> env = Optional.absent();

    public SpoonBuild(SpoonProject project) throws IOException {
        super(project);
    }

    public SpoonBuild(SpoonProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public Date getStartDate() {
        long startTime = this.getStartTimeInMillis();
        return new Date(startTime);
    }

    void setCredentials(StandardUsernamePasswordCredentials credentials) {
        this.credentials = Optional.of(credentials);
    }

    void setBuiltImage(String builtImage) {
        this.builtImage = Optional.of(builtImage);
    }

    void setScript(FilePath script) {
        this.script = Optional.of(script);
    }

    void setEnv(EnvVars env) {
        this.env = Optional.of(env);
    }
}
