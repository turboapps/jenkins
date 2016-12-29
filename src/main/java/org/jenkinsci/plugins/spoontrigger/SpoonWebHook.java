package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.plugins.spoontrigger.git.PushCause;
import org.jenkinsci.plugins.spoontrigger.git.Repository;
import org.jenkinsci.plugins.spoontrigger.utils.Identity;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkState;

@Extension
public class SpoonWebHook implements UnprotectedRootAction {

    public static final int HTTP_OK = 200;

    private static final String URL_NAME = "turbo-webhook";
    private static final Function<AbstractProject, SpoonTrigger> GET_SPOON_TRIGGER = new GetSpoonTrigger();

    static final String URL_VALIDATION_HEADER = "X-Jenkins-Validation";
    static final String X_INSTANCE_IDENTITY = "X-Instance-Identity";

    @Inject
    private InstanceIdentity identity;

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return URL_NAME;
    }

    public static SpoonWebHook getInstance() {
        return Jenkins.getInstance().getExtensionList(RootAction.class).get(SpoonWebHook.class);
    }

    @RequirePOST
    public void doIndex(StaplerRequest request, StaplerResponse response) {
        if (isJenkinsValidation(request)) {
            response.setHeader(X_INSTANCE_IDENTITY, Identity.getValueOrDefault(this.identity));
            response.setStatus(HTTP_OK);
            return;
        }

        String payload = getPayload(request);

        checkState(payload != null, "Not intended to be browsed interactively (must specify payload parameter)."
                + " Ensure that the web hook Content-Type header is application/x-www-form-urlencoded");

        String eventName = getEventType(request);
        GitHubEvent event = GitHubEvent.from(eventName);
        switch (event) {
            case PING:
            case SUPPORT:
                response.setStatus(HTTP_OK);
                break;
            case PUSH:
                PushCause cause = createCause(payload);
                this.triggerBuilds(Jenkins.getInstance(), cause);
                break;
            case UNKNOWN:
                String msg = String.format("Turbo WebHook event type (%s) is not supported. Only push and support events are supported", eventName);
                throw new IllegalArgumentException(msg);
        }
    }

    void triggerBuilds(Jenkins server, PushCause cause) {
        for (SpoonTrigger trigger : getAllTriggers(server)) {
            if(shouldRun(trigger, cause)) {
                trigger.run(cause);
            }
        }
    }

    private static Iterable<SpoonTrigger> getAllTriggers(Jenkins server) {
        return FluentIterable.from(server.getAllItems(AbstractProject.class))
                .transform(GET_SPOON_TRIGGER)
                .filter(Predicates.notNull());
    }

    private static PushCause createCause(String payload) throws IllegalStateException {
        try {
            JSONObject json = JSONObject.fromObject(payload);
            String repository = json.getJSONObject("repository").getString("url");
            String pusher = json.getJSONObject("pusher").getString("name");
            String newHeadId = json.getString("after");
            String branch = json.getString("ref");
            return new PushCause(repository, pusher, branch, newHeadId);
        } catch (JSONException ex) {
            throw new IllegalStateException("Failed parsing web hook payload", ex);
        }
    }

    private static boolean shouldRun(SpoonTrigger trigger, PushCause cause) {
        String triggerRepo = Util.fixNull(trigger.repositoryUrl);
        Repository causeRepo = cause.repository;
        return triggerRepo.equalsIgnoreCase(causeRepo.url);
    }

    private static boolean isJenkinsValidation(StaplerRequest request) {
        return request.getHeader(URL_VALIDATION_HEADER) != null;
    }

    private static String getEventType(StaplerRequest request) {
        return request.getHeader("X-GitHub-Event");
    }

    private static String getPayload(StaplerRequest request) {
        return request.getParameter("payload");
    }

    private static enum GitHubEvent {
        UNKNOWN,
        PING,
        SUPPORT,
        PUSH;

        public static GitHubEvent from(@Nullable String name) {
            if(Strings.isNullOrEmpty(name)) {
                return SUPPORT;
            }

            try {
                return GitHubEvent.valueOf(name.toUpperCase(Locale.ROOT));
            }catch (IllegalArgumentException ex) {
                return UNKNOWN;
            }
        }
    }

    private static final class GetSpoonTrigger implements Function<AbstractProject, SpoonTrigger> {
        @Nullable
        @Override
        public SpoonTrigger apply(@Nullable AbstractProject project) {
            if(project == null) {
                return null;
            }
            return (SpoonTrigger) project.getTrigger(SpoonTrigger.class);
        }
    }
}
