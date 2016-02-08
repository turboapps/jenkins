package org.jenkinsci.plugins.spoontrigger.hub;

import com.google.common.base.Optional;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class VersionParametrizedTests {

    private final String versionName;

    public VersionParametrizedTests(String versionName) {
        this.versionName = versionName;
    }

    @Parameterized.Parameters
    public static Iterable data() {
        return Arrays.asList(
                new Object[][]{
                        {"44.0.0.4096"}
                }
        );
    }

    @Test
    public void parseVersion() {
        // when
        Optional<Version> versionOpt = Version.tryParse(versionName);

        // then
        Assert.assertTrue(versionOpt.isPresent());
        Assert.assertEquals(versionName, versionOpt.get().toString());
    }
}
