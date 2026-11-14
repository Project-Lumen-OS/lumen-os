#!/usr/bin/env python3
"""check_handshake.py: check K of the design notes. The stock boot handshake of the Play 6
projector app stays exactly as it was tested (t1..t11): BootHandshake.java, BootReceiver.java with at most the
added statistics line, App.java with at most the one added Play6App line, and the handshake calls of the IGmpf client.

  check_handshake.py [APP_DIR]        default APP_DIR: the assembled Play 6 projector tree
  (devices/dragon_healthy/app/Play6Projector.layers, written to a temp dir by play6_tree.py;
  apps/Play6Projector alone is only the Play 6 layer since the design notes)

Baseline: tools/play6/baseline/{App,BootReceiver,BootHandshake}.java, copied at setup step 0. The sha256 of each
baseline copy is fixed below, so an edited baseline fails too.
- BootReceiver.java: the baseline, or the baseline plus exactly one line right after `BootHandshake.start(ctx);`:
  `if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) org.lumen.stats.StatsJob.schedule(ctx);` (any
  indentation): the statistics of apps/core/lumen-stats, scheduled after the handshake has started.
- BootHandshake.java: the baseline with exactly the MIGRATION edits (each applied exactly once), nothing else: the
  order and the text of every handshake step stay. The edits are the typed boot stage and the exception of the
  shared gmpf client (apps/core/lumen-gmpf).
- App.java: the baseline, or the baseline plus exactly one line right after `BootHandshake.start(this);`:
  `org.lumen.projector.Play6App.onProcessStart(this);` (any indentation). Nothing else may differ.
- The handshake calls run in the shared client body GmpfBase (Play6Gmpf extends it):
    setSystemUiReady()      sends key(SET_SYSTEM_UI_STATE)   with SYSTEM_UI_STATE_READY = 1
    goToBootCompleted(s)    sends key(GO_TO_BOOT_COMPLETED)  with s.wire, enum BootStage exactly EARLY(0),
                            AFTER_BOOTANIM(1)
    setBootAnimFinished()   sends key(SET_BOOT_ANIM_STATE)   with BOOT_ANIM_STATE_FINISHED = 2
    getBootAnimState()      sends key(GET_BOOT_ANIM_STATE)
  each exactly once in GmpfBase; Play6Codes maps the four operations to HalCodes.GMPF_* constants that hold
  391 / 237 / 395 / 394; Play6Gmpf talks to IGMPF = xgimi.hardware.gmpf@1.0::IGmpf, its no-argument constructor
  (BootHandshake's) is `this(null)` (no focus callback) and connect() is `return hidl.connect(null, 0);` (retry
  false, no death recipient). The bytes of the six steps are the `handshake` session of the wire golden
  (apps/core/lumen-gmpf/test/wire/golden/dragon_healthy.txt; run.sh proves the clients still send it): exactly
  391(1), 237(0), 395(2), 237(1), 394(), 307(17) on IGmpf.
- Play6Gmpf may not change what the handshake calls do at run time: it does not declare a handshake member, a
  member named like a member of the transport (HidlWire, HidlCaller; connect excepted, pinned above) or of the
  shared plumbing (key, has, rules, wire), no finalize(), no static or instance initializer block, field
  initializers only constant expressions (no call, no `new` other than a primitive array, no lambda, no method
  reference: they run when BootHandshake creates its Play6Gmpf or loads the class).
Exit 0 = K green, 1 = red (every difference is printed).
"""
import hashlib
import os
import re
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import javascan  # noqa: E402
import play6_tree  # noqa: E402

G = os.path.dirname(os.path.dirname(HERE))
BASE = os.path.join(HERE, "baseline")
GOLDEN = os.path.join(G, "apps/core/lumen-gmpf/test/wire/golden/dragon_healthy.txt")

# sha256 of the baseline copies (plan section 5, check K); re-pinned 2026-10-29 for the org.z9x -> org.lumen
# rename (each copy differs from the t1..t11 baseline only by that rename: org/lumen, org.lumen)
BASELINE_SHA = {
    "BootHandshake.java": "13da0f11bdd437da111ab41c55cc3643e8bd8004b53ea017533616f3b5ca1461",
    "BootReceiver.java": "2fcbe4ce33217930c5c4efb104f67636e67473fe88421f92e6170c4b24a33dd1",
    "App.java": "be8e9ee665514f1a5d9bad4f50ffa56b5ff2a5fd5c6f9cf7792337199b923e9a",
}
PATHS = {
    "BootHandshake.java": "src/org/lumen/play6/BootHandshake.java",
    "BootReceiver.java": "src/org/lumen/play6/BootReceiver.java",
    "App.java": "src/org/lumen/play6/App.java",
}
HAL = "src/org/lumen/projector/hal/"
# BootHandshake.java on the shared gmpf client (2026-11-12): the typed boot stage and the shared ReplyException
MIGRATION = (
    ("import org.lumen.projector.hal.Play6Gmpf;\n",
     "import org.lumen.projector.hal.BootStage;\nimport org.lumen.projector.hal.Play6Gmpf;\n"
     "import org.lumen.projector.hal.ReplyException;\n"),
    ("gmpf.goToBootCompleted(Play6Gmpf.STAGE_EARLY)", "gmpf.goToBootCompleted(BootStage.EARLY)"),
    ("gmpf.goToBootCompleted(Play6Gmpf.STAGE_AFTER_BOOTANIM)", "gmpf.goToBootCompleted(BootStage.AFTER_BOOTANIM)"),
    ("catch (Play6Gmpf.ReplyException e)", "catch (ReplyException e)"),
)
# member -> (operation, value expression or None)
HANDSHAKE = {
    "setSystemUiReady": ("SET_SYSTEM_UI_STATE", "SYSTEM_UI_STATE_READY"),
    "goToBootCompleted": ("GO_TO_BOOT_COMPLETED", "s.wire"),
    "setBootAnimFinished": ("SET_BOOT_ANIM_STATE", "BOOT_ANIM_STATE_FINISHED"),
    "getBootAnimState": ("GET_BOOT_ANIM_STATE", None),
}
VALUES = {"SYSTEM_UI_STATE_READY": 1, "BOOT_ANIM_STATE_FINISHED": 2}
HALCODES = {"SET_SYSTEM_UI_STATE": ("GMPF_SET_SYSTEM_UI_STATE", 391), "GO_TO_BOOT_COMPLETED": ("GMPF_GO_TO_BOOT_COMPLETED", 237),
            "SET_BOOT_ANIM_STATE": ("GMPF_SET_BOOT_ANIM_STATE", 395), "GET_BOOT_ANIM_STATE": ("GMPF_GET_BOOT_ANIM_STATE", 394)}
BOOT_STAGE = [("EARLY", 0), ("AFTER_BOOTANIM", 1)]
IGMPF = "xgimi.hardware.gmpf@1.0::IGmpf"
GOLDEN_STEPS = [("391", "i32:1"), ("237", "i32:0"), ("395", "i32:2"), ("237", "i32:1"), ("394", ""), ("307", "i8:17")]
PLUMBING = {"key", "has", "rules", "wire", "isConnected"}
# a field initializer that runs code: a call, an object (not a primitive array), a lambda, a method reference
RUNS_CODE = re.compile(r"[\w$]\s*\(|\bnew\b(?!\s+(?:int|long|short|byte|char|boolean|float|double)\s*\[)|->|::")
START_LINE = re.compile(r"^\s*BootHandshake\.start\(this\);\s*$")
ADDED_LINE = re.compile(r"^\s*org\.lumen\.projector\.Play6App\.onProcessStart\(this\);\s*$")
RECEIVER_START = re.compile(r"^\s*BootHandshake\.start\(ctx\);\s*$")
RECEIVER_ADDED = re.compile(r"^\s*if \(Intent\.ACTION_BOOT_COMPLETED\.equals\(intent\.getAction\(\)\)\) "
                            r"org\.lumen\.stats\.StatsJob\.schedule\(ctx\);\s*$")


def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def one_line_after(base_path, now_path, start, added):
    """now = the baseline plus exactly one line matching added, right after the only baseline line matching start."""
    base = open(base_path, encoding="utf-8").read().split("\n")
    now = open(now_path, encoding="utf-8").read().split("\n")
    starts = [i for i, l in enumerate(base) if start.match(l)]
    if len(starts) != 1 or len(now) != len(base) + 1:
        return False
    i = starts[0] + 1
    return now[:i] == base[:i] and added.match(now[i]) is not None and now[i + 1:] == base[i:]


def body_of(src, name, nparams=None):
    """[(params, body)] of the top-level methods named name of masked src."""
    out = []
    for m in re.finditer(r"^[ ]{4}[\w<>\[\], ]*\b%s\s*\(([^)]*)\)[^;{=]*\{" % re.escape(name), src, re.M):
        end = javascan.matching(src, m.end() - 1)
        out.append((m.group(1).strip(), " ".join(src[m.end():end].split())))
    return [x for x in out if nparams is None or len([p for p in x[0].split(",") if p.strip()]) == nparams]


def main():
    app = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else play6_tree.default_app("check_handshake")
    if not os.path.isfile(os.path.join(app, HAL, "GmpfBase.java")):
        app = play6_tree.default_app("check_handshake")
    errors = []
    notes = []

    for name, want in BASELINE_SHA.items():
        p = os.path.join(BASE, name)
        if not os.path.isfile(p):
            errors.append("baseline %s is missing" % os.path.relpath(p, G))
        elif sha(p) != want:
            errors.append("baseline %s was edited (sha256 %s, expected %s)" % (os.path.relpath(p, G), sha(p), want))
    if errors:
        return report(errors, notes)

    cur = {}
    for name, rel in PATHS.items():
        p = os.path.join(app, rel)
        if not os.path.isfile(p):
            errors.append("%s is missing" % rel)
        else:
            cur[name] = p

    if "BootReceiver.java" in cur:
        if sha(cur["BootReceiver.java"]) == BASELINE_SHA["BootReceiver.java"]:
            notes.append("BootReceiver.java: identical to the baseline")
        elif one_line_after(os.path.join(BASE, "BootReceiver.java"), cur["BootReceiver.java"], RECEIVER_START, RECEIVER_ADDED):
            notes.append("BootReceiver.java: the baseline plus the statistics line after BootHandshake.start(ctx)")
        else:
            errors.append("%s differs from the baseline by more than the one statistics line right after "
                          "BootHandshake.start(ctx) (sha256 %s)" % (PATHS["BootReceiver.java"], sha(cur["BootReceiver.java"])))

    if "BootHandshake.java" in cur:
        want = open(os.path.join(BASE, "BootHandshake.java"), encoding="utf-8").read()
        for old, new in MIGRATION:
            if want.count(old) != 1:
                errors.append("check_handshake: MIGRATION %r is not in the baseline exactly once (script out of date)" % old)
            want = want.replace(old, new)
        now = open(cur["BootHandshake.java"], encoding="utf-8").read()
        if now == want:
            notes.append("BootHandshake.java: the baseline with the %d MIGRATION edits, nothing else" % len(MIGRATION))
        else:
            a, b = want.split("\n"), now.split("\n")
            i = next((k for k in range(min(len(a), len(b))) if a[k] != b[k]), min(len(a), len(b)))
            errors.append("BootHandshake.java differs from the migrated baseline at line %d:\n--- want\n%s\n--- now\n%s"
                          % (i + 1, a[i] if i < len(a) else "<end>", b[i] if i < len(b) else "<end>"))

    if "App.java" in cur:
        if sha(cur["App.java"]) == BASELINE_SHA["App.java"]:
            notes.append("App.java: identical to the baseline (Play6App line not added yet)")
        elif one_line_after(os.path.join(BASE, "App.java"), cur["App.java"], START_LINE, ADDED_LINE):
            notes.append("App.java: the baseline plus the Play6App line after BootHandshake.start(this)")
        else:
            errors.append("App.java: differs from the baseline by more than the one Play6App line right "
                          "after BootHandshake.start(this) (allowed: `org.lumen.projector.Play6App.onProcessStart(this);`)")

    check_members(app, errors, notes)
    check_golden(errors, notes)
    return report(errors, notes)


def read(app, name):
    p = os.path.join(app, HAL, name + ".java")
    return javascan.mask(open(p, encoding="utf-8").read()) if os.path.isfile(p) else None


def check_members(app, errors, notes):
    base, client, codes, hc, stage = (read(app, n) for n in ("GmpfBase", "Play6Gmpf", "Play6Codes", "HalCodes", "BootStage"))
    if None in (base, client, codes, hc, stage):
        errors.append("hal/: GmpfBase, Play6Gmpf, Play6Codes, HalCodes and BootStage are needed")
        return
    for name, (op, val) in HANDSHAKE.items():
        found = body_of(base, name)
        if len(found) != 1:
            errors.append("GmpfBase.%s: %d declarations, expected one" % (name, len(found)))
            continue
        params, body = found[0]
        if name == "goToBootCompleted" and params.split() != ["BootStage", "s"]:
            errors.append("GmpfBase.goToBootCompleted(%s): the parameter is not BootStage s" % params)
        sends = re.findall(r"\bwire\s*\.\s*\w+\s*\(\s*key\s*\(\s*(\w+)\s*\)\s*(?:,\s*([\w.]+)\s*)?\)", body)
        if sends != [(op, val or "")] or len(re.findall(r"\bwire\s*\.", body)) != 1:
            errors.append("GmpfBase.%s: sends %s, expected exactly key(%s)%s" % (
                name, sends or "nothing", op, ", " + val if val else ""))
        if body_of(client, name):
            errors.append("Play6Gmpf declares %s: the handshake call is the shared one" % name)
    for k, v in VALUES.items():
        m = re.search(r"\bstatic\s+final\s+int\s+%s\s*=\s*(\d+)\s*;" % k, base)
        if not m or int(m.group(1)) != v:
            errors.append("GmpfBase.%s is %s, expected %d" % (k, m.group(1) if m else "missing", v))
    m = re.search(r"\benum\s+BootStage\s*\{([^;}]*)", stage)
    got = [(n, int(v)) for n, v in re.findall(r"([A-Z][A-Z0-9_]*)\s*\(\s*(-?\d+)\s*\)", m.group(1))] if m else []
    if got != BOOT_STAGE:
        errors.append("BootStage is %s, expected %s" % (got, BOOT_STAGE))
    for op, (ref, code) in HALCODES.items():
        if not re.search(r"\.\s*put\s*\(\s*%s\s*,\s*HalCodes\.%s\s*\)" % (op, ref), codes):
            errors.append("Play6Codes.GMPF has no row %s -> HalCodes.%s" % (op, ref))
        m = re.search(r"\bstatic\s+final\s+int\s+%s\s*=\s*(\d+)\s*;" % ref, hc)
        if not m or int(m.group(1)) != code:
            errors.append("HalCodes.%s is %s, expected %d" % (ref, m.group(1) if m else "missing", code))
    raw = open(os.path.join(app, HAL, "Play6Gmpf.java"), encoding="utf-8").read()
    if not re.search(r"\bIGMPF\s*=\s*\"%s\"\s*;" % re.escape(IGMPF), raw):
        errors.append("Play6Gmpf.IGMPF is not \"%s\"" % IGMPF)
    if [b for _, b in body_of(client, "Play6Gmpf", 0)] != ["this(null);"]:
        errors.append("Play6Gmpf(): BootHandshake's constructor is not `this(null);` (no focus callback)")
    if [b for _, b in body_of(client, "connect", 0)] != ["return hidl.connect(null, 0);"]:
        errors.append("Play6Gmpf.connect(): not `return hidl.connect(null, 0);` (retry false, no death recipient)")
    if not re.search(r"\bthis\s*\(\s*new\s+HidlWire\s*\(\s*IGMPF\s*,\s*false\s*\)\s*,\s*sink\s*\)", client) \
            or not re.search(r"\bsuper\s*\(\s*hidl\s*,\s*Play6Codes\.GMPF\s*,", client):
        errors.append("Play6Gmpf: not on new HidlWire(IGMPF, false) and Play6Codes.GMPF")
    else:
        notes.append("the four handshake calls are GmpfBase's, on Play6Codes / HalCodes 391 237 395 394")
    check_added(app, client, errors, notes)


def check_added(app, client, errors, notes):
    """Play6Gmpf may not change what the handshake calls do (see the docstring)."""
    reserved = set(PLUMBING) | set(HANDSHAKE)
    for n in ("HidlWire", "HidlCaller"):
        p = os.path.join(app, HAL, n + ".java")
        if os.path.isfile(p):
            reserved |= {x for m in javascan.JavaFile(p).members for x in m.names}
    reserved.discard("connect")
    try:
        jf = javascan.JavaFile(os.path.join(app, HAL, "Play6Gmpf.java"))
    except ValueError as e:
        errors.append("Play6Gmpf.java: cannot scan: %s" % e)
        return
    bad = 0
    for m in jf.members:
        where = "Play6Gmpf.java:%d %s" % (m.line(jf.src), m.key)
        found = []
        if m.kind == "init":
            found.append("an initializer block (it runs when BootHandshake creates its Play6Gmpf or loads the class)")
        hit = sorted(set(m.names) & reserved)
        if hit:
            found.append("named like %s (overrides or hides what the handshake runs on)" % ", ".join(hit))
        if m.kind == "method" and m.names == ["finalize"]:
            found.append("finalize() (runs on the handshake's instance)")
        if m.kind == "field":
            for name, init in m.declarators:
                if init and RUNS_CODE.search(javascan.mask(init)):
                    found.append("field %s: the initializer runs code (%s); only constant expressions" % (
                        name, " ".join(init.split())))
        for f in found:
            errors.append("%s: %s" % (where, f))
        bad += bool(found)
    notes.append("Play6Gmpf.java: %d members checked against %d reserved names: %s"
                 % (len(jf.members), len(reserved), "%d refused" % bad if bad else "none overrides, hides or runs code"))


def check_golden(errors, notes):
    if not os.path.isfile(GOLDEN):
        errors.append("no wire golden %s" % os.path.relpath(GOLDEN, G))
        return
    steps = []
    block = None
    for line in open(GOLDEN, encoding="utf-8"):
        if line.startswith("C "):
            block = line.startswith("C handshake ")
            continue
        m = re.match(r"^  TX \S+::IGmpf (\d+) f0 tok:\S+ ?(.*)$", line.rstrip("\n"))
        if block and m:
            steps.append((m.group(1), m.group(2)))
        elif block and line.startswith("  TX "):
            steps.append(("other", line.strip()))
    if steps != GOLDEN_STEPS:
        errors.append("wire golden: the handshake session sends %s, expected %s" % (steps, GOLDEN_STEPS))
    else:
        notes.append("wire golden: the handshake session is 391(1) 237(0) 395(2) 237(1) 394() 307(17)")


def report(errors, notes):
    for x in notes:
        print("check_handshake: " + x)
    for x in errors:
        print("check_handshake: ERROR " + x)
    if errors:
        print("check_handshake: FAILED (%d)" % len(errors))
        return 1
    print("check_handshake: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
