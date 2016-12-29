package org.jenkinsci.plugins.spoontrigger;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.SequentialExecutionQueue;
import jenkins.model.Jenkins;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.git.PushCause;
import org.jenkinsci.plugins.spoontrigger.utils.Identity;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

public class SpoonTrigger extends Trigger<AbstractProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(SpoonTrigger.class.getName());

    @Getter
    private final String repositoryUrl;

    @DataBoundConstructor
    public SpoonTrigger(String repositoryUrl) {
        this.repositoryUrl = Util.fixEmptyAndTrim(repositoryUrl);
    }

    public void run(PushCause cause) {
        ScheduledBuild runnable = new ScheduledBuild(super.job, cause);
        DescriptorImpl descriptor = this.getDescriptor();
        descriptor.queueJob(runnable);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
    }

    private static final class ScheduledBuild implements Runnable {
        private final AbstractProject project;
        private final PushCause cause;

        public ScheduledBuild(AbstractProject project, PushCause cause) {
            this.project = project;
            this.cause = cause;
        }

        @Override
        public void run() {
            final boolean scheduled = this.project.scheduleBuild(this.cause);
            String msgPattern = scheduled ? "Changes detected in (%s). Triggering (%s) build."
                    : "Ignoring changes in (%s). Build (%s) is already in the queue";
            String msg = String.format(msgPattern, this.cause.getRepository(), this.project.getName());
            LOGGER.info(msg);
        }
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        private static final String DEFAULT_URL;
        private static final Validator<String> REPOSITORY_STRING_VALIDATOR;
        private static final Validator<String> WEB_HOOK_VALIDATOR;

        static {
            DEFAULT_URL = Jenkins.getInstance().getRootUrl() + SpoonWebHook.getInstance().getUrlName();

            Validator<String> notNullValidator = StringValidators.isNotNull(String.format(Messages.REQUIRE_NON_EMPTY_STRING_S, "URL"), Level.ERROR);
            WEB_HOOK_VALIDATOR = Validators.chain(notNullValidator, new ConnectionValidator());
            REPOSITORY_STRING_VALIDATOR = Validators.chain(notNullValidator,
                    new PredicateValidator<String>(Patterns.Predicates.REPOSITORY_NAME, "Parameter is not a correct URL to GitHub repository", Level.ERROR));
        }

        private transient final SequentialExecutionQueue queue;

        private URL defaultHookUrl;

        public DescriptorImpl() {
            this.load();

            this.queue = new SequentialExecutionQueue(Jenkins.MasterComputer.threadPoolForRemoting);
        }

        public void queueJob(Runnable runnable) {
            this.queue.execute(runnable);
        }

        public FormValidation doCheckRepositoryUrl(@QueryParameter String value) {
            String repositoryUrl = Util.fixEmptyAndTrim(value);
            return Validators.validate(REPOSITORY_STRING_VALIDATOR, repositoryUrl);
        }

        public FormValidation doCheckHookUrl(@QueryParameter String value) {
            String rawWebHookUrl = Util.fixEmptyAndTrim(value);
            return Validators.validate(WEB_HOOK_VALIDATOR, rawWebHookUrl);
        }

        public URL getHookUrl() throws MalformedURLException {
            if (this.defaultHookUrl == null) {
                this.defaultHookUrl = new URL(Jenkins.getInstance().getRootUrl() + SpoonWebHook.getInstance().getUrlName());
            }
            return this.defaultHookUrl;
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof SpoonProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when a Turbo webhook message is received";
        }

        private static final class ConnectionValidator implements Validator<String> {
            @Override
            public void validate(String url) throws ValidationException {
                FormValidation formValidation = this.testConnection(url);
                throw new ValidationException(formValidation);
            }

            private FormValidation testConnection(String url) {
                try {
                    if (!DEFAULT_URL.equalsIgnoreCase(url)) {
                        String errMsg = String.format("Parameter is not intended to be changed. Initial value of WebHook URL (%s) will remain active.", DEFAULT_URL);
                        return FormValidation.error(errMsg);
                    }

                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty(SpoonWebHook.URL_VALIDATION_HEADER, "true");
                    connection.connect();

                    if (connection.getResponseCode() != SpoonWebHook.HTTP_OK) {
                        String msg = String.format("Got %s from %s", connection.getResponseCode(), url);
                        return FormValidation.warning(msg);
                    }

                    String identityValue = connection.getHeaderField(SpoonWebHook.X_INSTANCE_IDENTITY);
                    if (identityValue == null) {
                        String msg = String.format("It doesn't look like %s to any Jenkins", url);
                        return FormValidation.warning(msg);
                    }

                    if (Identity.isDefault(identityValue)) {
                        return FormValidation.warning("Default identity in response header. It is expected if you not have defined SSH identity for Jenkins.");
                    }

                    return FormValidation.ok();
                } catch (IOException ex) {
                    String errMsg = String.format("Failed to test a connection to %s", url);
                    return FormValidation.error(ex, errMsg);
                }
            }
        }
    }
}
