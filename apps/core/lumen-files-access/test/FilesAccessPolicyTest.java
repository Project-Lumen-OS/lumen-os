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
        check(FilesAccessPolicy.PERMISSION.equals("android.permission.MANAGE_EXTERNAL_STORAGE"), "permission name");
        check("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(FilesAccessPolicy.sha256("abc".getBytes())),
            "certificate digest: SHA-256 in lower case hex");
        if (failed > 0) {
            System.out.println(failed + " failed");
            System.exit(1);
        }
    }
}
