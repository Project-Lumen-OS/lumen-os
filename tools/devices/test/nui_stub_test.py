#!/usr/bin/env python3
"""The native-UI fifo stub (overlay/v1/z9x_nui.sh) answers "ok" only on the models whose mask is the Projector
app's own window (Z9X, Z9X HB, Play 6) and refuses on every other model, above all the X50 family, whose vendor
expects the stock nativeui to draw the mask. Each rule must also fail on a broken copy of the script.

  python3 tools/devices/test/nui_stub_test.py
"""
import os
import re
import sys

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../.."))
NUI = os.path.join(G, "overlay/v1/z9x_nui.sh")
# the registry devices that ship the stub, plus the HB's third model code (same vendor build, XR03D)
STUB_DEVICES = ("horse_popular", "goat_elegant", "dragon_healthy")
EXTRA = {"G0118"}


def registry_models(codename):
    for line in open(os.path.join(G, "devices", codename, "device.conf"), encoding="utf-8"):
        if line.startswith("models="):
            return set(line.split("=", 1)[1].split())
    return set()


def family_models():
    src = open(os.path.join(G, "apps/core/lumen-hal/src/org/lumen/hal/GmpfModel.java"), encoding="utf-8").read()
    return set(re.findall(r'\("g0\d+", "(G\d+)"', src))


def problems(text):
    bad = []
    m = re.search(r"case \$\(getprop ro\.boot\.xgimi\.modelname\) in\n\s*([A-Z0-9|]+)\) OWN=1 ;;\n\s*\*\) OWN=0 ;;", text)
    if not m:
        return ["no model switch: OWN=1 for a list of models, OWN=0 for every other"]
    acked = set(m.group(1).split("|"))
    want = set().union(*(registry_models(c) for c in STUB_DEVICES)) | EXTRA
    if acked != want:
        bad.append("acked models %s, the stub devices are %s" % (sorted(acked), sorted(want)))
    foreign = acked & family_models()
    if foreign:
        bad.append("an X50 family model gets the ack: %s" % sorted(foreign))
    body = text[m.end():]
    ok_reply = body.find('print -nr -- "$a;$g;ok;0" >&4')
    ready = body.find("setprop sys.z9x.nui ready")
    if body.count("if [ $OWN = 1 ]; then") != 2:
        bad.append("the ready prop and the ok reply are not each behind OWN=1")
    for what, at in (("ok reply", ok_reply), ("ready prop", ready)):
        gate = body.rfind("if [ $OWN = 1 ]; then", 0, at)
        if at < 0 or gate < 0 or "else" in body[gate:at] or "fi\n" in body[gate:at]:
            bad.append("the %s is not inside the OWN=1 branch" % what)
    refusals = re.findall(r'else\n\s*print -nr -- "([^"]*)" >&4', body)
    if len(refusals) != 1:
        bad.append("no refusal reply for the other models")
    elif ";0" in refusals[0].replace("$a", "1") or "ok" in refusals[0]:
        bad.append("the refusal %r reads as a success to the vendor (';0')" % refusals[0])
    if 'setprop sys.z9x.nui refused' not in body:
        bad.append("sys.z9x.nui does not say refused on another model")
    return bad


def main():
    text = open(NUI, encoding="utf-8").read()
    fails = 0
    got = problems(text)
    for b in got:
        print("FAIL " + b)
    fails += bool(got)
    if not got:
        print("ok   z9x_nui.sh acks only the stub models " + ", ".join(sorted(set().union(*(registry_models(c) for c in STUB_DEVICES)) | EXTRA)))
    mutants = {
        "ack everywhere": ("  *) OWN=0 ;;", "  *) OWN=1 ;;"),
        "x50 acked": ("G0082|G0130", "G0082|G0111|G0130"),
        "play 6 dropped": ("|G0058)", ")"),
        "refusal says ok": ('"$a;none;refused;1"', '"$a;none;ok;0"'),
        "ready for everyone": ("  setprop sys.z9x.nui refused", "  setprop sys.z9x.nui ready"),
        "ok reply ungated": ('  if [ $OWN = 1 ]; then                        # reply first', '  if true; then  # reply first'),
    }
    for name, (old, new) in mutants.items():
        if text.count(old) != 1:
            print("FAIL mutant %s: edit not applied" % name)
            fails += 1
            continue
        if problems(text.replace(old, new)):
            print("ok   red on %s" % name)
        else:
            print("FAIL mutant %s survived" % name)
            fails += 1
    print("nui_stub_test: %s" % ("OK" if not fails else "%d failed" % fails))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
