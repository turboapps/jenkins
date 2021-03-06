package org.jenkinsci.plugins.spoontrigger.commands;

import com.google.common.io.Closeables;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

public class FilterOutputCommand extends BaseCommand {

    private int errorCode = 0;
    private OutputStreamCollector outputStream;

    protected FilterOutputCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public void run(CommandDriver client) throws IllegalStateException {
        this.outputStream = new OutputStreamCollector(client.getLogger(), client.getCharset());
        try {
            errorCode = client.launch(this.argumentList, this.outputStream);
        } finally {
            try {
                final boolean swallowException = true;
                Closeables.close(this.outputStream, swallowException);
            } catch (IOException ex) {
                // no-op
            }
        }
    }

    protected Collection<String> findInOutput(Pattern pattern) {
        if (outputStream == null) {
            return Collections.emptyList();
        }

        return outputStream.findAll(pattern);
    }
}
