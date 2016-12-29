package org.jenkinsci.plugins.spoontrigger.commands;

import hudson.util.ArgumentListBuilder;

public abstract class BaseCommand {

    public static final String SPOON_CLIENT = "turbo";
    public static final String VAGRANT_CLIENT = "vagrant";


    public final ArgumentListBuilder argumentList;

    protected BaseCommand(ArgumentListBuilder argumentList) {
        this.argumentList = argumentList;
    }
}
