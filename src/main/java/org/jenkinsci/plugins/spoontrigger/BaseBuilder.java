package org.jenkinsci.plugins.spoontrigger;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import org.jenkinsci.plugins.spoontrigger.hub.HubApi;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.utils.TaskListeners;

import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.FAILED_RESOLVE_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.requireInstanceOf;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

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

    protected boolean isAvailableRemotely(Image remoteImage, BuildListener listener) {
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
