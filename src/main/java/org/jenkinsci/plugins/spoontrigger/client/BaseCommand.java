package org.jenkinsci.plugins.spoontrigger.client;

import hudson.util.ArgumentListBuilder;
import lombok.Getter;

public abstract class BaseCommand {

    public static final String SPOON_CLIENT = "turbo";

    @Getter
    private final ArgumentListBuilder argumentList;

    BaseCommand(ArgumentListBuilder argumentList) {
        this.argumentList = argumentList;
    }
}
