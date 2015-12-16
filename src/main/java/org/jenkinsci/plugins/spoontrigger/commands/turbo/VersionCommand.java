package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.FilterOutputCommand;

import java.util.Collection;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

public class VersionCommand extends FilterOutputCommand {

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\s*Version:\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    private VersionCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    @Override
    public void run(CommandDriver client) throws IllegalStateException {
        super.run(client);

        Collection<String> versions = findInOutput(VERSION_PATTERN);
        checkState(!versions.isEmpty(), "Failed to find the version of Turbo installed on the host machine in the process output");
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static final class CommandBuilder {

        public VersionCommand build() {
            ArgumentListBuilder versionArgs = new ArgumentListBuilder(SPOON_CLIENT, "version");
            return new VersionCommand(versionArgs);
        }
    }
}
