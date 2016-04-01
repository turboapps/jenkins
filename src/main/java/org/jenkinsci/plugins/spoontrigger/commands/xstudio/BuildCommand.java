package org.jenkinsci.plugins.spoontrigger.commands.xstudio;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;

import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_PRESENT_S;

public class BuildCommand extends VoidCommand {
    protected BuildCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder(String xStudioPath) {
        return new CommandBuilder(xStudioPath);
    }

    public static class CommandBuilder {
        private final String xStudioPath;
        private Optional<String> licensePath = Optional.absent();
        private Optional<String> xapplPath = Optional.absent();
        private Optional<String> imagePath = Optional.absent();
        private Optional<String> startupFilePath = Optional.absent();
        private ArrayList<String> dependencies = new ArrayList<String>();

        public CommandBuilder(String xStudioPath) {
            this.xStudioPath = xStudioPath;
        }

        public CommandBuilder licensePath(String path) {
            licensePath = Optional.of(path);
            return this;
        }

        public CommandBuilder xapplPath(String path) {
            xapplPath = Optional.of(path);
            return this;
        }

        public CommandBuilder imagePath(String path) {
            imagePath = Optional.of(path);
            return this;
        }

        public CommandBuilder startupFilePath(String path) {
            startupFilePath = Optional.of(path);
            return this;
        }

        public CommandBuilder dependency(String dependency) {
            dependencies.add(dependency);
            return this;
        }

        public BuildCommand build() {
            checkState(xapplPath.isPresent(), String.format(REQUIRE_PRESENT_S, "xapplPath"));
            checkState(imagePath.isPresent(), String.format(REQUIRE_PRESENT_S, "imagePath"));

            ArgumentListBuilder args = new ArgumentListBuilder(xStudioPath)
                    .add(xapplPath.get())
                    .add("/o")
                    .add(imagePath.get())
                    .add("/component")
                    .add("/uncompressed");


            if (startupFilePath.isPresent()) {
                args.add("/startupfile")
                        .addQuoted(startupFilePath.get());
            }

            if (licensePath.isPresent()) {
                args.add("/l")
                        .add(licensePath.get());
            }

            for (String dependency : dependencies) {
                args.add("/dependency")
                        .add(dependency);
            }

            return new BuildCommand(args);
        }
    }
}
