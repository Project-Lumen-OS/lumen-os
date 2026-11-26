#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""dsu_x50_mutate.py: mutants of the X50 family's install from the trial (apps/LumenDsuInstall: the x50 / x50max /
titanpro / titanmax rows of Revisions, Profile.X50 / X50MAX / TITAN_PRO / TITAN_MAX, the eye and stock-vbmeta rules of Engine, the write guard of vbmeta_s, the image
check). Each mutant breaks one rule in a scratch copy of the app; the "x50" cases of DsuTests (X50Tests.java and the
X50 cases of DsuTests.java, -Donly=x50) must turn red for every one of them. Never touches the tree it reads.

    python3 tools/devices/test/dsu_x50_mutate.py              every mutant
    MUT_ONLY=rev_ python3 tools/devices/test/dsu_x50_mutate.py   only mutants whose name starts so
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dsu_z9x_mutate  # noqa: E402  (the runner: scratch copy, javac, DsuTests, kill count)

S = "src/org/lumen/dsuinstall/"
R, P, E = S + "Revisions.java", S + "Profile.java", S + "Engine.java"
U64 = 'row("x50", "G0111", X50, "1.2.64", RevisionVerdict.LIVE, "Product_dianapro", X50_BLOBS_SET,'
U54 = 'row("x50", "G0111", X50, "1.2.54", RevisionVerdict.DRY, "Product_dianapro", X50_BLOBS_SET,'
UANY = 'row("x50", "G0111", X50, ANY, RevisionVerdict.REFUSE, null, null,'
M64 = 'row("x50max", "G0100", X50MAX, "1.2.64", G0100_LIVE ? RevisionVerdict.LIVE : RevisionVerdict.DRY, "Product_dianamax", X50_BLOBS_SET,'
M54 = 'row("x50max", "G0100", X50MAX, "1.2.54", RevisionVerdict.DRY, "Product_dianamax", X50_BLOBS_SET,'
T99 = 'row("titanpro", "H0062", TITAN, "1.2.99", RevisionVerdict.DRY, "Product_dianahypro", X50_BLOBS_SET,'
T54 = 'row("titanpro", "H0062", TITAN, "1.2.54", RevisionVerdict.DRY, "Product_dianahypro", X50_BLOBS_SET,'
TANY = 'row("titanpro", "H0062", TITAN, ANY, RevisionVerdict.REFUSE, null, null,'
XP = '16 << 20, "aidl", "f2fs", true, ALLOW_X50, true, true, "g0111");'
# the shared XGIMI set (apps/core/lumen-fwcopy), named relative to the app copy
FWM = dsu_z9x_mutate.FWCOPY_SRC + "FwModel.java"
TP = 'TITAN_PRO = x50Family("titanpro", "H0062", "XGIMI TITAN Noir Pro", "h0062", false);'
TM = 'TITAN_MAX = x50Family("titanmax", "H0063", "XGIMI TITAN Noir Max", "h0063", false);'
M99 = 'row("titanmax", "H0063", TITAN_MAX, "1.2.99", RevisionVerdict.DRY, "Product_dianahymax", X50_BLOBS_SET,'

# name, file, old, new (one anchor each, must occur exactly once)
MUTANTS = [
    # ---- the table: which stock may write
    ("rev_u54_live", R, U54, U54.replace("RevisionVerdict.DRY", "RevisionVerdict.LIVE")),
    ("rev_u64_dry", R, U64, U64.replace("RevisionVerdict.LIVE", "RevisionVerdict.DRY")),
    ("rev_u_any_dry", R, UANY, 'row("x50", "G0111", X50, ANY, RevisionVerdict.DRY, "Product_dianapro", X50_BLOBS_SET,'),
    ("rev_u64_no_product", R, U64, U64.replace('"Product_dianapro"', "null")),
    ("rev_u64_any_blobs_set", R, U64, U64.replace("X50_BLOBS_SET,", "null,")),
    ("rev_u64_max_product", R, U64, U64.replace('"Product_dianapro"', '"Product_dianamax"')),
    ("rev_m54_live", R, M54, M54.replace("RevisionVerdict.DRY", "RevisionVerdict.LIVE")),
    ("rev_m64_ultra_product", R, M64, M64.replace('"Product_dianamax"', '"Product_dianapro"')),
    ("rev_g0100_switch_on", R, "public static final boolean G0100_LIVE = false;", "public static final boolean G0100_LIVE = true;"),
    ("rev_m64_live", R, M64, M64.replace("G0100_LIVE ? RevisionVerdict.LIVE : RevisionVerdict.DRY", "RevisionVerdict.LIVE")),
    ("rev_live_versions_any_model", R, "if (r.model.equals(model) && r.verdict == RevisionVerdict.LIVE) out.add(r.version);",
     "if (r.verdict == RevisionVerdict.LIVE) out.add(r.version);"),
    ("rev_t99_live", R, T99, T99.replace("RevisionVerdict.DRY", "RevisionVerdict.LIVE")),
    ("rev_t54_live", R, T54, T54.replace("RevisionVerdict.DRY", "RevisionVerdict.LIVE")),
    ("rev_t_any_dry", R, TANY, 'row("titanpro", "H0062", TITAN, ANY, RevisionVerdict.DRY, "Product_dianahypro", X50_BLOBS_SET,'),
    ("rev_t99_ultra_product", R, T99, T99.replace('"Product_dianahypro"', '"Product_dianapro"')),
    ("rev_t99_any_blobs_set", R, T99, T99.replace("X50_BLOBS_SET,", "null,")),
    ("rev_t99_on_x50_profile", R, T99, T99.replace('row("titanpro",', 'row("x50",')),
    ("rev_m99_live", R, M99, M99.replace("RevisionVerdict.DRY", "RevisionVerdict.LIVE")),
    ("rev_m99_pro_product", R, M99, M99.replace('"Product_dianahymax"', '"Product_dianahypro"')),
    ("rev_m99_on_pro_profile", R, M99, M99.replace('row("titanmax",', 'row("titanpro",')),
    ("rev_blobs_set", R, 'X50_BLOBS_SET = "x50-v1264-b";', 'X50_BLOBS_SET = "x50-test-1";'),
    # ---- the profiles
    ("prof_no_updates_to_stock", P, XP, XP.replace("ALLOW_X50, true, true,", "ALLOW_X50, false, true,")),
    ("prof_no_eye", P, XP, XP.replace("ALLOW_X50, true, true,", "ALLOW_X50, true, false,")),
    ("set_x50_keeps_mboot", FWM, 'MT5877_PAIRS,\n            list(""), null, "G0111', 'MT5877_PAIRS,\n            list("mboot"), null, "G0111'),
    ("set_x50_no_h0063", FWM, 'list("G0111,G0100,H0062,H0063")', 'list("G0111,G0100,H0062")'),
    ("prof_share_no_product", P, 'Arrays.asList("vendor", "product", "system_ext"),\n            Collections.singletonList("z9x_gapps"),\n            16 << 20',
     'Arrays.asList("vendor", "system_ext"),\n            Collections.singletonList("z9x_gapps"),\n            16 << 20'),
    ("prof_blobs_4m", P, XP, XP.replace("16 << 20", "4 << 20")),
    ("prof_family_own_blobs", P, "X50.share, X50.drop, X50.blobsSize, X50.hal,", "X50.share, X50.drop, 4 << 20, X50.hal,"),
    ("prof_family_updates", P, "updates, X50.eyeSwitch, image);", "true, X50.eyeSwitch, image);"),
    ("prof_family_no_eye", P, "updates, X50.eyeSwitch, image);", "updates, false, image);"),
    ("prof_family_default_no_updates", P, "return x50Family(id, model, name, image, X50.updatesToStockSlot);",
     "return x50Family(id, model, name, image, false);"),
    ("prof_titan_updates", P, TP, TP.replace(", false);", ", true);")),
    ("prof_titan_max_updates", P, TM, TM.replace(", false);", ", true);")),
    ("prof_family_image_any", P, "if (!familyImage.equals(dev)) return", "if (false) return"),
    ("prof_family_not_x50_build", P, 'if (!"x50".equals(d.prop("ro.z9x.device"))) return "the running image is not an X50 build',
     'if (false) return "the running image is not an X50 build'),
    ("prof_max_image_none", P, '"XGIMI X50 Ultra Max", "g0100");', '"XGIMI X50 Ultra Max", null);'),
    ("prof_max_image_ultra", P, '"XGIMI X50 Ultra Max", "g0100");', '"XGIMI X50 Ultra Max", "g0111");'),
    ("prof_ultra_image_none", P, XP, XP.replace('"g0111");', "null);")),
    ("prof_titan_image_ultra", P, TP, TP.replace('"h0062", false);', '"g0111", false);')),
    ("prof_titan_image_none", P, TP, TP.replace('"h0062", false);', "null, false);")),
    ("prof_titan_model_ultra", P, TP, TP.replace('"H0062"', '"G0111"')),
    ("prof_titan_unknown", P, "Arrays.asList(HB, Z9X, X50, X50MAX, TITAN_PRO, TITAN_MAX)", "Arrays.asList(HB, Z9X, X50, X50MAX, TITAN_MAX)"),
    ("prof_titan_max_unknown", P, "Arrays.asList(HB, Z9X, X50, X50MAX, TITAN_PRO, TITAN_MAX)", "Arrays.asList(HB, Z9X, X50, X50MAX, TITAN_PRO)"),
    ("prof_titan_max_image_pro", P, TM, TM.replace('"h0063", false);', '"h0062", false);')),
    ("prof_titan_max_image_ultra_max", P, TM, TM.replace('"h0063", false);', '"g0100", false);')),
    ("prof_titan_max_model_pro", P, TM, TM.replace('"H0063"', '"H0062"')),
    # ---- the engine
    ("eng_eye_by_id", E, "        if (p.profile.eyeSwitch) {", '        if ("x50".equals(p.profile.id)) {'),
    ("eng_eye_off", E, "        if (p.profile.eyeSwitch) {", "        if (false) {"),
    ("eng_stock_vbmeta_always", E, "p.vbmetaStockNew = p.profile.updatesToStockSlot && p.vbmetaFlagsStock", "p.vbmetaStockNew = p.vbmetaFlagsStock"),
    ("eng_channel_check_off", E, "if (!p.profile.updatesToStockSlot && !chan.isEmpty()) {", "if (false) {"),
    ("eng_dry_row_writes", E, "            if (r.verdict != RevisionVerdict.LIVE)\n", "            if (r.verdict == RevisionVerdict.REFUSE)\n"),
    ("eng_no_gate", E, "            String gate = writeGate(p.profile);\n", "            String gate = null;\n"),
    ("guard_vbmeta_s_unplanned", E, ': p.vbmetaStockNew == null ? "vbmeta" + p.s + " of stock is not in the plan', ': false ? "vbmeta" + p.s + " of stock is not in the plan'),
]


if __name__ == "__main__":
    sys.exit(dsu_z9x_mutate.main(MUTANTS, "x50"))
