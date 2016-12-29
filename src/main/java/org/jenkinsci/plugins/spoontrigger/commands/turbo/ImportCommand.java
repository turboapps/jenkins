package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.FilterOutputCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;

import java.util.Collection;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public class ImportCommand extends FilterOutputCommand {
    private static final Pattern OUTPUT_IMAGE_PATTERN = Pattern.compile("^Output\\simage:\\s+(?<image>\\S+)$");


    private Optional<Image> outputImage = Optional.absent();

    protected ImportCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    @Override
    public void run(CommandDriver client) throws IllegalStateException {
        super.run(client);

        Collection<String> images = findInOutput(OUTPUT_IMAGE_PATTERN);
        if (!images.isEmpty()) {
            String outputImageName = Iterables.getLast(images);
            outputImage = Optional.of(Image.parse(outputImageName));
        }
    }

    public Optional<Image> getOutputImage() {
        return outputImage;
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static class CommandBuilder {
        private Optional<String> type = Optional.absent();
        private Optional<String> path = Optional.absent();
        private Optional<String> name = Optional.absent();
        private Optional<Boolean> overwrite = Optional.absent();

        public CommandBuilder type(String type) {
            this.type = Optional.of(type);
            return this;
        }

        public CommandBuilder path(String path) {
            this.path = Optional.of(path);
            return this;
        }

        public CommandBuilder name(String name) {
            this.name = Optional.of(name);
            return this;
        }

        public CommandBuilder overwrite(boolean overwrite) {
            this.overwrite = Optional.of(overwrite);
            return this;
        }

        public ImportCommand build() {
            checkState(path.isPresent(), REQUIRE_PRESENT_S, "path");
            checkState(type.isPresent(), REQUIRE_PRESENT_S, "type");

            ArgumentListBuilder arguments = new ArgumentListBuilder(SPOON_CLIENT, "import")
                    .add(type.get())
                    .addQuoted(path.get());

            if (name.isPresent()) {
                arguments.add("--name=" + name.get());
            }

            if (overwrite.or(Boolean.FALSE)) {
                arguments.add("--overwrite");
            }

            return new ImportCommand(arguments);
        }
    }
}
