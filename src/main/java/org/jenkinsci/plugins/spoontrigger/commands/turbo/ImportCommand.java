package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.FilterOutputCommand;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public class ImportCommand extends FilterOutputCommand {
    protected ImportCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
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
