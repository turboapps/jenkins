package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.FilePath;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public final class ExportCommand extends VoidCommand {

    private ExportCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static final class CommandBuilder {

        private Optional<FilePath> outputFile = Optional.absent();
        private Optional<String> image = Optional.absent();

        public CommandBuilder outputFile(FilePath outputFile) {
            checkArgument(outputFile != null, REQUIRE_NOT_NULL_S, "outputFile");

            this.outputFile = Optional.of(outputFile);
            return this;
        }

        public CommandBuilder image(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);

            this.image = Optional.of(image);
            return this;
        }

        public ExportCommand build() {
            checkState(this.image.isPresent(), REQUIRE_PRESENT_S, "image");
            checkState(this.outputFile.isPresent(), REQUIRE_PRESENT_S, "outputFile");

            ArgumentListBuilder buildArgs = new ArgumentListBuilder(SPOON_CLIENT, "export", this.image.get());
            buildArgs.addQuoted(this.outputFile.get().getRemote());
            return new ExportCommand(buildArgs);
        }
    }
}
