package org.jenkinsci.plugins.spoontrigger.commands.vagrant;

import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.spoontrigger.commands.VoidCommand;

public class DestroyCommand extends VoidCommand {
    public DestroyCommand() {
        super(new ArgumentListBuilder(VAGRANT_CLIENT, "destroy", "--force"));
    }
}
