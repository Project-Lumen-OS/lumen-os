#!/usr/bin/env python3
"""Play 6 focus rule mutants: each mutant weakens one rule of P6AfRules (the manual move start order, the close stop)
or the MotorController wiring to it; test/afrules/run.sh must turn red for every one. Works on a scratch copy, never
in place.

  python3 apps/Play6Projector/test/afrules/mutate.py          MUT_ONLY=REGEX: only the mutants whose name matches
"""
import os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
SRC = "src/org/lumen/projector/"
R = SRC + "P6AfRules.java"
M = SRC + "MotorController.java"
MUTANTS = [
    # ---- P6AfRules.startMove: motor first, the flag after it, a failed flag stops, a failed motor flags then stops
    ("flag written before the motor", R,
     "    static int startMove(BooleanSupplier motor, BooleanSupplier saveFlag, Runnable stop) {\n",
     "    static int startMove(BooleanSupplier motor, BooleanSupplier saveFlag, Runnable stop) {\n"
     "        if (!saveFlag.getAsBoolean()) { stop.run(); return MOVE_FLAG_FAILED; }\n"),
    ("flag failure keeps the move", R,
     "        if (saveFlag.getAsBoolean()) return MOVE_STARTED;\n        stop.run();\n        return MOVE_FLAG_FAILED;",
     "        saveFlag.getAsBoolean();\n        return MOVE_STARTED;"),
    ("motor failure without the flag", R,
     "            saveFlag.getAsBoolean();\n            stop.run();\n", "            stop.run();\n"),
    ("motor failure without the stop", R,
     "            saveFlag.getAsBoolean();\n            stop.run();\n", "            saveFlag.getAsBoolean();\n"),
    ("motor failure stops before the flag", R,
     "            saveFlag.getAsBoolean();\n            stop.run();\n", "            stop.run();\n            saveFlag.getAsBoolean();\n"),
    ("motor failure reported as started", R, "return MOVE_MOTOR_FAILED;", "return MOVE_STARTED;"),
    ("flag failure reported as a motor failure", R, "return MOVE_FLAG_FAILED;\n    }", "return MOVE_MOTOR_FAILED;\n    }"),
    ("flag failure saves the flag twice", R,
     "        if (saveFlag.getAsBoolean()) return MOVE_STARTED;\n",
     "        if (saveFlag.getAsBoolean() || saveFlag.getAsBoolean()) return MOVE_STARTED;\n"),
    # ---- P6AfRules.stopOnClose
    ("close ignores the persisted flag", R, "return moving || flagged;", "return moving;"),
    # ---- MotorController wiring
    ("controller writes the flag before the motor", M,
     "        // the motor command first, the flag's disk write after it",
     "        setFlag(true);\n        // the motor command first, the flag's disk write after it"),
    ("controller passes the flag as the motor step", M,
     "P6AfRules.startMove(() -> sendMove(d), () -> setFlag(true), this::sendStop)",
     "P6AfRules.startMove(() -> setFlag(true), () -> sendMove(d), this::sendStop)"),
    ("controller sends the move outside sendMove", M,
     "        // the motor command first, the flag's disk write after it",
     "        client.manualFocus(d == 1 ? ManualFocus.STOCK_LEFT : ManualFocus.STOCK_RIGHT);\n"
     "        // the motor command first, the flag's disk write after it"),
]


def main():
    only = os.environ.get("MUT_ONLY")
    tmp = tempfile.mkdtemp(prefix="p6afrules_mut.")
    try:
        root = os.path.join(tmp, "Play6Projector")
        for rel in (R, M, "test/afrules"):
            s, d = os.path.join(APP, rel), os.path.join(root, rel)
            os.makedirs(os.path.dirname(d), exist_ok=True)
            (shutil.copytree if os.path.isdir(s) else shutil.copy)(s, d)
        run = ["sh", os.path.join(root, "test/afrules/run.sh")]
        base = subprocess.run(run, capture_output=True, text=True)
        if base.returncode != 0:
            print("the unmutated copy is red already: fix test/afrules/run.sh first")
            print(base.stdout[-2000:] + base.stderr[-1000:])
            sys.exit(2)
        killed, survived = 0, []
        for name, rel, old, new in MUTANTS:
            if only and not re.search(only, name):
                continue
            p = os.path.join(root, rel)
            src = open(p, encoding="utf-8").read()
            if src.count(old) != 1:
                print("BROKEN mutant (pattern %d times): %s" % (src.count(old), name))
                survived.append(name)
                continue
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            try:
                r = subprocess.run(run, capture_output=True, text=True)
            finally:
                open(p, "w", encoding="utf-8").write(src)
            if r.returncode != 0:
                killed += 1
                why = next((l for l in r.stdout.splitlines() if l.startswith("FAIL")),
                           (r.stderr.strip().splitlines() or ["red"])[-1])
                print("killed   %-48s %s" % (name, why[:100]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        print("play6 focus rule mutants: %d killed, %d survived" % (killed, len(survived)))
        sys.exit(0 if not survived else 1)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
