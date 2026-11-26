// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.filesaccess;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Host test of FilesAccessPolicy: who gets all files access and when. */
public final class FilesAccessPolicyTest {
    private static int failed;
    private static final String PLAY = "com.xgimiru.player";
    private static final String KEY = "39ab3b9e6436929ef3663681a8e10e78b429cbac53e93bd9e904cb74be4772a0";
    private static final String OTHER = "8adaaa27" + KEY.substring(8);

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failed++;
    }

    private static List<String> one(String s) {
        return Collections.singletonList(s);
    }

    public static void main(String[] a) {
        // media read permissions (runtime): the same rule, never over a fixed choice
        check(FilesAccessPolicy.shouldGrantRuntime(PLAY, one(KEY), true, false, false, 1000L, -1L),
            "runtime: Lumen Play asks for a media permission it lacks: granted");
        check(!FilesAccessPolicy.shouldGrantRuntime(PLAY, one(KEY), true, false, true, 1000L, -1L),
            "runtime: a fixed choice (don't ask again, policy, system) is never overridden");
        check(!FilesAccessPolicy.shouldGrantRuntime(PLAY, one(KEY), false, false, false, 1000L, -1L),
            "runtime: a permission the manifest does not ask for is not granted");
        check(!FilesAccessPolicy.shouldGrantRuntime(PLAY, one(KEY), true, true, false, 1000L, -1L),
            "runtime: already granted, nothing to do");
        check(!FilesAccessPolicy.shouldGrantRuntime(PLAY, one(KEY), true, false, false, 1000L, 1000L),
            "runtime: a user who took it back keeps it off until the next update");
        check(FilesAccessPolicy.shouldGrantRuntime(PLAY, one(KEY), true, false, false, 2000L, 1000L),
            "runtime: the next update grants it again");
        check(!FilesAccessPolicy.shouldGrantRuntime(PLAY, one(OTHER), true, false, false, 1000L, -1L),
            "runtime: another key never gets it");
        check(!FilesAccessPolicy.shouldGrantRuntime("com.example.other", one(KEY), true, false, false, 1000L, -1L),
            "runtime: an unknown package never gets it");
        check(Arrays.asList(FilesAccessPolicy.runtimeGrants()).equals(Arrays.asList(
                "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")),
            "runtime: exactly the media reads (Lumen Play 2.0 asks for audio, video and visual user selected)");
        check(!FilesAccessPolicy.handledKey(PLAY, "android.permission.READ_MEDIA_AUDIO").equals(PLAY),
            "runtime: each permission keeps its own handled install");
        check(FilesAccessPolicy.shouldGrant(PLAY, one(KEY), true, false, 1000L, -1L),
            "Lumen Play signed with the release key, never handled: granted");
        check(FilesAccessPolicy.shouldGrant(PLAY, one(KEY.toUpperCase()), true, false, 1000L, -1L),
            "the digest compares without case");
        check(FilesAccessPolicy.shouldGrant(PLAY, one(KEY), true, false, 2000L, 1000L),
            "an update (new lastUpdateTime) is granted again");
        check(!FilesAccessPolicy.shouldGrant(PLAY, one(KEY), true, false, 1000L, 1000L),
            "the same install handled once: a user who turned it off keeps it off");
        check(!FilesAccessPolicy.shouldGrant(PLAY, one(KEY), true, true, 1000L, -1L),
            "already allowed: nothing to set");
        check(!FilesAccessPolicy.shouldGrant(PLAY, one(OTHER), true, false, 1000L, -1L),
            "the same package signed with another key: never");
        check(!FilesAccessPolicy.shouldGrant(PLAY, Arrays.asList(KEY, OTHER), true, false, 1000L, -1L),
            "our key plus another signer: never");
        check(!FilesAccessPolicy.shouldGrant(PLAY, Collections.<String>emptyList(), true, false, 1000L, -1L),
            "no signer read: never");
        check(!FilesAccessPolicy.shouldGrant(PLAY, Arrays.asList(KEY, null), true, false, 1000L, -1L),
            "an unreadable signer: never");
        check(!FilesAccessPolicy.shouldGrant(PLAY, null, true, false, 1000L, -1L), "no signer list: never");
        check(!FilesAccessPolicy.shouldGrant(PLAY, one(KEY), false, false, 1000L, -1L),
            "a build that does not ask for MANAGE_EXTERNAL_STORAGE: never");
        check(!FilesAccessPolicy.shouldGrant("com.example.player", one(KEY), true, false, 1000L, -1L),
            "a package outside the table, even with our key: never");
        check(Arrays.equals(FilesAccessPolicy.packages(), new String[] {PLAY}), "the table holds Lumen Play only");
        check(KEY.equals(FilesAccessPolicy.pin(PLAY)), "Lumen Play is pinned to the release certificate 39ab3b9e");
        check(FilesAccessPolicy.OP.equals("android:manage_external_storage"), "app op name");
        String[][] g = FilesAccessPolicy.grants();
        check(g.length == 2 && Arrays.equals(g[0], new String[] {FilesAccessPolicy.PERMISSION, FilesAccessPolicy.OP})
                && Arrays.equals(g[1], new String[] {"android.permission.SYSTEM_ALERT_WINDOW", "android:system_alert_window"}),
            "two app ops: all files access, then drawing over other apps (videos from a phone open in the background)");
        g[1][1] = "android:changed";
        check(FilesAccessPolicy.grants()[1][1].equals("android:system_alert_window"), "the grant table cannot be changed from outside");
        check(FilesAccessPolicy.handledKey(PLAY, FilesAccessPolicy.OP).equals(PLAY),
            "all files access keeps its handled key of 2.0 units (the package name)");
        check(FilesAccessPolicy.handledKey(PLAY, FilesAccessPolicy.OVERLAY_OP).equals(PLAY + "|android:system_alert_window"),
            "the overlay op has a handled key of its own: an update grants it even where files access was handled");
        check(FilesAccessPolicy.PERMISSION.equals("android.permission.MANAGE_EXTERNAL_STORAGE"), "permission name");
        check("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(FilesAccessPolicy.sha256("abc".getBytes())),
            "certificate digest: SHA-256 in lower case hex");
        if (failed > 0) {
            System.out.println(failed + " failed");
            System.exit(1);
        }
    }
}
