package org.jenkinsci.plugins.spoontrigger.snapshot;

public enum InstallScriptStrategy {
    /**
     * Generate installation script using a template:
     * * <pre>
     * {@code
     * & install.exe {silent install arguments} | Out-Null
     * exit 0 # optional if ignore exit code was specified
     * }
     * </pre>
     */
    TEMPLATE,
    /**
     * Use installation script specified in the build definition
     */
    FIXED;
}
