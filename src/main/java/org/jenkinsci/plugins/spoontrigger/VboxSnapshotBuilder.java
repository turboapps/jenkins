package org.jenkinsci.plugins.spoontrigger;

import com.google.common.base.Optional;
import com.google.common.io.Closeables;
import com.google.common.io.Resources;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.commands.CommandDriver;
import org.jenkinsci.plugins.spoontrigger.commands.turbo.ImportCommand;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkState;

public class VboxSnapshotBuilder extends BaseBuilder {

    private static final String IMAGE_NAME_FILE = "image.txt";
    private static final String BUILD_SCRIPT_FILENAME = "buildScript.ps1";
    private static final String PS_MAIN_SCRIPT_FILENAME = "completeBuildProcedure.ps1";
    private transient String xStudioPath;
    private String studioLicensePath;
    private String vmName;
    private String installScriptPath;
    private String preInstallScriptPath;
    private String postSnapshotScriptPath;
    private String mountDirectoryPath;
    private String configurationXMLPath;
    private String virtualboxDir;
    private Optional<Image> image;
    private String PSBuildScriptPath;
    private Boolean overwriteFlag;
    private String buildScriptPath;


    @DataBoundConstructor
    public VboxSnapshotBuilder(String configurationXMLPath) {
        this.configurationXMLPath = configurationXMLPath;
    }

    @Override
    protected void prebuild(SpoonBuild build, BuildListener listener) {
        Descriptor globalConfig = (Descriptor)getDescriptor();
        xStudioPath = globalConfig.getxStudioPath();
        studioLicensePath = globalConfig.getStudioLicensePath();
        virtualboxDir = globalConfig.getVirtualboxDir();
    }

    @Override
    protected boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        try {
            Document configurationFileDocument = openXMLConfigurationPath(configurationXMLPath);
            loadConfigurationFromXML(configurationFileDocument);

        } catch (ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
        build.allowOverwrite = overwriteFlag;

        loadImageNameFor(build);
        if(!image.isPresent())
        {
            listener.getLogger().println("Image name not available before build. Will check again after snapshot completes.");
        }
        else
        {
            if (shouldAbort(build, listener)) return false;
        }

        PSBuildScriptPath = copyResourceToWorkspace(build.getWorkspace().getRemote(), PS_MAIN_SCRIPT_FILENAME);
        buildScriptPath = copyResourceToWorkspace(build.getWorkspace().getRemote(), BUILD_SCRIPT_FILENAME);

        CommandDriver commandDriver = CommandDriver.builder()
                .charset(build.getCharset())
                .env(build.getEnvironment(listener))
                .pwd(build.getWorkspace())
                .launcher(launcher)
                .listener(listener)
                .build();

        ArgumentListBuilder vboxSnapshotCommand = generateBuildCommand();
        int buildReturnCode = takeVboxSnapshot(build, launcher, listener, vboxSnapshotCommand);

        if(!image.isPresent())
        {
            listener.getLogger().println("Image name was not load before build. Trying to load again.");
            loadImageNameFor(build);
            if (shouldAbort(build, listener)) {
                listener.getLogger().println("Image available on the hub. Aborting the build.");
                return false;
            }
        }
        build.setOutputImage(image.get());

        importImageToLocalTurbo(commandDriver, build);

        listener.getLogger().println("Build returned: " + buildReturnCode);

        return true;
    }

    private boolean shouldAbort(SpoonBuild build, BuildListener listener) {
        if(this.image.isPresent())
        {
            boolean imageAvailableRemotely = isAvailableRemotely(this.image.get(), build, listener);
            if(imageAvailableRemotely)
            {
                if(!overwriteFlag)
                {
                    build.setResult(Result.ABORTED);
                    return true;
                }
                listener.getLogger().println("Image available remotely, but overwrite flag is 'true'. Building anyway.");
            }
        }
        return false;
    }

    private int takeVboxSnapshot(SpoonBuild build, Launcher launcher, BuildListener listener, ArgumentListBuilder vboxSnapshotCommand) throws IOException, InterruptedException {
        return runCmdCommand(build, launcher, listener, vboxSnapshotCommand, listener.getLogger());
    }

    private static int runCmdCommand(SpoonBuild build, Launcher launcher, BuildListener listener, ArgumentListBuilder argumentList, OutputStream outputStream) throws IOException, InterruptedException {
        Launcher.ProcStarter procStarter = launcher.new ProcStarter();
        procStarter = procStarter.cmds(argumentList).stdout(outputStream);
        procStarter = procStarter.pwd(build.getWorkspace()).envs(build.getEnvironment(listener));
        Proc proc = launcher.launch(procStarter);
        return proc.join();
    }

    private String copyResourceToWorkspace(String workspacePath, String fileName) throws IOException {
        Path resourceOutputPath = Paths.get(workspacePath, fileName);
        URL resourceId = Resources.getResource(getClass(), fileName);
        FileOutputStream fileOutputStream = new FileOutputStream(resourceOutputPath.toFile());
        try {
            Resources.copy(resourceId, fileOutputStream);
        } finally {
            final boolean swallowIoException = true;
            Closeables.close(fileOutputStream, swallowIoException);
        }

        return resourceOutputPath.toString();
    }

    private void loadConfigurationFromXML(Document configurationFileDocument) throws ParserConfigurationException, IOException, SAXException {
        vmName = configurationFileDocument.getElementsByTagName("vmName").item(0).getTextContent();
        installScriptPath = configurationFileDocument.getElementsByTagName("installScriptPath").item(0).getTextContent();
        preInstallScriptPath = configurationFileDocument.getElementsByTagName("preInstallScriptPath").item(0).getTextContent();
        postSnapshotScriptPath = configurationFileDocument.getElementsByTagName("postSnapshotScriptPath").item(0).getTextContent();
        mountDirectoryPath = configurationFileDocument.getElementsByTagName("mountDirectoryPath").item(0).getTextContent();
        overwriteFlag = Boolean.parseBoolean(configurationFileDocument.getElementsByTagName("overwrite").item(0).getTextContent());

        replaceEmptyArgument();
    }

    private Document openXMLConfigurationPath(String configurationXMLPath) throws ParserConfigurationException, SAXException, IOException {
        File configurationXMLFile = new File(configurationXMLPath);
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(configurationXMLFile);
    }

    private void loadImageNameFor(SpoonBuild build) throws IOException {
        String workspacePath = build.getWorkspace().getRemote();
        Path imageFilePath = Paths.get(workspacePath, IMAGE_NAME_FILE);
        if (imageFilePath.toFile().exists()) {
            BufferedReader reader = Files.newBufferedReader(imageFilePath, Charset.defaultCharset());
            try {
                String imageName = reader.readLine();
                this.image = Optional.of(Image.parse(imageName));
            } finally {
                final boolean swallowException = true;
                Closeables.close(reader, swallowException);
            }
        }
        else {
            this.image = Optional.absent();
        }
    }

    private void replaceEmptyArgument() {
        preInstallScriptPath = preInstallScriptPath.isEmpty() ? " " : preInstallScriptPath;
        postSnapshotScriptPath = postSnapshotScriptPath.isEmpty() ? " " : postSnapshotScriptPath;
        mountDirectoryPath = mountDirectoryPath.isEmpty() ? " " : mountDirectoryPath;
    }

    private ArgumentListBuilder generateBuildCommand() {
        ArgumentListBuilder command = new ArgumentListBuilder();
        command.addTokenized("Powershell -File ");
        command.add(PSBuildScriptPath,
                buildScriptPath,
                vmName,
                installScriptPath,
                xStudioPath,
                studioLicensePath,
                preInstallScriptPath,
                postSnapshotScriptPath,
                mountDirectoryPath,
                virtualboxDir);
        return command.toWindowsCommand();
    }

    private void importImageToLocalTurbo(CommandDriver commandDriver, SpoonBuild build) {
        ImportCommand.CommandBuilder commandBuilder = ImportCommand.builder()
                .type("svm")
                .path(image.get().namespace + "_" + image.get().repo + "_" + image.get().tag + ".svm")
                .name(image.get().printIdentifier())
                .overwrite(overwriteFlag);

        ImportCommand command = commandBuilder.build();
        command.run(commandDriver);

        Optional<Image> outputImage = command.getOutputImage();
        checkState(outputImage.isPresent(), "Failed to find imported image in command output");

        build.setOutputImage(outputImage.get());
    }

    @Extension
    public static final class Descriptor extends BuildStepDescriptor<Builder> {

        private String xStudioPath;
        private String studioLicensePath;
        private String virtualboxDir;

        public Descriptor() {
            super(VboxSnapshotBuilder.class);

            this.load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Take VirtualBox snapshot";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            json = json.getJSONObject("vboxsnapshot");
            xStudioPath = json.getString("xStudioPath");
            studioLicensePath = json.getString("studioLicensePath");
            virtualboxDir = json.getString("virtualboxDir");

            save();
            return super.configure(req, json);
        }

        public String getxStudioPath() {
            return xStudioPath;
        }

        public String getStudioLicensePath() {
            return studioLicensePath;
        }

        public String getVirtualboxDir() {
            return virtualboxDir;
        }
    }

    public String getConfigurationXMLPath() {
        return configurationXMLPath;
    }
}
