package org.jenkinsci.plugins.spoontrigger.snapshot;

public enum StartupFileStrategy {
    /**
     * Use startup files and triggers selected by Studio
     */
    STUDIO,
    /**
     * Startup files and triggers selected by Studio will be replaced by a startup file specified in the build definition
     */
    FIXED;
}
