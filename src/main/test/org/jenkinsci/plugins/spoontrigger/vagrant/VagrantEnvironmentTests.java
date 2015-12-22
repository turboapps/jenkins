package org.jenkinsci.plugins.spoontrigger.vagrant;

import junit.framework.Assert;
import org.jenkinsci.plugins.spoontrigger.SnapshotBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jenkinsci.plugins.spoontrigger.vagrant.VagrantEnvironment.*;

public class VagrantEnvironmentTests {

    @Rule
    public TemporaryFolder workingDir = new TemporaryFolder();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void canSetupEnvironment() throws IOException {
        // given
        tempDir.newFile(XSTUDIO_EXE_FILE);
        tempDir.newFile(XSTUDIO_LICENSE_FILE);
        tempDir.newFile(INSTALLER_EXE_FILE);
        final String boxName = SnapshotBuilder.DescriptorImpl.DEFAULT_VAGRANT_BOX;
        final VagrantEnvironment.EnvironmentBuilder builder = VagrantEnvironment.builder(workingDir.getRoot().toPath());

        // when
        VagrantEnvironment environment = builder.generateInstallScript("/S", true)
                .box(boxName)
                .xStudioPath(getTempPath(XSTUDIO_EXE_FILE))
                .installerPath(getTempPath(INSTALLER_EXE_FILE))
                .build();

        // then
        String workingDirPath = workingDir.getRoot().getPath();
        Assert.assertTrue(Files.exists(Paths.get(workingDirPath, INSTALL_DIRECTORY, INSTALL_SCRIPT_FILE)));
        Assert.assertTrue(Files.exists(Paths.get(workingDirPath, INSTALL_DIRECTORY, INSTALLER_EXE_FILE)));
        Assert.assertTrue(Files.exists(Paths.get(workingDirPath, TOOLS_DIRECTORY, XSTUDIO_EXE_FILE)));

        // when
        environment.close();

        Assert.assertTrue(Files.exists(Paths.get(workingDirPath, VAGRANT_FILE)));
        String[] workspaceFiles = workingDir.getRoot().list();
        Assert.assertNotNull(workspaceFiles);
        Assert.assertTrue(workspaceFiles.length == 1); // contains only Vagrantfile which is left for debugging purpose
    }

    private String getTempPath(String filename) {
        return Paths.get(tempDir.getRoot().getPath(), filename).toString();
    }
}
