package org.jenkinsci.plugins.spoontrigger.validation;

import hudson.util.FormValidation;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.Messages.REQUIRE_NOT_NULL_OR_EMPTY_S;

public final class Validators {

    @SafeVarargs
    public static <T> Validator<T> chain(Validator<T>... validators) {
        return new ValidatorChain<T>(validators);
    }

    public static <T> FormValidation validate(Validator<T> validator, T value) {
        try {
            validator.validate(value);
            return FormValidation.ok();
        } catch (ValidationException ex) {
            return ex.getFailureMessage();
        }
    }

    static final class ValidatorChain<T> implements Validator<T> {

        private final Validator<T>[] validators;

        @SafeVarargs
        public ValidatorChain(Validator<T>... validators) {
            checkArgument(validators != null && validators.length > 0, REQUIRE_NOT_NULL_OR_EMPTY_S, "validators");

            this.validators = validators;
        }

        @Override
        public void validate(T value) throws ValidationException {
            for (Validator<T> validator : this.validators) {
                validator.validate(value);
            }
        }
    }
}
