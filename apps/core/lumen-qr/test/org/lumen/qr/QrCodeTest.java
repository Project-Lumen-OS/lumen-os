// SPDX-License-Identifier: Apache-2.0
package org.lumen.qr;

import java.util.ArrayList;
import java.util.List;

/**
 * QrCode on the JVM: the version grows with the text, a text past version 10-M is refused, and the matrices of 58
 * texts are pinned by their SHA-256 (recorded when the first-run app's own encoder, which drew the same matrices,
 * moved onto this module), so the guide, the update screen, About and the setup wizard keep drawing the same codes.
 */
public final class QrCodeTest {
    private static int failed;
    /** SHA-256 of the 58 matrices below, equal to the former first-run encoder's (org.lumen.setup.ui.Qr). */
    private static final String MATRICES = "6aae24d94b43202163f1e62db07409a198d633bde4a654207a2cc0805776df50";

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failed++;
    }

    private static String text(int n, int seed) {
        StringBuilder b = new StringBuilder("https://product-library.static.xgimi.com/firmware/release/");
        for (int i = 0; b.length() < n; i++) b.append((char) ('a' + (i * 7 + seed) % 26));
        return b.substring(0, n);
    }

    public static void main(String[] a) {
        check(QrCode.encode("G0082").version == 1, "a short text is version 1");
        check(QrCode.encode("G0082").size == 21, "version 1 has 21 modules");
        check(QrCode.encode(text(120, 1)).version == 7, "a 120 byte address is version 7");
        check(QrCode.encode(text(213, 2)).version == 10, "213 bytes still fit version 10-M");
        boolean refused = false;
        try {
            QrCode.encode(text(214, 3));
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "214 bytes are refused");
        List<String> texts = new ArrayList<>();
        texts.add("");
        texts.add("https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/ru.md#stock");
        texts.add("https://product-library.static.xgimi.com/firmware/release/MTK9681/dapengend/"
                + "MTK9681_LEO_6.15.62_20260325-154151/XGIMI_HORSEPOPULAR_XR03D.bin");
        texts.add("Прошивка XGIMI");
        for (int n = 1; n <= 213; n += 4) texts.add(text(n, n));
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (String t : texts) {
            QrCode q = QrCode.encode(t);
            md.update((byte) q.size);
            for (int y = 0; y < q.size; y++)
                for (int x = 0; x < q.size; x++) md.update((byte) (q.dark(x, y) ? 1 : 0));
        }
        StringBuilder h = new StringBuilder();
        for (byte b : md.digest()) h.append(String.format("%02x", b));
        if (a.length > 0 && a[0].equals("--print")) System.out.println(h);
        check(h.toString().equals(MATRICES), texts.size() + " texts draw the pinned matrices (" + h + ")");
        System.out.println("QrCodeTest: " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
