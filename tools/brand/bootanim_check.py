#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""bootanim_check.py: the boot animation an image ships plays Lumen's power-on sound (read only, stdlib only).

    bootanim_check.py ZIP [--base BASE_ZIP]

The AOSP 14 player (libbootanimation, audioplay.cpp) maps Stored entries only and plays part0/audio.wav when part0
starts (tools/brand/gen_bootanim.py writes the zip so). ZIP passes when every entry is Stored, desc.txt comes first and
the one sound is part0/audio.wav, RIFF PCM 16 bit 48 kHz stereo of 2.0 to 3.5 s. With --base (the zip of the image's
base, the Z9X 1.0.9 member for the X50 family): every other entry is the base's, same names, order and bytes, so only
the sound was added (gen_bootanim.py --add-audio). The 1.0.9 member has no sound: an image shipping it boots silent
from cold, which is refused here. verify_tree_x50.py imports problems(); the X50 family and RS30 recipes run it on the
staged input. Exit 0 = OK, 1 = refused, 2 = usage.
"""
import struct
import sys
import zipfile

sys.dont_write_bytecode = True
AUDIO_ENTRY = "part0/audio.wav"
AUDIO_SECONDS = (2.0, 3.5)


def wav_info(data):
    """The fmt of a RIFF WAVE the way audioplay.cpp parseClipBuf walks it (chunks until "data") plus its length in
    seconds (the same reader as gen_bootanim.py, without its numpy imports). ValueError when the player refuses it."""
    if len(data) < 12 or data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("not a riff/wave file")
    pos, fmt = 12, None
    while pos + 8 <= len(data):
        cid, size = data[pos:pos + 4], struct.unpack_from("<I", data, pos + 4)[0]
        pos += 8
        if cid == b"fmt ":
            if size < 16 or pos + 16 > len(data):
                raise ValueError("short fmt chunk")
            fmt = struct.unpack_from("<HHIIHH", data, pos)
        elif cid == b"data":
            if fmt is None:
                raise ValueError("no fmt chunk before data")
            tag, ch, rate, _, align, bits = fmt
            if size > len(data) - pos:
                raise ValueError("data chunk runs past the file")
            if not align or not rate:
                raise ValueError("fmt has a zero block align or rate")
            return dict(tag=tag, channels=ch, rate=rate, bits=bits, seconds=size / float(align * rate))
        pos += size + (size & 1)
    raise ValueError("no data chunk")


def problems(path, base=None):
    """([refusals], summary): the refusals of ZIP (and against BASE when given), empty when it passes."""
    try:
        z = zipfile.ZipFile(path)
    except (OSError, zipfile.BadZipFile) as e:
        return ["%s: not a zip (%s)" % (path, e)], ""
    errs, summary = [], ""
    with z:
        infos = z.infolist()
        names = [i.filename for i in infos]
        if not names or names[0] != "desc.txt":
            errs.append("desc.txt is not the first entry")
        packed = [i.filename for i in infos if i.compress_type != zipfile.ZIP_STORED]
        if packed:
            errs.append("entries not Stored (libbootanimation maps Stored entries only): %s" % ", ".join(packed[:3]))
        sounds = [n for n in names if n.lower().endswith(".wav")]
        if sounds != [AUDIO_ENTRY]:
            errs.append("sound entries %s, expected exactly %s (without it the cold boot is silent)" % (sounds, AUDIO_ENTRY))
        else:
            try:
                w = wav_info(z.read(AUDIO_ENTRY))
            except ValueError as e:
                errs.append("%s: %s" % (AUDIO_ENTRY, e))
            else:
                if (w["tag"], w["bits"], w["rate"], w["channels"]) != (1, 16, 48000, 2):
                    errs.append("%s is not PCM 16 bit 48 kHz stereo: %s" % (AUDIO_ENTRY, w))
                if not AUDIO_SECONDS[0] <= w["seconds"] <= AUDIO_SECONDS[1]:
                    errs.append("%s lasts %.2f s, allowed %.1f to %.1f s" % ((AUDIO_ENTRY, w["seconds"]) + AUDIO_SECONDS))
                summary = "%s PCM %d bit %d Hz %d ch %.2f s" % (AUDIO_ENTRY, w["bits"], w["rate"], w["channels"], w["seconds"])
        if base is not None:
            try:
                with zipfile.ZipFile(base) as b:
                    want = [(i.filename, b.read(i.filename)) for i in b.infolist() if i.filename != AUDIO_ENTRY]
            except (OSError, zipfile.BadZipFile) as e:
                return errs + ["base %s: not a zip (%s)" % (base, e)], summary
            have = [(n, z.read(n)) for n in names if n != AUDIO_ENTRY]
            if [n for n, _ in have] != [n for n, _ in want]:
                errs.append("the entries besides the sound are not the base's (names or order): %d here, %d in the base"
                            % (len(have), len(want)))
            else:
                diff = [n for (n, x), (_, y) in zip(have, want) if x != y]
                if diff:
                    errs.append("entries differ from the base: %s" % ", ".join(diff[:3]))
                else:
                    summary += ", the other %d entries are the base's" % len(have)
        summary = "%d entries Stored, %s" % (len(names), summary)
    return errs, summary


def main(argv):
    if len(argv) not in (1, 3) or (len(argv) == 3 and argv[1] != "--base"):
        print("usage: bootanim_check.py ZIP [--base BASE_ZIP]")
        return 2
    errs, summary = problems(argv[0], argv[2] if len(argv) == 3 else None)
    for e in errs:
        print("bootanim_check: ERROR %s: %s" % (argv[0], e))
    if errs:
        return 1
    print("bootanim_check: OK %s: %s" % (argv[0], summary))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
