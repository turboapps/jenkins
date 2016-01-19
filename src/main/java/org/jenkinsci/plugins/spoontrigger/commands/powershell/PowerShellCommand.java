package org.jenkinsci.plugins.spoontrigger.commands.powershell;

import com.google.common.base.Optional;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;

import java.nio.file.Path;

public class PowerShellCommand extends VoidCommand {
    protected PowerShellCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static class CommandBuilder {
        private Optional<Path> scriptPath = Optional.absent();

        public CommandBuilder() {
        }

        public CommandBuilder scriptPath(Path scriptPath) {
            this.scriptPath = Optional.of(scriptPath);
            return this;
        }

        public PowerShellCommand build() {
            ArgumentListBuilder args = new ArgumentListBuilder("powershell");

            if (scriptPath.isPresent()) {
                args.add("-File")
                        .add(this.scriptPath.get().toFile());
            }

            return new PowerShellCommand(args);
        }
    }
}
