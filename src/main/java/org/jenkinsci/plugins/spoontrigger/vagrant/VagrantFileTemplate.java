package org.jenkinsci.plugins.spoontrigger.vagrant;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import hudson.util.ArgumentListBuilder;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.misc.ErrorBuffer;
import org.stringtemplate.v4.misc.STMessage;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class VagrantFileTemplate {
    private static final String VAGRANT_FILE_TEMPLATE_RESOURCE_ID = "Vagrantfile.template";
    private static final Charset CHARSET = Charsets.UTF_8;

    private static final String TOOLS_DIR = "C:\\vagrant\\tools";
    private static final String OUTPUT_DIR = "C:\\vagrant\\output";
    private static final String XSTUDIO_PATH = TOOLS_DIR + "\\xstudio.exe";
    private static final String SNAPSHOT_PATH = OUTPUT_DIR + "\\snapshot";

    private final String installScriptFileName;
    private final String box;

    public VagrantFileTemplate(String installScriptFileName, String box) {
        this.installScriptFileName = installScriptFileName;
        this.box = box;
    }

    public void save(Path templatePath) throws IOException {
        ST template = getTemplate();
        template.add("t", this);

        Files.deleteIfExists(templatePath);

        ErrorBuffer errorBuffer = new ErrorBuffer();
        template.write(templatePath.toFile(), errorBuffer, CHARSET.name());

        if (!errorBuffer.errors.isEmpty()) {
            StringBuilder msgBuilder = new StringBuilder();
            msgBuilder.append("Failed to generate Vagrantfile due to following errors:");

            List<STMessage> messages = errorBuffer.errors;
            for (STMessage message : messages) {
                msgBuilder.append(System.lineSeparator());
                msgBuilder.append(message.toString());
            }

            throw new IllegalStateException(msgBuilder.toString());
        }
    }

    public String getBox() {
        return this.box;
    }

    public String getBeforeSnapshotCommand() {
        return new RubyArgumentListBuilder()
                .addPath(XSTUDIO_PATH)
                .add("/before")
                .add("/beforepath")
                .addPath(SNAPSHOT_PATH)
                .toString();
    }

    public String getInstallCommand() {
        return "install//" + installScriptFileName;
    }

    public String getAfterSnapshotCommand() {
        return new RubyArgumentListBuilder()
                .addPath(XSTUDIO_PATH)
                .add("/after")
                .add("/beforepath")
                .addPath(SNAPSHOT_PATH)
                .add("/o")
                .addPath(OUTPUT_DIR)
                .toString();
    }

    private ST getTemplate() throws IOException {
        URL resourceId = Resources.getResource(getClass(), VAGRANT_FILE_TEMPLATE_RESOURCE_ID);
        String template = Joiner.on(System.lineSeparator()).join(Resources.readLines(resourceId, CHARSET));
        return new ST(template, '$', '$');
    }

    private static class RubyArgumentListBuilder extends ArgumentListBuilder {
        private final String backSlash = "\\\\";
        private final String doubleBackSlash = backSlash + backSlash;

        public RubyArgumentListBuilder addPath(String path) {
            String pathToUse = path.replaceAll(backSlash, doubleBackSlash);
            return add(pathToUse);
        }

        @Override
        public RubyArgumentListBuilder add(String a) {
            super.add(a);
            return this;
        }
    }
}
