// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import org.lumen.home.proj.MirrorReceiver;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The screen mirroring tile (proj/MirrorReceiver): shown only while the receiver's control provider resolves to its
 * own system package, which LumenMiracast's ControlGate switches off on a unit without Wi-Fi Direct (the projector
 * menu's tile follows the same provider, lumen-menu QpdMirror.installed).
 */
public final class MirrorTileTest {
    public static void run() throws Exception {
        T.ok(MirrorReceiver.shown("org.lumen.miracast", true), "the receiver's provider resolves: tile shown");
        T.ok(!MirrorReceiver.shown(null, true), "no provider (no Wi-Fi Direct, or no receiver): tile hidden");
        T.ok(!MirrorReceiver.shown("com.example.cast", true), "another package's provider: tile hidden");
        T.ok(!MirrorReceiver.shown("org.lumen.miracast", false), "a user-installed copy: tile hidden");
        T.eq(InputSource.PKG_MIRACAST, MirrorReceiver.PKG, "one receiver package");
        File home = new File(System.getProperty("home.dir", "."));
        String src = new String(Files.readAllBytes(new File(home, "src/org/lumen/home/data/InputSource.java").toPath()), StandardCharsets.UTF_8);
        T.ok(src.contains("        if (MirrorReceiver.offered(c)) tile(out, c, \"miracast\", R.string.tile_miracast, R.drawable.ic_cast);")
                && !src.contains("installed(c, PKG_MIRACAST)"), "the tile is built by the provider rule, not by the package");
        String gate = new String(Files.readAllBytes(new File(home, "../LumenMiracast/src/org/lumen/miracast/ControlGate.java").toPath()),
                StandardCharsets.UTF_8);
        T.ok(gate.contains("new ComponentName(c, StateProvider.class)"), "the receiver switches the same provider off");
        String mf = new String(Files.readAllBytes(new File(home, "../LumenMiracast/AndroidManifest.xml").toPath()), StandardCharsets.UTF_8);
        T.ok(mf.contains("android:authorities=\"org.lumen.miracast\""), "the provider's authority");
    }
}
