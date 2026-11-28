#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Writes the adapter the focus test runs: the onBindViewHolder of SettingsPreferenceFragment's PreferenceGroupAdapter
(onCreateAdapter) taken word for word from the given source; the LineageOS 21 class has none (the adapter then binds
as androidx does). The rest of the class needs all of TvSettings.
Usage: focus_extract.py <SettingsPreferenceFragment.java> <out FocusAdapter.java>"""
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read()
start = src.index("        return new PreferenceGroupAdapter(preferenceScreen) {")
end = src.index("\n        };\n", start)
adapter = src[start:end + 1]
if adapter.count("public PreferenceViewHolder onCreateViewHolder(") != 1:
    sys.exit("focus_extract: onCreateAdapter does not have the adapter this test expects")
m = re.search(r"\n((?:            //[^\n]*\n)*            @Override\n            public void onBindViewHolder\(.*?\n            }\n)",
              adapter, re.S)
if adapter.count("onBindViewHolder(") != (2 if m else 0):
    sys.exit("focus_extract: an onBindViewHolder this test does not expect")
open(sys.argv[2], "w", encoding="utf-8").write(
    "package com.android.tv.settings;\n\nimport androidx.annotation.NonNull;\nimport androidx.preference.Preference;\n"
    "import androidx.preference.PreferenceGroup;\nimport androidx.preference.PreferenceGroupAdapter;\n"
    "import androidx.preference.PreferenceViewHolder;\n\n"
    "/** The adapter of SettingsPreferenceFragment.onCreateAdapter as far as binding goes (test/focus_extract.py). */\n"
    "public class FocusAdapter extends PreferenceGroupAdapter {\n"
    "    public FocusAdapter(PreferenceGroup group) {\n        super(group);\n    }\n"
    + (m.group(1).replace("\n            ", "\n    ").replace("            ", "    ", 1) if m else "") + "}\n")
