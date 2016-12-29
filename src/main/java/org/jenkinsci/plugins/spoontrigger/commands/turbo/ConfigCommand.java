package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import hudson.util.ArgumentListBuilder;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.FilterOutputCommand;

import java.util.Collection;
import java.util.regex.Pattern;

public class ConfigCommand extends FilterOutputCommand {

    private static final Pattern HUB_URL_PATTERN = Pattern.compile("^Hub server:\\s+(?<hubUrl>\\S+)$");

    @Getter
    private Optional<String> hub = Optional.absent();

    ConfigCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    @Override
    public void run(CommandDriver client) throws IllegalStateException {
        super.run(client);

        Collection<String> patterns = findInOutput(HUB_URL_PATTERN);
        if (patterns.size() > 0) {
            String hubUrl = Iterables.getLast(patterns);
            hub = Optional.of(hubUrl);
        }
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static class CommandBuilder {
        private Optional<Boolean> reset = Optional.absent();
        private Optional<String> hub = Optional.absent();

        public CommandBuilder reset(boolean value) {
            this.reset = Optional.of(value);
            return this;
        }

        public CommandBuilder hub(String hubUrl) {
            this.hub = Optional.of(hubUrl);
            return this;
        }

        public ConfigCommand build() {
            ArgumentListBuilder buildArgs = new ArgumentListBuilder(SPOON_CLIENT, "config");

            if (reset.or(Boolean.FALSE)) {
                buildArgs.add("--reset");
            }

            if (hub.isPresent()) {
                buildArgs.add("--hub=" + hub.get());
            }

            return new ConfigCommand(buildArgs);
        }
    }
}
