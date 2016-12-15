package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.FilterOutputCommand;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SINGLE_WORD_SP;

public final class PushCommand extends FilterOutputCommand {

    private PushCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static final class CommandBuilder {

        private String imageName = null;
        private Optional<String> remoteImageName = Optional.absent();
        private boolean buildExe = false;

        public CommandBuilder image(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);

            this.imageName = image.trim();
            return this;
        }

        public CommandBuilder remoteImage(String image) {
            checkArgument(Patterns.isSingleWord(image), REQUIRE_SINGLE_WORD_SP, "image", image);

            this.remoteImageName = Optional.of(image.trim());
            return this;
        }

        public void buildExe(boolean buildExe) {
            this.buildExe = buildExe;
        }

        public PushCommand build() {
            checkState(imageName!=null, REQUIRE_PRESENT_S, "image");
            ArgumentListBuilder args = new ArgumentListBuilder(SPOON_CLIENT, "push");

            if(buildExe){
                args.add("--include-exe");
            }

            args.add(this.imageName);
            if (this.remoteImageName.isPresent()) {
                args.add(this.remoteImageName.get());
            }

            return new PushCommand(args);
        }
    }
}
