package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SINGLE_WORD_SP;

public class PullCommand extends VoidCommand {

    private PullCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static final class CommandBuilder {

        private Optional<String> imageName = Optional.absent();

        public CommandBuilder image(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);

            this.imageName = Optional.of(image.trim());
            return this;
        }

        public PullCommand build() {
            checkState(this.imageName.isPresent(), REQUIRE_PRESENT_S, "image");

            ArgumentListBuilder args = new ArgumentListBuilder(SPOON_CLIENT, "pull", this.imageName.get());
            return new PullCommand(args);
        }
    }
}
