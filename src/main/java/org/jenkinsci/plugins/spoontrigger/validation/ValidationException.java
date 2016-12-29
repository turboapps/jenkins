package org.jenkinsci.plugins.spoontrigger.validation;

import hudson.util.FormValidation;

public final class ValidationException extends Exception {

    public final FormValidation failureMessage;

    public ValidationException(FormValidation failureMessage) {
        this.failureMessage = failureMessage;
    }
}