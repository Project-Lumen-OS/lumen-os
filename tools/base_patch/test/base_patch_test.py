#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""base_patch.py host tests without Android tools: the zip swap keeps every other entry byte for byte, the pins
refuse a foreign base or result, compare() passes a dexlib2 relayout and stops on a real change, the fixes of one
file apply in their order, link() stops on a reference that does not resolve, the About and focus fixes only add to
LineageOS."""
import difflib
import hashlib
import os
import struct
import sys
import tempfile
import zipfile

T = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(T))
import base_patch as bp  # noqa: E402

FAILS = []


def check(name, ok):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        FAILS.append(name)


def raises(fn):
    try:
        fn()
    except bp.Stop:
        return True
    return False


def raw_entries(path):
    """{name: (compress_type, raw compressed bytes)} read straight from the file."""
    out = {}
    with zipfile.ZipFile(path) as z, open(path, "rb") as f:
        for i in z.infolist():
            f.seek(i.header_offset + 26)
            n, m = int.from_bytes(f.read(2), "little"), int.from_bytes(f.read(2), "little")
            f.seek(n + m, 1)
            out[i.filename] = (i.compress_type, f.read(i.compress_size))
    return out


def make_apk(path, dex):
    with zipfile.ZipFile(path, "w") as z:
        z.writestr(zipfile.ZipInfo("AndroidManifest.xml", (2008, 1, 1, 0, 0, 0)), b"<manifest/>" * 50,
                   zipfile.ZIP_DEFLATED)
        z.writestr(zipfile.ZipInfo("classes.dex", (2008, 1, 1, 0, 0, 0)), dex, zipfile.ZIP_STORED)
        z.writestr(zipfile.ZipInfo("resources.arsc", (2008, 1, 1, 0, 0, 0)), b"\x02\x00" * 300, zipfile.ZIP_STORED)
        z.writestr(zipfile.ZipInfo("res/a.xml", (2008, 1, 1, 0, 0, 0)), b"<a/>" * 99, zipfile.ZIP_DEFLATED)


def main():
    w = tempfile.mkdtemp(prefix="base-patch-test-")
    base_dex, new_dex = b"dex\n035\0" + b"A" * 4000, b"dex\n035\0" + b"B" * 4100
    apk = os.path.join(w, "X.apk")
    make_apk(apk, base_dex)

    # 1. swap_entry: classes.dex replaced and still stored, everything else the same raw bytes in the same order
    out = os.path.join(w, "out.apk")
    bp.swap_entry(apk, out, "classes.dex", new_dex)
    a, b = raw_entries(apk), raw_entries(out)
    with zipfile.ZipFile(apk) as za, zipfile.ZipFile(out) as zb:
        check("swap keeps the entry order", za.namelist() == zb.namelist())
        check("swap writes the new dex", zb.read("classes.dex") == new_dex)
        check("swap passes the zip CRC test", zb.testzip() is None)
    check("swap keeps classes.dex stored", b["classes.dex"][0] == zipfile.ZIP_STORED)
    check("swap copies the other entries raw", all(a[n] == b[n] for n in a if n != "classes.dex"))

    # 2. patched_apk with a fake fix row
    fix_dir = os.path.join(w, "dex")
    os.makedirs(os.path.join(fix_dir, "fakefix"))
    open(os.path.join(fix_dir, "fakefix", "classes.dex"), "wb").write(new_dex)
    row = {"path": "system/app/X/X.apk", "id": "fakefix", "cls": "La/B;",
           "base": hashlib.sha256(base_dex).hexdigest(), "out": hashlib.sha256(new_dex).hexdigest()}
    real_rows = bp.rows
    bp.rows = lambda: [dict(row)]
    try:
        p = bp.patched_apk("system/app/X/X.apk", apk, w, fix_dir)
        with zipfile.ZipFile(p) as z:
            check("patched_apk puts the pinned dex in", z.read("classes.dex") == new_dex)
        check("patched_apk leaves other files alone", bp.patched_apk("system/app/Y/Y.apk", apk, w, fix_dir) is None)
        check("patched_apk matches the image path, not the fix id", bp.patched_apk("fakefix", apk, w, fix_dir) is None)
        other = os.path.join(w, "other.apk")
        make_apk(other, base_dex + b"x")
        check("patched_apk refuses another base dex", raises(lambda: bp.patched_apk("system/app/X/X.apk", other, w,
                                                                                    fix_dir)))
        open(os.path.join(fix_dir, "fakefix", "classes.dex"), "wb").write(new_dex + b"x")
        check("patched_apk refuses a dex off its pin", raises(lambda: bp.patched_apk("system/app/X/X.apk", apk, w,
                                                                                     fix_dir)))
        bp.rows = lambda: [dict(row, out=bp.PENDING)]
        check("patched_apk refuses an unpinned fix", raises(lambda: bp.patched_apk("system/app/X/X.apk", apk, w,
                                                                                   fix_dir)))
    finally:
        bp.rows = real_rows

    # 3. compare(): dexdump texts of a base and a relaid result
    head = "Class #%d            -\n  Class descriptor  : '%s'\n"
    meth = ("      registers     : 3\n      ins           : 1\n      outs          : %d\n"
            "      insns size    : %d 16-bit code units\n"
            "%06x:                                        |[%06x] a.b:()V\n"
            "%06x: 7010 %04x 0000                         |%04x: invoke-direct {v0}, La/C;.<init>:()V // method@%04x\n"
            "%06x: 3800 0400                              |%04x: if-eqz v0, %04x // +0004\n"
            "      positions     : \n        0x%04x line=12\n")

    def cls(n, name, shift, outs=1, op="invoke-direct"):
        return (head % (n, name)) + (meth % (outs, 4 + shift, 0x1000 + shift, 0x1000 + shift, 0x1010, 0x55 + shift,
                                             0, 0x55 + shift, 0x1020, 2 + shift, 6 + shift, shift)).replace(
            "invoke-direct", op)

    nested = "Lcom/x/Conn$1;"
    base_txt = cls(0, "La/A;", 0) + cls(1, "Lcom/x/Conn;", 0) + cls(2, nested, 0)
    good_txt = cls(0, nested, 7) + cls(1, "Lcom/x/Conn;", 3) + cls(2, "La/A;", 1, outs=4)
    bad_txt = cls(0, "Lcom/x/Conn;", 0) + cls(1, nested, 0) + cls(2, "La/A;", 0, op="invoke-static")
    texts = {}
    real_run = bp.run
    bp.run = lambda cmd, **kw: texts[cmd[-1]]
    try:
        texts.update({"base": base_txt, "good": good_txt, "bad": bad_txt, "lost": cls(0, "Lcom/x/Conn;", 0)})
        check("compare passes offsets, outs and a replaced nested class",
              bp.compare("dexdump", "base", "good", "Lcom/x/Conn;") == (1, ["Lcom/x/Conn$1;", "Lcom/x/Conn;"]))
        check("compare stops on a changed opcode", raises(lambda: bp.compare("dexdump", "base", "bad", "Lcom/x/Conn;")))
        check("compare stops on a lost class", raises(lambda: bp.compare("dexdump", "base", "lost", "Lcom/x/Conn;")))
    finally:
        bp.run = real_run

    # 4. the table: the TvSettings fixes, both pins written, the About fix on the result of the A2DP one
    r = bp.row("tvsettings_a2dp")
    check("patches.tsv pins the TvSettings fix", r is not None and len(r["base"]) == 64 and len(r["out"]) == 64)
    tvs = bp.chain(r["path"]) if r else []
    check("TvSettings: the A2DP fix, the About fix on its result, the focus fix on that",
          [x["id"] for x in tvs] == ["tvsettings_a2dp", "tvsettings_about", "tvsettings_focus"]
          and tvs[1]["base"] == tvs[0]["out"] and tvs[2]["base"] == tvs[1]["out"]
          and all(len(x["out"]) == 64 for x in tvs))

    # 5. a chain of two fixes on one file: each on the result of the one before
    mid_dex, end_dex = b"dex\n035\0" + b"M" * 4050, b"dex\n035\0" + b"E" * 4200
    h = lambda b: hashlib.sha256(b).hexdigest()  # noqa: E731
    for fid, data in (("one", mid_dex), ("two", end_dex)):
        os.makedirs(os.path.join(fix_dir, fid), exist_ok=True)
        open(os.path.join(fix_dir, fid, "classes.dex"), "wb").write(data)
    one = {"path": "system/app/X/X.apk", "id": "one", "cls": "La/B;", "base": h(base_dex), "out": h(mid_dex)}
    two = {"path": "system/app/X/X.apk", "id": "two", "cls": "La/C;", "base": h(mid_dex), "out": h(end_dex)}
    bp.rows = lambda: [dict(one), {"path": "system/app/Y/Y.apk", "id": "y", "cls": "", "base": "", "out": ""}, dict(two)]
    built = []
    real_build_dex = bp.build_dex
    bp.build_dex = lambda r, dex, out_dir=None: built.append((r["id"], h(dex)))
    try:
        p = bp.patched_apk("system/app/X/X.apk", apk, w, fix_dir)
        with zipfile.ZipFile(p) as z:
            check("chain: the file gets the result of the last fix", z.read("classes.dex") == end_dex)
        check("chain: the copy is named after the last fix", os.path.basename(p) == "two-X.apk")
        bp.build("two", apk, fix_dir)
        check("chain: build of the second fix starts from the first one's result", built == [("two", h(mid_dex))])
        bp.rows = lambda: [dict(one), dict(two, base=h(base_dex))]
        check("chain: a second fix pinned on the base, not on the first result, is refused",
              raises(lambda: bp.patched_apk("system/app/X/X.apk", apk, w, fix_dir)))
        bp.rows = lambda: [dict(two), dict(one)]
        check("chain: the fixes apply in the order of the table",
              raises(lambda: bp.patched_apk("system/app/X/X.apk", apk, w, fix_dir)))
    finally:
        bp.rows = real_rows
        bp.build_dex = real_build_dex

    # 6. link(): every member the new classes use is in the dex or the platform, static where the opcode says so
    def klass(name, sup, members, code=(), ifs=()):
        t = "Class #0            -\n  Class descriptor  : '%s'\n  Access flags      : 0x0001 (PUBLIC)\n" % name
        t += "  Superclass        : '%s'\n  Interfaces        -\n" % sup
        t += "".join("    #%d              : '%s'\n" % (i, x) for i, x in enumerate(ifs))
        t += "  Static fields     -\n  Instance fields   -\n  Direct methods    -\n"
        for i, (n, ty, acc) in enumerate(members):
            t += "    #%d              : (in %s)\n      name          : '%s'\n      type          : '%s'\n" % (i, name, n, ty)
            t += "      access        : 0x0001 (%s)\n" % acc
        t += "".join("%06x: 0000                                   |%04x: %s\n" % (0x100 + i, i, c)
                     for i, c in enumerate(code))
        return t

    use = ["invoke-virtual {v0, v1}, Landroidx/preference/Preference;.setKey:(Ljava/lang/String;)V // method@0010",
           "invoke-virtual {v0}, Lcom/x/Screen;.getOrder:()I // method@0011",
           "invoke-static {v0}, Landroid/text/TextUtils;.isEmpty:(Ljava/lang/CharSequence;)Z // method@0012",
           "sget-object v0, Lcom/x/Fix;.TAG:Ljava/lang/String; // field@0003",
           "invoke-virtual {v0}, [Ljava/lang/Object;.clone:()Ljava/lang/Object; // method@0013",
           "invoke-interface {v0}, Lcom/x/Rows;.size:()I // method@0014"]
    pref = klass("Landroidx/preference/Preference;", "Ljava/lang/Object;",
                 [("setKey", "(Ljava/lang/String;)V", "PUBLIC"), ("getOrder", "()I", "PUBLIC")])
    screen = klass("Lcom/x/Screen;", "Landroidx/preference/Preference;", [])
    rows_if = klass("Lcom/x/Rows;", "Ljava/lang/Object;", [], ifs=("Ljava/util/List;",))
    fix = lambda code: klass("Lcom/x/Fix;", "Ljava/lang/Object;", [("TAG", "Ljava/lang/String;", "PRIVATE STATIC FINAL")],
                             code)  # noqa: E731
    # the platform: a jar of class files as javac writes them (the parser), then lookups of it
    def class_bytes(this, sup, ifs, fields, methods):
        cp, idx = [], {}

        def utf(x):
            if ("u", x) not in idx:
                b = x.encode()
                cp.append(b"\x01" + struct.pack(">H", len(b)) + b)
                idx[("u", x)] = len(cp)
            return idx[("u", x)]

        def klass_ref(x):
            if ("c", x) not in idx:
                u = utf(x)
                cp.append(b"\x07" + struct.pack(">H", u))
                idx[("c", x)] = len(cp)
            return idx[("c", x)]
        this_i, sup_i = klass_ref(this), klass_ref(sup) if sup else 0
        cp.append(b"\x05" + struct.pack(">q", 1 << 40))  # a long: two slots
        cp.append(b"")
        cp.append(b"\x03" + struct.pack(">i", 7))
        ifs_i = [klass_ref(x) for x in ifs]
        code = utf("Code")
        mems = []
        for group in (fields, methods):
            body = struct.pack(">H", len(group))
            for n, d, acc in group:
                body += struct.pack(">HHHH", acc, utf(n), utf(d), 1) + struct.pack(">HI", code, 3) + b"\x00\x01\x02"
            mems.append(body)
        out = b"\xca\xfe\xba\xbe" + struct.pack(">HHH", 0, 61, len(cp) + 1) + b"".join(cp)
        out += struct.pack(">HHHH", 0x21, this_i, sup_i, len(ifs_i)) + b"".join(struct.pack(">H", x) for x in ifs_i)
        return out + mems[0] + mems[1] + struct.pack(">H", 0)

    jar = os.path.join(w, "platform.jar")
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr("java/lang/Object.class", class_bytes("java/lang/Object", None, [], [], [
            ("toString", "()Ljava/lang/String;", 0x1), ("<init>", "()V", 0x1)]))
        z.writestr("android/text/TextUtils.class", class_bytes("android/text/TextUtils", "java/lang/Object", [], [
            ("SAFE_STRING_FLAG_TRIM", "I", 0x19)], [("isEmpty", "(Ljava/lang/CharSequence;)Z", 0x9)]))
        z.writestr("java/util/Collection.class", class_bytes("java/util/Collection", "java/lang/Object",
                                                             ["java/lang/Iterable"], [], [("size", "()I", 0x401)]))
        z.writestr("java/util/List.class", class_bytes("java/util/List", "java/lang/Object", ["java/util/Collection"],
                                                       [], []))
    platform = bp.platform_classes([jar])
    check("class files: superclass, interfaces, static and instance members",
          platform("Landroid/text/TextUtils;") == ("Ljava/lang/Object;", [], {
              ("SAFE_STRING_FLAG_TRIM", "I"): True, ("isEmpty", "(Ljava/lang/CharSequence;)Z"): True})
          and platform("Ljava/util/List;") == ("Ljava/lang/Object;", ["Ljava/util/Collection;"], {})
          and platform("Ljava/lang/Object;")[0] is None and platform("Lcom/none/X;") is None)

    texts.clear()
    bp.run = lambda cmd, **kw: texts[cmd[-1]]
    try:
        texts["ok"] = pref + screen + rows_if + fix(use + [
            "invoke-virtual {v0}, Lcom/x/Screen;.toString:()Ljava/lang/String; // method@0018"])
        check("link: dex members, inherited ones, the platform and array clone resolve",
              bp.link("dexdump", "ok", "Lcom/x/Fix;", platform) == len(use) + 1)
        texts["gone"] = pref + screen + rows_if + fix(use + [
            "invoke-virtual {v0}, Landroidx/preference/Preference;.setIconSpaceReserved:(Z)V // method@0015"])
        check("link: a method the base library does not have stops the build",
              raises(lambda: bp.link("dexdump", "gone", "Lcom/x/Fix;", platform)))
        texts["plat"] = pref + screen + rows_if + fix(use + [
            "invoke-static {v0}, Landroid/text/TextUtils;.isBlank:(Ljava/lang/CharSequence;)Z // method@0019"])
        check("link: a platform method the headers do not have stops the build",
              raises(lambda: bp.link("dexdump", "plat", "Lcom/x/Fix;", platform)))
        texts["missing"] = pref + rows_if + fix(use)
        check("link: a class neither in the dex nor the platform stops the build",
              raises(lambda: bp.link("dexdump", "missing", "Lcom/x/Fix;", platform)))
        texts["static"] = pref + screen + rows_if + fix(use + [
            "invoke-static {v0}, Landroidx/preference/Preference;.getOrder:()I // method@0016"])
        check("link: an instance method called as static stops the build",
              raises(lambda: bp.link("dexdump", "static", "Lcom/x/Fix;", platform)))
        texts["pstatic"] = pref + screen + rows_if + fix(use + [
            "invoke-virtual {v0}, Landroid/text/TextUtils;.isEmpty:(Ljava/lang/CharSequence;)Z // method@001a"])
        check("link: a static platform method called on an instance stops the build",
              raises(lambda: bp.link("dexdump", "pstatic", "Lcom/x/Fix;", platform)))
        texts["other"] = pref + screen + rows_if + fix(use) + klass("Lcom/y/Other;", "Ljava/lang/Object;", [], [
            "invoke-virtual {v0}, Lcom/y/Gone;.x:()V // method@0017"])
        check("link: only the classes of the fix are checked",
              bp.link("dexdump", "other", "Lcom/x/Fix;", platform) == len(use))
    finally:
        bp.run = real_run

    # 7. the About fix is the LineageOS 21 AboutFragment plus the Lumen part, nothing of it changed or dropped
    lineage = open(os.path.join(T, "lineage21_about", "AboutFragment.java"), encoding="utf-8").read().splitlines()
    fixed = open(os.path.join(os.path.dirname(T), "tvsettings_about", "AboutFragment.java"),
                 encoding="utf-8").read().splitlines()
    gone = [ln for ln in difflib.unified_diff(lineage, fixed, lineterm="", n=0)
            if ln.startswith("-") and not ln.startswith("---")]
    check("About fix: every LineageOS line kept", gone == [])
    body = "\n".join(fixed)
    start = body.index("public void onCreatePreferences(")
    end = body.index("\n    }\n", start)
    call = body.find("LumenRows.insert(screen, findPreference(KEY_DEVICE_MODEL),", start)
    check("About fix: onCreatePreferences puts the rows after Model, after everything else it does",
          start < body.index("updateTutorials();", start) < call < end)

    # 8. the focus fix is the LineageOS 21 SettingsPreferenceFragment plus one method of its adapter, nothing else
    lineage = open(os.path.join(T, "lineage21_focus", "SettingsPreferenceFragment.java"), encoding="utf-8").read()
    fixed = open(os.path.join(os.path.dirname(T), "tvsettings_focus", "SettingsPreferenceFragment.java"),
                 encoding="utf-8").read()
    diff = list(difflib.unified_diff(lineage.splitlines(), fixed.splitlines(), lineterm="", n=0))
    gone = [ln for ln in diff if ln.startswith("-") and not ln.startswith("---")]
    added = [ln[1:] for ln in diff if ln.startswith("+") and not ln.startswith("+++")]
    check("focus fix: every LineageOS line kept", gone == [])
    body = "\n".join(ln for ln in added if not ln.lstrip().startswith("//"))
    check("focus fix: only onBindViewHolder added", body.count("public void onBindViewHolder(") == 1
          and body.count("public ") == 1 and "new " not in body)
    a0 = fixed.index("        return new PreferenceGroupAdapter(preferenceScreen) {")
    a1 = fixed.index("\n        };\n", a0)
    m = fixed.index("            public void onBindViewHolder(")
    check("focus fix: the method sits in the adapter of onCreateAdapter", a0 < m < a1)
    print("base_patch_test: %d failed" % len(FAILS))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
