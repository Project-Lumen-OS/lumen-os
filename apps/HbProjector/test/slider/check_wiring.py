#!/usr/bin/env python3
"""HB slider wiring (source level): every picture slider of the advanced picture page (panel/ProPicture: 2-point and
11-point white balance, colour correction) and of the quick panel (panel/QuickPanelController: brightness, contrast,
saturation, sharpness) writes through its own SlideCoalescer (SlideMain.of), on every step, with no direct change()
per commit and no page re-read while a slide is in flight (4PDA report 2026-11-10: flicker on a fast slide)."""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
HB = os.path.abspath(os.path.join(HERE, "..", ".."))
P = os.path.join(HB, "src/org/lumen/projector/panel")
n = bad = 0


def ok(what, cond):
    global n, bad
    n += 1
    if not cond:
        bad += 1
        print("FAIL " + what)


def body(src, sig):
    i = src.find(sig)
    if i < 0: return ""
    j = src.find("{", i); depth = 0
    for k in range(j, len(src)):
        if src[k] == "{": depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0: return src[i:k + 1]
    return ""


pp = open(os.path.join(P, "ProPicture.java"), encoding="utf-8").read()
qp = open(os.path.join(P, "QuickPanelController.java"), encoding="utf-8").read()
sm = open(os.path.join(P, "SlideMain.java"), encoding="utf-8").read()

# ---- ProPicture
sl = body(pp, "private SliderRow.OnSlide slide(")
ok("ProPicture.slide builds a coalescer per slider", "SlideMain.of(app, write, this::refresh)" in sl and "slides.add(c)" in sl)
ok("ProPicture.slide submits every step", re.search(r"onChanging\([^)]*\)\s*\{[^}]*c\.submit\(value\)", sl, re.S) is not None)
ok("ProPicture.slide submits the commit", re.search(r"onCommit\([^)]*\)\s*\{[^}]*c\.submit\(value\)", sl, re.S) is not None)
ok("ProPicture.slide no direct write", "change(" not in sl and "Hal.run" not in sl)
for what, anchor in (("2-point wb", "g.setWb(item, v)"), ("11-point wb", "g2.setMultiPointWb(point"),
                     ("colour correction", "g2.setHsyColorModel(type, arr)")):
    i = pp.find(anchor)
    seg = pp[max(0, i - 400):i]
    ok("ProPicture %s goes through slide(...) as a SlideMain.Step" % what,
       i >= 0 and "slide(v ->" in seg and "SlideMain.Step(" in seg and "change(" not in seg[seg.rfind("slide(v ->"):])
rf = body(pp, "void refresh()")
ok("ProPicture.refresh skips while sliding", rf.find("if (sliding()) return;") >= 0 and
   rf.find("if (sliding()) return;") < rf.find("Hal.query"))
ok("ProPicture.refresh forgets the written values on a read", "c.forget()" in rf)
sg = body(pp, "private boolean sliding()")
ok("ProPicture.sliding asks every coalescer", "for (SlideCoalescer c : slides) if (c.busy()) return true;" in sg)

# ---- QuickPanelController
i = qp.find("final GmpfClient.PqItem item = PQ_ORDER[i];")
seg = qp[i:i + 1500]
ok("quick panel pq: coalescer per slider", "SlideMain.of(app," in seg and "g.setPq(item, v)" in seg and "pqSlides.add(slide)" in seg)
ok("quick panel pq: every step submitted", re.search(r"onChanging\([^)]*\)\s*\{[^}]*slide\.submit\(value\)", seg, re.S) is not None)
ok("quick panel pq: commit submitted", re.search(r"onCommit\([^)]*\)\s*\{[^}]*slide\.submit\(value\)", seg, re.S) is not None)
ok("quick panel pq: no change() per commit", 'change("pq "' not in qp)
rq = body(qp, "void refresh()")
ok("quick panel refresh skips while a pq slide is busy", "for (SlideCoalescer c : pqSlides) if (c.busy()) return;" in rq
   and rq.find("c.busy()") < rq.find("Hal.query"))
ok("quick panel refresh forgets the written values on a read", "c.forget()" in rq)

# ---- SlideMain: the write reports back, on the HAL thread
wr = body(sm, "private static void write(")
ok("SlideMain writes through Hal.query (result on the main thread)", "Hal.query(" in wr and "Hal.run(" not in wr)
ok("SlideMain reports a failed write", "done.done(false)" in wr and "done.done(ok)" in wr)
ok("SlideMain ok only on TRUE", "Boolean.TRUE.equals(r)" in wr)
ok("SlideMain main scheduler", "Ui.main().postDelayed(r, delayMs)" in sm and "Ui.main().removeCallbacks(r)" in sm)

if bad:
    print("check_wiring (slider): %d of %d FAILED" % (bad, n)); sys.exit(1)
print("check_wiring (slider): %d checks passed" % n)
