#!/usr/bin/env python3
"""Mutants of lumen-aipq (the stock AI picture quality): each breaks one path of AiPq (XRMService AIPQ) or the lock,
the Android reads or a stock name of AiPqDriver; test/run.sh must turn red for every one. Works on a scratch copy of
the module, never in place.

  python3 apps/core/lumen-aipq/test/mutate.py
"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
AQ = "src/org/lumen/aipq/AiPq.java"
AD = "src/org/lumen/aipq/AiPqDriver.java"
MUTANTS = [
    ("reset only on AI 10", AQ, "        if (aiScene(pm)) {\n            log.accept(\"reset: ", "        if (pm == PIC_TV) {\n            log.accept(\"reset: "),
    ("scene 17 is not an AI scene", AQ, "return pm >= AI_FIRST && pm <= AI_LAST;", "return pm >= AI_FIRST && pm < AI_LAST;"),
    ("scene 18 is an AI scene", AQ, "return pm >= AI_FIRST && pm <= AI_LAST;", "return pm >= AI_FIRST && pm <= AI_LAST + 1;"),
    ("HDR Vivid not excluded", AQ, "if ((vividAuto && vividPlaying) || hdr == HDR_DOLBY) return;", "if (hdr == HDR_DOLBY) return;"),
    ("Dolby Vision not excluded", AQ, "if ((vividAuto && vividPlaying) || hdr == HDR_DOLBY) return;", "if (vividAuto && vividPlaying) return;"),
    ("a second event 10 rewrites", AQ, "            if (aiScene(prePic)) {", "            if (false) {"),
    ("no needReset", AQ, "if (PIC_TV != curPic || needReset) {", "if (PIC_TV != curPic) {"),
    ("needReset never set", AQ, "needReset = !aiScene(n);", "needReset = false;"),
    ("the event of another type", AQ, "if (type != EVENT_PICTURE_MODE || msg == null) return;", "if (msg == null) return;"),
    ("cropped PQ default ignored", AQ, "multiPqType == MULTIPQ_CROPPED ? PIC_SPORTS : picDefault", "picDefault"),
    ("persist ignored", AQ, "this.picDefault = named != null ? named : persistPic;", "this.picDefault = named != null ? named : PIC_TV;"),
    ("ro ignored", AQ, "Integer named = mode(roDefault);", "Integer named = null;"),
    ("film is scene 15", AQ, "TYPE_MODES = {10, 11, 12, 13, 14, 15, 16, 17, 19, 20};", "TYPE_MODES = {10, 11, 12, 13, 14, 16, 15, 17, 19, 20};"),
    ("multi window not excluded", AQ, "IGNORE.equals(type) || v.multiWindow()) return;", "IGNORE.equals(type)) return;"),
    ("IMAX not excluded", AQ, "if (hdr == HDR_DOLBY || imax || ", "if (hdr == HDR_DOLBY || "),
    ("unknown type in 3D rewrites", AQ, "            if (fmt3d != NO_3D) return;\n", ""),
    ("a known type stopped keeps its scene", AQ, "                if (curPic != picDefault) {\n                    set(v, picDefault);", "                if (false) {\n                    set(v, picDefault);"),
    ("the app in front not checked", AQ, "if (!empty(aiPkg) && !aiPkg.equals(v.topPackage())) {", "if (false) {"),
    ("monitor in the vibrant light mode", AQ, "return !(pm == PIC_USER || lumens == LUMENS_VIBRANT || hdr == HDR_DOLBY);", "return !(pm == PIC_USER || hdr == HDR_DOLBY);"),
    ("monitor in the user mode", AQ, "return !(pm == PIC_USER || lumens == LUMENS_VIBRANT || hdr == HDR_DOLBY);", "return !(lumens == LUMENS_VIBRANT || hdr == HDR_DOLBY);"),
    ("monitor in Dolby Vision", AQ, "return !(pm == PIC_USER || lumens == LUMENS_VIBRANT || hdr == HDR_DOLBY);", "return !(pm == PIC_USER || lumens == LUMENS_VIBRANT);"),
    ("monitor stop keeps the scene", AQ, "if (v.pictureMode() >= PIC_TV) updatePQ(v,", "if (false) updatePQ(v,"),
    ("start writes nothing", AQ, "        curPic = modelDefault();\n        reset(v);", "        curPic = modelDefault();"),
    ("start twice", AQ, "        if (started) return;\n", ""),
    ("driver: no lock", AD, "        synchronized (aipq) {\n            try {\n                t.run(new AndroidSide(v));", "        {\n            try {\n                t.run(new AndroidSide(v));"),
    ("driver: no Android reads", AD, "t.run(new AndroidSide(v));", "t.run(v);"),
    ("driver: own write outside the lock", AD, "locked(\"own write \" + mode, v, x -> aipq.onTvEvent(x,", "post(\"own write \" + mode, x -> aipq.onTvEvent(x,"),
    ("driver: the wrong persist property", AD, "PROP_PERSIST = \"persist.xgimi.default.aipq\"", "PROP_PERSIST = \"persist.xgimi.aipq\""),
    ("driver: the wrong multi window property", AD, "PROP_MULTIWINDOW = \"sys.mw.enable\"", "PROP_MULTIWINDOW = \"sys.multiwindow\""),
    ("driver: no Youku broadcast", AD, "        f.addAction(ACTION_YOUKU_BLURAY);\n", ""),
]


def main():
    base = subprocess.run(["sh", os.path.join(HERE, "run.sh")], capture_output=True, text=True)
    if base.returncode != 0:
        print("the unmutated module is red already: fix test/run.sh first")
        print(base.stdout[-2000:])
        sys.exit(2)
    killed, survived = 0, []
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix="lumen_aipq_mut.")
        try:
            dst = os.path.join(tmp, "gsi", "apps", "core", "lumen-aipq")
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
                print("killed   %-45s %s" % (name, why[:100]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print("lumen-aipq mutants: %d killed, %d survived of %d" % (killed, len(survived), len(MUTANTS)))
    sys.exit(1 if survived else 0)


main()
