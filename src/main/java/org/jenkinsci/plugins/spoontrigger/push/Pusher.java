package org.jenkinsci.plugins.spoontrigger.push;

import com.google.common.base.Optional;
import hudson.model.Result;
import org.jenkinsci.plugins.spoontrigger.Messages;
import org.jenkinsci.plugins.spoontrigger.SpoonBuild;
import org.jenkinsci.plugins.spoontrigger.client.PushCommand;
import org.jenkinsci.plugins.spoontrigger.client.SpoonClient;
import org.jenkinsci.plugins.spoontrigger.hub.Image;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public class Pusher {

    private final SpoonClient client;

    public Pusher(SpoonClient client) {
        this.client = client;
    }

    public void push(SpoonBuild build) throws InterruptedException, IOException {
        validate(build);

        PushCommand pushCmd = createPushCommand(build);
        pushCmd.run(client);
    }

    private void validate(SpoonBuild build) throws IllegalStateException {
        Optional<Result> buildResult = Optional.fromNullable(build.getResult());
        if (buildResult.isPresent()) {
            checkState(buildResult.get().isBetterThan(Result.FAILURE), "%s requires a healthy build to continue. Result of the current build is %s.",
                    Messages.toString(this.getClass()), buildResult.get());
        }
        Optional<Image> builtImage = build.getOutputImage();
        checkState(builtImage.isPresent(), REQUIRE_PRESENT_S, "built image");
    }

    private PushCommand createPushCommand(SpoonBuild build) {
        Optional<Image> builtImage = build.getOutputImage();
        PushCommand.CommandBuilder cmdBuilder = PushCommand.builder().image(builtImage.get().printIdentifier());

        Optional<Image> remoteImage = build.getRemoteImage();
        if (remoteImage.isPresent()) {
            cmdBuilder.remoteImage(remoteImage.get().printIdentifier());
        }

        return cmdBuilder.build();
    }
}
