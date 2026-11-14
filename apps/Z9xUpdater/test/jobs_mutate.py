#!/usr/bin/env python3
"""jobs_mutate.py: each mutant of UpdateService's worker (JobRunner, ServiceJob) must make JobRunnerTest fail.

  python3 apps/Z9xUpdater/test/jobs_mutate.py

Works on a temporary copy; the tree is never changed. Exit 0 = every mutant killed and the unmutated copy green.
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "org", "lumen", "updater")
TEST = os.path.join(HERE, "org", "lumen", "updater", "JobRunnerTest.java")
FILES = ("ServiceJob.java", "JobRunner.java")

MUTANTS = [
    ("any job ends the service (the incident)", "JobRunner.java",
     "return t == null || t == Thread.currentThread() || t.getState() == Thread.State.TERMINATED;", "return true;"),
    ("a second press is never dropped", "JobRunner.java",
     "return busy() && !stop && requested.repeats(job);", "return false;"),
    ("an ending job still counts as running", "JobRunner.java",
     "return busy() && !stop && requested.repeats(job);", "return busy() && requested.repeats(job);"),
    ("the replaced job is never asked to stop", "JobRunner.java",
     "        if (prev != null && prev.isAlive()) stop = true;\n", ""),
    ("the new job keeps the stop of the old one", "JobRunner.java", "            stop = false;\n", ""),
    ("a crash is swallowed", "JobRunner.java", "crash.accept(e);", "{ }"),
    ("busy also after the end", "JobRunner.java",
     "return t != null && t.getState() != Thread.State.TERMINATED;", "return t != null;"),
    ("adding the install to a download counts as a repeat", "ServiceJob.java",
     "return !(running == DOWNLOAD && this != DOWNLOAD);", "return true;"),
    ("OTHER repeats", "ServiceJob.java", "if (this == OTHER || running == null || running == OTHER) return false;",
     "if (running == null) return false;"),
]


def run(tree):
    out = os.path.join(tree, "out")
    os.makedirs(out, exist_ok=True)
    srcs = [os.path.join(tree, f) for f in FILES] + [TEST]
    c = subprocess.run(["javac", "-nowarn", "-proc:none", "-d", out] + srcs, capture_output=True, text=True)
    if c.returncode != 0:
        return "compile"
    r = subprocess.run(["java", "-cp", out, "org.lumen.updater.JobRunnerTest"], capture_output=True, text=True,
                       timeout=120)
    return "green" if r.returncode == 0 else "red"


def main():
    killed = 0
    with tempfile.TemporaryDirectory() as base:
        clean = os.path.join(base, "clean")
        os.makedirs(clean)
        for f in FILES:
            shutil.copy(os.path.join(SRC, f), clean)
        if run(clean) != "green":
            print("FAIL unmutated copy is not green")
            return 1
        for i, (name, f, old, new) in enumerate(MUTANTS):
            tree = os.path.join(base, "m%d" % i)
            shutil.copytree(clean, tree, ignore=shutil.ignore_patterns("out"))
            path = os.path.join(tree, f)
            text = open(path, encoding="utf-8").read()
            if text.count(old) != 1:
                print("BROKEN anchor: %s" % name)
                return 1
            open(path, "w", encoding="utf-8").write(text.replace(old, new))
            res = run(tree)
            ok = res in ("red", "compile")
            killed += ok
            print("%s %s (%s)" % ("killed" if ok else "ALIVE ", name, res))
    print("jobs mutants: %d/%d killed" % (killed, len(MUTANTS)))
    return 0 if killed == len(MUTANTS) else 1


if __name__ == "__main__":
    sys.exit(main())
