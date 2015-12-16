package org.jenkinsci.plugins.spoontrigger.utils;

import com.google.common.base.Optional;
import net.sf.json.JSONObject;

public class JsonOption {
    public interface ObjectWrapper {
        Optional<String> getString(String key);
    }

    private enum NullJsonObjectOption implements ObjectWrapper {
        Instance;

        @Override
        public Optional<String> getString(String key) {
            return Optional.absent();
        }
    }

    private static class JsonObjectOption implements ObjectWrapper {

        private final JSONObject delegate;

        public JsonObjectOption(JSONObject delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<String> getString(String key) {
            if (delegate.containsKey(key)) {
                return Optional.fromNullable(delegate.getString(key));
            }
            return Optional.absent();
        }
    }

    public static ObjectWrapper wrap(JSONObject json) {
        if (json == null || json.isNullObject()) {
            return NullJsonObjectOption.Instance;
        }
        return new JsonObjectOption(json);
    }
}
