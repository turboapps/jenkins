package org.jenkinsci.plugins.spoontrigger.jira;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.Closeables;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.spoontrigger.TurboTool;
import org.jenkinsci.plugins.spoontrigger.utils.Credentials;
import org.jenkinsci.plugins.spoontrigger.utils.JsonOption;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public class JiraApi implements Closeable {
    private HttpHost targetHost;
    private HttpClientContext clientContext;
    private TurboTool.BugTrackerSettings bugTrackerSettings;
    private CloseableHttpClient httpClient;

    public JiraApi(TurboTool.BugTrackerSettings bugTrackerSettings) {
        setUp(bugTrackerSettings);

        this.clientContext = createClientContext(this.targetHost, bugTrackerSettings.getCredentialsId());
    }

    public JiraApi(TurboTool.BugTrackerSettings bugTrackerSettings, String login, String password) {
        setUp(bugTrackerSettings);

        this.clientContext = createClientContext(this.targetHost, Optional.fromNullable(login), Optional.fromNullable(password));
    }

    private void setUp(TurboTool.BugTrackerSettings bugTrackerSettings) {
        this.bugTrackerSettings = bugTrackerSettings;
        this.targetHost = new HttpHost(bugTrackerSettings.getHost(), bugTrackerSettings.getPort(), "https");
        this.httpClient = HttpClients.custom().setSSLSocketFactory(SSLConnectionSocketFactory.getSystemSocketFactory()).build();
    }

    public void createOrReopenIssue(String projectName) throws Exception {
        String issueTitle = bugTrackerSettings.getIssueTitle(projectName);
        Optional<String> issueKeyOpt = getIssueKey(Pattern.compile(projectName, Pattern.CASE_INSENSITIVE));
        CloseableHttpResponse httpResponse = null;
        try {
            if (issueKeyOpt.isPresent()) {
                String issueKey = issueKeyOpt.get();
                Optional<String> transitionKeyOpt = getIssueTransition(issueKey, Pattern.compile(bugTrackerSettings.getTransitionName(), Pattern.CASE_INSENSITIVE));
                if (!transitionKeyOpt.isPresent()) {
                    String errorMsg = String.format("\"%s\" transition not found. Ignore this error if the work item \"%s\" is already opened.", bugTrackerSettings.getTransitionName(), issueKey);
                    throw new Exception(errorMsg);
                }

                httpResponse = updateStatus(issueKey, transitionKeyOpt.get());

                handleHttpResponse(httpResponse, HttpStatus.SC_NO_CONTENT, "Failed to transition issue %s-\"%s\" to \"%s\" state.", issueKey, issueTitle, bugTrackerSettings.getTransitionName());
            } else {
                httpResponse = createIssue(
                        bugTrackerSettings.getProjectKey(),
                        bugTrackerSettings.getIssueTitle(projectName),
                        bugTrackerSettings.getIssueType(),
                        bugTrackerSettings.getIssueLabel());

                handleHttpResponse(httpResponse, HttpStatus.SC_CREATED, "Failed to create %s with \"%s\" title.", bugTrackerSettings.getIssueType(), issueTitle);
            }
        } finally {
            if (httpResponse != null) {
                EntityUtils.consume(httpResponse.getEntity());

                final boolean swallowIoException = true;
                Closeables.close(httpResponse, swallowIoException);
            }
        }
    }

    public Optional<String> getIssueTransition(String key, Pattern transitionPattern) throws URISyntaxException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException {
        URI issueTransitionsUrl = getIssueTransitionsUrl(key);
        JSONObject issueTransitions = getJsonObject(issueTransitionsUrl).orNull();
        if (issueTransitions == null) {
            return Optional.absent();
        }

        if (!issueTransitions.has("transitions")) {
            return Optional.absent();
        }

        JSONArray transitions = issueTransitions.getJSONArray("transitions");
        for (int index = 0; index < transitions.size(); ++index) {
            JSONObject rawTransitionObject = transitions.getJSONObject(index);
            JsonOption.ObjectWrapper transitionObject = JsonOption.wrap(rawTransitionObject);

            String id = transitionObject.getString("id").orNull();
            if (id == null) {
                continue;
            }

            String name = transitionObject.getString("name").orNull();
            if (name == null) {
                continue;
            }

            if (transitionPattern.matcher(name).find()) {
                return Optional.of(id);
            }
        }

        return Optional.absent();
    }

    public Optional<String> getIssueKey(Pattern summaryPattern) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException, URISyntaxException {
        int totalIssues, currentIssue = 0;

        do {
            URI issuesUrl = getIssuesUrl("labels=" + this.bugTrackerSettings.getIssueLabel(), currentIssue);
            Optional<JSONObject> issuesBagOption = getJsonObject(issuesUrl);
            if (!issuesBagOption.isPresent()) {
                return Optional.absent();
            }

            JSONObject issuesBag = issuesBagOption.get();
            if (!issuesBag.has("issues")) {
                return Optional.absent();
            }

            if (!issuesBag.has("total")) {
                return Optional.absent();
            }
            totalIssues = issuesBag.getInt("total");

            JSONArray issues = issuesBag.getJSONArray("issues");
            for (int index = 0; index < issues.size(); ++index, ++currentIssue) {
                JSONObject rawIssue = issues.getJSONObject(index);
                JsonOption.ObjectWrapper issue = JsonOption.wrap(rawIssue);
                String key = issue.getString("key").orNull();
                if (key == null) {
                    continue;
                }

                JsonOption.ObjectWrapper fields = issue.getObject("fields").orNull();
                if (fields == null) {
                    continue;
                }

                String summary = fields.getString("summary").orNull();
                if (summary == null) {
                    continue;
                }

                if (summaryPattern.matcher(summary).find()) {
                    return Optional.of(key);
                }
            }
        } while (currentIssue < totalIssues);

        return Optional.absent();
    }

    public CloseableHttpResponse createIssue(String projectKey, String summary, String issueType, String labels) throws IOException, URISyntaxException {
        JSONObject projectJson = new JSONObject();
        projectJson.put("key", projectKey);

        JSONObject issueJson = new JSONObject();
        issueJson.put("name", issueType);

        JSONArray labelsJson = new JSONArray().element(labels);

        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("summary", summary);
        fieldsJson.put("project", projectJson);
        fieldsJson.put("issuetype", issueJson);
        fieldsJson.put("labels", labelsJson);

        JSONObject json = new JSONObject();
        json.put("fields", fieldsJson);

        URI issuePostUrl = getIssueUrl();
        return postJson(issuePostUrl, json);
    }

    public CloseableHttpResponse updateStatus(String issueKey, String transitionId) throws URISyntaxException, IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        JSONObject transitionJson = new JSONObject();
        transitionJson.put("id", transitionId);

        JSONObject json = new JSONObject();
        json.put("transition", transitionJson);

        URI transitionUpdateUrl = getIssueTransitionsUrl(issueKey);
        return postJson(transitionUpdateUrl, json);
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private void handleHttpResponse(CloseableHttpResponse httpResponse, int expectedStatusCode, String errorMsgFormat, Object... params) throws Exception {
        if (httpResponse.getStatusLine().getStatusCode() != expectedStatusCode) {
            String errorMsg = String.format(errorMsgFormat, params);
            StringBuilder msgBuilder = new StringBuilder(errorMsg)
                    .append(System.lineSeparator())
                    .append(httpResponse.getStatusLine())
                    .append(EntityUtils.toString(httpResponse.getEntity()));
            throw new Exception(msgBuilder.toString());
        }
    }

    private CloseableHttpResponse postJson(URI url, JSONObject json) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "application/json");
        StringEntity input = new StringEntity(json.toString());
        httpPost.setEntity(input);

        return httpClient.execute(targetHost, httpPost, clientContext);
    }

    private Optional<JSONObject> getJsonObject(URI url) throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Content-Type", "application/json");
        CloseableHttpResponse response = httpClient.execute(targetHost, httpGet, clientContext);

        int code = response.getStatusLine().getStatusCode();
        if (code == HttpStatus.SC_NOT_FOUND) {
            return Optional.absent();
        }

        HttpEntity entity = response.getEntity();
        try {
            String rawJson = EntityUtils.toString(entity);
            return Optional.of(JSONObject.fromObject(rawJson));
        } finally {
            EntityUtils.consume(entity);
        }
    }

    private static HttpClientContext createClientContext(HttpHost targetHost, String credentialsId) {
        String username = null;
        String password = null;
        if (!Strings.isNullOrEmpty(credentialsId)) {
            Optional<StandardUsernamePasswordCredentials> credentialsOtp = Credentials.lookupById(StandardUsernamePasswordCredentials.class, credentialsId);
            if (credentialsOtp.isPresent()) {
                username = credentialsOtp.get().getUsername();
                password = credentialsOtp.get().getPassword().getPlainText();
            }
        }

        return createClientContext(targetHost, Optional.fromNullable(username), Optional.fromNullable(password));
    }

    private static HttpClientContext createClientContext(HttpHost targetHost, Optional<String> usernameOpt, Optional<String> passwordOpt) {
        HttpClientContext context = HttpClientContext.create();

        if (usernameOpt.isPresent() && passwordOpt.isPresent()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(targetHost.getHostName(), AuthScope.ANY_PORT), new UsernamePasswordCredentials(usernameOpt.get(), passwordOpt.get()));
            AuthCache authCache = new BasicAuthCache();
            AuthScheme basicAuth = new BasicScheme();
            authCache.put(targetHost, basicAuth);

            context.setCredentialsProvider(credentialsProvider);
            context.setAuthCache(authCache);
        }

        return context;
    }

    private URI getIssueTransitionsUrl(String key) throws URISyntaxException {
        URIBuilder builder = new URIBuilder()
                .setPath(bugTrackerSettings.getFirstSegment() + "/rest/api/latest/issue/" + key + "/transitions");
        return builder.build();
    }

    private URI getIssueUrl() throws URISyntaxException {
        return URI.create(bugTrackerSettings.getFirstSegment() + "/rest/api/2/issue/");
    }

    private URI getIssuesUrl(String jql, int startAt) throws URISyntaxException {
        URIBuilder builder = new URIBuilder()
                .setPath(bugTrackerSettings.getFirstSegment() + "/rest/api/2/search")
                .addParameter("jql", jql)
                .addParameter("startAt", Integer.toString(startAt));
        return builder.build();
    }
}
