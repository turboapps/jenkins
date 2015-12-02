package org.jenkinsci.plugins.spoontrigger.push;

import com.google.common.base.Optional;
import hudson.model.Result;
import org.jenkinsci.plugins.spoontrigger.Messages;
import org.jenkinsci.plugins.spoontrigger.SpoonBuild;
import org.jenkinsci.plugins.spoontrigger.client.PushCommand;
import org.jenkinsci.plugins.spoontrigger.client.SpoonClient;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public class Pusher {

    private final RemoteImageNameStrategy remoteImageNameStrategy;
    private final SpoonClient client;

    public Pusher(RemoteImageNameStrategy remoteImageNameStrategy, SpoonClient client) {
        this.remoteImageNameStrategy = remoteImageNameStrategy;
        this.client = client;
    }

    public void validate(SpoonBuild build) throws IllegalStateException {
        Optional<Result> buildResult = Optional.fromNullable(build.getResult());
        checkState(buildResult.isPresent(), "%s requires a healthy build to continue. The result of current build is not available", Messages.toString(this.getClass()));
        checkState(buildResult.get().isBetterThan(Result.FAILURE), "%s requires a healthy build to continue. Result of the current build is %s.",
                Messages.toString(this.getClass()), buildResult.get());

        Optional<String> builtImage = build.getBuiltImage();
        checkState(builtImage.isPresent(), REQUIRE_PRESENT_S, "built image");
    }

    public void push(PushConfig pushConfig, SpoonBuild build) throws InterruptedException, IOException {
        PushCommand pushCmd = createPushCommand(pushConfig, build);
        pushCmd.run(client);
    }

    private PushCommand createPushCommand(PushConfig pushConfig, SpoonBuild build) {
        Optional<String> builtImage = build.getBuiltImage();
        PushCommand.CommandBuilder cmdBuilder = PushCommand.builder().image(builtImage.get());

        Optional<String> remoteImage = this.remoteImageNameStrategy.tryGetRemoteImage(pushConfig, build);
        if (remoteImage.isPresent()) {
            cmdBuilder.remoteImage(remoteImage.get());
        }

        return cmdBuilder.build();
    }
}
