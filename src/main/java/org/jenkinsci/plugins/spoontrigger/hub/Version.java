package org.jenkinsci.plugins.spoontrigger.hub;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version implements Comparable<Version> {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)*$");

    private final ArrayList<Integer> numbers;

    public static Optional<Version> tryParse(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (matcher.matches()) {
            String match = matcher.group();
            String[] segments = match.split("\\.");
            ArrayList<Integer> numbers = new ArrayList<Integer>();
            for (int position = 0; position < segments.length; ++position) {
                numbers.add(Integer.parseInt(segments[position]));
            }
            return Optional.of(new Version(numbers));
        }
        return Optional.absent();
    }

    private Version(ArrayList<Integer> numbers) {
        this.numbers = numbers;
    }

    @Override
    public int compareTo(Version other) {
        final int length = Math.min(numbers.size(), other.numbers.size());
        for (int position = 0; position < length; ++position) {
            int result = numbers.get(position) - other.numbers.get(position);
            if (result != 0) {
                return result;
            }
        }
        return numbers.size() - other.numbers.size();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (int number : numbers) {
            if (builder.length() > 0) {
                builder.append(".");
            }
            builder.append(number);
        }

        return Joiner.on(".").join(numbers);
    }
}
