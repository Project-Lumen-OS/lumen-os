#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""powerhid.py MS [MS ...] -> the Android `hid` tool's script: a virtual HID remote (consumer page, Red = KEY_RED =
PROG_RED by Generic.kl, test/ui/PowerWalkActivity hands it on as the power key) registered as a real input device
(EventHub, Android's own repeats), one press held MS ms per argument, a negative MS waits."""
import json
import sys

DESC = [0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x19, 0x00, 0x2A, 0x3C, 0x02, 0x15, 0x00, 0x26, 0x3C, 0x02,
        0x95, 0x01, 0x75, 0x10, 0x81, 0x00, 0xC0]
out = [{"id": 1, "command": "register", "name": "Power walk remote", "vid": 0x18d1, "pid": 0x9001, "bus": "usb",
        "descriptor": DESC},
       {"id": 1, "command": "delay", "duration": 1500}]
for a in sys.argv[1:]:
    ms = int(a)
    if ms < 0:
        out.append({"id": 1, "command": "delay", "duration": -ms})
        continue
    out += [{"id": 1, "command": "report", "report": [0x69, 0x00]},
            {"id": 1, "command": "delay", "duration": ms},
            {"id": 1, "command": "report", "report": [0x00, 0x00]},
            {"id": 1, "command": "delay", "duration": 80}]
out.append({"id": 1, "command": "delay", "duration": 300})
print("\n".join(json.dumps(o) for o in out))
