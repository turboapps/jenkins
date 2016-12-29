package org.jenkinsci.plugins.spoontrigger.push;


import org.jenkinsci.plugins.spoontrigger.hub.Image;


public class PushConfig {
    public final Image localImage;
    public final String remoteImageName;
    public final String dateFormat;
    public final TagGenerationStrategy tagGenerationStrategy;
    public final String organization;
    public final boolean overwriteOrganization;
    public final String hubUrls;
    public final boolean buildExe;

    public PushConfig(
            Image localImage,
            String remoteImageName,
            String dateFormat,
            TagGenerationStrategy tagGenerationStrategy,
            String organization,
            boolean overwriteOrganization,
            String hubUrls,
            boolean buildExe) {
        this.localImage = localImage;
        this.remoteImageName = remoteImageName;
        this.dateFormat = dateFormat;
        this.tagGenerationStrategy = tagGenerationStrategy;
        this.organization = organization;
        this.overwriteOrganization = overwriteOrganization;
        this.hubUrls = hubUrls;
        this.buildExe = buildExe;
    }

    public Image getRemoteImage() {
        return Image.parse(remoteImageName);
    }
}

