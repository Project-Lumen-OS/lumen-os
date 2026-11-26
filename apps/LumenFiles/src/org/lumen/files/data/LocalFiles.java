// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.data;

import android.content.Context;
import android.webkit.MimeTypeMap;

import org.lumen.files.model.Kind;
import org.lumen.files.model.Kinds;
import org.lumen.files.model.Names;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Local files for the viewers and the providers (since Lumen OS 1.0.4 inside Home, its own app now): which files
 * may be read (only inside the drives of {@link Volumes}), their MIME type, and the photos or songs of a folder.
 * Read through MANAGE_EXTERNAL_STORAGE, granted to the platform-signed app by signature.
 */
public final class LocalFiles {
    private LocalFiles() {}

    /** The type a player or viewer is asked with: Files' table, the system's, else the kind's wildcard. */
    public static String mime(String name) {
        String m = Kinds.mime(name);
        if (m == null) m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Kinds.ext(name));
        return m != null ? m : Kinds.fallback(Kinds.of(name));
    }

    /** True when {@code f} lies on a drive of Files (canonical paths: no "..", no link out of it). */
    public static boolean inPlaces(Context c, File f) {
        for (Volumes.Volume v : Volumes.list(c)) if (v.fs.holds(f.getPath())) return true;
        return false;
    }

    /** The files of one kind in a folder, by name (the viewer's LEFT and RIGHT, the player's next song). */
    public static List<File> same(File dir, Kind k) {
        List<File> out = new ArrayList<>();
        File[] all = dir == null ? null : dir.listFiles();
        if (all == null) return out;
        Collator col = Collator.getInstance();
        col.setStrength(Collator.SECONDARY);
        Arrays.sort(all, (a, b) -> col.compare(a.getName(), b.getName()));
        for (File f : all) if (f.isFile() && !Names.hidden(f.getName()) && Kinds.of(f.getName()) == k) out.add(f);
        return out;
    }
}
