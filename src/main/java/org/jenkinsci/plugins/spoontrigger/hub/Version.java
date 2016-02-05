package org.jenkinsci.plugins.spoontrigger.hub;

import com.google.common.base.Optional;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version implements Comparable<Version> {
    private static final int UNKNOWN = -1;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))*$");

    private final int[] numbers;

    public static Optional<Version> tryParse(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (matcher.matches()) {
            int[] numbers = new int[4];
            Arrays.fill(numbers, UNKNOWN);

            final int groupCount = matcher.groupCount();
            // group 0 contains entire match
            // group 1 and above contain version numbers
            for (int group = 1; group <= groupCount; ++group) {
                String match = matcher.group(group);
                if (match == null) {
                    break;
                }
                numbers[group - 1] = Integer.parseInt(match);
            }

            return Optional.of(new Version(numbers));
        }
        return Optional.absent();
    }

    private Version(int[] numbers) {
        if (numbers.length < 4) {
            throw new IllegalArgumentException("numbers");
        }
        this.numbers = numbers;
    }

    @Override
    public int compareTo(Version other) {
        final int length = Math.min(numbers.length, other.numbers.length);
        for (int position = 0; position < length; ++position) {
            int result = numbers[position] - other.numbers[position];
            if (result != 0) {
                return result;
            }
        }
        return numbers.length - other.numbers.length;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (int number : numbers) {
            if (number == UNKNOWN) {
                break;
            }

            if (builder.length() > 0) {
                builder.append(".");
            }
            builder.append(number);
        }

        return builder.toString();
    }
}
