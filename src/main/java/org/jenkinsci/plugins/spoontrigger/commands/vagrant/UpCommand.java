package org.jenkinsci.plugins.spoontrigger.commands.vagrant;

import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;

public class UpCommand extends VoidCommand {
    public UpCommand() {
        super(new ArgumentListBuilder(VAGRANT_CLIENT, "up"));
    }
}
