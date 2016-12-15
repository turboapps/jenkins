package org.jenkinsci.plugins.spoontrigger.push;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jenkinsci.plugins.spoontrigger.hub.Image;

@Data
@AllArgsConstructor
public class PushConfig {
    private Image localImage;
    private String remoteImageName;
    private String dateFormat;
    private TagGenerationStrategy tagGenerationStrategy;
    private String organization;
    private boolean overwriteOrganization;
    private String hubUrls;
    private boolean buildExe;

    public Image getRemoteImage() {
        return Image.parse(remoteImageName);
    }
}

