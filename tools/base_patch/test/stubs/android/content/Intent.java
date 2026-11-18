package android.content;

import java.util.HashMap;
import java.util.Map;

public class Intent {
    private final String mAction;
    private final Map<String, Object> mExtras = new HashMap<>();

    public Intent(String action) {
        mAction = action;
    }

    public String getAction() {
        return mAction;
    }

    public Intent putExtra(String name, Object value) {
        mExtras.put(name, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getParcelableExtra(String name) {
        return (T) mExtras.get(name);
    }

    public int getIntExtra(String name, int def) {
        Object v = mExtras.get(name);
        return v instanceof Integer ? (Integer) v : def;
    }
}
