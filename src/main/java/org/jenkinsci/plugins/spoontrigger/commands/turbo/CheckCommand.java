package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public class CheckCommand extends VoidCommand {

    protected CheckCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static class CommandBuilder {
        private Optional<Image> image = Optional.absent();
        private Optional<String> screenshotPath = Optional.absent();

        public CommandBuilder image(Image image) {
            this.image = Optional.of(image);
            return this;
        }

        public CommandBuilder screenshotPath(String screenshotPath) {
            this.screenshotPath = Optional.of(screenshotPath);
            return this;
        }

        public CheckCommand build() {
            checkState(image.isPresent(), REQUIRE_PRESENT_S, "image");
            checkState(screenshotPath.isPresent(), REQUIRE_PRESENT_S, "screenshotPath");

            ArgumentListBuilder arguments = new ArgumentListBuilder(SPOON_CLIENT, "check")
                    .add(image.get().printIdentifier())
                    .add("--screencast")
                    .addQuoted(screenshotPath.get());

            return new CheckCommand(arguments);
        }
    }
}
