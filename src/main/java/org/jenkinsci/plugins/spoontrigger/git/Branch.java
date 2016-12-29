package org.jenkinsci.plugins.spoontrigger.git;

import hudson.Util;
import lombok.Data;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_NOT_NULL_OR_EMPTY_S;

@Data
public final class Branch {
    private static final int HEAD_LENGTH = 40;
    private static final int HEAD_CHUNK_LENGTH = 6;

    private final String name;
    private final String head;

    public Branch(String name, String head) {
        checkArgument(Util.fixEmptyAndTrim(name) != null, REQUIRE_NOT_NULL_OR_EMPTY_S, "name");
        checkArgument(Util.fixEmptyAndTrim(head) != null, REQUIRE_NOT_NULL_OR_EMPTY_S, "head");
        checkArgument(head.length() == HEAD_LENGTH, "head must be %s characters long", HEAD_LENGTH);

        this.name = name;
        this.head = head;
    }

    public Branch(hudson.plugins.git.Branch branch) {
        this(branch.getName(), branch.getSHA1String());
    }

    public String getShortName() {
        int shortNameStart = this.name.lastIndexOf('/') + 1;
        if (shortNameStart == 0 || shortNameStart == this.name.length()) {
            return this.name;
        }
        return this.name.substring(shortNameStart);
    }

    public String getHeadChunk() {
        return this.head.substring(0, HEAD_CHUNK_LENGTH);
    }
}
