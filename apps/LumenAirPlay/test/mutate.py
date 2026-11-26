#!/usr/bin/env python3
"""Targeted mutants of the casting screen's rules (src/org/lumen/airplay/cast): each one must make test/run.sh's
CastTest fail. python3 -u test/mutate.py ; exit 0 = every mutant killed. Runs on a builder (JAVA_HOME or javac in PATH)."""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAST = "src/org/lumen/airplay/cast/"
TABLE = os.path.join(HERE, "assets", "cast", "models.tsv")
MUTANTS = [
    ("airplay off ignored", "CastRules.java", "if (!enabled) return CardState.OFF;\n        if (ERROR", "if (ERROR"),
    ("airplay error read as starting", "CastRules.java", "if (ERROR.equals(server)) return CardState.ERROR;", ""),
    ("airplay session ignored", "CastRules.java", "if (session) return CardState.CONNECTED;", ""),
    ("airplay never not visible", "CastRules.java", "return notVisible ? CardState.NOT_VISIBLE : CardState.READY;",
     "return CardState.READY;"),
    ("airplay ready while starting", "CastRules.java", "if (!RUNNING.equals(server)) return CardState.STARTING;", ""),
    ("sink absent shown", "CastRules.java", "if (!present) return CardState.ABSENT;", ""),
    ("sink cause never shown", "CastRules.java", "return waiting && detail != null && !detail.isEmpty() ? detail : how;", "return how;"),
    ("sink cause hides the how text when ready", "CastRules.java",
     "boolean waiting = st == CardState.STARTING || st == CardState.ERROR;", "boolean waiting = true;"),
    ("sink silence read as ready", "CastRules.java", "if (!answered) return CardState.ERROR;",
     "if (!answered) return CardState.READY;"),
    ("sink mirroring ignored", "CastRules.java", "if (mirroring || \"IN_USE\".equals(code)) return CardState.CONNECTED;",
     "if (\"IN_USE\".equals(code)) return CardState.CONNECTED;"),
    ("sink switch ignored", "CastRules.java", "if (!enabled || \"OFF\".equals(code)) return CardState.OFF;",
     "if (\"OFF\".equals(code)) return CardState.OFF;"),
    ("sink unsupported shown", "CastRules.java", "if (\"UNSUPPORTED\".equals(code)) return CardState.ABSENT;", ""),
    ("sink no wifi ignored", "CastRules.java", "if (\"NO_WIFI\".equals(code)) return CardState.NO_WIFI;", ""),
    ("dlna absent shown", "CastRules.java", "if (!installed) return CardState.ABSENT;", ""),
    ("dlna disabled app ready", "CastRules.java", "if (!enabled) return CardState.OFF;\n        if (!network)", "if (!network)"),
    ("dlna network ignored", "CastRules.java", "if (!network) return CardState.NO_WIFI;", ""),
    ("dlna silence read as ready", "CastRules.java", "return answered ? CardState.READY : CardState.OFF;",
     "return CardState.READY;"),
    ("dlna answer ignored", "CastRules.java", "return answered ? CardState.READY : CardState.OFF;",
     "return CardState.OFF;"),
    ("probe takes any status", "DlnaProbe.java", 'answer.startsWith("200 ", sp + 1)', "true"),
    ("probe takes any device", "DlnaProbe.java", "&& answer.contains(RENDERER)", ""),
    ("probe takes any protocol", "DlnaProbe.java", '|| !answer.startsWith("HTTP/1.")', ""),
    ("probe name kept escaped", "DlnaProbe.java", '.replace("&amp;", "&")', ""),
    ("probe blank name taken", "DlnaProbe.java", "return n.isEmpty() ? null : n;", "return n;"),
    ("probe on another port", "DlnaProbe.java", "PORT = 49595;", "PORT = 49596;"),
    ("sink card always", "CastRules.java", "if (miracast != CardState.ABSENT) out.add(Receiver.MIRACAST);",
     "out.add(Receiver.MIRACAST);"),
    ("dlna card dropped", "CastRules.java", "if (dlna != CardState.ABSENT) out.add(Receiver.DLNA);", ""),
    ("dlna card always", "CastRules.java", "if (dlna != CardState.ABSENT) out.add(Receiver.DLNA);", "out.add(Receiver.DLNA);"),
    ("sink headline first", "CastRules.java", "if (airplay == CardState.CONNECTED) return Receiver.AIRPLAY;", ""),
    ("connected not good", "CastRules.java", "return s == CardState.READY || s == CardState.CONNECTED;",
     "return s == CardState.READY;"),
    ("mirroring rows always", "CastRules.java", "return miracast != CardState.ABSENT;", "return true;"),
    ("all off ignores the sink", "CastRules.java",
     "return airplay == CardState.OFF && (miracast == CardState.OFF || miracast == CardState.ABSENT);",
     "return airplay == CardState.OFF;"),
    ("all off while airplay runs", "CastRules.java",
     "return airplay == CardState.OFF && (miracast", "return airplay != CardState.READY && (miracast"),
    ("unknown model gets 4K", "CastModels.java", "UNKNOWN = new Model(\"\", \"\", \"\", false);",
     "UNKNOWN = new Model(\"\", \"\", \"\", true);"),
    ("4K flag read wrong", "CastModels.java", "m.put(f[0], new Model(f[0], f[1], f[2], \"1\".equals(f[3])));",
     "m.put(f[0], new Model(f[0], f[1], f[2], !\"0\".equals(f[2])));"),
    ("short lines taken", "CastModels.java", "if (f.length != 4 || ", "if (f.length < 3 || "),
    ("model code not trimmed", "CastModels.java", "rows.get(bootModel.trim())", "rows.get(bootModel)"),
]


def javac():
    jh = os.environ.get("JAVA_HOME")
    return (os.path.join(jh, "bin", "javac"), os.path.join(jh, "bin", "java")) if jh else ("javac", "java")


def run(tree):
    jc, j = javac()
    out = os.path.join(tree, "out")
    os.makedirs(out, exist_ok=True)
    srcs = [os.path.join(tree, "src", f) for f in os.listdir(os.path.join(tree, "src"))] + [os.path.join(tree, "CastTest.java")]
    if subprocess.run([jc, "-encoding", "UTF-8", "-proc:none", "-d", out] + srcs, capture_output=True).returncode:
        return "compile"
    r = subprocess.run([j, "-cp", out, "org.lumen.airplay.cast.CastTest", TABLE, shipping()], capture_output=True, text=True)
    return "pass" if r.returncode == 0 else "fail"


def shipping():
    """The shipping model codes, as test/run.sh passes them: every model of a device with an image recipe."""
    g = os.path.normpath(os.path.join(HERE, "..", ".."))
    sys.path.insert(0, os.path.join(g, "tools", "devices"))
    import devices
    out = []
    for c in sorted(os.listdir(os.path.join(g, "devices"))):
        if os.path.isfile(os.path.join(g, "devices", c, "build.conf")):
            m = devices.get(g, c).get("models", "")
            out += m.split() if isinstance(m, str) else list(m)
    return " ".join(sorted(set(out)))


def tree():
    t = tempfile.mkdtemp()
    shutil.copytree(os.path.join(HERE, CAST), os.path.join(t, "src"))
    shutil.copy(os.path.join(HERE, "test/org/lumen/airplay/cast/CastTest.java"), os.path.join(t, "CastTest.java"))
    return t


def main():
    t = tree()
    base = run(t)
    shutil.rmtree(t)
    if base != "pass":
        print("mutate: the unmutated rules do not pass (%s)" % base)
        return 1
    killed = survived = broken = 0
    for name, f, old, new in MUTANTS:
        t = tree()
        p = os.path.join(t, "src", f)
        s = open(p, encoding="utf-8").read()
        if s.count(old) != 1:
            print("BROKEN %s: anchor found %d times" % (name, s.count(old)))
            broken += 1
            shutil.rmtree(t)
            continue
        open(p, "w", encoding="utf-8").write(s.replace(old, new))
        res = run(t)
        shutil.rmtree(t)
        if res == "pass":
            survived += 1
            print("SURVIVED " + name)
        else:
            killed += 1
    print("mutate: %d killed, %d survived, %d broken of %d" % (killed, survived, broken, len(MUTANTS)))
    return 0 if survived == 0 and broken == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
