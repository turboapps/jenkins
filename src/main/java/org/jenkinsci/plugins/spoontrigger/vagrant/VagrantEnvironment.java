package org.jenkinsci.plugins.spoontrigger.vagrant;

import com.google.common.base.Optional;
import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.utils.FileUtils.quietDeleteDirectoryTreeIfExists;

public class VagrantEnvironment implements Closeable {
    public static final String TOOLS_DIRECTORY = "tools";
    public static final String INSTALL_DIRECTORY = "install";
    public static final String INSTALLER_EXE_FILE = "install.exe";
    public static final String OUTPUT_DIRECTORY = "output";
    public static final String XSTUDIO_EXE_FILE = "xstudio.exe";
    public static final String XSTUDIO_LICENSE_FILE = "license.txt";
    public static final String IMAGE_SVM_FILE = "image.svm";
    public static final String VAGRANT_FILE = "Vagrantfile";
    public static final String INSTALL_SCRIPT_FILE = "install.ps1";
    public static final String INSTALLER_PATH_ON_GUEST_MACHINE = "C:\\vagrant\\install\\install.exe";

    @Getter
    private final Path workingDir;

    private VagrantEnvironment(Path workingDir) {
        this.workingDir = workingDir;
    }

    public static EnvironmentBuilder builder(Path workingDir) {
        return new EnvironmentBuilder(workingDir);
    }

    public Path getImagePath() {
        return Paths.get(workingDir.toString(), OUTPUT_DIRECTORY, IMAGE_SVM_FILE);
    }

    @Override
    public void close() {
        String workingDirToUse = workingDir.toString();
        quietDeleteDirectoryTreeIfExists(Paths.get(workingDirToUse, TOOLS_DIRECTORY));
        quietDeleteDirectoryTreeIfExists(Paths.get(workingDirToUse, INSTALL_DIRECTORY));
        quietDeleteDirectoryTreeIfExists(Paths.get(workingDirToUse, OUTPUT_DIRECTORY));
        quietDeleteDirectoryTreeIfExists(Paths.get(workingDirToUse, ".vagrant"));
    }

    public static class EnvironmentBuilder {
        private final Path workingDir;

        private Optional<String> xStudioPath = Optional.absent();
        private Optional<String> xStudioLicensePath = Optional.absent();
        private Optional<String> box = Optional.absent();
        private Optional<String> installerPath = Optional.absent();
        private Optional<String> installScriptPath = Optional.absent();
        private Optional<String> installerArgs = Optional.absent();
        private Optional<String> startupFilePath = Optional.absent();
        private boolean ignoreExitCode = false;

        public EnvironmentBuilder(Path workingDir) {
            this.workingDir = workingDir;
        }

        public EnvironmentBuilder xStudioPath(String path) {
            this.xStudioPath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder xStudioLicensePath(String path) {
            this.xStudioLicensePath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder installerPath(String path) {
            this.installerPath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder startupFilePath(String path) {
            this.startupFilePath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder generateInstallScript(String args, boolean ignoreExitCode) {
            this.installerArgs = Optional.of(args);
            this.ignoreExitCode = ignoreExitCode;
            return this;
        }

        public EnvironmentBuilder installScriptPath(String path) {
            this.installScriptPath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder box(String vagrantBox) {
            this.box = Optional.of(vagrantBox);
            return this;
        }

        public VagrantEnvironment build() {
            checkState(box.isPresent(), "VagrantBox not defined");
            checkState(xStudioPath.isPresent(), "XStudioPath not defined");
            checkState(installerPath.isPresent(), "InstallerPath not defined");
            checkState(installerArgs.isPresent() ^ installScriptPath.isPresent(), "Only one parameter: `installerArgs` or `installScriptPath` must be defined");

            setupToolsDirectory();
            String installScriptName = setupInstallDirectory();
            setupWorkingDirectory(box.get(), installScriptName);

            return new VagrantEnvironment(workingDir);
        }

        private void setupToolsDirectory() {
            Path toolsDir = Paths.get(workingDir.toString(), TOOLS_DIRECTORY);
            createDirectoryIfNotExist(toolsDir);

            Path xStudioSourcePath = Paths.get(xStudioPath.get());
            Path xStudioDestPath = Paths.get(toolsDir.toString(), XSTUDIO_EXE_FILE);
            copyFile(xStudioSourcePath, xStudioDestPath);

            if (xStudioLicensePath.isPresent()) {
                Path licenseSourcePath = Paths.get(xStudioLicensePath.get());
                Path licenseDestPath = Paths.get(toolsDir.toString(), XSTUDIO_LICENSE_FILE);
                copyFile(licenseSourcePath, licenseDestPath);
            }
        }

        private String setupInstallDirectory() {
            Path installDir = Paths.get(workingDir.toString(), INSTALL_DIRECTORY);

            createDirectoryIfNotExist(installDir);

            if (installerPath.isPresent()) {
                Path installerSourcePath = Paths.get(installerPath.get());
                Path installerDestPath = Paths.get(workingDir.toString(), INSTALL_DIRECTORY, INSTALLER_EXE_FILE);
                copyFile(installerSourcePath, installerDestPath);
            }

            if (installScriptPath.isPresent()) {
                Path installScriptSourcePath = Paths.get(installScriptPath.get());
                String installScriptFileName = installScriptSourcePath.getFileName().toString();
                Path installScriptDestPath = Paths.get(installDir.toString(), installScriptFileName);
                copyFile(installScriptSourcePath, installScriptDestPath);

                return installScriptFileName;
            }

            if (installerArgs.isPresent()) {
                ArrayList<String> scriptContent = new ArrayList<String>(2);
                scriptContent.add("& " + INSTALLER_PATH_ON_GUEST_MACHINE + " " + installerArgs.get() + " | Write-Host");
                if (ignoreExitCode) {
                    scriptContent.add("exit 0");
                }

                Path installerScriptPath = Paths.get(installDir.toString(), INSTALL_SCRIPT_FILE);
                try {
                    Files.write(
                            installerScriptPath,
                            scriptContent,
                            Charset.defaultCharset(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                } catch (IOException ex) {
                    throw new IllegalStateException(String.format("Failed to create %s", installerScriptPath), ex);
                }

                return INSTALL_SCRIPT_FILE;
            }

            throw new IllegalStateException("Failed to create installer script");
        }

        private void setupWorkingDirectory(String vagrantBox, String installScriptName) {
            VagrantFileTemplate vagrantFileTemplate = new VagrantFileTemplate(installScriptName, vagrantBox, startupFilePath.orNull());
            try {
                vagrantFileTemplate.save(Paths.get(workingDir.toString(), VAGRANT_FILE));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to generate Vagrantfile", ex);
            }
        }

        private void createDirectoryIfNotExist(Path directory) {
            try {
                if (Files.notExists(directory)) {
                    Files.createDirectory(directory);
                }
            } catch (IOException ex) {
                throw new IllegalStateException(String.format("Failed to create %s directory", directory), ex);
            }
        }

        private void copyFile(Path source, Path destination) {
            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                String msg = String.format("Failed to copy %s to %s", source, destination);
                throw new IllegalStateException(msg, ex);
            }
        }
    }
}
