package org.jenkinsci.plugins.spoontrigger.jira;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
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
import org.apache.http.client.HttpClient;
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
    private final HttpHost targetHost;
    private final HttpClientContext clientContext;
    private final TurboTool.BugTrackerSettings bugTrackerSettings;
    private final CloseableHttpClient httpClient;

    public JiraApi(TurboTool.BugTrackerSettings bugTrackerSettings) {
        this.targetHost = new HttpHost(bugTrackerSettings.getHost(), bugTrackerSettings.getPort(), "https");
        this.clientContext = createClientContext(this.targetHost, bugTrackerSettings.getCredentialsId());
        this.bugTrackerSettings = bugTrackerSettings;
        this.httpClient = HttpClients.custom().setSSLSocketFactory(SSLConnectionSocketFactory.getSystemSocketFactory()).build();
    }

    private Optional<String> getIssueTransition(JSONObject issue, Pattern transitionPattern) throws URISyntaxException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException {
        JsonOption.ObjectWrapper wrappedIssue = JsonOption.wrap(issue);
        String key = wrappedIssue.getString("key").orNull();
        if (key == null) {
            return Optional.absent();
        }

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
        URI issuesUrl = getIssuesUrl("labels=" + this.bugTrackerSettings.getLabel());
        Optional<JSONObject> issuesBagOption = getJsonObject(issuesUrl);

        if (!issuesBagOption.isPresent()) {
            return Optional.absent();
        }

        JSONObject issuesBag = issuesBagOption.get();
        if (!issuesBag.has("issues")) {
            return Optional.absent();
        }

        JSONArray issues = issuesBag.getJSONArray("issues");
        for (int index = 0; index < issues.size(); ++index) {
            JSONObject rawIssue = issues.getJSONObject(index);
            JsonOption.ObjectWrapper issue = JsonOption.wrap(rawIssue);
            JsonOption.ObjectWrapper fields = issue.getObject("fields").orNull();
            if (fields == null) {
                continue;
            }

            String key = fields.getString("key").orNull();
            if (key == null) {
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

        return Optional.absent();
    }

    public int createIssue(String projectKey, String summary, String issueType) throws IOException, URISyntaxException {
        JSONObject projectJson = new JSONObject();
        projectJson.put("key", projectKey);

        JSONObject issueJson = new JSONObject();
        issueJson.put("name", issueType);

        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("summary", summary);
        fieldsJson.put("project", projectJson);
        fieldsJson.put("issuetype", issueJson);

        JSONObject json = new JSONObject();
        json.put("fields", fieldsJson);

        URI issuePostUrl = getIssueUrl();
        return postJson(issuePostUrl, json);
    }

    public int updateStatus(String issueKey, String transitionId) throws URISyntaxException, IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
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

    private int postJson(URI url, JSONObject json) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "application/json");
        StringEntity input = new StringEntity(json.toString());
        httpPost.setEntity(input);

        CloseableHttpResponse response = httpClient.execute(targetHost, httpPost, clientContext);
        return response.getStatusLine().getStatusCode();
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
        HttpClientContext context = HttpClientContext.create();

        if (!Strings.isNullOrEmpty(credentialsId)) {
            Optional<StandardUsernamePasswordCredentials> credentialsOtp = Credentials.lookupById(StandardUsernamePasswordCredentials.class, credentialsId);
            if (credentialsOtp.isPresent()) {
                String userName = credentialsOtp.get().getUsername();
                String password = credentialsOtp.get().getPassword().getPlainText();

                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(targetHost.getHostName(), AuthScope.ANY_PORT), new UsernamePasswordCredentials(userName, password));
                AuthCache authCache = new BasicAuthCache();
                AuthScheme basicAuth = new BasicScheme();
                authCache.put(targetHost, basicAuth);

                context.setCredentialsProvider(credentialsProvider);
                context.setAuthCache(authCache);
            }
        }

        return context;
    }

    private URI getIssueTransitionsUrl(String key) throws URISyntaxException {
        URIBuilder builder = new URIBuilder()
                .setPath("/bugs/rest/api/latest/issue/" + key + "/transitions");
        return builder.build();
    }

    private URI getIssueUrl() throws URISyntaxException {
        return URI.create("/bugs/rest/api/2/issue/");
    }

    private URI getIssuesUrl(String jql) throws URISyntaxException {
        URIBuilder builder = new URIBuilder()
                .setPath("/bugs/rest/api/2/search")
                .setCustomQuery("jql=" + jql);
        return builder.build();
    }
}
