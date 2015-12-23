package org.jenkinsci.plugins.spoontrigger.utils;

import hudson.model.BuildListener;
import hudson.util.ArgumentListBuilder;

import java.io.PrintStream;
import java.nio.file.Path;

public final class LogUtils {
    public static void log(BuildListener listener, String msg, Throwable th) {
        PrintStream output = listener.getLogger();
        output.println(msg);
        th.printStackTrace(output);
    }

    public static void log(BuildListener listener, String msg) {
        PrintStream output = listener.getLogger();
        output.println(msg);
    }

    public static void log(BuildListener listener, Path workingDir, ArgumentListBuilder command) {
        log(listener, String.format("[%s] $ %s", workingDir.getFileName(), command));
    }
}
