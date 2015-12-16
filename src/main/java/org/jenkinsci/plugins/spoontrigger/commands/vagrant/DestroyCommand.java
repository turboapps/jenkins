package org.jenkinsci.plugins.spoontrigger.commands.vagrant;

import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.BaseCommand;

public class DestroyCommand extends BaseCommand {
    public DestroyCommand() {
        super(new ArgumentListBuilder(VAGRANT_CLIENT, "destroy", "--force"));
    }
}
