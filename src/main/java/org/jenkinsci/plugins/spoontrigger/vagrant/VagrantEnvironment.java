package org.jenkinsci.plugins.spoontrigger.vagrant;

import com.google.common.base.Optional;
import lombok.Getter;
import org.jenkinsci.plugins.spoontrigger.utils.FileUtils;

import java.io.File;
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
    public static final String OUTPUT_DIRECTORY = "output";
    public static final String RESOURCE_DIRECTORY = "resources";
    public static final String PRE_INSTALL_SCRIPT_FILE = "pre_install.ps1";
    public static final String POST_SNAPSHOT_SCRIPT_FILE = "post_snapshot.ps1";
    public static final String XAPPL_FILE = "snapshot.xappl";
    public static final String XSTUDIO_EXE_FILE = "xstudio.exe";
    public static final String XSTUDIO_LICENSE_FILE = "license.txt";
    public static final String IMAGE_SVM_FILE = "image.svm";
    public static final String VAGRANT_FILE = "Vagrantfile";
    public static final String INSTALL_SCRIPT_FILE = "install.ps1";
    public static final String INSTALLER_DIRECTORY_ON_GUEST_MACHINE = "C:\\vagrant\\install";


    @Getter
    private final Path workingDir;

    private VagrantEnvironment(Path workingDir) {
        this.workingDir = workingDir;
    }

    public static EnvironmentBuilder builder(Path workingDir) {
        return new EnvironmentBuilder(workingDir);
    }

    public Path getOutputPath() {
        return Paths.get(workingDir.toString(), OUTPUT_DIRECTORY);
    }

    public Path getSnapshotPath() {
        return Paths.get(getOutputPath().toString(), "Files");
    }

    public Path getXapplPath() {
        return Paths.get(getOutputPath().toString(), XAPPL_FILE);
    }

    public Path getPostSnapshotScriptPath() {
        return Paths.get(getOutputPath().toString(), POST_SNAPSHOT_SCRIPT_FILE);
    }

    public Path getImagePath() {
        return Paths.get(getOutputPath().toString(), IMAGE_SVM_FILE);
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
        private Optional<String> box = Optional.absent();
        private Optional<String> installerPath = Optional.absent();
        private Optional<String> installScriptPath = Optional.absent();
        private Optional<String> installerArgs = Optional.absent();
        private Optional<String> postSnapshotScriptPath = Optional.absent();
        private Optional<String> preInstallScriptPath = Optional.absent();
        private Optional<String> resourceDirectoryPath = Optional.absent();
        private boolean ignoreExitCode = false;

        public EnvironmentBuilder(Path workingDir) {
            this.workingDir = workingDir;
        }

        public EnvironmentBuilder xStudioPath(String path) {
            this.xStudioPath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder installerPath(String path) {
            this.installerPath = Optional.of(path);
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

        public EnvironmentBuilder postSnapshotScriptPath(String path) {
            this.postSnapshotScriptPath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder preInstallScriptPath(String path) {
            this.preInstallScriptPath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder resourceDirectoryPath(String path) {
            this.resourceDirectoryPath = Optional.of(path);
            return this;
        }

        public EnvironmentBuilder box(String vagrantBox) {
            this.box = Optional.of(vagrantBox);
            return this;
        }

        public VagrantEnvironment build() {
            checkState(box.isPresent(), "VagrantBox not defined");
            checkState(xStudioPath.isPresent(), "XStudioPath not defined");

            if (installerArgs.isPresent()) {
                checkState(installerPath.isPresent(), "InstallerPath not defined");
                checkState(!installScriptPath.isPresent(), "Only one parameter: `installerArgs` or `installScriptPath` must be defined");
            } else {
                checkState(installScriptPath.isPresent(), "InstallerScriptPath not defined");
            }

            setupToolsDirectory();
            setupResourceDirectory();
            VagrantFileTemplate.Config vagrantConfig = setupInstallDirectory();
            setupWorkingDirectory(vagrantConfig);
            setupOutputDirectory();

            return new VagrantEnvironment(workingDir);
        }

        private void setupToolsDirectory() {
            Path toolsDir = Paths.get(workingDir.toString(), TOOLS_DIRECTORY);
            createDirectoryIfNotExist(toolsDir);

            Path xStudioSourcePath = Paths.get(xStudioPath.get());
            Path xStudioDestPath = Paths.get(toolsDir.toString(), XSTUDIO_EXE_FILE);
            copyFile(xStudioSourcePath, xStudioDestPath);
        }

        private void setupResourceDirectory() {
            if (!resourceDirectoryPath.isPresent()) {
                return;
            }

            File resourceSourceDir = new File(resourceDirectoryPath.get());
            File resourceDestDir = new File(workingDir.toFile(), RESOURCE_DIRECTORY);
            try {
                org.apache.commons.io.FileUtils.copyDirectory(resourceSourceDir, resourceDestDir);
            } catch (Throwable th) {
                String msg = String.format("Failed to copy directory with content from %s to %s", resourceSourceDir, resourceDestDir);
                throw new IllegalStateException(msg, th);
            }
        }

        private VagrantFileTemplate.Config setupInstallDirectory() {
            Path installDir = Paths.get(workingDir.toString(), INSTALL_DIRECTORY);

            createDirectoryIfNotExist(installDir);

            String preInstallScriptFileName = null;
            if (preInstallScriptPath.isPresent()) {
                Path preInstallScriptSourcePath = Paths.get(preInstallScriptPath.get());
                preInstallScriptFileName = PRE_INSTALL_SCRIPT_FILE;
                Path preInstallScriptDestPath = Paths.get(installDir.toString(), preInstallScriptFileName);
                copyFile(preInstallScriptSourcePath, preInstallScriptDestPath);
            }

            VagrantFileTemplate.Config config = null;
            if (installScriptPath.isPresent()) {
                Path installScriptSourcePath = Paths.get(installScriptPath.get());
                String installScriptFileName = installScriptSourcePath.getFileName().toString();
                Path installScriptDestPath = Paths.get(installDir.toString(), installScriptFileName);
                copyFile(installScriptSourcePath, installScriptDestPath);
                config = new VagrantFileTemplate.Config(preInstallScriptFileName, installScriptFileName, box.get());
            }

            if (installerPath.isPresent()) {
                Path installerSourcePath = Paths.get(installerPath.get());
                String installerFileName = installerSourcePath.getFileName().toString();
                Path installerDestPath = Paths.get(workingDir.toString(), INSTALL_DIRECTORY, installerFileName);
                copyFile(installerSourcePath, installerDestPath);

                if (installerArgs.isPresent()) {
                    Path installerScriptPath = Paths.get(installDir.toString(), INSTALL_SCRIPT_FILE);
                    try {
                        Files.write(
                                installerScriptPath,
                                generateScriptContent(installerDestPath),
                                Charset.defaultCharset(),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE);
                    } catch (IOException ex) {
                        throw new IllegalStateException(String.format("Failed to create %s", installerScriptPath), ex);
                    }

                    if (config == null) {
                        config = new VagrantFileTemplate.Config(preInstallScriptFileName, INSTALL_SCRIPT_FILE, box.get());
                    }
                }
            }

            if (config == null) {
                throw new IllegalStateException("Failed to create installer script");
            }

            return config;
        }

        private void setupOutputDirectory() {
            Path outputDir = Paths.get(workingDir.toString(), OUTPUT_DIRECTORY);

            createDirectoryIfNotExist(outputDir);

            if (postSnapshotScriptPath.isPresent()) {
                Path postSnapshotScriptSourcePath = Paths.get(postSnapshotScriptPath.get());
                Path postSnapshotScriptDestPath = Paths.get(outputDir.toString(), POST_SNAPSHOT_SCRIPT_FILE);
                copyFile(postSnapshotScriptSourcePath, postSnapshotScriptDestPath);
            }
        }

        private ArrayList<String> generateScriptContent(Path installerFile) {
            ArrayList<String> scriptContent = new ArrayList<String>(2);
            StringBuilder commandBuilder = new StringBuilder("& ");

            if ("msi".equals(FileUtils.getExtension(installerFile))) {
                commandBuilder.append("msiexec /i ");
            }

            commandBuilder.append(Paths.get(INSTALLER_DIRECTORY_ON_GUEST_MACHINE, installerFile.getFileName().toString()));
            commandBuilder.append(" ");
            commandBuilder.append(installerArgs.get());
            commandBuilder.append(" | Write-Host");

            scriptContent.add(commandBuilder.toString());
            if (ignoreExitCode) {
                scriptContent.add("exit 0");
            }

            return scriptContent;
        }

        private void setupWorkingDirectory(VagrantFileTemplate.Config config) {
            VagrantFileTemplate vagrantFileTemplate = new VagrantFileTemplate(config);
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
