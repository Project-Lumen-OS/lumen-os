package android.content;

import java.util.ArrayList;
import java.util.List;

public class IntentFilter {
    private final List<String> mActions = new ArrayList<>();

    public IntentFilter(String action) {
        mActions.add(action);
    }

    public final void addAction(String action) {
        mActions.add(action);
    }

    public final boolean hasAction(String action) {
        return mActions.contains(action);
    }
}
