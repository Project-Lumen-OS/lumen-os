package lumen.fake;
/** One pairing implementation under test, driven through its public entry points only. */
public interface Subject {
    /** the persistent app starts it (Application.onCreate / boot) */
    void start();
    /** the user asks to pair a remote (Projector settings row / menu remote row) */
    void pair();
    /** what the first-run setup would read: connected / bonded / mode */
    String state();
}
