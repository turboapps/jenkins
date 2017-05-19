package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.FilterOutputCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public class CheckCommand extends FilterOutputCommand {

    protected CheckCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static class CommandBuilder {
        private Optional<String> bootstrapDuration = Optional.absent();
        private Optional<String> launchDuration = Optional.absent();
        private Optional<String> exitCode = Optional.absent();
        private boolean consoleApp = false;
        private boolean hasChildProcess = false;

        private Optional<Image> image = Optional.absent();
        private Optional<String> screenshotPath = Optional.absent();

        public CommandBuilder image(Image image) {
            this.image = Optional.of(image);
            return this;
        }

        public CommandBuilder screenshotPath(String screenshotPath) {
            this.screenshotPath = Optional.fromNullable(screenshotPath);
            return this;
        }

        public CommandBuilder bootstrapDuration(String value) {
            this.bootstrapDuration = Optional.fromNullable(value);
            return this;
        }

        public CommandBuilder launchDuration(String value) {
            this.launchDuration = Optional.fromNullable(value);
            return this;
        }

        public CommandBuilder exitCode(String value) {
            this.exitCode = Optional.fromNullable(value);
            return this;
        }

        public CommandBuilder consoleApp(boolean value) {
            this.consoleApp = value;
            return this;
        }

        public CommandBuilder hasChildProcess(boolean value) {
            this.hasChildProcess = value;
            return this;
        }

        public CheckCommand build() {
            checkState(image.isPresent(), REQUIRE_PRESENT_S, "image");

            ArgumentListBuilder arguments = new ArgumentListBuilder(SPOON_CLIENT, "check")
                    .add(image.get().printIdentifier())
                    .add("--enable-screencast");

            if (bootstrapDuration.isPresent()) {
                arguments
                        .add("--bootstrap-duration")
                        .add(bootstrapDuration.get());

            }

            if (launchDuration.isPresent()) {
                arguments
                        .add("--launch-duration")
                        .add(launchDuration.get());
            }

            if (exitCode.isPresent()) {
                arguments
                        .add("--exit-code")
                        .add(exitCode.get());
            }

            if (consoleApp) {
                arguments.add("--console-app");
            }

            if (hasChildProcess) {
                arguments.add("--has-child-process");
            }

            return new CheckCommand(arguments);
        }
    }
}
