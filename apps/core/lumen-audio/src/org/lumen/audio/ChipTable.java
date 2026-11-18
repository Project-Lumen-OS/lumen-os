// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A model's chip volume table: which outputs the vendor DSP attenuates, with which HAL key, and the vendor path of each
 * output. Data only, one file per model (devices/&lt;code&gt;/audio/chip_volume.tsv, shipped as the app asset
 * lumen_audio/chip_volume.tsv), read from that model's stock AudioService.setDspVolumesToHal and the stock
 * libxgimiaudiopolicy threadInit path table. Plain Java, host tested. Lines ('#' starts a comment):
 * <pre>
 *   out    TYPE  KEY|-  DEVICE|-  PATH|-   an AudioDeviceInfo type, its HAL key (- none), ";device=" id, MediaPath
 *   path   PATH                            the path of every type the table does not list (stock: the speaker)
 *   fixed  TYPE                            a fixed volume output (AudioService FIXED behaviour, stock device list)
 *   levels after-end                       the HAL takes a level only for its current sink: send it after switch_end
 * </pre>
 */
public final class ChipTable {
    /** AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: the HAL level stays full while AVRCP absolute volume is on (stock). */
    public static final int TYPE_A2DP = 8;

    private final List<ChipKey> rows;
    private final int defaultPath;
    private final int[] fixed;
    private final boolean levelsAfterEnd;

    private ChipTable(List<ChipKey> rows, int defaultPath, int[] fixed, boolean levelsAfterEnd) {
        this.rows = Collections.unmodifiableList(rows);
        this.defaultPath = defaultPath;
        this.fixed = fixed;
        this.levelsAfterEnd = levelsAfterEnd;
    }

    /** A table without rows: nothing is owned, every output keeps Android's volume (a model without the asset). */
    public static ChipTable empty() {
        return new ChipTable(new ArrayList<>(), MediaPath.SPEAKER, new int[0], false);
    }

    /** Parses the table text. Throws IllegalArgumentException naming the bad line. */
    public static ChipTable parse(String text) {
        List<ChipKey> rows = new ArrayList<>();
        List<Integer> fixed = new ArrayList<>();
        int defaultPath = MediaPath.NONE;
        boolean after = false;
        String[] lines = text == null ? new String[0] : text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] f = line.split("\\s+");
            try {
                switch (f[0]) {
                    case "out":
                        need(f, 5);
                        rows.add(new ChipKey(Integer.parseInt(f[1]), dash(f[2]) ? null : f[2],
                                dash(f[3]) ? ChipKey.NO_DEVICE : Integer.parseInt(f[3]),
                                dash(f[4]) ? MediaPath.NONE : Integer.parseInt(f[4])));
                        break;
                    case "path":
                        need(f, 2);
                        defaultPath = Integer.parseInt(f[1]);
                        break;
                    case "fixed":
                        need(f, 2);
                        fixed.add(Integer.parseInt(f[1]));
                        break;
                    case "levels":
                        need(f, 2);
                        if (!"after-end".equals(f[1])) throw new IllegalArgumentException("levels " + f[1]);
                        after = true;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown directive " + f[0]);
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("chip table line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        int[] fx = new int[fixed.size()];
        for (int i = 0; i < fx.length; i++) fx[i] = fixed.get(i);
        return new ChipTable(rows, defaultPath, fx, after);
    }

    private static boolean dash(String s) {
        return "-".equals(s);
    }

    private static void need(String[] f, int n) {
        if (f.length != n) throw new IllegalArgumentException(f[0] + " takes " + (n - 1) + " values, got " + (f.length - 1));
    }

    public List<ChipKey> rows() {
        return rows;
    }

    /**
     * The outputs whose level the chip applies (every row with a key): Android stays at full scale on them. A2DP only
     * while AVRCP absolute volume is off (stock: with it on, the headphones apply the index).
     */
    public int[] owned(boolean avrcpAbsolute) {
        Set<Integer> types = new LinkedHashSet<>();
        for (ChipKey r : rows) {
            if (!r.hasKey() || (avrcpAbsolute && r.type == TYPE_A2DP)) continue;
            types.add(r.type);
        }
        int[] out = new int[types.size()];
        int i = 0;
        for (int t : types) out[i++] = t;
        return out;
    }

    /** The vendor path of an output type: its row, else the table's path for unlisted types. */
    public int pathOf(int type) {
        for (ChipKey r : rows) if (r.type == type) return r.path;
        return defaultPath;
    }

    /** The rows with a key whose output plays on {@code path}. */
    public List<ChipKey> ofPath(int path) {
        List<ChipKey> out = new ArrayList<>();
        for (ChipKey r : rows) if (r.hasKey() && r.path == path && path != MediaPath.NONE) out.add(r);
        return out;
    }

    /** The rows with a key, one list per slot in table order (rows of one slot are alternatives, e.g. two USB types). */
    public List<List<ChipKey>> slots() {
        List<List<ChipKey>> out = new ArrayList<>();
        for (ChipKey r : rows) {
            if (!r.hasKey()) continue;
            List<ChipKey> slot = null;
            for (List<ChipKey> s : out) if (s.get(0).slot().equals(r.slot())) slot = s;
            if (slot == null) {
                slot = new ArrayList<>();
                out.add(slot);
            }
            slot.add(r);
        }
        return out;
    }

    public int[] fixed() {
        return fixed.clone();
    }

    /** The HAL keeps a level only for its current sink (Play 6 mt5889): levels go again after switch_end. */
    public boolean levelsAfterEnd() {
        return levelsAfterEnd;
    }
}
