#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of Files (rules, wiring to Home and to the images): each must turn test/run.sh red.
python3 apps/LumenFiles/test/mutate.py [-j N]   (a copy of the files it needs per mutant, nothing changes in the tree)"""
import concurrent.futures, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, ".."))
ROOT = os.path.normpath(os.path.join(APP, "..", ".."))
M = "apps/LumenFiles/src/org/lumen/files/model/"
F = "apps/LumenFiles/src/org/lumen/files/"
H = "apps/LumenHome/"
COPY = ["apps/LumenFiles", "apps/core/lumen-icons/res", H + "AndroidManifest.xml", H + "src/org/lumen/home/FilesLink.java",
        H + "src/org/lumen/home/Launch.java", H + "src/org/lumen/home/data/HomeLoader.java", H + "src/org/lumen/home/data/ServersHandOff.java",
        "tools/play6/build_play6.sh", "tools/devices/layered.py", "devices/_family"]

MUTANTS = [
    ("card never grows", M + "Fit.java", "return Math.max(min, Math.min(max, need));", "return min;"),
    ("card grows past the cap", M + "Fit.java", "return Math.max(min, Math.min(max, need));", "return Math.max(min, need);"),
    ("card cuts the rounding", M + "Fit.java", "int need = pad + (int) Math.ceil(text);", "int need = pad + (int) text;"),
    ("strip height fixed again", F + "ui/FilesActivity.java", "        fitStripHeight();\n    }", "    }"),
    ("strip ignores its paddings", F + "ui/FilesActivity.java",
     "int want = Math.max(Ui.px(STRIP_H), h + mStripScroll.getPaddingTop() + mStripScroll.getPaddingBottom());",
     "int want = Math.max(Ui.px(STRIP_H), h);"),
    ("card width ignores its text", F + "ui/FilesActivity.java", "new LinearLayout.LayoutParams(width, -1);", "new LinearLayout.LayoutParams(Ui.px(w), -1);"),
    ("clash names start at 1", M + "Names.java", "for (int i = 2; ; i++)", "for (int i = 1; ; i++)"),
    ("$ folders shown", M + "Names.java", ' || n.startsWith("$")', ""),
    ("FAT characters allowed", M + "Names.java", "BAD.indexOf(n.charAt(i)) >= 0 || ", ""),
    ("archive may climb out", M + "Archive.java", 'if (part.equals("..") || part.indexOf(\'\\0\') >= 0) return null;', "if (part.indexOf('\\0') >= 0) return null;"),
    ("archive ignores free space", M + "Archive.java", "if (written > room) throw new Stop(Outcome.NO_SPACE);", ""),
    ("tar links written", M + "Archive.java", "} else if (type == '0' || type == 0 || type == '7') {", "} else if (type != '5') {"),
    ("tgz not unpacked", M + "ArchiveFormat.java", 'n.endsWith(".tgz") || ', ""),
    ("no free space check", M + "Transfer.java", "if (free >= 0 && free < total) return Outcome.NO_SPACE;", ""),
    ("cancel leaves half files", M + "Transfer.java", "wipe(dst, target);\n                return Outcome.CANCELLED;", "return Outcome.CANCELLED;"),
    ("same drive move copies", M + "Transfer.java", "if (op == TransferOp.MOVE && src.same(dst)) return renames();", ""),
    ("move keeps the source", M + "Transfer.java", "                    removeAll(src, e.path);\n", ""),
    ("folder into itself", M + "Transfer.java", "for (Entry e : items) if (e.dir() && src.same(dst) && inside(e.path, into)) return Outcome.INTO_ITSELF;", ""),
    ("move into its own folder offered", M + "Clip.java", "return !(op == TransferOp.MOVE && all);", "return true;"),
    ("folders not first", M + "Sorter.java", "if (a.dir() != b.dir()) return a.dir() ? -1 : 1;", ""),
    ("search needs one word only", M + "Search.java", 'for (String w : query.split(" ")) if (!n.contains(w)) return false;', 'if (!n.contains(query.split(" ")[0])) return false;'),
    ("walk enters Android/", M + "Walk.java", '!(depth == 0 && "Android".equals(e.name))', "true"),
    ("renderers taken as servers", M + "Upnp.java", 'if (loc == null || !(st.contains("MediaServer") || st.contains("ContentDirectory"))) return null;', "if (loc == null) return null;"),
    ("object id not escaped", M + "Upnp.java", '.replace("&", "&amp;")', ""),
    ("first resource wins", M + "Didl.java", 'if (info.startsWith("http-get")) break;', "break;"),
    ("apk not an app", M + "Kinds.java", '        kind(Kind.APK, "apk");\n', ""),
    ("bytes round down", M + "Sizes.java", "Math.max(1, (b + 999) / 1000)", "b / 1000"),
    ("BACK forgets the folder", M + "Nav.java", 'if (t != null) t.focus = focused == null ? "" : focused;', ""),
    ("gone rows stay picked", M + "Selection.java", "on.keySet().retainAll(keep);", ""),
    ("installer sees an unknown source", F + "open/ApkInstall.java", ".putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)", ".putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)"),
    ("Lumen Play skipped", F + "open/Opener.java", ".setPackage(LUMEN_PLAY)", ""),
    ("silent install", "apps/LumenFiles/AndroidManifest.xml", '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />',
     '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n    <uses-permission android:name="android.permission.INSTALL_PACKAGES" />'),
    ("provider exported", "apps/LumenFiles/AndroidManifest.xml", 'android:authorities="org.lumen.files.local"\n            android:exported="false"',
     'android:authorities="org.lumen.files.local"\n            android:exported="true"'),
    ("Russian misses a string", "apps/LumenFiles/res/values-ru/strings.xml", '    <string name="files_paste_here">Вставить сюда</string>\n', ""),
    ("RS30 image without Files", "devices/_family/rs30/rs30.build.conf", " LumenFiles\n", "\n"),
    ("X50 image does not place Files", "devices/g0111/image/steps.tsv", "{sys}/priv-app/LumenFiles/LumenFiles.apk", "{sys}/app/LumenFiles/LumenFiles.apk"),
    ("any app takes the servers", H + "src/org/lumen/home/data/ServersHandOff.java",
     "\n                || c.getPackageManager().checkSignatures(c.getPackageName(), FILES) != PackageManager.SIGNATURE_MATCH", ""),
    ("USB card opens Files at the start", H + "src/org/lumen/home/Launch.java", "FilesLink.open(FilesLink.path(k.intent))", "FilesLink.open(null)"),
    ("Home names another extra", H + "src/org/lumen/home/FilesLink.java", 'EXTRA_PATH = "org.lumen.files.extra.PATH"', 'EXTRA_PATH = "path"'),
    ("ring in the actions panel", F + "ui/Sheet.java", "            b.ring = false;\n", ""),
    ("arrows leave the window", F + "ui/FilesActivity.java", "return trap(e, mPrompt) || super.dispatchKeyEvent(e);", "return super.dispatchKeyEvent(e);"),
    ("delete confirm starts on Delete", F + "ui/Prompt.java", "(safe ? n : y).requestFocus();", "y.requestFocus();"),
    ("panel drops the focus", F + "ui/Sheet.java", "        give(mBack, lost);\n", ""),
    ("BACK during a job closes without Cancel", F + "ui/FilesActivity.java", "if (mPrompt.busy()) mPrompt.cancel();\n                    else mPrompt.hide();", "mPrompt.hide();"),
]


def tree(tmp):
    root = os.path.join(tmp, "r")
    for rel in COPY:
        s, d = os.path.join(ROOT, rel), os.path.join(root, rel)
        os.makedirs(os.path.dirname(d), exist_ok=True)
        if os.path.isdir(s):
            shutil.copytree(s, d, ignore=shutil.ignore_patterns("build"))
        else:
            shutil.copy(s, d)
    for c in os.listdir(os.path.join(ROOT, "devices")):
        for rel in ("build.conf", "device.conf", "image/steps.tsv"):
            s = os.path.join(ROOT, "devices", c, rel)
            if os.path.isfile(s):
                d = os.path.join(root, "devices", c, rel)
                os.makedirs(os.path.dirname(d), exist_ok=True)
                shutil.copy(s, d)
    return root


def run(root):
    env = dict(os.environ, TMPDIR=os.path.join(os.path.dirname(root), "t"))
    os.makedirs(env["TMPDIR"], exist_ok=True)
    return subprocess.run(["sh", os.path.join(root, "apps/LumenFiles/test/run.sh")], capture_output=True, text=True, env=env, timeout=600)


def one(m):
    name, rel, old, new = m
    tmp = tempfile.mkdtemp(prefix="files-mut.")
    try:
        root = tree(tmp)
        p = os.path.join(root, rel)
        text = open(p, encoding="utf-8").read()
        if text.count(old) != 1:
            return name, "BROKEN (%d matches)" % text.count(old)
        open(p, "w", encoding="utf-8").write(text.replace(old, new))
        r = run(root)
        return name, "killed" if r.returncode != 0 else "SURVIVED"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    j = int(sys.argv[sys.argv.index("-j") + 1]) if "-j" in sys.argv else 4
    tmp = tempfile.mkdtemp(prefix="files-mut.")
    try:
        base = run(tree(tmp))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if base.returncode != 0:
        print(base.stdout[-3000:], base.stderr[-3000:])
        print("files mutate: the unmutated copy is red")
        return 1
    bad = 0
    with concurrent.futures.ThreadPoolExecutor(j) as ex:
        for name, res in ex.map(one, MUTANTS):
            print("%-10s %s" % (res, name))
            bad += res != "killed"
    print("files mutate: %d mutants, %d not killed" % (len(MUTANTS), bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
