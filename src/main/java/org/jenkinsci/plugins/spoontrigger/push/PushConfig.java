package org.jenkinsci.plugins.spoontrigger.push;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PushConfig {
    private String remoteImageName;
    private String dateFormat;
    private boolean appendDate;
    private String organization;
    private boolean overwriteOrganization;
}
