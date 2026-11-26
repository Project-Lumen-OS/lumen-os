// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.open;


import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Install from USB (1.0.1): which files of a stick are offered (root, apks/, lumen-os/, one level deep). */
final class ApkFilesTest {
    /** Own run (test/run.sh): android.jar first, ApkFiles links PackageManager classes. */
    public static void main(String[] a) throws Exception {
        run();
        System.out.println("apk file tests passed=" + T.passed + " failed=" + T.failed);
        System.exit(T.failed == 0 ? 0 : 1);
    }

    static void run() throws Exception {
        T.ok(ApkFiles.candidate("Kodi.apk"), "plain .apk");
        T.ok(ApkFiles.candidate("FDROID.APK"), "upper-case .APK");
        T.ok(!ApkFiles.candidate("._Kodi.apk"), "macOS resource fork ._ file");
        T.ok(!ApkFiles.candidate(".hidden.apk"), "hidden file");
        T.ok(!ApkFiles.candidate("bundle.apks"), ".apks bundle (not in 1.0.1)");
        T.ok(!ApkFiles.candidate("app.xapk"), ".xapk");
        T.ok(!ApkFiles.candidate(".apk"), "bare extension");
        T.ok(!ApkFiles.candidate(null), "null");

        File root = Files.createTempDirectory("usbapks").toFile();
        try {
            put(root, "b.apk", 3);
            put(root, "A.APK", 3);
            put(root, "._b.apk", 3);
            put(root, "empty.apk", 0);
            put(root, "notes.txt", 3);
            put(root, "apks/c.apk", 3);
            put(root, "apks/deeper/d.apk", 3);
            put(root, "lumen-os/e.apk", 3);
            put(root, "other/f.apk", 3);
            new File(root, "dir.apk").mkdirs();
            List<String> got = new ArrayList<>();
            String rp = root.getAbsolutePath() + "/";
            for (File f : ApkFiles.files(root)) got.add(f.getAbsolutePath().substring(rp.length()));
            T.eq(String.join(",", got), "A.APK,b.apk,apks/c.apk,lumen-os/e.apk", "files of a stick");
        } finally {
            delete(root);
        }
    }

    private static void put(File root, String rel, int size) throws Exception {
        File f = new File(root, rel);
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(new byte[size]);
        }
    }

    private static void delete(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File c : l) delete(c);
        f.delete();
    }

    /** The small assertion helper of Home's tests, kept with the moved test. */
    static final class T {
        static int passed, failed;

        static void ok(boolean c, String what) {
            if (c) passed++;
            else {
                failed++;
                System.out.println("FAIL " + what);
            }
        }

        static void eq(String got, String want, String what) {
            ok(got.equals(want), what + ": got <" + got + "> want <" + want + ">");
        }
    }
}
