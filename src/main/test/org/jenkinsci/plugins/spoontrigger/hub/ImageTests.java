package org.jenkinsci.plugins.spoontrigger.hub;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class ImageTests {
    private static final String Repo = "name";
    private static final String Namespace = "namespace";
    private static final String Tag = "1.0";

    private String imageName;
    private String repo;
    private String namespace;
    private String tag;

    public ImageTests(String imageName, String namespace, String repo, String tag) {
        this.imageName = imageName;
        this.namespace = namespace;
        this.repo = repo;
        this.tag = tag;
    }

    @Parameterized.Parameters
    public static Iterable data() {
        return Arrays.asList(
                new Object[][]{
                        {Namespace + "/" + Repo + ":" + Tag, Namespace, Repo, Tag},
                        {Repo, null, Repo, null},
                        {Repo + ":" + Tag, null, Repo, Tag},
                        {Namespace + "/" + Repo, Namespace, Repo, null}
                }
        );
    }

    @Test
    public void parseImage() {
        Image image = Image.parse(imageName);

        Assert.assertEquals(repo, image.getRepo());
        Assert.assertEquals(namespace, image.getNamespace());
        Assert.assertEquals(tag, image.getTag());
    }
}
