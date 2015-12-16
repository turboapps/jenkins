package org.jenkinsci.plugins.spoontrigger.commands.vagrant;

import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.BaseCommand;

public class UpCommand extends BaseCommand {
    public UpCommand() {
        super(new ArgumentListBuilder(VAGRANT_CLIENT, "up"));
    }
}
