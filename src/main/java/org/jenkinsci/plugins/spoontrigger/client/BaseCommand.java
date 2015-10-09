package org.jenkinsci.plugins.spoontrigger.client;

import hudson.util.ArgumentListBuilder;

abstract class BaseCommand {

    static final String SPOON_CLIENT = "turbo";

    private final ArgumentListBuilder argumentList;

    BaseCommand(ArgumentListBuilder argumentList) {
        this.argumentList = argumentList;
    }

    ArgumentListBuilder getArgumentList() {
        return argumentList;
    }
}
