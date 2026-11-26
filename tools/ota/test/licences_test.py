#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Host test of tools/ota/licences.py: python3 tools/ota/test/licences_test.py (exit 0 green)."""
import os
import shutil
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import licences as L  # noqa: E402

GSI = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
fails = []


def ok(what, cond):
    print(("ok   " if cond else "FAIL ") + what)
    if not cond:
        fails.append(what)


own_java = b"package org.lumen.x;\nclass A {}\n"
ok("own java without header is a problem", L.problems("apps/core/x/src/A.java", own_java))
ok("fix puts the header on line 1", L.fixed("apps/core/x/src/A.java", own_java).startswith(
    b"// SPDX-License-Identifier: " + L.OWN.encode() + b"\npackage"))
ok("fixed own java is clean", not L.problems("apps/core/x/src/A.java", L.fixed("apps/core/x/src/A.java", own_java)))
sh = b"#!/bin/sh\necho hi\n"
ok("shell header goes after the shebang", L.fixed("tools/x.sh", sh).split(b"\n")[:2] == [
    b"#!/bin/sh", b"# SPDX-License-Identifier: " + L.OWN.encode()])
apache = b"# SPDX-License-" + b"Identifier: Apache-2.0\nx=1\n"   # split: this file is own code too
ok("a full stop after the id is not part of it", L.problems("tools/x.json",
   b'{"_comment": "SPDX-License-' + b'Identifier: ' + L.OWN.encode() + b'. Lumen"}') == [])
ok("an own Apache header is rewritten in place", L.fixed("tools/x.py", apache) ==
   b"# SPDX-License-Identifier: " + L.OWN.encode() + b"\nx=1\n")
prose = b"/*\n * Copyright (C) 2026 Lumen OS. Licensed under the " + b"Apache License, Version 2.0.\n */\npackage a;\n"
ok("an own Apache sentence is a problem", any("Apache" in p for p in L.problems("apps/LumenTvInput/src/A.java", prose)))
ok("fix drops the Apache sentence", b"Apache" not in L.fixed("apps/LumenTvInput/src/A.java", prose))
aosp = b"/*\n * Copyright (C) 2019 The Android Open Source Project\n */\npackage com.android.x;\n"
ok("an AOSP file is third-party by content", L.component("apps/LumenX/src/B.java", aosp) == "aosp")
ok("fix never touches an AOSP file", L.fixed("apps/LumenX/src/B.java", aosp) == aosp)
ok("an AOSP file with the Lumen licence is a problem",
   L.problems("apps/LumenX/src/B.java", b"// SPDX-License-Identifier: " + L.OWN.encode() + b"\n" + aosp))
ok("keyboard upstream part is AOSP", L.component("apps/LumenKeyboard/src/com/android/inputmethod/leanback/K.java") == "aosp")
ok("keyboard own part is own", L.component("apps/LumenKeyboard/src/org/lumen/keyboard/K.java", b"") == "own")
ok("Material Symbols icon", L.component("apps/core/lumen-icons/res/drawable/lumen_ic_wifi.xml") == "material")
ok("Lucide icon", L.component("apps/core/lumen-report/res/drawable/lumen_report_ic_check.xml") == "lucide")
ok("AirPlay stays GPL", L.component("apps/LumenAirPlay/src/org/lumen/airplay/A.java") == "airplay")
ok("the PC installer is own", L.component("installer/lumen-install.sh", b"") == "own")
ok("the installer profiles test is own", L.component("tools/ota/test/installer_profiles.sh", b"") == "own")
shield = b"# SPDX-License-" + b"Identifier: LicenseRef-PolyForm-" + b"Shield-1.0.0\nx=1\n"
ok("an own header of another PolyForm licence is rewritten", L.fixed("tools/x.py", shield) ==
   b"# SPDX-License-Identifier: " + L.OWN.encode() + b"\nx=1\n")
snip = (b"#!/usr/bin/env bash\n# SPDX-License-Identifier: " + L.OWN.encode() + b"\na=1\n# SPDX-" + b"SnippetBegin\n"
        b"# SPDX-" + b"License-Identifier: Apache-2.0\nf() { :; }\n# SPDX-" + b"SnippetEnd\n")
ok("an own file with its marked Apache snippet is clean", L.problems("installer/lumen-install.sh", snip) == [])
ok("fix leaves the snippet as it is", L.fixed("installer/lumen-install.sh", snip) == snip)
ok("the snippet may not go", L.problems("installer/lumen-install.sh", snip.split(b"# SPDX-" + b"SnippetBegin")[0]))
ok("the snippet may not change its licence", L.problems("installer/lumen-install.sh",
                                                       snip.replace(b"Apache-2.0", L.OWN.encode())))
ok("an Apache id outside the snippet is a problem", L.problems("installer/lumen-install.sh",
                                                               snip.replace(L.OWN.encode(), b"Apache-2.0", 1)))
ok("own research script is own", L.component("devices/goat_elegant/research/gen/regen.sh", b"") == "own")
ok("licences.py itself is own", L.component("tools/ota/licences.py", open(L.__file__, "rb").read()) == "own")
who = L.HOLDER.encode()
ok("the licensor's name is allowed in NOTICE", who not in L.without_licensor("NOTICE", who))
ok("and nowhere else", L.without_licensor("docs/x.md", who) == who)
for c in L.CREDITED:
    ok("a credited contributor's name is allowed in NOTICE", c not in L.without_licensor("NOTICE", c))
    ok("and nowhere else", L.without_licensor("installer/lumen-install.sh", c) == c)

d = tempfile.mkdtemp()
if not os.path.isdir(os.path.join(GSI, "docs/repo/licences")):   # the public repository: no templates
    print("skip root files (docs/repo/licences is in the source tree only)")
try:
    if not os.path.isdir(os.path.join(GSI, "docs/repo/licences")):
        raise SystemExit(1 if fails else 0)
    for name in ("README.md", "README.ru.md"):
        with open(os.path.join(d, name), "w", encoding="utf-8") as f:
            f.write(f"# x\n\n## Licence\n\n{L.BEGIN}\nOLDTEXT\n{L.END}\n\nend\n")
    os.makedirs(os.path.join(d, "src"))
    with open(os.path.join(d, "src", "A.java"), "wb") as f:
        f.write(L.fixed("src/A.java", own_java))
    L.write_root(d, "lumen-os", GSI)
    ok("root files written and clean", L.check(d) == [])
    lic = open(os.path.join(d, "LICENSE"), encoding="utf-8").read()
    ok("LICENSE is the text OWN names", lic.startswith("# " + L.LICENCES[L.OWN]["title"]))
    notice = open(os.path.join(d, "NOTICE"), encoding="utf-8").read().splitlines()
    ok("NOTICE has the Required Notice", L.REQUIRED_NOTICE in notice)
    ok("NOTICE credits the contributions", all(any(c.decode() in x for x in notice) for c in L.CREDITED))
    ok("NOTICE has no template field left", not any("{" in x for x in notice))
    ok("NOTICE keeps the earlier Apache rights in one sentence",
       sum("keep the rights that licence gave" in x for x in notice) == 1)
    ok("README block filled", "OLDTEXT" not in open(os.path.join(d, "README.md"), encoding="utf-8").read())
    for other in L.LICENCES:   # the switch: every prepared licence renders
        L.OWN, keep = other, L.OWN
        L.write_root(d, "lumen-installer", GSI)
        ok(f"{other}: LICENSE and README render",
           open(os.path.join(d, "LICENSE"), encoding="utf-8").read().startswith("# " + L.LICENCES[other]["title"]))
        L.OWN = keep
finally:
    shutil.rmtree(d)
print(f"licences_test: {len(fails)} failed")
sys.exit(1 if fails else 0)
