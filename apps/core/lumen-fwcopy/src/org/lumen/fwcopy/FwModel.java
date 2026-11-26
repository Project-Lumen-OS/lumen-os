// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One projector family's XGIMI firmware facts, read from its stock dumps (xgimi-builder ~/devices in
 * the README): the stock ro.product.ab_ota_partitions (product build.prop of the stock slot: the partitions XGIMI's own
 * update writes into the other half), the A/B pairs of /dev/block/by-name, and what this model keeps out of the copy
 * or out of Lumen's own update.
 * A new projector is one new row here, never code (models only as data).
 */
public final class FwModel {
    /** the family (z9x, hb, x50, rs30, play6) */
    public final String family;
    /** its model codes (ro.boot.xgimi.modelname) */
    public final List<String> codes;
    /** the stock ro.product.ab_ota_partitions, as XGIMI ships it */
    public final List<String> stockList;
    /** the partitions the stock dump has as an _a/_b pair */
    public final List<String> pairs;
    /** in the stock list but never written on this model: must already be the same in both halves (no model now) */
    public final List<String> keepEqual;
    /**
     * copied by the install like the rest of the set, left out of the list a Lumen image gives update_engine: Lumen's
     * own update never writes it, the install has made both halves the same
     */
    public final List<String> installOnly;
    /**
     * written by XGIMI's update from the new vendor's post-install step, outside the stock list (the first boot stage,
     * {@link PostWrite}): copied by the install the stock way, never by Lumen's own update. Listed only where the
     * model's stock writer was read and does the same (empty: not proven, or no install on the model)
     */
    public final List<PostWrite> postWrites;
    /** null: the copy may run on this model; else why it never does */
    public final String refusal;
    /** the dumps the facts come from */
    public final String evidence;

    FwModel(String family, List<String> codes, List<String> stockList, List<String> pairs, List<String> keepEqual,
            String refusal, String evidence, List<String> installOnly, PostWrite... postWrites) {
        this.family = family; this.codes = codes; this.stockList = stockList; this.pairs = pairs; this.keepEqual = keepEqual;
        this.refusal = refusal; this.evidence = evidence; this.installOnly = installOnly;
        this.postWrites = Collections.unmodifiableList(Arrays.asList(postWrites));
    }

    static List<String> list(String csv) {
        return csv.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(Arrays.asList(csv.split(",")));
    }

    /** The mt5877 layout (Z9X, X50 family, RS30: one XGIMI product image, the same 37 partitions). */
    static final List<String> MT5877_STOCK = list("boot,bootdata,dtbo,mboot,optee,owl,product,satf,system,system_ext,"
            + "tvconfig,tvservice,vbmeta,vbmeta_system,vendor,xgimiconfig");
    static final List<String> MT5877_PAIRS = list("boot,bootdata,dtbo,mboot,optee,owl,rom_emmc_boot,satf,tvconfig,tvservice,"
            + "vbmeta,vbmeta_system,xgimiconfig");
    /** The Z9X HB (MT9660). */
    static final List<String> HB_STOCK = list("armfw,boot,bootdata,dtbo,mboot,optee,product,system,system_ext,tvconfig,"
            + "tvservice,vbmeta,vbmeta_system,vendor,xgimiconfig");
    static final List<String> HB_PAIRS = list("armfw,boot,bootdata,dtbo,mboot,optee,rom_emmc_boot,tvconfig,tvservice,vbmeta,"
            + "vbmeta_system,xgimiconfig");

    public static final FwModel Z9X = new FwModel("z9x", list("G0082"), MT5877_STOCK, MT5877_PAIRS, list(""), null,
            "G0082 6.15.62: v61562/fs/product_a/etc/build.prop, lumendump by-name.txt; first stage: postinst_mtk of the official "
            + "6.15.19, 6.15.58, 6.15.62 and rom_emmc_boot_a of a unit on 6.15.62", list(""), PostWrite.MT5877_FIRST_STAGE);
    /**
     * The install copies mboot on the HB like every other model (2026-11-17: Lumen OS runs on exactly the
     * firmware stock runs on, which lifts a project decision for the install copy). O3 stays for Lumen's own update: mboot is
     * not in the HB update list, the two halves are the same after the install. The first boot stage is copied with it
     * the way XGIMI's post-install step writes it (2026-11-18): the update that wrote mboot_b wrote rom_emmc_boot_b.
     */
    public static final FwModel HB = new FwModel("hb", list("G0130,G0118,G0148"), HB_STOCK, HB_PAIRS, list(""), null,
            "G0130 6.16.24 and G0148 6.17.12: work/fs/product/etc/build.prop, by-name.txt; a G0130 after its stock update "
            + "to 6.17.12 (2026-11-17): mboot_b is the mboot.img of that update, rom_emmc_boot_b its rom_emmc_boot.bin",
            list("mboot"), PostWrite.HB_FIRST_STAGE);
    public static final FwModel X50 = new FwModel("x50", list("G0111,G0100,H0061,H0062,H0063"), MT5877_STOCK, MT5877_PAIRS,
            list(""), null, "G0111 1.2.64 full dump 2026-11-08: getprop.txt, parts.txt (G0100, H0061, H0062, H0063: the same "
            + "XGIMI build line, XR03E/XR04E/XR03EOS/XR04EOS/XR06EOS). First stage: postinst_mtk of G0111 1.2.64 and H0062 1.2.54 / 1.2.99, "
            + "rom_emmc_boot_a/_b of the G0111 dump (G0100, H0061, H0063: no dump, the writer's sha256 is checked on the unit)",
            list(""), PostWrite.MT5877_FIRST_STAGE);
    /**
     * Its stock writes the first stage the same way (postinst_mtk 288c51e8, the HB's code built again, and the area of the
     * dump is its vendor file outside the mt5877 ranges), but the RS30 copies nothing: no first stage in its row.
     */
    public static final FwModel RS30 = new FwModel("rs30", list("G0155,G0170,G0114,G0115,G0124,G0125,G0104,G0109,G0110"), MT5877_STOCK, MT5877_PAIRS, list(""),
            "the RS30 has no install and no updater (trial only): its second half is not a way back", "G0155 dump: "
            + "getprop.txt, by-name.txt (G0170: its ab_ota_partitions in a Lumen Installer report)", list(""));
    public static final FwModel PLAY6 = new FwModel("play6", list("G0058"), list(""), list(""), list(""),
            "the Play 6 has one half only: there is nothing to copy", "G0058 stock: no A/B", list(""));

    public static final List<FwModel> ALL = Collections.unmodifiableList(Arrays.asList(Z9X, HB, X50, RS30, PLAY6));

    /** The row of a model code, null when unknown. */
    public static FwModel of(String code) {
        for (FwModel m : ALL) if (m.codes.contains(code)) return m;
        return null;
    }
}
