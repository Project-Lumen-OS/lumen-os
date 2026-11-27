// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

/** Host stub: records the calls Halt makes (the real ones block until the system goes down). */
public class PowerManager {
    public static final String SHUTDOWN_USER_REQUESTED = "userrequested";
    public static final java.util.List<String> CALLS = new java.util.ArrayList<>();

    public void shutdown(boolean confirm, String reason, boolean wait) { CALLS.add("shutdown " + confirm + " " + reason + " " + wait); }

    public void reboot(String reason) { CALLS.add("reboot " + reason); }

    public void goToSleep(long time) { CALLS.add("goToSleep"); }
}
