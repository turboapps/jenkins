package org.jenkinsci.plugins.spoontrigger;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.jenkinsci.plugins.spoontrigger.utils.Credentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static org.jenkinsci.plugins.spoontrigger.utils.Credentials.fillCredentialsIdItems;

public class TesterCheckBuilder extends BaseBuilder {

    private static final String  CHECK_APP_URL = "/buildByToken/buildWithParameters?job=Check%20App&token=checkapp";
    private static final int JOB_TRIGGER_HTTP_RESPONSE_CODE = 201;
    private static final String TRIGGER_PARAMETER_VM = "&VmMachines=";
    private static final String TRIGGER_PARAMETER_APP = "&app=";
    private static final String TRIGGER_PARAMETER_HUB = "&hub=";

    private Image imageToCheck;
    private int expectedExitCode;
    private String hubURL;
    private String turboTesterServer;
    private String testVms;
    private Optional<StandardUsernamePasswordCredentials> credentials;

    private URL testerRequestURL;
    private int maxMinutesToWaitForResult;
    private SpoonBuild build;
    private Launcher launcher;
    private BuildListener listener;
    private final String credentialsId;

    @DataBoundConstructor
    public TesterCheckBuilder(int expectedExitCode, String testVms, String maxMinutesToWaitForResult, String credentialsId) {
        this.testVms = testVms;
        this.expectedExitCode = expectedExitCode;
        this.credentialsId = credentialsId;
        this.maxMinutesToWaitForResult = Integer.parseInt(maxMinutesToWaitForResult);
    }

    @Override
    protected void prebuild(SpoonBuild build, BuildListener listener) {
        Descriptor globalConfig = (Descriptor)getDescriptor();
        turboTesterServer = globalConfig.getTurboTesterServer();
        hubURL = globalConfig.getHubUrl();
        Optional<StandardUsernamePasswordCredentials> credentials = this.getCredentials();
        if (credentials.isPresent()) {
            build.setCredentials(credentials.get());
        }
    }

    @Override
    protected boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;
        this.credentials = build.getCredentials();

        getImageToCheck();

        triggerJenkinsJobAndCheckHTTPResponse();

        List<String> testVMsList = Arrays.asList(testVms.split("\\s*,\\s*"));
        waitForAndReadResultFiles(testVMsList);
        deleteRepoFromLocalServer();
        return true;
    }

    private void getImageToCheck() {
        imageToCheck = build.getOutputImage().orNull();
    }

    private void triggerJenkinsJobAndCheckHTTPResponse() throws IOException {
        HttpURLConnection connection = prepareTesterServerConnection();
        int response = connection.getResponseCode();
        if (JOB_TRIGGER_HTTP_RESPONSE_CODE != response) {
            throw new IllegalStateException("Tester server didn't accept the request and returned code " + response);
        }
        listener.getLogger().println("HTTP response code from tester server is: " + response);
    }

    private HttpURLConnection prepareTesterServerConnection() throws IOException {
        String fullImageName = imageToCheck.printIdentifier();
        testVms = testVms.replaceAll(" ","");
        generateTurboTesterURL(fullImageName);
        listener.getLogger().println("Tester server trigger URL: " + testerRequestURL);
        HttpURLConnection connection = (HttpURLConnection) testerRequestURL.openConnection();
        connection.setRequestMethod("GET");
        return connection;
    }

    private void generateTurboTesterURL(String fullImageName) throws MalformedURLException {
        testerRequestURL = new URL("http://" +
                turboTesterServer +
                CHECK_APP_URL+
                TRIGGER_PARAMETER_VM +
                testVms +
                TRIGGER_PARAMETER_APP +
                fullImageName +
                TRIGGER_PARAMETER_HUB +
                hubURL);
    }

    private void waitForAndReadResultFiles(List<String> testVMsList) throws InterruptedException, IOException {
        String dashedImageName = imageToCheck.namespace + "-" + imageToCheck.repo + "-" + imageToCheck.tag;
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("CODE",
                                                                        credentials.get().getUsername(),
                                                                        Secret.toString(credentials.get().getPassword()));
        for (String testVm : testVMsList)
        {
            String exitcodeFilePath = getSMBexitcodeFilePath(testVm, dashedImageName);
            SmbFile exitcodeFile = new SmbFile(exitcodeFilePath, auth);
            waitForExitcodeFile(exitcodeFile);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new SmbFileInputStream(exitcodeFile)));
            int result  = Integer.parseInt(bufferedReader.readLine());
            String testResultMessage = "Test on machine " + testVm + " returned: " + result;
            if (result != expectedExitCode)
            {
                throw new IllegalStateException(testResultMessage);
            }
            listener.getLogger().println(testResultMessage);
        }
    }

    private String getSMBexitcodeFilePath(String testVm, String dashedImageName) {
        return "smb://" + getTurboTesterNameWithoutPort()
                + "/" + "Results/CheckApp"
                + "/" + dashedImageName
                + "-" + testVm + "-"
                + "exitcode.txt";
    }

    private String getTurboTesterNameWithoutPort() {
        return (turboTesterServer.split(":"))[0];
    }

    private void waitForExitcodeFile(SmbFile smbFile) throws InterruptedException, SmbException {
        int waitCounter = 0;
        //We wait for checkapp job to delete previous exitcode file.
        Thread.sleep(2000);
        while (!smbFile.exists() && waitCounter < maxMinutesToWaitForResult*6)
        {
            Thread.sleep(10000);
            waitCounter++;
        }
        if (!smbFile.exists())
        {
            throw new IllegalStateException("Result file " + smbFile.getCanonicalPath() + "not found after wait time passed.");
        }
    }

    private void deleteRepoFromLocalServer() throws IOException, InterruptedException {
        checkTurboConfig();
        executeRepoDelete();
    }

    private void checkTurboConfig() throws IOException, InterruptedException {
        ArgumentListBuilder command = new ArgumentListBuilder();
        command.addTokenized("turbo config");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        runCmdCommand(build, launcher, listener, command, outputStream);
        String turboConfigOutput = outputStream.toString();
        if (!turboConfigOutput.contains("s33"))
        {
            throw new IllegalStateException("turbo not connected to s33 hub!");
        }
    }

    private static int runCmdCommand(SpoonBuild build, Launcher launcher, BuildListener listener, ArgumentListBuilder argumentList, OutputStream outputStream) throws IOException, InterruptedException {
        Launcher.ProcStarter procStarter = launcher.new ProcStarter();
        procStarter = procStarter.cmds(argumentList).stdout(outputStream);
        procStarter = procStarter.pwd(build.getWorkspace()).envs(build.getEnvironment(listener));
        Proc proc = launcher.launch(procStarter);
        return proc.join();
    }

    private void executeRepoDelete() {
        //todo write deleting the repo.
        listener.getLogger().println("Lalala! Nuking repo!");
    }

    public int getExpectedExitCode() {
        return expectedExitCode;
    }

    public String getTestVms() {
        return testVms;
    }

    public int getMaxMinutesToWaitForResult() {
        return maxMinutesToWaitForResult;
    }

    private Optional<StandardUsernamePasswordCredentials> getCredentials() throws IllegalStateException {
        if (Strings.isNullOrEmpty(this.credentialsId)) {
            return Optional.absent();
        }

        Optional<StandardUsernamePasswordCredentials> credentials = Credentials.lookupById(StandardUsernamePasswordCredentials.class, this.credentialsId);

        checkState(credentials.isPresent(), "Cannot find any credentials with id (%s)", this.credentialsId);

        return credentials;
    }

    @Extension
    public static final class Descriptor extends BuildStepDescriptor<Builder> {

        private String turboTesterServer;

        private String hubUrl;

        public Descriptor() {
            super(TesterCheckBuilder.class);

            this.load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return TypeToken.of(SpoonProject.class).isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Execute turbo check on Turbotester server";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            json = json.getJSONObject("turbotester");
            turboTesterServer = json.getString("turboTesterServer");
            hubUrl = json.getString("hubUrl");

            save();
            return super.configure(req, json);
        }

        public String getTurboTesterServer() {
            return turboTesterServer;
        }

        public String getHubUrl() {
            return hubUrl;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
            return fillCredentialsIdItems(project);
        }
    }
}
