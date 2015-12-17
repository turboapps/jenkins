package org.jenkinsci.plugins.spoontrigger;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import org.jenkinsci.plugins.spoontrigger.utils.TaskListeners;

import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.FAILED_RESOLVE_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.requireInstanceOf;

public abstract class BaseBuilder extends Builder {

    @Override
    public boolean prebuild(AbstractBuild<?, ?> abstractBuild, BuildListener listener) {
        checkArgument(abstractBuild instanceof SpoonBuild, requireInstanceOf("build", SpoonBuild.class));

        try {
            SpoonBuild build = (SpoonBuild) abstractBuild;

            if (!build.getEnv().isPresent()) {
                EnvVars env = this.getEnvironment(build, listener);
                build.setEnv(env);
            }

            prebuild(build, listener);
            return true;
        } catch (IllegalStateException ex) {
            TaskListeners.logFatalError(listener, ex);
            return false;
        }
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        try {
            return perform((SpoonBuild) abstractBuild, launcher, listener);
        } catch (IllegalStateException ex) {
            TaskListeners.logFatalError(listener, ex);
            return false;
        }
    }

    @SuppressWarnings("SpellCheckingInspection")
    protected void prebuild(SpoonBuild build, BuildListener listener) {
    }

    protected abstract boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException;

    private EnvVars getEnvironment(AbstractBuild<?, ?> build, BuildListener listener) throws IllegalStateException {
        try {
            EnvVars env = build.getEnvironment(listener);
            Map<String, String> buildVariables = build.getBuildVariables();
            env.overrideAll(buildVariables);
            return env;
        } catch (IOException ex) {
            throw onGetEnvironmentFailed(ex);
        } catch (InterruptedException ex) {
            throw onGetEnvironmentFailed(ex);
        }
    }

    private static IllegalStateException onGetEnvironmentFailed(Exception ex) {
        String errMsg = String.format(FAILED_RESOLVE_S, "environment variables");
        return new IllegalStateException(errMsg, ex);
    }
}
