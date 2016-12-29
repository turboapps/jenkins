package org.jenkinsci.plugins.spoontrigger.commands;

import hudson.util.ArgumentListBuilder;
import lombok.Getter;

public abstract class BaseCommand {

    public static final String SPOON_CLIENT = "turbo";
    public static final String VAGRANT_CLIENT = "vagrant";

    @Getter
    private final ArgumentListBuilder argumentList;

    protected BaseCommand(ArgumentListBuilder argumentList) {
        this.argumentList = argumentList;
    }
}
