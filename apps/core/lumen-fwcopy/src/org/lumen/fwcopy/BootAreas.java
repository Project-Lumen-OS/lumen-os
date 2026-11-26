// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.io.IOException;

/**
 * What the first-stage copy ({@link PostWrite}) needs from the projector besides the partitions: where a by-name link
 * points, which eMMC boot area starts the projector now, the force_ro switch of a boot area, and a file of the
 * running vendor. The real one is in the installer (sysfs, debugfs, /vendor), the host tests use fakes.
 */
public interface BootAreas {
    /** the kernel block device a by-name link points at (rom_emmc_boot_b -> mmcblk0boot1) */
    String node(String name) throws IOException;

    /** EXT_CSD[179] PARTITION_CONFIG of the eMMC {@code disk} (mmcblk0) as read now */
    int partitionConfig(String disk) throws IOException;

    /** /sys/block/&lt;node&gt;/force_ro reads 1 */
    boolean readOnly(String node) throws IOException;

    /** writes /sys/block/&lt;node&gt;/force_ro (the stock post-install step does the same around its write) */
    void setReadOnly(String node, boolean readOnly) throws IOException;

    /** a whole file of the running vendor (/vendor/bin/...) */
    byte[] vendorFile(String path) throws IOException;

    /** BOOT_PARTITION_ENABLE (bits 3..5 of EXT_CSD[179]): 1 = boot area 1, 2 = boot area 2, 7 = user area, 0 = none */
    static int bootEnable(int partitionConfig) { return (partitionConfig >> 3) & 7; }

    /**
     * EXT_CSD[179] from the kernel's debugfs text (/sys/kernel/debug/mmcN/mmcN:RCA/ext_csd: 512 bytes as 1024 hex
     * digits, as read live on the HB trial 2026-11-17). IOException when it is no such text.
     */
    static int parseExtCsd(String extCsd) throws IOException {
        String h = extCsd == null ? "" : extCsd.trim();
        if (h.length() != 1024 || !h.matches("[0-9a-fA-F]+")) throw new IOException("ext_csd is not 512 bytes of hex (" + h.length() + " characters)");
        return Integer.parseInt(h.substring(2 * 179, 2 * 179 + 2), 16);
    }

    /** No boot areas: every call fails (a model without a first stage in its data never calls it). */
    BootAreas NONE = new BootAreas() {
        @Override public String node(String name) throws IOException { throw new IOException("no eMMC boot areas here"); }

        @Override public int partitionConfig(String disk) throws IOException { throw new IOException("no eMMC boot areas here"); }

        @Override public boolean readOnly(String node) throws IOException { throw new IOException("no eMMC boot areas here"); }

        @Override public void setReadOnly(String node, boolean readOnly) throws IOException { throw new IOException("no eMMC boot areas here"); }

        @Override public byte[] vendorFile(String path) throws IOException { throw new IOException("no vendor file here"); }
    };
}
