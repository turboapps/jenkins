package org.jenkinsci.plugins.spoontrigger.hub;

import com.google.common.base.Optional;
import hudson.model.TaskListener;
import jenkins.util.BuildListenerAdapter;
import org.junit.Assert;
import org.junit.Test;

public class HubApiTests {
    private static final String HubApiKey = "secret";
    private static final String ImageName = "turbobrowsers/turbobase";

    public static HubApi createHubApi(String hubUrl) {
        return new HubApi(hubUrl, HubApiKey, new BuildListenerAdapter(TaskListener.NULL));
    }

    @Test
    public void getLatestVersion() throws Exception {
        // given
        HubApi hubApi = createHubApi("https://stage.turbo.net");
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
