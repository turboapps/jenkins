package org.jenkinsci.plugins.spoontrigger.utils;

import hudson.model.BuildListener;

import java.io.PrintStream;

public class LogUtils {
    public static void log(BuildListener listener, String msg, Throwable th) {
        PrintStream output = listener.getLogger();
        output.println(msg);
        th.printStackTrace(output);
    }

    public static void log(BuildListener listener, String msg) {
        PrintStream output = listener.getLogger();
        output.println(msg);
    }
}
