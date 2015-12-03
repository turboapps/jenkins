package org.jenkinsci.plugins.spoontrigger.hub;

import hudson.model.TaskListener;
import jenkins.util.BuildListenerAdapter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class HubApiTests {
    private static final String MissingImageName = "mozilla/ffox";
    private static final String ImageName = "mozilla/firefox";
    private static final String MissingTag = "missing-tag";
    private static final String Tag = "42.0";

    private Image image;
    private boolean shouldExist;

    private HubApi hubApi;

    public HubApiTests(String imageName, boolean shouldExist) {
        this.image = Image.parse(imageName);
        this.shouldExist = shouldExist;

        this.hubApi = new HubApi(new BuildListenerAdapter(TaskListener.NULL));
    }

    @Parameterized.Parameters
    public static Iterable data() {
        return Arrays.asList(
                new Object[][]{
                        {ImageName, true},
                        {ImageName + ":" + Tag, true},
                        {ImageName + ":" + MissingTag, false},
                        {MissingImageName, false}
                }
        );
    }

    @Test
    public void parseImage() throws Exception {
        boolean isAvailableRemotely = hubApi.isAvailableRemotely(image);

        Assert.assertEquals(shouldExist, isAvailableRemotely);
    }
}
