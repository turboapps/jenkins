package org.jenkinsci.plugins.spoontrigger;

import com.google.common.reflect.TypeToken;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.spoontrigger.hub.Image;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * Created by turbo1 on 14.07.2017.
 */
public class TesterCheckBuilder extends BaseBuilder {

    public static final String  CHECK_APP_URL = "/buildByToken/buildWithParameters?job=Check%20App&token=checkapp";

    private int expectedExitCode;
    private String testVms;
    private String turboTesterServer;
    private String hubURL;
    private Image imageToCheck;
    private URL testerRequestURL;
    private int maxMinutesToWaitForResult;
    private int resultsInitialCount;
    private String resultDirPath;
    private int vmCount;

    @DataBoundConstructor
    public TesterCheckBuilder(int expectedExitCode, String testVms, String maxMinutesToWaitForResult) {
        this.expectedExitCode = expectedExitCode;
        this.testVms = testVms;
        this.maxMinutesToWaitForResult = Integer.parseInt(maxMinutesToWaitForResult);
    }

    @Override
    protected void prebuild(SpoonBuild build, BuildListener listener) {
        Descriptor globalConfig = (Descriptor)getDescriptor();
        turboTesterServer = globalConfig.getTurboTesterServer();
        hubURL = globalConfig.getHubUrl();
    }

    @Override
    protected boolean perform(SpoonBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        getImageToCheck(build);
//        resultDirPath = "//" + getTurboTesterNameWithoutPort() + "/Results/CheckApp/";
        resultDirPath = "//s43.code.net/Results/CheckApp/";
        getResultsInitialCount();

        HttpURLConnection connection = prepareTesterServerConnection();
        int response = connection.getResponseCode();
        if (checkHTTPResponseCode(response))
            throw new IllegalStateException("Tester server didn't accept the request and returned code " + response);
        listener.getLogger().println("HTTP response code from tester server is: " + response);
//        int triggeredJobNumber = getTriggeredJobNumber(connection);
int triggeredJobNumber = 25;
        List<String> testVMsList = Arrays.asList(testVms.split("\\s*,\\s*"));
        vmCount = testVMsList.size();
        for (String testVm : testVMsList)
        {
            String dashedImageName = imageToCheck.namespace + "-" + imageToCheck.repo + "-" + imageToCheck.tag;
            String resultFileUrl = getUNCResultFileUrl(triggeredJobNumber, testVm, dashedImageName);
            waitForExitCodeFile(resultFileUrl);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(resultFileUrl));
            int result  = Integer.parseInt(bufferedReader.readLine());
            String testResultMessage = "Test on machine " + testVm + " returned " + result;
            if (result != expectedExitCode)
            {
                throw new IllegalStateException(testResultMessage);
            }
            listener.getLogger().println(testResultMessage);

        }
        return true;
    }

    private boolean areResultsReady() {
        return getNewResultsNumber() >= vmCount;
    }

    private int getNewResultsNumber () {
        return new File(resultDirPath).list().length - resultsInitialCount;
    }

    private void getResultsInitialCount () {
        resultsInitialCount = new File(resultDirPath).list().length;
    }

    private boolean checkHTTPResponseCode(int response) {
        boolean compareResult = response != 201;
        return (compareResult);
    }

    private String getUNCResultFileUrl(int triggeredJobNumber, String testVm, String dashedImageName) {
        return "//" + getTurboTesterNameWithoutPort()
                    + "/Results/CheckApp/"
                    + dashedImageName
                    + "-" + testVm + "-"
                    + triggeredJobNumber
                    + "/exitcode.txt";
    }

    private String getTurboTesterNameWithoutPort() {
        return (turboTesterServer.split(":"))[0];
    }

    private void waitForExitCodeFile(String filePath) throws InterruptedException {
        Path path = Paths.get(filePath);
        int waitCounter = 0;
        while (areResultsReady() && waitCounter < maxMinutesToWaitForResult)
        {
            Thread.sleep(10000);
            waitCounter++;
        }

    }

    private void getImageToCheck(SpoonBuild build) {
        imageToCheck = build.getOutputImage().orNull();

        imageToCheck = new Image("mroova","sampleapp","17.07.14");
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
        int jobNumber = waitGetJobNumberFromJson(queueUrlForApi);
        return jobNumber;
    }

    private int waitGetJobNumberFromJson(String queueUrlForApi) throws IOException, InterruptedException {
        int waitCounter = 0; //5 minutes
        JSONObject json = JSONObject.fromObject(IOUtils.toString(new URL(queueUrlForApi + "api/json")));
        String jsonWhy = json.getString("why");
        while (jsonWhy != "null" && (waitCounter < 600))
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
    }
}
