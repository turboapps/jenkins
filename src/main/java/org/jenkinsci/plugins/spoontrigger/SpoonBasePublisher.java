package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Optional;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import lombok.AccessLevel;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.client.SpoonClient;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.utils.TaskListeners;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.requireInstanceOf;

abstract class SpoonBasePublisher extends Publisher {

    @Getter(AccessLevel.MODULE)
    private transient Optional<Image> image = Optional.absent();

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> abstractBuild, BuildListener listener) {
        if (!(abstractBuild instanceof SpoonBuild)) {
            listener.fatalError(requireInstanceOf("build", SpoonBuild.class));
            return false;
        }

        return super.prebuild(abstractBuild, listener);
    }

    void beforePublish(SpoonBuild build, BuildListener listener) throws IllegalStateException {
        Optional<Result> buildResult = Optional.fromNullable(build.getResult());
        checkState(buildResult.isPresent(), "%s requires a healthy build to continue. The result of current build is not available", Messages.toString(this.getClass()));
        checkState(buildResult.get().isBetterThan(Result.FAILURE), "%s requires a healthy build to continue. Result of the current build is %s.",
                Messages.toString(this.getClass()), buildResult.get());

        Optional<Image> builtImage = build.getBuiltImage();

        checkState(builtImage.isPresent(), REQUIRE_PRESENT_S, "built image");

        this.image = builtImage;
    }

    protected abstract void publish(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IllegalStateException;

    @Override
    public final boolean perform(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        try {
            SpoonBuild build = (SpoonBuild) abstractBuild;
            this.beforePublish(build, listener);
            this.publish(build, launcher, listener);


            return true;
        } catch (IllegalStateException ex) {
            TaskListeners.logFatalError(listener, ex);
            return false;
        }
    }

    SpoonClient createClient(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener listener) {
        SpoonBuild build = (SpoonBuild) abstractBuild;
        return SpoonClient.builder(build).launcher(launcher).listener(listener).build();
    }
}
