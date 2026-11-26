#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# All settings editor wiring (Slava 2026-11-22, FullLayout + FullPageRows + FullEditPages): the pages are laid out for
# the stored layout when built and whenever All settings opens, the editor's row stays last and is never a feature row,
# its keys come before the panel's, the backup row opens Lumen Home's backup page, every language has the strings.
# sh apps/core/lumen-menu/test/fulledit/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
python3 - "$HERE" <<'PY'
import os, re, sys
m = sys.argv[1]
P = os.path.join(m, "src/org/lumen/projector/panel/")
ctl = open(P + "MenuController.java").read()
rows = open(P + "FullPageRows.java").read()
ed = open(P + "FullEditPages.java").read()
home = open(os.path.join(m, "../../LumenHome/AndroidManifest.xml")).read()
bad = 0
def ok(good, what):
    global bad
    if not good:
        bad += 1
        print("FAIL " + what)
def body(src, head):
    i = src.index(head)
    return src[i:src.index("\n    }\n", i)]
build = body(ctl, "    private Page buildSection(String sec) {")
ok("rec.rows(block, f, new ArrayList<>(p.rows().subList(before, p.rows().size())));" in build, "every row of a section page is recorded with its views")
ok("FullPageRows.Block block = rec.block(hr, MenuSpec.DISPLAY_MENU.equals(g.header));" in build and "rec.head(top);" in build, "groups and the note line are recorded")
ok("        fullEditRow = fullEditRow();\n        loadFull(true);\n    }" in ctl, "the constructor lays the pages out for the stored layout")
ok("        loadFull(false);" in body(ctl, "    private void openAll(String category, View focusRow) {"), "All settings reads the stored layout again when it opens")
ok("arrange(sec);" in body(ctl, "    private void extensionAdded(String sec, Extension x) {"), "rows of other modules keep the user's order and the editor's row last")
ok("MenuSpec.GENERAL.equals(sec) ? fullEditRow : null" in ctl, "the editor's row is laid out last on the General page")
ok("fullEdit.onKey(ev, panel.currentPage())" in body(ctl, "    private boolean onKey(KeyEvent ev) {"), "a taken row takes the keys before the panel")
ok("tilePopup(sec);" in body(ctl, "    private void arrange(String sec) {") and "refreshGamePopup();" in body(ctl, "    private void arrange(String sec) {"), "the tile and game popups follow the page")
ok("if (!own.contains(v) && v != last) rest.add(v);" in rows, "rows the page does not own stay")
ok("if (shown.isEmpty()) continue;" in rows, "a group whose rows are all hidden loses its header")
ok("if (title(views) == null && !b.ids.isEmpty())" in rows, "a row without a title rides with the row before it")
ok("new ToggleRow(" in ed and "CheckRow" not in ed, "the show and hide list is switches with a visible off state")
ok('HOME_CUSTOMIZE = "org.lumen.home.action.CUSTOMIZE"' in ctl and 'putExtra("page", "backup")' in ctl, "the backup row opens Lumen Home's backup page")
ok("org.lumen.home.action.CUSTOMIZE" in home, "Lumen Home answers that action")
ok("new SecureQuickStore(app, FullLayout.KEY)" in ctl, "the layout lives in Settings.Secure of the current user")
keys = ["lumen_fe_edit", "lumen_fe_show_hint", "lumen_fe_order", "lumen_fe_move_hint", "lumen_fe_backup"]
res = os.path.join(m, "res")
for d in (os.path.abspath(res), os.getcwd()):         # the tree of res, else the tree the test runs in
    while d != os.path.dirname(d) and not os.path.isfile(os.path.join(d, "tools/i18n/res_strings.py")):
        d = os.path.dirname(d)
    if os.path.isfile(os.path.join(d, "tools/i18n/res_strings.py")):
        break
sys.path.insert(0, os.path.join(d, "tools/i18n"))
import res_strings  # strings files as the APK resolves them (aliases of lumen-common-strings)
for d in sorted(os.listdir(res)):
    if not d.startswith("values"):
        continue
    f = os.path.join(res, d, "lumen_strings_fe.xml")
    names = re.findall(r'<string name="(\w+)">([^<]+)</string>', res_strings.text(f))
    ok(sorted(n for n, _ in names) == sorted(keys), "every editor string in " + d)
    ok(all(";" not in t and "\u2014" not in t and "\u2013" not in t for _, t in names), "no semicolons or dashes in " + d)
print("fulledit wiring: " + ("all green" if bad == 0 else "%d failed" % bad))
sys.exit(1 if bad else 0)
PY
