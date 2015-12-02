package org.jenkinsci.plugins.spoontrigger.hub;

import com.google.common.base.Optional;
import hudson.model.BuildListener;
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.jenkinsci.plugins.spoontrigger.utils.LogUtils.log;
import static com.google.common.base.Preconditions.checkArgument;

public class HubApi {

    private static final String BaseHubUrl = "turbo.net";

    private final BuildListener listener;

    public HubApi(BuildListener listener) {
        this.listener = listener;
    }

    public boolean isAvailableRemotely(Image image) throws Exception {
        checkArgument(image.getNamespace() != null, "image");

        try {
            URI uri = getRepoUrl(image);
            Optional<JSONObject> jsonObject = getJsonObject(uri);

            if (!jsonObject.isPresent()) {
                return false;
            }

            if (image.getTag() == null) {
                return true;
            }

            JSONObject repo = jsonObject.get();
            if (!repo.has("tags")) {
                return false;
            }

            JSONArray tags = repo.getJSONArray("tags");
            return tags.contains(image.getTag());
        } catch (Exception ex) {
            String msg = String.format(
                    "Failed to check if image %s is available in the remote repo: %s",
                    image.printIdentifier(),
                    ex.getMessage());
            log(listener, msg, ex);

            throw new Exception(msg, ex);
        }
    }

    private Optional<JSONObject> getJsonObject(URI url) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpGet = new HttpGet(url);
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
        URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setHost(BaseHubUrl)
                .setPath("/io/_hub/repo/" + image.getNamespace() + "/" + image.getRepo());
        return builder.build();
    }
}
