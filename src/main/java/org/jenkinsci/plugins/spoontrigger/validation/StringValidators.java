package org.jenkinsci.plugins.spoontrigger.validation;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import java.text.SimpleDateFormat;

public final class StringValidators {

    public static Validator<String> isNotNull(String failureMsg, Level level) {
        return new PredicateValidator<String>(Predicates.IS_NOT_NULL, failureMsg, level);
    }

    public static Validator<String> isVersionNumber() {
        return new PredicateValidator<String>(Patterns.Predicates.VERSION_NUMBER, "Turbo VM version number should consist of 4 numbers separated by dot", Level.ERROR);
    }

    public static Validator<String> isSingleWord(String failureMsg) {
        return new PredicateValidator<String>(Patterns.Predicates.SINGLE_WORD, failureMsg, Level.ERROR);
    }

    public static Validator<String> isDateFormat(String failureMsg) {
        return new PredicateValidator<String>(Predicates.IS_DATE_FORMAT, failureMsg, Level.ERROR);
    }

    public enum Predicates implements Predicate<String> {
        IS_NULL {
            @Override
            public boolean apply(String value) {
                return value == null;
            }
        },
        IS_NOT_NULL {
            @Override
            public boolean apply(String value) {
                return value != null;
            }
        },
        IS_DATE_FORMAT {
            @Override
            public boolean apply(String value) {
                if(Strings.isNullOrEmpty(value)) {
                    return false;
                }

                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat();
                    dateFormat.applyPattern(value);
                    return true;
                } catch (IllegalArgumentException ex) {
                    return false;
                }
            }
        };

        @Override
        public abstract boolean apply(String s);
    }
}
