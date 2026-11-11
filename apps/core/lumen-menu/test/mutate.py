#!/usr/bin/env python3
"""Gate mutants of lumen-menu: each mutant weakens the "absent feature = no row" rule, the
profile checks or the window hosts (section 11); test/run.sh must turn red for every one. Works on a scratch copy of
the module, never in place.

  python3 apps/core/lumen-menu/test/mutate.py
"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
G = os.path.dirname(os.path.dirname(os.path.dirname(MOD)))
S = "src/org/lumen/menu/"
MC = "src/org/lumen/projector/panel/MenuController.java"
UI = "src/org/lumen/projector/ui/"
MUTANTS = [
    ("row shown without the feature", S + "MenuSpec.java", "if (p.has((String) g[i])) rs.add", "rs.add"),
    ("header kept without rows", S + "MenuSpec.java", "if (!rs.isEmpty()) groups.add", "groups.add"),
    ("round eye without the feature", S + "MenuSpec.java", "if (t[1] == null || p.has(t[1]))", "if (true)"),
    ("tile without the feature", S + "MenuSpec.java", "if (a) r.add(new Tile(t[0], id, b ? alt : null));",
     "r.add(new Tile(t[0], id, b ? alt : null));"),
    ("second candidate kept without the feature", S + "MenuSpec.java", "b = alt != null && present(alt)",
     "b = alt != null"),
    ("section tile without rows", S + "MenuSpec.java", ": sections.containsKey(id);", ": true;"),
    ("All settings category without rows", S + "MenuSpec.java", "if (secs.containsKey(c) || GENERAL.equals(c))",
     "if (true)"),
    ("empty tile row kept", S + "MenuSpec.java", "if (!r.isEmpty()) rws.add", "rws.add"),
    ("parameters of an absent feature", S + "MenuProfile.java",
     "public int number(String feature, String key, int fallback) {\n        if (!has(feature)) return fallback;",
     "public int number(String feature, String key, int fallback) {\n        if (!features.containsKey(feature)) return fallback;"),
    ("has() ignores the profile", S + "MenuProfile.java", "return ok() && features.containsKey(feature);",
     "return ok();"),
    ("has() ignores problems", S + "MenuProfile.java", "return ok() && features.containsKey(feature);",
     "return features.containsKey(feature);"),
    ("hint of an absent feature", S + "MenuProfile.java", "return has(feature) ? hints.get(feature) : null;",
     "return hints.get(feature);"),
    ("unknown feature accepted", S + "MenuProfile.java", "if (d == null) { problem(", "if (d == null) { if (false) problem("),
    ("hint for an absent feature accepted", S + "MenuProfile.java",
     "if (!features.containsKey(e.getKey())) problem(", "if (false) problem("),
    ("choice without options accepted", S + "MenuProfile.java", "if (opts.isEmpty()) problem(", "if (false) problem("),
    ("boost without lamp accepted", S + "MenuProfile.java", "if (!features.containsKey(Feature.LAMP)) problem(\"boost needs lamp\");",
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
            # the adapters the profiles name (read by the contract test)
            for c in os.listdir(os.path.join(root, "devices")):
                txt = open(os.path.join(root, "devices", c, "menu.json")).read()
                import json
                ad = json.loads(txt).get("adapter", "")
                if ad and os.path.isfile(os.path.join(G, ad)):
                    os.makedirs(os.path.dirname(os.path.join(root, ad)), exist_ok=True)
                    shutil.copy(os.path.join(G, ad), os.path.join(root, ad))
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
