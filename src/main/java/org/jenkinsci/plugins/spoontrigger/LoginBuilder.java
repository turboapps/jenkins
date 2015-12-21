package org.jenkinsci.plugins.spoontrigger;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ConfigCommand;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.LoginCommand;
import org.jenkinsci.plugins.spoontrigger.utils.Credentials;
import org.jenkinsci.plugins.spoontrigger.validation.Level;
import org.jenkinsci.plugins.spoontrigger.validation.StringValidators;
import org.jenkinsci.plugins.spoontrigger.validation.Validator;
import org.jenkinsci.plugins.spoontrigger.validation.Validators;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.Messages.IGNORE_PARAMETER;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_SINGLE_WORD_S;
import static org.jenkinsci.plugins.spoontrigger.utils.Credentials.checkCredetntials;
import static org.jenkinsci.plugins.spoontrigger.utils.Credentials.fillCredentialsIdItems;

public class LoginBuilder extends BaseBuilder {

    @Getter
    private final String credentialsId;
    @Getter
    private final String hubUrl;

    @DataBoundConstructor
    public LoginBuilder(String credentialsId, String hubUrl) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
        this.hubUrl = Util.fixEmptyAndTrim(hubUrl);
    }

    @Override
    public void prebuild(SpoonBuild build, BuildListener listener) {
        Optional<StandardUsernamePasswordCredentials> credentials = this.getCredentials();
        if (credentials.isPresent()) {
            build.setCredentials(credentials.get());
        }
    }

    @Override
    protected boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        CommandDriver client = CommandDriver.scriptBuilder(build).launcher(launcher).listener(listener).ignoreErrorCode(false).build();

        switchHub(client);

        Optional<StandardUsernamePasswordCredentials> credentials = build.getCredentials();
        if (credentials.isPresent()) {
            login(client, credentials.get());
        }

        return true;
    }

    private void switchHub(CommandDriver client) {
        ConfigCommand.CommandBuilder cmdBuilder = ConfigCommand.builder();
        if (Strings.isNullOrEmpty(this.hubUrl)) {
            cmdBuilder.reset(true);
        } else {
            cmdBuilder.hub(this.hubUrl);
        }

        ConfigCommand configCommand = cmdBuilder.build();
        configCommand.run(client);
    }

    private void login(CommandDriver client, StandardUsernamePasswordCredentials credentials) {
        LoginCommand loginCmd = LoginCommand.builder().login(credentials.getUsername()).password(credentials.getPassword()).build();
        loginCmd.run(client);
    }

    private Optional<StandardUsernamePasswordCredentials> getCredentials() throws IllegalStateException {
        if (Strings.isNullOrEmpty(this.credentialsId)) {
            return Optional.absent();
        }

        Optional<StandardUsernamePasswordCredentials> credentials = Credentials.lookupById(StandardUsernamePasswordCredentials.class, this.credentialsId);

        checkState(credentials.isPresent(), "Cannot find any credentials with id (%s)", this.credentialsId);

        return credentials;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final Validator<String> NULL_OR_SINGLE_WORD_VALIDATOR = Validators.chain(
                StringValidators.isNotNull(IGNORE_PARAMETER, Level.OK),
                StringValidators.isSingleWord(String.format(REQUIRE_SINGLE_WORD_S, "Parameter")));

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Login to Turbo Hub";
        }

        public FormValidation doCheckHubUrl(@QueryParameter String value) {
            String hubUrl = Util.fixEmptyAndTrim(value);
            return Validators.validate(NULL_OR_SINGLE_WORD_VALIDATOR, hubUrl);
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Item project, @QueryParameter String value) {
            return checkCredetntials(project, value);
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
            return fillCredentialsIdItems(project);
        }
    }
}
