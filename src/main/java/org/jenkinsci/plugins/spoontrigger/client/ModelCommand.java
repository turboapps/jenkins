package org.jenkinsci.plugins.spoontrigger.client;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SINGLE_WORD_SP;

public class ModelCommand extends FilterOutputCommand {

    ModelCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static class CommandBuilder {
        private Optional<String> image;
        private Optional<String> transcriptDirectory;
        private Optional<String> modelDirectory;

        public CommandBuilder image(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);
            this.image = Optional.of(image);
            return this;
        }

        public CommandBuilder transcriptDirectory(String transcriptDirectory) {
            this.transcriptDirectory = Optional.of(transcriptDirectory);
            return this;
        }

        public CommandBuilder modelDirectory(String modelDirectory) {
            this.modelDirectory = Optional.of(modelDirectory);
            return this;
        }

        public ModelCommand build() {
            checkState(image.isPresent(), REQUIRE_PRESENT_S, "image");
            checkState(transcriptDirectory.isPresent(), REQUIRE_PRESENT_S, "transcriptDirectory");
            checkState(modelDirectory.isPresent(), REQUIRE_PRESENT_S, "modelDirectory");

            ArgumentListBuilder args = new ArgumentListBuilder(SPOON_CLIENT, "model", image.get())
                    .addQuoted(transcriptDirectory.get())
                    .addQuoted(modelDirectory.get());
            return new ModelCommand(args);
        }
    }
}
