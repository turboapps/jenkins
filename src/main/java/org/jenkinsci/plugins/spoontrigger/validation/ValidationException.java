package org.jenkinsci.plugins.spoontrigger.validation;

import hudson.util.FormValidation;
import lombok.Getter;

public final class ValidationException extends Exception {

    @Getter private final FormValidation failureMessage;

    public ValidationException(FormValidation failureMessage) {
        this.failureMessage = failureMessage;
    }
}