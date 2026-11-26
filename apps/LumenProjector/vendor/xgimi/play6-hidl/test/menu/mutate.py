#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Play 6 menu gate mutants: each mutant weakens one rule of the Play 6 adapter of the shared menu (rule in
Play6MenuGates, the p6-hal re-checks and entry gates of Play6Features, the device pages, the owner, the recipe and the
profile); test/menu/run.sh must turn red for every one. Works on a scratch copy, never in place.

  python3 apps/LumenProjector/vendor/xgimi/play6-hidl/test/menu/mutate.py            MUT_ONLY=REGEX: only the mutants whose name matches
"""
import os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
G = os.environ.get("LUMEN_GSI") or os.path.abspath(os.path.join(APP, "..", "..", "..", "..", ".."))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import menu_layers  # noqa: E402  (menu profiles have one reader, devices/README.md)
A = "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/panel/"
CP = "apps/LumenProjector/core/src/org/lumen/projector/panel/"
GT = CP + "Play6MenuGates.java"
FT = "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/panel/Play6Features.java"
FT_Play6GameRows = 'apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/panel/Play6GameRows.java'
FT_Play6GeneralRows = 'apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/panel/Play6GeneralRows.java'
FT_Play6HdmiRows = 'apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/panel/Play6HdmiRows.java'
FT_Play6PictureRows = 'apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/panel/Play6PictureRows.java'
PG = CP + "Play6MenuPages.java"
OW = CP + "Play6Menu.java"
MJ = "devices/dragon_healthy/menu.json"
DV = "apps/LumenProjector/core/src/org/lumen/projector/Device.java"
SD = "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/Play6SettingsDevice.java"
LY = "devices/dragon_healthy/app/Play6Projector.layers"
MUTANTS = [
    # ---- rule: values (Play6MenuGates.state)
    ("pq items outside Custom", GT, "if (custom && v != null && v >= 0 && v <= 100) b.number(f, v);", "if (v != null && v >= 0 && v <= 100) b.number(f, v);"),
    ("motion compensation in every mode", GT, "if (memcMode(r.pictureMode)) b.put(FeatureId.PICTURE_MEMC", "if (true) b.put(FeatureId.PICTURE_MEMC"),
    ("HDMI rows on any input", GT, "        if (hdmi(r)) {\n", "        if (true) {\n"),
    ("game mode without IGmpf2", GT, "if (r.gmpf2 && r.gameMode >= 0)", "if (r.gameMode >= 0)"),
    ("eye available without the NUI reader", GT, "case EYE: return eyeReady;", "case EYE: return true;"),
    ("home screen choice where it cannot switch", GT, "case SETTINGS_LAUNCHER: return launcherChoice;", "case SETTINGS_LAUNCHER: return true;"),
    ("power saving without the battery", GT, "case LAMP_ECO: return battery;", "case LAMP_ECO: return true;"),
    ("picture reset shown outside Custom", GT, "            b.hide(FeatureId.PICTURE_RESET);\n", ""),
    ("a value row without its value shown", GT, "for (FeatureId f : VALUE_ROWS) if (!probe.has(f)) b.hide(f);", ""),
    ("equalizer entry while it cannot work", GT, "if (Boolean.FALSE.equals(r.eqOk)) b.hide(FeatureId.SOUND_EQ);", ""),
    ("no stock hint in Game", GT, "if (gamePicture(r.pictureMode)) b.note(MenuSpec.PICTURE, text.apply(\"p6_pic_game_hint\"));\n        else ", ""),
    ("smart focus without IGmpf2", GT, "if (r.gmpf2) b.on(FeatureId.FOCUS_SMART, r.smartAf);", "b.on(FeatureId.FOCUS_SMART, r.smartAf);"),
    ("dynamic contrast without IGmpf2", GT, "if (r.gmpf2) b.on(FeatureId.PICTURE_DYNAMIC_CONTRAST", "b.on(FeatureId.PICTURE_DYNAMIC_CONTRAST"),
    ("AI scene sub-modes not shown as AI", GT, "raw >= AI_SCENE_FIRST && raw <= AI_SCENE_LAST ? Integer.valueOf(PM_AI) : raw", "raw"),
    ("light level out of range shown", GT, "if (r.lamp != null && lampValue(r.lamp)) b.number", "if (r.lamp != null) b.number"),
    ("pq value out of range shown", GT, "if (custom && v != null && v >= 0 && v <= 100) b.number", "if (custom && v != null) b.number"),
    ("light level 0 written", GT, "return v >= 1 && v <= 10;", "return v >= 0 && v <= 10;"),
    ("HDR in Performance", GT, "|| pm == PM_OFFICE);", "|| pm == PM_OFFICE || pm == PM_PERFORMANCE);"),
    ("motion compensation in Sport", GT, "(pm == PM_MOVIE || pm == PM_TV || pm == PM_CUSTOM)", "(pm == PM_MOVIE || pm == PM_TV || pm == PM_CUSTOM || pm == PM_SPORT)"),
    # ---- rule: writes (Play6MenuGates.refusal)
    ("Custom-only writes in any mode", GT, "            return custom(r.pictureMode) ? null : \"\";\n        }", "            return null;\n        }"),
    ("motion compensation write in any mode", GT, "return memcMode(r.pictureMode) ? null : \"\";", "return null;"),
    ("HDMI writes off HDMI", GT, "return hdmi(r) ? null : \"panel_game_hdmi_only\";", "return null;"),
    ("game mode write off HDMI", GT, "if (!hdmi(r)) return \"panel_game_hdmi_only\";", ""),
    ("game mode write unread", GT, "return r.gmpf2 && GAME_MODE.token(r.gameMode) != null ? null", "return r.gmpf2 ? null"),
    ("picture mode write in Game without the stock hint", GT, "if (gamePicture(r.pictureMode)) return \"p6_pic_game_hint\";", ""),
    ("picture mode write from an unknown mode", GT, "return PICTURE_MODE.token(shownPictureMode(r.pictureMode)) == null ? \"\" : null;", "return r.pictureMode == null ? \"\" : null;"),
    ("dynamic contrast write without IGmpf2", GT, "return custom(r.pictureMode) && r.gmpf2 ? null : \"\";", "return custom(r.pictureMode) ? null : \"\";"),
    ("smart focus write without IGmpf2", GT, "            case FOCUS_SMART:\n                return r.gmpf2 ? null : \"toast_not_ready\";", "            case FOCUS_SMART:\n                return null;"),
    ("projection write without a read", GT, "return r.putMode == null || r.autoReverse == null ? \"toast_not_ready\" : null;", "return null;"),
    # ---- tables and mappings
    ("table loses the rear side", GT, "return front ? PUT_FRONT_TABLE : PUT_REAR_TABLE;", "return PUT_FRONT_TABLE;"),
    ("rear switch ignores ceiling", GT, "boolean table = cur == PUT_FRONT_TABLE || cur == PUT_REAR_TABLE;", "boolean table = cur == PUT_FRONT_TABLE;"),
    ("Original shown where it does not fit", GT, "if (shown == ASPECT_ORIGINAL && !originalFits) shown = ASPECT_AUTO;", ""),
    ("Original fits the full panel", GT, "w < 1920 && h < 1080", "w <= 1920 && h <= 1080"),
    ("sound mode wires swapped", GT, "new int[]{3, 2, 1, 4, 12, 20}", "new int[]{1, 2, 3, 4, 12, 20}"),
    ("HDMI mode wires swapped", GT, "new int[]{EDID_14, EDID_20}", "new int[]{EDID_20, EDID_14}"),
    ("an absent feature implemented", GT, "FeatureId.LAMP, FeatureId.LAMP_ECO, FeatureId.LAMP_OFF,\n", "FeatureId.LAMP, FeatureId.LAMP_ECO, FeatureId.LAMP_OFF, FeatureId.BOOST,\n"),
    ("a profile feature not implemented", GT, "FeatureId.SOUND_EQ, FeatureId.SOUND_EARC, FeatureId.SOUND_OUTPUT,", "FeatureId.SOUND_EQ, FeatureId.SOUND_OUTPUT,"),
    ("outputs not limited to the connected ones", GT, "            b.options(FeatureId.SOUND_OUTPUT, outs);\n", ""),
    ("a gone output not shown as Auto", GT, "if (out != null && !outs.contains(out)) out = \"auto\";", ""),
    ("Original offered for a full panel signal", GT, "if (!originalFits(r.hdmiInfo)) b.options(FeatureId.PICTURE_ASPECT,", "if (false) b.options(FeatureId.PICTURE_ASPECT,"),
    # ---- the v3 hooks: refusals before a press, the stock questions
    ("game page off HDMI opens", GT, "return r == null || hdmi(r) ? null : Note.of(\"panel_game_hdmi_only\");", "return null;"),
    ("blocked ignores the refusal", GT, "        String no = refusal(feature, r);\n        return no == null || no.isEmpty() ? null : Note.of(no);", "        return null;"),
    ("Performance without the heat warning", GT, "return change && m == PM_PERFORMANCE", "return false && m == PM_PERFORMANCE"),
    ("a flip without the question", GT, "return flip ? new Confirm(", "return false ? new Confirm("),
    ("rear flip without the question", GT, "return putModeForRear(\"on\".equals(value), r.putMode) != r.putMode", "return false"),
    ("HDMI mode without the question", GT, "return v != null && !v.equals(r.edid) ? new Confirm(", "return false ? new Confirm("),
    ("power saving asked every time", GT, " || (on ? ecoOnAsked : ecoOffAsked)", ""),
    ("picture reset asks the keystone text", GT, "\"p6_pic_reset_msg\"", "\"panel_reset_keystone_msg\""),
    ("home screen question the wrong way", GT, "if (\"classic\".equals(value)) return new Confirm(\"launcher_row\", \"launcher_confirm_classic\"",
     "if (\"classic\".equals(value)) return new Confirm(\"launcher_row\", \"launcher_confirm_lumen\""),
    # ---- the adapter (Play6Features): entry gates and p6-hal re-checks
    ("set without the implemented gate", FT, "if (!implemented().contains(feature) && feature.parent != FeatureId.PICTURE_PQ) { refuse(cb, null); return; }", ""),
    ("set without the refusal", FT, "String no = Play6MenuGates.refusal(feature, r);", "String no = null;"),
    ("run without the implemented gate", FT, "if (!implemented().contains(action)) { refuse(cb, null); return; }", ""),
    ("open without the implemented gate", FT, "if (!implemented().contains(screen)) return false;", ""),
    ("available ignores the NUI reader", FT, "Play6MenuGates.available(feature, eyeReady,", "Play6MenuGates.available(feature, true,"),
    ("blocked hook dropped", FT, "{ return Play6MenuGates.blocked(feature, value, last); }", "{ return null; }"),
    ("confirm hook dropped", FT, "        return Play6MenuGates.confirm(feature, value, last,", "        if (true) return null;\n        return Play6MenuGates.confirm(feature, value, last,"),
    ("setup gate ignored", FT, "public boolean setupDone() { return KeyReceiver.isSetupComplete(app); }", "public boolean setupDone() { return true; }"),
    ("power saving answer not kept", FT, "            prefs.edit().putBoolean(\"on\".equals(value) ? KEY_ECO_ON_CONFIRMED : KEY_ECO_OFF_CONFIRMED, true).apply();\n", ""),
    ("HDMI range without the 696 re-read", FT_Play6HdmiRows, "if (!hdmiNow(g, \"635\")) return \"panel_game_hdmi_only\";", ""),
    ("aspect without the 696 re-read", FT_Play6HdmiRows, "if (!hdmiNow(g, \"665\")) return \"panel_game_hdmi_only\";", ""),
    ("game mode without the 696 re-read", FT_Play6GameRows, "if (!hdmiNow(g, \"game mode\")) return \"panel_game_hdmi_only\";", ""),
    ("HDMI mode without the 696 re-read", FT_Play6HdmiRows, "if (!hdmiNow(g, \"660\")) return \"panel_game_hdmi_only\";", ""),
    ("pq without the Custom re-read", FT_Play6PictureRows, "        if (!Play6MenuGates.custom(g.getPictureModeRaw())) return \"\";\n        boolean r;", "        boolean r;"),
    ("motion compensation without the mode re-read", FT_Play6PictureRows, "if (!Play6MenuGates.memcMode(g.getPictureModeRaw())) return \"\";", ""),
    ("eye without the NUI reader check", FT_Play6GeneralRows, "if (!Device.hasEyeProtection()) return \"\";", ""),
    ("picture reset without the Custom re-read", FT_Play6PictureRows, "if (!Play6MenuGates.custom(g.getPictureModeRaw())) return \"\";\n                    g.resetCustomPicture();", "g.resetCustomPicture();"),
    ("refusal note that is no string", FT, "if (r == null) { refuse(cb, \"toast_not_ready\"); return; }", "if (r == null) { refuse(cb, \"toast_not_redy\"); return; }"),
    # ---- device pages, owner, recipe, profile
    ("white balance rows disabled, not hidden", PG, "visible(wbRows[k], v != null);", ""),
    ("noise reduction in every mode", PG, "choiceIf(nrRow, custom ? sn.nr : null, 4);", "choiceIf(nrRow, sn.nr, 4);"),
    ("pages bypass the menu", PG, "if (menu != null) menu.refreshPage(null);", ""),
    ("controller released while a screen comes back to it", OW, "if (sMenu == null || sHeld) return;", "if (sMenu == null) return;"),
    ("old menu left open on a language change", OW, "            sMenu.close();\n", ""),
    ("extension rows dropped", OW, "MenuController.addExtension(sec, x, built());", ""),
    ("controller kept after its window closed", OW, "Log.i(TAG, \"menu window closed: controller released\");\n        sMenu = null;", "Log.i(TAG, \"menu window closed: controller released\");"),
    ("gear long builds the menu without a game menu", OW, "if (!p.ok() || \"none\".equals(p.gearLong())) {", "if (!p.ok()) {"),
    ("recipe without the module", LY, "module    lumen-menu\n", ""),
    ("profile turns the game menu on", MJ, "\"game_menu\": {\"enabled\": false}", "\"game_menu\": {\"enabled\": true}"),
    ("profile adds optical zoom", MJ, "\"focus.af_now\": {},", "\"focus.af_now\": {},\n    \"zoom\": {},"),
    ("profile hint with an unknown picture", MJ, "\"hints\": {\"picture.mode\": \"picture_mode\", \"projection.rear\": \"rear_projection\", \"game.mode\": \"game_mode\"}", "\"hints\": {\"picture.mode\": \"picture_mode\", \"projection.rear\": \"rear_projection\", \"game.mode\": \"game_mode\", \"sound.earc\": \"x\"}"),
    # ---- screen mirroring: the tile only with the receiver's provider (Wi-Fi Direct found), Home's tile the same
    ("mirroring tile not implemented", GT, "FeatureId.NETWORK, FeatureId.MIRRORING,", "FeatureId.NETWORK,"),
    ("profile drops the mirroring tile", MJ, "    \"mirroring\": {},\n", ""),
    ("mirroring opened as an adapter screen", FT, "Log.i(TAG, \"open \" + screen + \": the menu's screen mirroring popup, not a screen\");\n                return false;",
     "Log.i(TAG, \"open \" + screen + \": the menu's screen mirroring popup, not a screen\");\n                return true;"),
    ("Home keeps the mirroring tile without Wi-Fi Direct", DV, "        if (!screenMirroring(c)) off.add(HOME_TILE_MIRRORING);\n", ""),
    ("Home caps without the receiver's provider", DV, "resolveContentProvider(MIRRORING_AUTHORITY, 0) != null", "true"),
    # ---- the shared settings rows: source at power-on and speech language through SettingsRows
    ("Play 6 boot tokens of two HDMI inputs", SD, "GeneralChoices.BOOT_ONE_HDMI.clone()", "GeneralChoices.BOOT_TWO_HDMI.clone()"),
    ("settings rows never handed over", FT, "        if (rows.set(feature, value, this, cb)) return;\n", ""),
    ("profile boot source the adapter cannot store", MJ, '"power.boot_source": {"options": ["home", "hdmi"]}',
     '"power.boot_source": {"options": ["home", "hdmi2"]}'),
    ("profile speech language the rows cannot write", MJ, '"system", "ru", "en", "uk",', '"system", "ru", "en", "ua",'),
    # ---- the one rotation card: the Play 6 HAL steps and its key queue as family data
    ("Play 6 rotation one key at a time", "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/Family.java",
     "public static final boolean ADJUST_COALESCE = true;", "public static final boolean ADJUST_COALESCE = false;"),
    ("Play 6 rotation without the enter watchdog", "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/Family.java",
     "public static final long ADJUST_WATCHDOG_MS = 6_000;", "public static final long ADJUST_WATCHDOG_MS = 0;"),
    ("Z9X rotation keys queued", "apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/Family.java",
     "public static final boolean ADJUST_COALESCE = false;", "public static final boolean ADJUST_COALESCE = true;"),
    ("Play 6 rotation opens without its push-pull bracket", A + "Play6PanelIo.java",
     "                if (!Play6AdjustSession.pushPullOn(app, g2, who)) return false;\n", ""),
    ("rotation card opens without IGmpf2", "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/Family.java",
     "Hal.isReady() && Hal.hasGmpf2()", "Hal.isReady()"),
    ("rotation refusals never hide the row", CP + "RotatePanel.java", "            if (io.refused(t)) markUnsupported(", "            if (false) markUnsupported("),
]

# what test/menu/run.sh reads: the core sources (the Play 6 core features Play6Menu, Play6MenuGates, Play6MenuPages,
# QuickPanel, Device, KeyReceiver, BootSource), the adapter, the profile, the hal sources of the wire values
COPY = ["tools/devices/menu_layers.py", "apps/LumenProjector/core/src", "apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/Family.java", "tools/devices/layered.py", "apps/LumenProjector/vendor/xgimi/play6-hidl/test", "apps/LumenProjector/core/res", "apps/common/projector/res",
        "apps/core/lumen-menu/src", "apps/core/lumen-menu/res", MJ, LY, "tools/play6/forks.tsv",
        "apps/common/projector/src/org/lumen/projector/panel/SettingsRows.java",
        "apps/common/projector/src/org/lumen/projector/voice/VoiceSettings.java",
        "apps/core/lumen-gmpf/src/org/lumen/projector/hal/GmpfBase.java",
        "apps/LumenProjector/vendor/xgimi/play6-hidl/src", "apps/LumenProjector/vendor/xgimi/gmpf/src/org/lumen/projector/hal/HdmiRange.java", "apps/LumenProjector/vendor/xgimi/gmpf/src/org/lumen/projector/hal/SoundEffect.java"]


def main():
    only = os.environ.get("MUT_ONLY")
    tmp = tempfile.mkdtemp(prefix="p6menu_mut.")
    try:
        root = os.path.join(tmp, "gsi")
        for c in COPY:
            s, d = os.path.join(G, c), os.path.join(root, c)
            os.makedirs(os.path.dirname(d), exist_ok=True)
            (shutil.copytree if os.path.isdir(s) else shutil.copy)(s, d)
        menu_layers.copy_profile(os.path.join(G, MJ), os.path.join(root, MJ))   # flat: its layers are not copied
        run = ["sh", os.path.join(root, "apps/LumenProjector/vendor/xgimi/play6-hidl/test/menu/run.sh")]
        env = dict(os.environ, LUMEN_GSI=root)
        base = subprocess.run(run, capture_output=True, text=True, env=env)
        if base.returncode != 0:
            print("the unmutated copy is red already: fix test/menu/run.sh first")
            print(base.stdout[-2000:] + base.stderr[-1000:])
            sys.exit(2)
        killed, survived = 0, []
        for name, rel, old, new in MUTANTS:
            if only and not re.search(only, name): continue
            p = os.path.join(root, rel)
            src = open(p, encoding="utf-8").read()
            if src.count(old) != 1:
                print("BROKEN mutant (pattern %d times): %s" % (src.count(old), name))
                survived.append(name)
                continue
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            try:
                r = subprocess.run(run, capture_output=True, text=True, env=env)
            finally:
                open(p, "w", encoding="utf-8").write(src)
            if r.returncode != 0:
                killed += 1
                why = next((l for l in r.stdout.splitlines() if l.startswith("FAIL")), (r.stderr.strip().splitlines() or ["red"])[-1])
                print("killed   %-50s %s" % (name, why[:100]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        print("play6 menu mutants: %d killed, %d survived" % (killed, len(survived)))
        sys.exit(0 if not survived else 1)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
