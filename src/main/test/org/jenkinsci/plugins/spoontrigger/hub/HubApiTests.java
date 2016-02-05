package org.jenkinsci.plugins.spoontrigger.hub;

import com.google.common.base.Optional;
import hudson.model.TaskListener;
import jenkins.util.BuildListenerAdapter;
import org.junit.Assert;
import org.junit.Test;

public class HubApiTests {
    private static final String ImageName = "mozilla/firefox";

    @Test
    public void getLatestVersion() throws Exception {
        // given
        HubApi hubApi = new HubApi("https://turbo.net", new BuildListenerAdapter(TaskListener.NULL));
        Image image = Image.parse(ImageName);

        // when
        Image latestImage = hubApi.getLatestVersion(image);
        final String tag = latestImage.getTag();

        // then
        Assert.assertNotNull(tag);

        Optional<Version> versionOpt = Version.tryParse(tag);
        Assert.assertTrue(versionOpt.isPresent());

        Version latestVersion = versionOpt.get();
        Assert.assertTrue(latestVersion.compareTo(Version.tryParse("43.0").get()) > 0);
    }
}
