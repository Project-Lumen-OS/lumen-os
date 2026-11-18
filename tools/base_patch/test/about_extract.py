#!/usr/bin/env python3
"""Writes the class the About rows test runs: AboutFragment with only its Lumen part, the constants and the nested
class LumenRows taken word for word from tvsettings_about/AboutFragment.java (the rest of the page needs all of
TvSettings). Usage: about_extract.py <AboutFragment.java> <out.java>"""
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read()
start = src.index("    static final class LumenRows {")
end = src.index("\n    }\n", start) + len("\n    }\n")
body = src[start:end]
consts = re.findall(r"^    (?:private )?static final String (?:TAG|LUMEN_ABOUT|LUMEN_ROWS) = .*$", src, re.M)
if len(consts) != 3 or body.count("static final class") != 1:
    sys.exit("about_extract: AboutFragment.java does not have the Lumen part this test expects")
imports = [i for i in re.findall(r"^import [a-zA-Z.]+;$", src, re.M)
           if re.search(r"\b" + re.escape(i.rsplit(".", 1)[1][:-1]) + r"\b", body)]
open(sys.argv[2], "w", encoding="utf-8").write(
    "package com.android.tv.settings.about;\n\n" + "\n".join(imports) + "\n\n"
    "/** The Lumen part of tools/base_patch/tvsettings_about/AboutFragment.java (test/about_extract.py). */\n"
    "public class AboutFragment {\n" + "\n".join(consts) + "\n\n" + body + "}\n")
