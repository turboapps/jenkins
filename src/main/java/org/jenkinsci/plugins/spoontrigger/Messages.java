package org.jenkinsci.plugins.spoontrigger;

public final class Messages {
    public static final String REQUIRED_PARAMETER = "Parameter is required";
    public static final String IGNORE_PARAMETER = "Parameter is ignored";

    public static final String REQUIRE_SINGLE_WORD_S = "%s must be a single word";
    public static final String REQUIRE_SINGLE_WORD_SP = "%s (%s) must be a single word";
    public static final String REQUIRE_SINGLE_WORD_OR_NULL_SP = "%s (%s) must be a single word or null";
    public static final String REQUIRE_NOT_NULL_S = "%s must be not null";
    public static final String REQUIRE_NOT_NULL_OR_EMPTY_S = "%s must be not null or empty";
    public static final String REQUIRE_PRESENT_S = "%s must be present";
    public static final String REQUIRE_NON_EMPTY_STRING_S = "%s must be a non empty string";
    public static final String REQUIRE_VALID_FORMAT_SP = "%s (%s) does not have a valid format";

    public static final String REQUIRE_OUTPUT_IMAGE = "Output image name is not available in build information";

    public static final String FAILED_RESOLVE_PLACEHOLDER = "<failed to resolve>";
    public static final String FAILED_RESOLVE_S = "Failed to resolve %s";
    public static final String FAILED_RESOLVE_SP = FAILED_RESOLVE_S + " (%s)";

    public static final String PATH_NOT_POINT_TO_ITEM_S = "Path does not point to %s";
    public static final String PATH_NOT_POINT_TO_ITEM_SPS = "%s (%s) does not point to %s";
    public static final String PATH_SHOULD_BE_ABSOLUTE = "Path should be absolute if the build will be executed on a remote machine";

    public static final String EXIST_S = "%s already exists";
    public static final String DOES_NOT_EXIST_S = "%s does not exist";

    public static final String INVALID_DATE_FORMAT = "Invalid date format. Click help link to get the list of supported date and time components.";

    public static final String EMPTY = "";

    public static String requireInstanceOf(String name, Class aClass) {
        return String.format("%s must be an instance of (%s) class", name, toString(aClass));
    }

    public static <T> String toString(Class<T> aClass) {
        return aClass.getSimpleName();
    }
}
