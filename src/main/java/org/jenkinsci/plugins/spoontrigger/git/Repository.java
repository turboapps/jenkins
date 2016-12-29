package org.jenkinsci.plugins.spoontrigger.git;

import hudson.Util;
import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.*;

@Data
public final class Repository {
    private static final String ORGANIZATION_GROUP = "organization";
    private static final String PROJECT_GROUP = "project";

    private static final Pattern GIT_REPOSITORY_PATTERN;

    static {
        String pattern = String.format("^https?://([^/]+)/(?<%s>[^/]+)/(?<%s>[^/]+)$", ORGANIZATION_GROUP, PROJECT_GROUP);
        GIT_REPOSITORY_PATTERN = Pattern.compile(pattern);
    }

    private final String url;
    private final String organization;
    private final String project;

    public Repository(String url) {
        checkArgument(Util.fixEmptyAndTrim(url) != null, REQUIRE_NON_EMPTY_STRING_S, "url", url);

        Matcher matcher = GIT_REPOSITORY_PATTERN.matcher(url);
        checkArgument(matcher.find(), REQUIRE_VALID_FORMAT_SP, "url", url);

        this.url = url;
        this.organization = matcher.group(ORGANIZATION_GROUP);
        this.project = matcher.group(PROJECT_GROUP);
    }
}
