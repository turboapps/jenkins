package org.jenkinsci.plugins.spoontrigger.commands;

import hudson.util.ArgumentListBuilder;

public abstract class VoidCommand extends BaseCommand {

    protected VoidCommand(ArgumentListBuilder argumentList) {
        super(argumentList);
    }

    public void run(CommandDriver client) throws IllegalStateException {
        client.launch(this.argumentList);
    }
}
