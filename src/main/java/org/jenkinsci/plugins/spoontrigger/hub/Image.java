package org.jenkinsci.plugins.spoontrigger.hub;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode
public class Image {
    private static final Pattern ParseImageNamePattern = Pattern.compile("^(?:(?<namespace>[^\\s/]+)/)?(?<repo>[^\\s:]+)(?::(?<tag>\\S+))?$");

    @Getter
    private final String repo;
    @Getter
    private final String namespace;
    @Getter
    private final String tag;

    public static Image parse(String imageName) {
        checkArgument(imageName != null, "imageName");

        String imageNameToUse = imageName.trim();
        Matcher matcher = ParseImageNamePattern.matcher(imageNameToUse);

        checkArgument(matcher.matches(), "failed to parse image name");

        String repo = matcher.group("repo");
        String namespace = matcher.group("namespace");
        String tag = matcher.group("tag");

        return new Image(namespace, repo, tag);
    }

    public Image(String namespace, String repo, String tag) {
        this.namespace = namespace;
        this.repo = repo;
        this.tag = tag;
    }

    public String printIdentifier() {
        String result = repo;

        if (namespace != null) {
            result = namespace + "/" + result;
        }

        if (tag != null) {
            result = result + ":" + tag;
        }

        return result;
    }
}
