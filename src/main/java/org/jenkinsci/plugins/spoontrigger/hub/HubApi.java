package org.jenkinsci.plugins.spoontrigger.hub;

import com.google.common.base.Optional;
import com.google.common.collect.Ordering;
import hudson.model.BuildListener;
import lombok.SneakyThrows;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.spoontrigger.SpoonBuild;
import org.jenkinsci.plugins.spoontrigger.TurboTool;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;

public class HubApi {

    public static final String DEFAULT_HUB_URL = "https://turbo.net";

    private final String hubUrl;
    private final String hubApiKey;
    private final BuildListener listener;

    public static HubApi create(SpoonBuild build, BuildListener listener) {
        return create(build, listener, null);
    }

    public static HubApi create(SpoonBuild build, BuildListener listener, @Nullable String defaultHubUrl) {
        final String hubUrl = getHubUrl(build, defaultHubUrl);

        TurboTool turboInstallation = TurboTool.getDefaultInstallation();
        final String hubApiKey = turboInstallation.getHubApiKey();
        return new HubApi(hubUrl, hubApiKey, listener);
    }

    public HubApi(String hubUrl, String hubApiKey, BuildListener listener) {
        this.hubUrl = hubUrl;
        this.hubApiKey = hubApiKey;
        this.listener = listener;
    }

    @SneakyThrows
    public Image getLatestVersion(Image image) {
        checkArgument(image.getNamespace() != null, "image");

        try {
            Optional<JSONArray> tags = getTags(image);
            if (tags.isPresent()) {
                JSONArray jsonArray = tags.get();
                final int length = jsonArray.size();
                ArrayList<Version> versions = new ArrayList<Version>(length);
                for (int position = 0; position < length; ++position) {
                    String tag = jsonArray.getString(position);
                    Optional<Version> versionOpt = Version.tryParse(tag);
                    if (versionOpt.isPresent()) {
                        versions.add(versionOpt.get());
                    } else {
                        log(listener, String.format("Failed to parse %s tag", tag));
                    }
                }
                if (!versions.isEmpty()) {
                    Version maxVersion = Ordering.<Version>natural().max(versions);
                    return new Image(image.getNamespace(), image.getRepo(), maxVersion.toString());
                }
            }

        } catch (Exception ex) {
            String msg = String.format(
                    "Failed to check the latest version of image %s in the remote repo: %s",
                    image.printIdentifier(),
                    ex.getMessage());
            log(listener, msg, ex);

            throw new Exception(msg, ex);
        }

        return image;
    }

    public boolean isAvailableRemotely(Image image) throws Exception {
        checkArgument(image.getNamespace() != null, "image");

        try {
            Optional<JSONArray> tags = getTags(image);

            if (image.getTag() == null) {
                return true;
            }

            return tags.isPresent() && tags.get().contains(image.getTag());
        } catch (Exception ex) {
            String msg = String.format(
                    "Failed to check if image %s is available in the remote repo: %s",
                    image.printIdentifier(),
                    ex.getMessage());
            log(listener, msg, ex);

            throw new Exception(msg, ex);
        }
    }

    private static String getHubUrl(SpoonBuild build, @Nullable String defaultHubUrl) {
        Optional<String> hubUrlOpt = build.getHubUrl();
        if (hubUrlOpt.isPresent()) {
            return hubUrlOpt.get();
        }
        if (defaultHubUrl != null) {
            return defaultHubUrl;
        }
        return DEFAULT_HUB_URL;
    }

    private Optional<JSONArray> getTags(Image image) throws Exception {
        URI uri = getRepoUrl(image);
        Optional<JSONObject> jsonObject = getJsonObject(uri);

        if (!jsonObject.isPresent()) {
            return Optional.absent();
        }

        JSONObject repo = jsonObject.get();
        if (!repo.has("tags")) {
            return Optional.absent();
        }

        JSONArray tags = repo.getJSONArray("tags");
        return Optional.of(tags);
    }

    private Optional<JSONObject> getJsonObject(URI url) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("Content-Type", "application/json");
            httpGet.addHeader("X-Spoon-Hub-Key", hubApiKey);
            CloseableHttpResponse response = httpclient.execute(httpGet);

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
        } finally {
            httpclient.close();
        }
    }

    private URI getRepoUrl(Image image) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(hubUrl)
                .setPath("/io/_hub/repo/" + image.getNamespace() + "/" + image.getRepo());
        return builder.build();
    }
}
