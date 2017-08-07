package org.jenkinsci.plugins.spoontrigger;

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
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jenkinsci.plugins.spoontrigger.utils.Credentials.fillCredentialsIdItems;

public class TesterCheckBuilder extends BaseBuilder {

    private static final String  CHECK_APP_URL = "/buildByToken/buildWithParameters?job=Check%20App&token=checkapp";
    private static final int JOB_TRIGGER_HTTP_RESPONSE_CODE = 201;
    //    public static final String  CHECK_APP_URL = "/job/Check%20App/buildWithParameters?delay=0sec";

    private int expectedExitCode;
    private String testVms;
    private String turboTesterServer;
    private String hubURL;
    private Image imageToCheck;
    private URL testerRequestURL;
    private int maxMinutesToWaitForResult;
    private SpoonBuild build;
    private Launcher launcher;
    private BuildListener listener;
    private final String credentialsId;

    @DataBoundConstructor
    public TesterCheckBuilder(int expectedExitCode, String testVms, String maxMinutesToWaitForResult, String credentialsId) {
        this.expectedExitCode = expectedExitCode;
        this.testVms = testVms;
        this.maxMinutesToWaitForResult = Integer.parseInt(maxMinutesToWaitForResult);
        this.credentialsId = credentialsId;
    }

    @Override
    protected void prebuild(SpoonBuild build, BuildListener listener) {
        Descriptor globalConfig = (Descriptor)getDescriptor();
        turboTesterServer = globalConfig.getTurboTesterServer();
        hubURL = globalConfig.getHubUrl();
    }

    @Override
    protected boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;

        getImageToCheck();

        triggerJenkinsJobAndCheckHTTPResponse();

        List<String> testVMsList = Arrays.asList(testVms.split("\\s*,\\s*"));
        waitForAndReadResultFiles(testVMsList);
        deleteRepoFromLocalServer();
        return true;
    }

    private void waitForAndReadResultFiles(List<String> testVMsList) throws InterruptedException, IOException {
        String dashedImageName = imageToCheck.namespace + "-" + imageToCheck.repo + "-" + imageToCheck.tag;
        for (String testVm : testVMsList)
        {
            String exitcodeFilePath = getUNCexitcodeFilePath(testVm, dashedImageName);
            waitForExitcodeFile(exitcodeFilePath);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(exitcodeFilePath));
            int result  = Integer.parseInt(bufferedReader.readLine());
            String testResultMessage = "Test on machine " + testVm + " returned: " + result;
            if (result != expectedExitCode)
            {
                throw new IllegalStateException(testResultMessage);
            }
            listener.getLogger().println(testResultMessage);
        }
    }

    private void triggerJenkinsJobAndCheckHTTPResponse() throws IOException {
        HttpURLConnection connection = prepareTesterServerConnection();
        int response = connection.getResponseCode();
        if (JOB_TRIGGER_HTTP_RESPONSE_CODE != response) {
            throw new IllegalStateException("Tester server didn't accept the request and returned code " + response);
        }
        listener.getLogger().println("HTTP response code from tester server is: " + response);
    }

    private void deleteRepoFromLocalServer() throws IOException, InterruptedException {
        checkTurboConfig();
        executeRepoDelete();
    }

    private void executeRepoDelete() {
        //todo write deleting the repo.
        listener.getLogger().println("Lalala! Nuking repo!");
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

    private String getUNCexitcodeFilePath(String testVm, String dashedImageName) {
        return "//" + getTurboTesterNameWithoutPort()
                    + "/" + "Results/CheckApp"
                    + "/" + dashedImageName
                    + "-" + testVm + "-"
                    + "exitcode.txt";
    }

    private String getTurboTesterNameWithoutPort() {
        return (turboTesterServer.split(":"))[0];
    }

    private void waitForExitcodeFile(String filePath) throws InterruptedException {
        Path path = Paths.get(filePath);
        int waitCounter = 0;
        //We wait for checkapp job to delete previous exitcode file.
        Thread.sleep(2000);
        while (Files.notExists(path) && waitCounter < maxMinutesToWaitForResult*6)
        {
            Thread.sleep(10000);
            waitCounter++;
        }
        if (Files.notExists(path))
        {
            throw new IllegalStateException("Result file " + filePath + "not found after wait time passed.");
        }
    }

    private void getImageToCheck() {
        imageToCheck = build.getOutputImage().orNull();
    }

    private HttpURLConnection prepareTesterServerConnection() throws IOException {
        String fullImageName = imageToCheck.printIdentifier();
        testVms = testVms.replaceAll(" ","");
        generateTurboTesterURL(fullImageName);
        HttpURLConnection connection = (HttpURLConnection) testerRequestURL.openConnection();
        connection.setRequestMethod("GET");
        return connection;
    }

    private int getTriggeredJobNumber(HttpURLConnection connection) throws IOException, InterruptedException {
        Map<String, List<String>> headerFields =  connection.getHeaderFields();
        String queueUrlForApi = headerFields.get("Location").get(0);
        return waitGetJobNumberFromJson(queueUrlForApi);
    }

    private int waitGetJobNumberFromJson(String queueUrlForApi) throws IOException, InterruptedException {
        int waitCounter = 0; //5 minutes
        JSONObject json = JSONObject.fromObject(IOUtils.toString(new URL(queueUrlForApi + "api/json")));
        String jsonWhy = json.getString("why");
        while (!Objects.equals(jsonWhy, "null") && (waitCounter < 600))
        {
            Thread.sleep(500);
            json = JSONObject.fromObject(IOUtils.toString(new URL(queueUrlForApi + "api/json")));
            jsonWhy = json.getString("why");
            waitCounter++;
        }
        return json.getJSONObject("executable").getInt("number");
    }

    private void generateTurboTesterURL(String fullImageName) throws MalformedURLException {
        testerRequestURL = new URL("http://" +
                                    turboTesterServer +
                                    CHECK_APP_URL+
                                    "&vms=" +
                                    testVms +
                                    "&app=" +
                                    fullImageName +
                                    "&hub=" +
                                    hubURL);
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
