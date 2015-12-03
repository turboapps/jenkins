package org.jenkinsci.plugins.spoontrigger.client;

import com.google.common.io.Closeables;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.regex.Pattern;

public class FilterOutputCommand extends BaseCommand {

    private OutputStreamCollector outputStream;

    FilterOutputCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public void run(SpoonClient client) throws IllegalStateException {
        this.outputStream = new OutputStreamCollector(client.getLogger(), client.getCharset());
        try {
            client.launch(this.getArgumentList(), this.outputStream);
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
        return outputStream.findAll(pattern);
    }
}
