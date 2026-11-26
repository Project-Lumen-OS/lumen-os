// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A partition XGIMI's own update writes outside ro.product.ab_ota_partitions, from the post-install step of the new
 * vendor (payload: vendor run_postinstall=1, postinstall_path=bin/postinst_mtk, not optional). The one such partition
 * is the first boot stage in the eMMC boot areas (rom_emmc_boot_a = mmcblk0boot0, rom_emmc_boot_b = mmcblk0boot1):
 * postinst_mtk reads /postinstall/bin/rom_emmc_boot.bin (the file inside the new vendor) and, when it is no
 * multi-chip package, writes the whole file at offset 0 of the half being installed (running a: mmcblk0boot1, running
 * b: mmcblk0boot0), force_ro set to 0 before and back to 1 after; the boot HAL's setActiveBootSlot then enables that
 * area in EXT_CSD[179]. Proven by disassembly of every writer listed in {@link #writerSha} (gsi-stock analysis).
 *
 * The install copy writes the same bytes the same way: the running half's own vendor file, the area of the other
 * half, force_ro cleared only around the write. Data only: a model lists it when its stock does the same.
 */
public final class PostWrite {
    /** the partition base name (rom_emmc_boot) */
    public final String name;
    /** the file of the running vendor stock writes (as /postinstall/bin/... of the new vendor) */
    public final String file;
    /** the vendor's post-install program that writes it */
    public final String writer;
    /** sha256 of every writer whose code was read (disassembly): only these are known to write the whole file at 0 */
    public final List<String> writerSha;
    /** the eMMC boot area of half a and of half b (the by-name links of the stock dumps) */
    public final String nodeA, nodeB;
    /**
     * [start, end) byte ranges inside the file the projector itself rewrites in the area it starts from (the memory
     * training table and a zeroed blob, read from the dumps): outside them the area of a half holds the file byte for byte
     */
    public final List<long[]> deviceWritten;

    /** A writer's multi-chip package starts with this magic: then it writes one part of the file, never the whole. */
    public static final String PACKAGE_MAGIC = "MOPF";

    PostWrite(String name, String file, String writer, List<String> writerSha, String nodeA, String nodeB, long[]... deviceWritten) {
        this.name = name; this.file = file; this.writer = writer; this.writerSha = writerSha; this.nodeA = nodeA; this.nodeB = nodeB;
        this.deviceWritten = Collections.unmodifiableList(Arrays.asList(deviceWritten));
    }

    /** the area of a half (_a, _b) */
    public String node(String half) { return "_a".equals(half) ? nodeA : nodeB; }

    /**
     * EXT_CSD[179] BOOT_PARTITION_ENABLE that starts this half: 1 = boot area 1 (mmcblk0boot0), 2 = boot area 2
     * (mmcblk0boot1), the values the boot HAL writes (do_write_boot_en(slot + 1)).
     */
    public int enable(String half) { return node(half).endsWith("boot0") ? 1 : node(half).endsWith("boot1") ? 2 : -1; }

    /** the eMMC whose boot areas these are (mmcblk0) */
    public String disk() { return nodeA.replaceFirst("boot[01]$", ""); }

    public boolean deviceWritten(long off) {
        for (long[] r : deviceWritten) if (off >= r[0] && off < r[1]) return true;
        return false;
    }

    /**
     * The area holds the file as stock leaves it: the first {@code file.length} bytes equal the file outside the ranges
     * the projector rewrites itself. null = yes, else the first offset that differs.
     */
    public Long differs(byte[] file, byte[] area) {
        if (area.length < file.length) return (long) area.length;
        for (int i = 0; i < file.length; i++) if (file[i] != area[i] && !deviceWritten(i)) return (long) i;
        return null;
    }

    static final String FIRST_STAGE = "rom_emmc_boot";
    static final String FILE = "/vendor/bin/rom_emmc_boot.bin";
    static final String WRITER = "/vendor/bin/postinst_mtk";

    /**
     * The Z9X HB (MT9660). postinst_mtk d57af229 (6020 B, arm32) of G0130 6.16.24 and G0148 6.17.12, the same file in both
     * official packages. Areas = the vendor file outside 0x46000-0x46300 (DRAM training table, 0x46004-0x46252 seen) and
     * 0x6cc00-0x6d000 (blob, 0x6cc00-0x6cd0b and 0x6cef4-0x6cfff seen): G0130 before and after its update, G0148.
     */
    static final PostWrite HB_FIRST_STAGE = new PostWrite(FIRST_STAGE, FILE, WRITER,
            FwModel.list("d57af229c8a351b9f1613a5de5d6fdf64e266e75f56564908c859a74d67ed8ea"), "mmcblk0boot0", "mmcblk0boot1",
            new long[] {0x46000, 0x46300}, new long[] {0x6cc00, 0x6d000});

    /**
     * The mt5877 line (Z9X, X50 family). postinst_mtk 303152c9 (134000 B, arm64) of G0082 6.15.19, 6.15.58, 6.15.62,
     * G0111 1.2.64, H0062 1.2.54 and 1.2.99: the same code path (no package magic in any of their files: the whole file
     * at 0). Areas = the vendor file outside 0x66c00-0x67000 (0x66c04-0x66d36, 0x66e78-0x66fa6 seen) and 0x9a400-0x9a800
     * (0x9a400-0x9a413, 0x9a6f4-0x9a7ff seen): G0082 6.15.62, G0111 1.2.64 (and G0155 1.1.42, another writer).
     */
    static final PostWrite MT5877_FIRST_STAGE = new PostWrite(FIRST_STAGE, FILE, WRITER,
            FwModel.list("303152c9c20eedfbe65a6b5f93c2cc48ac382c0fd0aa75632e4efec6cdc6181c"), "mmcblk0boot0", "mmcblk0boot1",
            new long[] {0x66c00, 0x67000}, new long[] {0x9a400, 0x9a800});
}
