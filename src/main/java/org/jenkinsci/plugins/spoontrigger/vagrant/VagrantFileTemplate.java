package org.jenkinsci.plugins.spoontrigger.vagrant;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import hudson.Util;
import hudson.util.ArgumentListBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VagrantFileTemplate {
    private static final String VAGRANT_FILE_TEMPLATE_RESOURCE_ID = "Vagrantfile.template";
    private static final Charset CHARSET = Charsets.UTF_8;

    private final String installScriptFileName;
    private final String vagrantBox;
    private final String startupFile;

    public VagrantFileTemplate(String installScriptFileName, String vagrantBox, @Nullable String startupFile) {
        this.installScriptFileName = installScriptFileName;
        this.vagrantBox = vagrantBox;
        this.startupFile = startupFile;
    }

    public void save(Path templatePath) throws IOException {
        ArrayList<String> content = new ArrayList<String>(Arrays.asList(
                "# Parameters",
                "",
                String.format("install_script = \"%s\"", installScriptFileName),
                String.format("vagrant_box = \"%s\"", vagrantBox),
                String.format("startup_file = \"%s\"", getStartupFile()),
                ""));
        content.addAll(getTemplate());
        Files.write(
                templatePath,
                content,
                CHARSET,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private List<String> getTemplate() throws IOException {
        URL resourceId = Resources.getResource(getClass(), VAGRANT_FILE_TEMPLATE_RESOURCE_ID);
        return Resources.readLines(resourceId, CHARSET);
    }

    private String getStartupFile() {
        String startupFileToUse = Util.fixEmptyAndTrim(startupFile);

        if (startupFileToUse == null) {
            return "";
        }

        final String backSlash = "\\\\";
        startupFileToUse = startupFileToUse.replaceAll(backSlash, backSlash + backSlash);

        // wrap in ArgumentListBuilder to add quotes if necessary
        return new ArgumentListBuilder(startupFileToUse).toString();
    }
}
