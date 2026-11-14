"""The UI click volume file: the pinned HB vendor audio_policy_engine_stream_volumes.xml with exactly the system
group's HEADSET and SPEAKER refs on MM (hb_audio.rc).
  audio_click.py VENDOR_FILE STAGED_FILE
"""
import hashlib, re, sys
VENDOR_SHA = "76581d77be90644f6d0285e069782d00f3669cb274ebda13cd35595bfd8025fb"
v, h = (open(p, "rb").read() for p in sys.argv[1:3])
if hashlib.sha256(v).hexdigest() != VENDOR_SHA:
    sys.exit("ERROR: audio: %s is not the pinned HB vendor file" % sys.argv[1])
vl, hl = v.decode().split("\n"), h.decode().split("\n")
if len(vl) != len(hl):
    sys.exit("ERROR: audio: line count %d, vendor %d" % (len(hl), len(vl)))
diff = [i for i in range(len(vl)) if vl[i] != hl[i]]
want = {"DEVICE_CATEGORY_HEADSET", "DEVICE_CATEGORY_SPEAKER"}
got = set()
for i in diff:
    m = re.fullmatch(r'(\s*<volume deviceCategory="(DEVICE_CATEGORY_[A-Z]+)" ref=")FULL_SCALE_VOLUME_CURVE("/>)', vl[i])
    if not m or hl[i] != m.group(1) + "MM" + m.group(3):
        sys.exit("ERROR: audio: line %d changed beyond the ref: %r -> %r" % (i + 1, vl[i], hl[i]))
    group = [x for x in vl[:i] if "<name>" in x][-1].strip()
    if group != "<name>system</name>":
        sys.exit("ERROR: audio: line %d is in %s, not the system group" % (i + 1, group))
    got.add(m.group(2))
if len(diff) != 2 or got != want:
    sys.exit("ERROR: audio: changed lines %s (%s), want the system group's HEADSET and SPEAKER" % ([d + 1 for d in diff], sorted(got)))
print("audio: click volume file = vendor file with system HEADSET and SPEAKER on MM (lines %s), nothing else"
      % ", ".join(str(d + 1) for d in diff))
