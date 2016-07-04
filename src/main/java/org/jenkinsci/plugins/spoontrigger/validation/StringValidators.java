package org.jenkinsci.plugins.spoontrigger.validation;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static Validator<String> isTimeSpanFormat(String failureMsg) {
        return new PredicateValidator<String>(Predicates.IS_TIME_SPAN_FORMAT, failureMsg, Level.ERROR);
    }

    public static Validator<String> isInteger(String failureMsg) {
        return new PredicateValidator<String>(Predicates.IS_INTEGER, failureMsg, Level.ERROR);
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
                if (Strings.isNullOrEmpty(value)) {
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
        },
        IS_TIME_SPAN_FORMAT {
            @Override
            public boolean apply(String value) {
                if (Strings.isNullOrEmpty(value)) {
                    return false;
                }

                final Matcher matcher = TIME_SPAN_REGEX.matcher(value);
                return matcher.matches();
            }
        },
        IS_INTEGER {
            @Override
            public boolean apply(String value) {
                if(Strings.isNullOrEmpty(value)) {
                    return false;
                }

                try {
                    Integer.valueOf(value);
                    return true;
                } catch (NumberFormatException ex) {
                    return false;
                }
            }
        };

        private static final Pattern TIME_SPAN_REGEX = Pattern.compile("\\d{2}(?::\\d{2}){0,2}");

        @Override
        public abstract boolean apply(String s);
    }
}
