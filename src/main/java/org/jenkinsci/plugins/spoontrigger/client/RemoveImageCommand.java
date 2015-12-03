package org.jenkinsci.plugins.spoontrigger.client;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SINGLE_WORD_SP;

public final class RemoveImageCommand extends FilterOutputCommand {

    private RemoveImageCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static final class CommandBuilder {

        private Optional<String> image = Optional.absent();

        public CommandBuilder image(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);

            this.image = Optional.of(image);
            return this;
        }

        public RemoveImageCommand build() {
            checkState(this.image.isPresent(), REQUIRE_PRESENT_S, "image");

            ArgumentListBuilder buildArgs = new ArgumentListBuilder(SPOON_CLIENT, "rmi", this.image.get());
            return new RemoveImageCommand(buildArgs);
        }
    }
}
