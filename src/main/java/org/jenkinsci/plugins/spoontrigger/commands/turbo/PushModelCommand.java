package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.FilterOutputCommand;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SINGLE_WORD_SP;

public class PushModelCommand extends FilterOutputCommand {

    PushModelCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static class CommandBuilder {
        private Optional<String> localImage = Optional.absent();
        private Optional<String> remoteImage = Optional.absent();
        private Optional<String> modelDirectory = Optional.absent();

        public CommandBuilder localImage(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);
            this.localImage = Optional.of(image);
            return this;
        }

        public CommandBuilder remoteImage(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);
            this.remoteImage = Optional.of(image);
            return this;
        }

        public CommandBuilder modelDirectory(String modelDirectory) {
            this.modelDirectory = Optional.of(modelDirectory);
            return this;
        }

        public PushModelCommand build() {
            checkState(localImage.isPresent(), REQUIRE_PRESENT_S, "localImage");
            checkState(modelDirectory.isPresent(), REQUIRE_PRESENT_S, "modelDirectory");

            ArgumentListBuilder args = new ArgumentListBuilder(SPOON_CLIENT, "pushm", localImage.get());
            if (remoteImage.isPresent()) {
                args.add(remoteImage.get());
            }
            args.add(modelDirectory.get());

            return new PushModelCommand(args);
        }
    }
}
