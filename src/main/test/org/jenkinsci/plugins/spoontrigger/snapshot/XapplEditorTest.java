package org.jenkinsci.plugins.spoontrigger.snapshot;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;

public class XapplEditorTest {
    @Test
    public void removeNodeTest() throws Exception {
        String pathToRemove = "@SYSDRIVE@\\tmp\\vagrant-shell.ps1";

        XapplEditor editor = new XapplEditor();
        editor.load(new ByteArrayInputStream(TEST_DOCUMENT.getBytes(StandardCharsets.UTF_8)));
        editor.removeFile(pathToRemove);

        assertFalse(editor.fileExists(pathToRemove));
    }

    private static final String TEST_DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<Configuration appVersion=\"16.0.482\" publisher=\"Turbo.net\" version=\"10.6\">\n" +
            "  <StartupFiles>\n" +
            "    <StartupFile architecture=\"AnyCpu\" commandLine=\"\" default=\"True\" node=\"@PROGRAMFILESX86@\\ASIO4ALL v2\\ASIO4ALL v2 Instruction Manual.pdf\" tag=\"ASIO4ALL v2 Instruction Manual\"/>\n" +
            "  </StartupFiles>\n" +
            "  <Layers>\n" +
            "    <Layer name=\"Default\">\n" +
            "      <Filesystem>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@STARTMENU@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@STARTMENUCOMMON@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@STARTUP@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@STARTUPCOMMON@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@SYSDRIVE@\" noSync=\"False\" readOnly=\"False\">\n" +
            "          <Directory created=\"2015-12-16T12:01:57.0783201Z\" hide=\"False\" isolation=\"Merge\" modified=\"2015-12-16T12:01:58.4681696Z\" name=\"tmp\" noSync=\"False\" readOnly=\"False\">\n" +
            "            <File created=\"2015-12-16T12:01:57.1573743Z\" hide=\"False\" isolation=\"Full\" modified=\"2015-12-16T12:08:35.561968Z\" name=\"vagrant-shell.ps1\" readOnly=\"False\" source=\".\\Files\\@SYSDRIVE@\\tmp\\vagrant-shell.ps1\" upgradeable=\"True\"/>\n" +
            "          </Directory>\n" +
            "        </Directory>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@SYSTEM@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@SYSWOW64@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@TEMPLATES@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@TEMPLATESCOMMON@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@VIDEOS@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "        <Directory hide=\"False\" isolation=\"Merge\" name=\"@WINDIR@\" noSync=\"False\" readOnly=\"False\"/>\n" +
            "      </Filesystem>\n" +
            "    </Layer>\n" +
            "  </Layers>\n" +
            "</Configuration>\n";
}
