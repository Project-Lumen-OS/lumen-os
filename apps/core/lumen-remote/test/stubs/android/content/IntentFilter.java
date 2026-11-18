package android.content;
public class IntentFilter {
    public static final int SYSTEM_HIGH_PRIORITY = 1000;
    public final java.util.List<String> actions = new java.util.ArrayList<>();
    public IntentFilter() {}
    public IntentFilter(String a) { actions.add(a); }
    public void addAction(String a) { actions.add(a); }
    public void setPriority(int p) {}
    public boolean hasAction(String a) { return actions.contains(a); }
}
