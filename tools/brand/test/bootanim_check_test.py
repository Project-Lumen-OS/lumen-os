#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""bootanim_check_test.py: tools/brand/bootanim_check.py and the recipes that ship the boot animation (stdlib only).

    python3 -B tools/brand/test/bootanim_check_test.py

The checker passes a zip of Stored entries with desc.txt first and one part0/audio.wav (PCM 16 bit 48 kHz stereo,
2.0 to 3.5 s) and refuses a deflated entry, no sound, a mono or 44.1 kHz or too long sound, a second sound, desc.txt
not first, and (with a base) changed or missing frames. The recipe guard: every descriptor on the shared X50 family
steps (G0111, G0100, TITAN H0061 to H0063) and the RS30 lists Lumen Setup (Z9xSetup) in apps, ships
overlay/v1/bootanimation.zip and runs the checker, and the X50 steps place both over the base's 1.0.9 copies.
"""
import os
import re
import struct
import sys
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # tools/brand, the tools under test
G = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True
import bootanim_check as bc  # noqa: E402


def wav(seconds=2.5, rate=48000, ch=2, bits=16):
    align = ch * bits // 8
    data = b"\0" * int(seconds * rate) * align
    fmt = struct.pack("<HHIIHH", 1, ch, rate, rate * align, align, bits)
    body = b"WAVE" + b"fmt " + struct.pack("<I", 16) + fmt + b"data" + struct.pack("<I", len(data)) + data
    return b"RIFF" + struct.pack("<I", len(body)) + body


FRAMES = [("desc.txt", b"152 248 60\nc 1 0 part0 #0A0908\nf 0 0 part1 24 #0A0908\n"),
          ("part0/00000.png", b"\x89PNG\r\n\x1a\nA"), ("part0/00001.png", b"\x89PNG\r\n\x1a\nB"),
          ("part1/00000.png", b"\x89PNG\r\n\x1a\nC")]


def make(path, entries, deflate=()):
    with zipfile.ZipFile(path, "w") as z:
        for n, d in entries:
            zi = zipfile.ZipInfo(n, date_time=(2008, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED if n in deflate else zipfile.ZIP_STORED
            z.writestr(zi, d)
    return path


def with_sound(sound=None, frames=FRAMES):
    return frames[:3] + [(bc.AUDIO_ENTRY, wav() if sound is None else sound)] + frames[3:]


class Checker(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp(prefix="bootanim-")
        self.base = make(os.path.join(self.d, "base.zip"), FRAMES)

    def z(self, name, entries, deflate=()):
        return make(os.path.join(self.d, name), entries, deflate)

    def refused(self, path, text, base=None):
        errs, _ = bc.problems(path, base)
        self.assertTrue(any(text in e for e in errs), "%r not in %r" % (text, errs))

    def test_good_zip_passes_with_and_without_base(self):
        p = self.z("good.zip", with_sound())
        self.assertEqual(bc.problems(p)[0], [])
        errs, summary = bc.problems(p, self.base)
        self.assertEqual(errs, [])
        self.assertIn("the other 4 entries are the base's", summary)
        self.assertEqual(bc.main([p, "--base", self.base]), 0)

    def test_base_without_sound_is_refused(self):
        self.refused(self.base, "without it the cold boot is silent")
        self.assertEqual(bc.main([self.base]), 1)

    def test_deflated_entries_are_refused(self):
        self.refused(self.z("deflated.zip", with_sound(), deflate=(bc.AUDIO_ENTRY,)), "not Stored")
        self.refused(self.z("deflated2.zip", with_sound(), deflate=("part0/00001.png",)), "part0/00001.png")

    def test_bad_sounds_are_refused(self):
        self.refused(self.z("mono.zip", with_sound(wav(ch=1))), "not PCM 16 bit 48 kHz stereo")
        self.refused(self.z("441.zip", with_sound(wav(rate=44100))), "not PCM 16 bit 48 kHz stereo")
        self.refused(self.z("long.zip", with_sound(wav(seconds=4.0))), "lasts 4.00 s")
        self.refused(self.z("junk.zip", with_sound(b"not a wave file")), "not a riff/wave file")
        self.refused(self.z("two.zip", with_sound() + [("part1/audio.wav", wav())]), "expected exactly part0/audio.wav")
        self.refused(self.z("moved.zip", FRAMES + [("part1/audio.wav", wav())]), "expected exactly part0/audio.wav")

    def test_desc_first(self):
        e = with_sound()
        self.refused(self.z("order.zip", e[1:2] + e[:1] + e[2:]), "desc.txt is not the first entry")

    def test_frames_must_be_the_base(self):
        changed = [(n, d + b"x" if n == "part1/00000.png" else d) for n, d in FRAMES]
        self.refused(self.z("changed.zip", with_sound(frames=changed)), "entries differ from the base: part1/00000.png",
                     self.base)
        self.refused(self.z("fewer.zip", with_sound(frames=FRAMES[:3])), "names or order", self.base)

    def test_not_a_zip(self):
        p = os.path.join(self.d, "x.zip")
        open(p, "wb").write(b"no zip")
        self.refused(p, "not a zip")
        self.assertEqual(bc.main([]), 2)


def conf(codename):
    sys.path.insert(0, os.path.join(G, "tools", "devices"))
    import layered
    out = dict(layered.effective(os.path.join(G, "devices", codename, "build.conf")))
    return out


class Recipes(unittest.TestCase):
    X50_STEPS = "devices/g0111/image/steps.tsv"

    def family(self):
        out = []
        for c in sorted(os.listdir(os.path.join(G, "devices"))):
            if os.path.isfile(os.path.join(G, "devices", c, "build.conf")) and conf(c).get("steps") == self.X50_STEPS:
                out.append(c)
        return out

    def test_family_is_found(self):
        self.assertTrue({"g0111", "g0100", "h0061", "h0062", "h0063"} <= set(self.family()), self.family())

    def test_every_model_ships_setup_and_the_sounding_animation(self):
        for c in self.family() + ["g0155", "g0170"]:
            d = conf(c)
            self.assertIn("Z9xSetup", d["apps"].split(), "%s: Lumen Setup (Z9xSetup) not in apps=" % c)
            self.assertIn("overlay/v1/bootanimation.zip", d["overlays"].split(), "%s: no bootanimation.zip" % c)
            self.assertIn("tools/brand/bootanim_check.py", d["tools"].split(), "%s: no bootanim_check.py" % c)

    def test_x50_steps_replace_the_base_copies(self):
        rows = [ln.rstrip("\n").split("\t") for ln in open(os.path.join(G, self.X50_STEPS), encoding="utf-8")
                if ln.strip() and not ln.startswith("#")]
        ops = {tuple(r[1:4]) for r in rows if len(r) >= 4}
        self.assertIn(("place", "stage/Z9xSetup.apk", "{sys}/priv-app/Z9xSetup/Z9xSetup.apk"), ops)
        self.assertIn(("place", "bootanimation.zip", "{sys}/product/media/bootanimation.zip"), ops)
        self.assertTrue(any(r[1:3] == ["cleanup", "{sys}/priv-app/Z9xSetup/oat"] for r in rows), "Setup oat not removed")
        self.assertTrue(any(r[1:4] == ["run", "bootanim_check.py", "{in}/bootanimation.zip"] for r in rows))
        need = {x for r in rows if r[1] == "need" for x in r[2:]}
        self.assertTrue({"stage/Z9xSetup.apk", "bootanimation.zip", "bootanim_check.py"} <= need)
        info = [r for r in rows if r[1] == "info" and r[2] == "STAGE_INFO.txt"][0]
        self.assertTrue(any(re.match(r"setup=system/priv-app/Z9xSetup/Z9xSetup\.apk @sha\(stage/Z9xSetup\.apk\)$", x)
                            for x in info), "STAGE_INFO has no setup row")
        self.assertTrue(any(x.startswith("bootanim=system/product/media/bootanimation.zip @sha(bootanimation.zip)")
                            for x in info), "STAGE_INFO has no bootanim row")


if __name__ == "__main__":
    unittest.main(verbosity=1)
