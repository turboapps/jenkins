package org.jenkinsci.plugins.spoontrigger.git;

import hudson.Util;
import hudson.model.Cause;
import lombok.Getter;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_NOT_NULL_OR_EMPTY_S;

public class PushCause extends Cause {

    @Getter
    private final Repository repository;
    @Getter
    private final Branch branch;
    @Getter
    private final String pusher;

    public PushCause(String repositoryUrl, String pusher, String branch, String head) {
        checkArgument(Util.fixEmptyAndTrim(pusher) != null, REQUIRE_NOT_NULL_OR_EMPTY_S, "pusher");

        this.repository = new Repository(repositoryUrl);
        this.branch = new Branch(branch, head);
        this.pusher = pusher;
    }

    @Override
    public String getShortDescription() {
        return String.format("New HEAD (%s) in repository (%s) pushed by (%s) to (%s) branch",
                this.branch.getHead(), this.repository.getUrl(), this.pusher, this.branch.getName());
    }
}
