package org.jenkinsci.plugins.spoontrigger.client;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import hudson.console.LineTransformationOutputStream;
import org.jenkinsci.plugins.spoontrigger.utils.Patterns;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

public class OutputStreamCollector extends LineTransformationOutputStream {

    private static final int MAX_LENGTH = 32 * 1024 * 10204;
    private static final Pattern MarqueePattern = Pattern.compile("^(?<line>.*?)[\\\\|/\\-]?$");

    private final ArrayList<String> lines;
    private String lastLine;
    private int totalBytes;

    private final PrintStream out;
    private final Charset charset;

    public OutputStreamCollector(PrintStream out, Charset charset) {
        this.out = out;
        this.charset = charset;

        this.lines = new ArrayList<String>();
        this.lastLine = null;
        this.totalBytes = 0;
    }

    @Override
    protected void eol(byte[] bytes, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length);
        String line = this.charset.decode(buffer).toString();

        String lineToUse = line.trim();
        Matcher matcher = MarqueePattern.matcher(lineToUse);
        if (matcher.matches()) {
            lineToUse = matcher.group("line");
        }

        if (lineToUse.equals(lastLine)) {
            // ignore duplicated lines
            return;
        }

        if (totalBytes + length <= MAX_LENGTH) {
            lines.add(lineToUse);
            totalBytes += length;
        }

        lastLine = lineToUse;
        if (line.endsWith("\n")) {
            this.out.println(lineToUse);
        } else {
            this.out.print(lineToUse);
        }
    }

    public Collection<String> findAll(Pattern pattern) {
        PatternGroupExtractor patternGroupExtractor = new PatternGroupExtractor(pattern);
        ArrayList<String> results = new ArrayList<String>();
        for (String line : lines) {
            Optional<String> value = patternGroupExtractor.getValue(line);
            if (value.isPresent()) {
                results.add(value.get());
            }
        }
        return results;
    }

    private static final class PatternGroupExtractor {

        private static final Pattern CONTAINS_GROUP_PATTERN = Pattern.compile("[^\\\\]*\\(.*[^\\\\]\\)");

        private final Pattern pattern;

        public PatternGroupExtractor(Pattern pattern) {
            checkArgument(pattern != null && Patterns.matches(pattern.toString(), CONTAINS_GROUP_PATTERN),
                    "pattern (%s) must be a not null regex with a matching group", pattern);

            this.pattern = pattern;
        }

        public Optional<String> getValue(String text) {
            if (Strings.isNullOrEmpty(text)) {
                return Optional.absent();
            }

            Matcher matcher = this.pattern.matcher(text);
            if (matcher.find() && matcher.groupCount() > 0) {
                return Optional.of(matcher.group(1));
            }

            return Optional.absent();
        }
    }
}
