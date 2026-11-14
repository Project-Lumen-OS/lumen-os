#!/usr/bin/env python3
"""Gate mutants of lumen-menu: each mutant weakens the "absent feature = no row" rule, the
profile checks or the window hosts (section 11); test/run.sh must turn red for every one. Works on a scratch copy of
the module, never in place.

  python3 apps/core/lumen-menu/test/mutate.py
"""
import glob, json, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
G = os.path.dirname(os.path.dirname(os.path.dirname(MOD)))
S = "src/org/lumen/menu/"
MC = "src/org/lumen/projector/panel/MenuController.java"
UI = "src/org/lumen/projector/ui/"
MUTANTS = [
    # ---- the shared "Turn off screen" (LampOff)
    ("power key passes while the light is off", S + "LampOff.java", "if (key == LampOffKey.POWER && off && down && repeatCount == 0) {",
     "if (false) {"),
    ("a held press from before the light-off relights", S + "LampOff.java", "&& off && down && repeatCount == 0) {",
     "&& off && down) {"),
    ("power repeats and UP of the relighting press reach the device", S + "LampOff.java",
     "if (key == LampOffKey.POWER) return downTime == relitPress;", "if (key == LampOffKey.POWER) return false;"),
    ("focus keys work in the dark", S + "LampOff.java", "return off && key == LampOffKey.FOCUS;", "return false;"),
    ("every key eaten in the dark", S + "LampOff.java", "return off && key == LampOffKey.FOCUS;", "return off;"),
    ("the power key does not relight", S + "LampOff.java", "        off = false;\n        marker.mark(false);\n        light.on();\n",
     "        off = false;\n        marker.mark(false);\n"),
    ("a failed light-off counts as off", S + "LampOff.java", "                if (ok) {\n                    off = true;",
     "                if (true) {\n                    off = true;"),
    ("a sleep keeps the light-off state", S + "LampOff.java", "        if (!off) return;\n        off = false;\n        marker.mark(false);",
     "        if (!off) return;\n        marker.mark(false);"),
    ("a sleep keeps the mark", S + "LampOff.java", "        if (!off) return;\n        off = false;\n        marker.mark(false);",
     "        if (!off) return;\n        off = false;"),
    ("a restart forgets the light is off", S + "LampOff.java", "        if (marker.thisBoot()) {\n            off = true;",
     "        if (marker.thisBoot()) {\n            off = false;"),
    ("a reboot leaves the light off", S + "LampOff.java", "            marker.mark(false);\n            light.on();\n        }",
     "            marker.mark(false);\n        }"),
    ("the off light is not marked", S + "LampOff.java", "                    marker.mark(true);\n", ""),
    ("lamp slider: left at the minimum without lamp.off turns the light off", S + "LampOff.java",
     "return offered && dir < 0 && repeatCount == 0", "return dir < 0 && repeatCount == 0"),
    ("lamp slider: right at the edge turns the light off", S + "LampOff.java",
     "return offered && dir < 0 && repeatCount == 0", "return offered && repeatCount == 0"),
    ("lamp slider: a held left at the minimum turns the light off", S + "LampOff.java",
     "return offered && dir < 0 && repeatCount == 0", "return offered && dir < 0"),
    ("lamp slider: left at the minimum never turns the light off", MC,
     "safe(\"lamp off\", () -> action(FeatureId.LAMP_OFF));", "safe(\"lamp off\", () -> { });"),
    # ---- the shared gear press (GearPress) and the eye guard's short press (keys.eye_gear_short)
    ("gear long press on every repeat", S + "GearPress.java", "if (!longFired && eventDownTime == downTime) {",
     "if (eventDownTime == downTime) {"),
    ("gear long press of another press", S + "GearPress.java", "if (!longFired && eventDownTime == downTime) {",
     "if (!longFired) {"),
    ("canceled gear UP is a short press", S + "GearPress.java",
     "boolean shortPress = !longFired && !canceled && eventDownTime == downTime;",
     "boolean shortPress = !longFired && eventDownTime == downTime;"),
    ("gear UP after a long press is a short press", S + "GearPress.java",
     "boolean shortPress = !longFired && !canceled && eventDownTime == downTime;",
     "boolean shortPress = !canceled && eventDownTime == downTime;"),
    ("gear UP keeps the press armed", S + "GearPress.java",
     "        longFired = false;\n        downTime = -1;\n        return shortPress;", "        return shortPress;"),
    ("gear reset keeps the press", S + "GearPress.java", "    public void reset() {\n        downTime = -1;",
     "    public void reset() {\n        if (false) downTime = -1;"),
    ("eye short press off in a broken profile", S + "MenuProfile.java",
     'return ok() && "restore_off".equals(keys.get("eye_gear_short"));', 'return "restore_off".equals(keys.get("eye_gear_short"));'),
    ("unknown eye_gear_short accepted", S + "MenuProfile.java", "if (!EYE_GEAR_SHORT.contains(e)) problem(", "if (false) problem("),
    ("row shown without the feature", S + "MenuSpec.java", "if (p.has((FeatureId) g[i])) rs.add", "rs.add"),
    ("header kept without rows", S + "MenuSpec.java", "if (!rs.isEmpty()) groups.add", "groups.add"),
    ("round eye without the feature", S + "MenuSpec.java", "if (t[1] == null || p.has((FeatureId) t[1]))", "if (true)"),
    ("tile without the feature", S + "MenuSpec.java", "if (a) r.add(new Tile((String) t[0], id, b ? alt : null));",
     "r.add(new Tile((String) t[0], id, b ? alt : null));"),
    ("second candidate kept without the feature", S + "MenuSpec.java", "b = alt != null && present(alt)",
     "b = alt != null"),
    ("section tile without rows", S + "MenuSpec.java", ": sections.containsKey(id);", ": true;"),
    ("All settings category without rows", S + "MenuSpec.java", "if (secs.containsKey(c) || GENERAL.equals(c))",
     "if (true)"),
    ("empty tile row kept", S + "MenuSpec.java", "if (!r.isEmpty()) rws.add", "rws.add"),
    ("parameters of an absent feature", S + "MenuProfile.java",
     "public int number(FeatureId feature, String key, int fallback) {\n        if (!has(feature)) return fallback;",
     "public int number(FeatureId feature, String key, int fallback) {\n        if (!features.containsKey(feature)) return fallback;"),
    ("has() ignores the profile", S + "MenuProfile.java", "return ok() && features.containsKey(feature);",
     "return ok();"),
    ("has() ignores problems", S + "MenuProfile.java", "return ok() && features.containsKey(feature);",
     "return features.containsKey(feature);"),
    ("hint of an absent feature", S + "MenuProfile.java", "return has(feature) ? hints.get(feature) : null;",
     "return hints.get(feature);"),
    ("unknown feature accepted", S + "MenuProfile.java", "if (id == null) { problem(", "if (id == null) { if (false) problem("),
    ("hint for an absent feature accepted", S + "MenuProfile.java",
     "if (f == null || !features.containsKey(f)) problem(", "if (false) problem("),
    ("choice without options accepted", S + "MenuProfile.java", "if (opts.isEmpty()) problem(", "if (false) problem("),
    ("boost without lamp accepted", S + "MenuProfile.java", "if (!features.containsKey(FeatureId.LAMP)) problem(\"boost needs lamp\");",
     "if (false) problem(\"boost needs lamp\");"),
    ("eye options not starting with off accepted", S + "MenuProfile.java", "&& !\"off\".equals(opts.get(0))) problem(",
     "&& false) problem("),
    ("gear long game menu without the game menu", S + "MenuProfile.java",
     "if (\"game_menu\".equals(keys.get(\"gear_long\"))) problem(", "if (false) problem("),
    ("game items out of order accepted", S + "MenuProfile.java", "else if (at <= last) problem(", "else if (false) problem("),
    ("broken profile keeps its features", S + "MenuProfile.java",
     "return ok() ? Collections.unmodifiableSet(features.keySet()) : Collections.emptySet();",
     "return Collections.unmodifiableSet(features.keySet());"),
    ("game sound list out of order accepted", S + "MenuProfile.java",
     " || !snd.equals(GAME_SOUNDS.subList(0, snd.size())))", ")"),
    ("game sound without the snd item accepted", S + "MenuProfile.java",
     'if (!gameItems.contains("snd")) problem(', 'if (false) problem('),
    ("game sound list of the profile dropped", S + "MenuProfile.java",
     "else { gameSounds.clear(); gameSounds.addAll(snd); gameSoundNamed = true; }", "else { gameSoundNamed = true; }"),
    ("activity host claims layers over the game", UI + "ActivityPanelHost.java",
     "@Override public boolean hasStaticLayers() { return false; }", "@Override public boolean hasStaticLayers() { return true; }"),
    ("duplicate keys accepted", S + "MenuJson.java", "if (m.containsKey(k)) throw err(", "if (false) throw err("),
    # section 11: windows only through the PanelHost, the overlay host the default (Z9X, HB unchanged)
    ("menu note past its host", MC, "        if (title == null || title.length() == 0) host.note(text);",
     "        if (title == null || title.length() == 0) org.lumen.projector.ui.Notify.show(app, text);"),
    ("menu toast past its host", MC, "case FAILED: host.toast(R.string.lumen_m_toast_failed); break;",
     "case FAILED: org.lumen.projector.Ui.toast(app, R.string.lumen_m_toast_failed); break;"),
    ("menu closes its panel past its host", MC, "        QpdPanel v = new QpdPanel(root, card, qpdHost, true);\n        if (panel != null && panel.isShowing()) host.dismiss(panel);",
     "        QpdPanel v = new QpdPanel(root, card, qpdHost, true);\n        if (panel != null && panel.isShowing()) panel.dismiss();"),
    ("no default overlay host", MC, "this.host = host != null ? host : OverlayPanelHost.get(app);", "this.host = host;"),
    ("overlay host is not the old overlay call", UI + "OverlayPanelHost.java",
     "public void dismissAll() { overlay().dismissAll(true); }", "public void dismissAll() { overlay().dismissAll(false); }"),
    ("activity host ignores the device's gate", UI + "ActivityPanelHost.java", "        if (!allowed()) {", "        if (false) {"),
    ("a failing gate opens the activity host", UI + "ActivityPanelHost.java",
     "            Log.w(TAG, \"gate: \" + t);\n            return false;", "            Log.w(TAG, \"gate: \" + t);\n            return true;"),
    ("activity host adds an overlay window", UI + "ActivityPanelHost.java",
     "    @Override public void removeStatic(View v) { }",
     "    @Override public void removeStatic(View v) { int t = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY; }"),
]


def main():
    base = subprocess.run(["sh", os.path.join(HERE, "run.sh")], capture_output=True, text=True)
    if base.returncode != 0:
        print("the unmutated tree is red already: fix test/run.sh first")
        print(base.stdout[-2000:])
        sys.exit(2)
    killed, survived = 0, []
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix="lumen_menu_mut.")
        try:
            root = os.path.join(tmp, "gsi")
            dst = os.path.join(root, "apps", "core", "lumen-menu")
            shutil.copytree(MOD, dst)
            os.makedirs(os.path.join(root, "devices"))
            for c in sorted(os.listdir(os.path.join(G, "devices"))):
                j = os.path.join(G, "devices", c, "menu.json")
                if os.path.isfile(j):
                    os.makedirs(os.path.join(root, "devices", c))
                    shutil.copy(j, os.path.join(root, "devices", c, "menu.json"))
            # the adapters the profiles name and their *Rows sections (read by the contract test)
            for c in os.listdir(os.path.join(root, "devices")):
                ad = json.load(open(os.path.join(root, "devices", c, "menu.json"))).get("adapter", "")
                if ad and os.path.isfile(os.path.join(G, ad)):
                    d = os.path.dirname(ad)
                    os.makedirs(os.path.join(root, d), exist_ok=True)
                    for f in [ad] + glob.glob(os.path.join(G, d, "*Rows.java")):
                        shutil.copy(os.path.join(G, f), os.path.join(root, d))
            p = os.path.join(dst, rel)
            src = open(p, encoding="utf-8").read()
            if src.count(old) != 1:
                print("BROKEN mutant (pattern %d times): %s" % (src.count(old), name))
                survived.append(name)
                continue
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            r = subprocess.run(["sh", os.path.join(dst, "test", "run.sh")], capture_output=True, text=True)
            if r.returncode != 0:
                killed += 1
                why = next((l for l in r.stdout.splitlines() if l.startswith("FAIL")), "red")
                print("killed   %-45s %s" % (name, why[:110]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print("lumen-menu mutants: %d killed, %d survived" % (killed, len(survived)))
    sys.exit(0 if not survived else 1)


if __name__ == "__main__":
    main()
