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
    private boolean appendDate;
    private String organization;
    private boolean overwriteOrganization;
}
