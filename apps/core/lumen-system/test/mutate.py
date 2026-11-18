#!/usr/bin/env python3
"""Gate mutants of lumen-system: each mutant breaks a way of SystemIntent.chain or the walk of SystemPages.open;
test/run.sh must turn red for every one. Works on a scratch copy of the module, never in place.

  python3 apps/core/lumen-system/test/mutate.py
"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
SI = "src/org/lumen/system/SystemIntent.java"
SP = "src/org/lumen/system/SystemPages.java"
CV = "src/org/lumen/system/ColourVision.java"
MUTANTS = [
    ("bluetooth: Bluetooth settings before Remotes & Accessories", SI,
     "return Arrays.asList(new SystemIntent(SLICE_SETTINGS, TV_SETTINGS, SLICE_URI, ACCESSORIES),\n                        of(BLUETOOTH), of(ROOT));",
     "return Arrays.asList(of(BLUETOOTH), new SystemIntent(SLICE_SETTINGS, TV_SETTINGS, SLICE_URI, ACCESSORIES),\n                        of(ROOT));"),
    ("bluetooth: no settings root at the end", SI, "of(BLUETOOTH), of(ROOT));", "of(BLUETOOTH));"),
    ("bluetooth: no Bluetooth settings step", SI, "of(BLUETOOTH), of(ROOT));", "of(ROOT));"),
    ("bluetooth: the slice page of any app", SI, "new SystemIntent(SLICE_SETTINGS, TV_SETTINGS,", "new SystemIntent(SLICE_SETTINGS, null,"),
    ("bluetooth: the wrong slice", SI, "sliceprovider/general\";", "sliceprovider/device\";"),
    ("bluetooth: the wrong extra name", SI, "SLICE_URI = \"slice_uri\"", "SLICE_URI = \"uri\""),
    ("bluetooth: the wrong settings app", SI, "TV_SETTINGS = \"com.android.tv.settings\"", "TV_SETTINGS = \"com.android.settings\""),
    ("network: no Wi-Fi page", SI, "return Arrays.asList(of(WIFI), of(ROOT));", "return Arrays.asList(of(ROOT));"),
    ("network: the Bluetooth chain", SI, "            case NETWORK:\n                return Arrays.asList(of(WIFI), of(ROOT));\n", ""),
    ("a missing way ends the chain", SP, "getPackageManager()) == null) continue;", "getPackageManager()) == null) return false;"),
    ("a refusing way ends the chain", SP, "                Log.w(TAG, page + \" via \" + w + \": \" + t);\n",
     "                Log.w(TAG, page + \" via \" + w + \": \" + t);\n                return false;\n"),
    ("the slice page without its app", SP, "                if (w.pkg != null) i.setPackage(w.pkg);\n", ""),
    ("the slice page without its slice", SP, "                if (w.extra != null) i.putExtra(w.extra, w.value);\n", ""),
    ("a started way reports nothing started", SP, "                ctx.startActivity(i);\n                return true;", "                ctx.startActivity(i);\n                return false;"),
    ("no way started reports a start", SP, "        }\n        return false;\n    }", "        }\n        return true;\n    }"),
    ("colour vision: red-green on deuteranomaly", CV, "static final int[] MODES = {0, 11, 12, 13};", "static final int[] MODES = {0, 12, 11, 13};"),
    ("colour vision: off forgets nothing but the switch stays on", CV, "ENABLED, mode >= 0 ? 1 : 0);", "ENABLED, 1);"),
    ("colour vision: a filter without the switch", CV, "ENABLED, mode >= 0 ? 1 : 0);", "ENABLED, 0);"),
    ("colour vision: off writes the gray filter", CV, "if (mode >= 0) Settings.Secure.putInt(c.getContentResolver(), MODE, mode);", "Settings.Secure.putInt(c.getContentResolver(), MODE, Math.max(mode, 0));"),
    ("colour vision: the switch ignored on read", CV, "if (Settings.Secure.getInt(c.getContentResolver(), ENABLED, 0) != 1) return OFF;", ""),
    ("colour vision: unknown token accepted", CV, "if (mode < 0 && !OFF.equals(token)) return false;", ""),
    ("colour vision: a wrong default mode", CV, "MODE, MODES[2]));", "MODE, MODES[1]));"),
    ("colour vision: the wrong setting", CV, "MODE = \"accessibility_display_daltonizer\";", "MODE = \"accessibility_display_daltonizer_saturation_level\";"),
]


def main():
    base = subprocess.run(["sh", os.path.join(HERE, "run.sh")], capture_output=True, text=True)
    if base.returncode != 0:
        print("the unmutated module is red already: fix test/run.sh first")
        print(base.stdout[-2000:])
        sys.exit(2)
    killed, survived = 0, []
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix="lumen_system_mut.")
        try:
            dst = os.path.join(tmp, "gsi", "apps", "core", "lumen-system")
            shutil.copytree(MOD, dst)
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
                print("killed   %-50s %s" % (name, why[:100]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print("lumen-system mutants: %d killed, %d survived of %d" % (killed, len(survived), len(MUTANTS)))
    sys.exit(1 if survived else 0)


main()
