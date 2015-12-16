package org.jenkinsci.plugins.spoontrigger.commands.turbo;

import com.google.common.base.Optional;
import hudson.Util;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

public final class LoginCommand extends VoidCommand {

    private LoginCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public static CommandBuilder builder() {
        return new CommandBuilder();
    }

    public static final class CommandBuilder {

        private Optional<String> login = Optional.absent();
        private Optional<Secret> password = Optional.absent();

        public CommandBuilder login(String login) {
            checkArgument(Util.fixEmptyAndTrim(login) != null, REQUIRE_NON_EMPTY_STRING_S, "login");

            this.login = Optional.of(login.trim());
            return this;
        }

        public CommandBuilder password(Secret password) {
            checkArgument(password != null, REQUIRE_NOT_NULL_S, "password");

            this.password = Optional.of(password);
            return this;
        }

        public LoginCommand build() {
            checkState(this.login.isPresent(), REQUIRE_PRESENT_S, "login");
            checkState(this.password.isPresent(), REQUIRE_PRESENT_S, "password");

            ArgumentListBuilder loginArgs = new ArgumentListBuilder(SPOON_CLIENT, "login");
            loginArgs.add(this.login.get());
            loginArgs.addMasked(this.password.get());
            return new LoginCommand(loginArgs);
        }
    }
}
