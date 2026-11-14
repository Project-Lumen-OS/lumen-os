#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""The installers' message catalog (installer/lib/messages.tsv) and the calls of its ids, read from the sources.

lumen-install.sh (m ID value...) and lumen-install.ps1 (M ID value...) show the user only texts of the catalog:
  - the catalog: the columns id, en, ru, ar, be, de, es, fr, it, ja, kk, ko, pl, pt, tr, uk, zh-CN, zh-TW; one row per
    id (lower case, digits, '_'), ID.win right after ID; every text with only the escapes \\n, \\t and \\\\; English in
    every row; each translation has exactly the values {1}, {2}, ... of its English; no dash, no semicolon and none of the
    internal words of BANNED (the house style of every public text), and no value of another projector's profile
    (lib/devices.conf is the one place for those);
  - the calls: every id a script calls is a row (the ps1 reads ID.win where there is one), each call gives exactly
    the values its text has ({1} to {n}, n values), every row is called by a script, no t "en" "ru" / T "en" "ru" is
    left and both scripts load the catalog (msg_load / Msg-Load) before the first text;
  - the parity: both scripts call the same ids, except the ids each one words differently today (SH_ONLY, PS_ONLY
    below: the texts the ps1 port already worded in its own way when the texts moved into the catalog). A new text
    called by one script only turns this red until it is called by the other or listed here with a reason.
    python3 tools/devices/test/installer_msgs.py [--mutants]
--mutants: the checks must catch each of these changes, made in memory one at a time: every id row removed (a text
the user would see as [id]), a value taken out of a translation, a call renamed to an unknown id, a call given one
value less, a dash in a text. Exit status 0 only when every check passes (and, with --mutants, every mutant fails).
"""
import hashlib
import os
import re
import sys
from pathlib import Path

G = Path(os.path.abspath(__file__)).parents[3]
INST = G / "installer"
LANGS = ["en", "ru", "ar", "be", "de", "es", "fr", "it", "ja", "kk", "ko", "pl", "pt", "tr", "uk", "zh-CN", "zh-TW"]
SPECIAL = {"answer_yes"}   # read by ask / Ask as a word list, not shown as a text
# words that never go into a public text, as the first 16 hex digits of the sha256 of the lower-case word
BANNED = {"67eb23d1aa53ac05", "33120debdf8cd9e4", "0a5021d58d1e5829"}

# ids one script calls and the other does not: each is a text the two scripts word in their own way (the ps1 port
# keeps its own lines for Windows, or one script has a step the other does not)
SH_ONLY = set("""
adb_push_zip_failed_run add_image_built_here_sha256 cannot_copy_erofs cannot_unpack_install_unzip
codec_audio_files_z9x_blobs done_projector_choose_language_wi done_projector_choose_language_wi_2
googles_platform_tools_run_macos if_projector_not_come_back image_carries_codec_files_itself
installer_certificate_certs_ota installer_has_no_erofs_utils lumen_os_runs_clean_data
lumen_os_update_still_being macos_helper_file_delete_copy manifest_read_back_add_image names_no_checked_add_image
no_sound_protected_video_until no_usb_connection_started_system not_enough_free_space_computer
one_manual_step_vbmeta_yourself openssl_needed_check_image projector_did_not_appear_cable
projector_not_match_allow_list projector_service_lamp_fans public_image_codec_placeholder_run
read_back_add_image_differs ro_z9x_version_missing running_slot_still_verifies_system tools_erofs_utils_erofs_missing
vbmeta_verification_off_slot_no which_stick_bash_lumen_install wipe_failed_projector_starts_lumen
xgimi_lumen_update_still_being zip_not_hold_expected_files
""".split())
PS_ONLY = set("""
adb_push_zip_failed add_image_built_here_sha256_2 another_programs_adb_running_x cannot_unpack_nothing_done
could_not_created_3 could_not_written_removed_again_3 done_finish_setup_projector done_finish_setup_projector_google
if_projector_not_come_back_2 installer_has_no_erofs_utils_2 lumen_os_runs_clean_data_2
lumen_os_update_still_being_2 macos_helper_file_delete n1_settings_network_internet_join n1_usb_port_only_fat32
n2_pull_power_adapter_wait n2_settings_device_preferences n3_note_what_see_xgimi n3_settings_device_preferences
n4_find_cause_we_need n5_back_stock_erases_everything names_no_checked_add_image_2 no_sha256sums_sig_installer
no_usb_connection_started_system_2 not_enough_free_space_computer_2 not_enough_free_space_super_4
now_projector_settings_device_3 one_manual_step_vbmeta_yourself_2 projector_did_not_appear_cable_5
projector_not_match_allow_list_4 projector_service_running read_back_add_image_differs_2
register_https_www_google_com_2 tools_erofs_utils_missing_damaged update_still_being_finished_let
usb_stick_card_external_network when_projector_asks_allow_usb which_stick_lumen_install_cmd
windows_sees_usb_device_without wipe_failed_settings_device_reset z9x_gapps_google_services_added_2
zip_not_hold_expected_files_2
""".split())


# ------------------------------------------------------------------ reading the values of a call
def sh_close(s, i, close):  # s[i] just after an opening $( or ${: index of the matching close char
    depth, n = 1, len(s)
    while i < n:
        c = s[i]
        if c == "\\":
            i += 2
            continue
        if c == "'" and close == ")":
            i = s.index("'", i + 1) + 1
            continue
        if c == '"':
            i = sh_dq_end(s, i + 1)
            continue
        if s.startswith("$(", i) or s.startswith("${", i):
            i = sh_close(s, i + 2, ")" if s[i + 1] == "(" else "}") + 1
            continue
        if c == ("(" if close == ")" else "{"):
            depth += 1
        elif c == close:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError("unbalanced")


def sh_dq_end(s, i):  # s[i] just after '"': index after the closing '"'
    while True:
        c = s[i]
        if c == "\\":
            i += 2
        elif c == '"':
            return i + 1
        elif s.startswith("$(", i) or s.startswith("${", i):
            i = sh_close(s, i + 2, ")" if s[i + 1] == "(" else "}") + 1
        else:
            i += 1


def sh_values(s, i):  # the shell words after 'm ID' at s[i]: their number
    n = 0
    while True:
        while i < len(s) and s[i] in " \t":
            i += 1
        if i >= len(s) or s[i] in ";\n)|&>":
            return n
        while i < len(s) and s[i] not in " \t;\n)|&>":
            c = s[i]
            if c == "'":
                i = s.index("'", i + 1) + 1
            elif c == '"':
                i = sh_dq_end(s, i + 1)
            elif s.startswith("$(", i) or s.startswith("${", i):
                i = sh_close(s, i + 2, ")" if s[i + 1] == "(" else "}") + 1
            elif c == "\\":
                i += 2
            else:
                i += 1
        n += 1


def ps_close(s, i):  # s[i] just after '(': index of the matching ')'
    depth = 1
    while True:
        c = s[i]
        if c == "`":
            i += 2
            continue
        if c == "'":
            i += 1
            while not (s[i] == "'" and s[i + 1:i + 2] != "'"):
                i += 2 if s[i:i + 2] == "''" else 1
            i += 1
            continue
        if c == '"':
            i = ps_dq_end(s, i + 1)
            continue
        if c in "({":
            depth += 1
        elif c in ")}":
            depth -= 1
            if depth == 0:
                return i
        i += 1


def ps_dq_end(s, i):
    while True:
        c = s[i]
        if c == "`":
            i += 2
        elif c == '"':
            if s[i + 1:i + 2] == '"':
                i += 2
            else:
                return i + 1
        elif s.startswith("$(", i):
            i = ps_close(s, i + 2) + 1
        else:
            i += 1


def ps_values(s, i):
    n = 0
    while True:
        while i < len(s) and s[i] in " \t":
            i += 1
        if i >= len(s) or s[i] in ";\n)}|":
            return n
        c = s[i]
        if c == '"':
            i = ps_dq_end(s, i + 1)
        elif c == "'":
            i += 1
            while not (s[i] == "'" and s[i + 1:i + 2] != "'"):
                i += 2 if s[i:i + 2] == "''" else 1
            i += 1
        elif c == "$" and s.startswith("$(", i):
            i = ps_close(s, i + 2) + 1
        else:
            while i < len(s) and s[i] not in " \t;\n)}|":
                i += 1
        n += 1


SH_PRE = ("$(", "(", ";", "&&", "||", "{", "|", ")", " then", " else", " do")


def sh_calls(src):  # [(id, values, line)] of every 'm ID ...' in command position
    out = []
    for mm in re.finditer(r"(?<![\w$./=-])m ([a-z0-9_]+)\b", src):
        ls = src.rfind("\n", 0, mm.start()) + 1
        pre = src[ls:mm.start()].rstrip(" \t")
        if pre.lstrip().startswith("#") or not (pre == "" or pre.endswith(SH_PRE) or pre in ("then", "else", "do")):
            continue
        out.append((mm.group(1), sh_values(src, mm.end()), src.count("\n", 0, mm.start()) + 1))
    return out


def ps_calls(src):
    out = []
    for mm in re.finditer(r"(?<![\w$-])M ([a-z0-9_]+)\b", src):
        ls = src.rfind("\n", 0, mm.start()) + 1
        if src[ls:mm.start()].lstrip().startswith("#"):
            continue
        out.append((mm.group(1), ps_values(src, mm.end()), src.count("\n", 0, mm.start()) + 1))
    return out


# ------------------------------------------------------------------ the checks
def parse(tsv):  # -> (rows [(id, [cells])], problems)
    bad, rows, head = [], [], None
    for k, line in enumerate(tsv.split("\n"), 1):
        if line == "" or line.startswith("#"):
            continue
        f = line.split("\t")
        if head is None:
            head = f
            if f != ["id"] + LANGS:
                bad.append("header: %s, want %s" % (f, ["id"] + LANGS))
            continue
        if len(f) != len(LANGS) + 1:
            bad.append("line %d (%s): %d columns, want %d" % (k, f[0], len(f), len(LANGS) + 1))
            continue
        rows.append((f[0], f[1:]))
    return rows, bad


def phs(t):
    return set(re.findall(r"\{([1-9][0-9]?)\}", t))


def conf_values():  # the model, board, fastboot product, firmware and restore md5 of every projector but the Z9X
    conf = (INST / "lib" / "devices.conf").read_text(encoding="utf-8").splitlines()
    out = []
    for l in conf:
        mm = re.match(r"^([a-z0-9]+)\.(MODEL|BOARD|FB_PRODUCT|VENDOR_OK|RESTORE_MD5)=(.*)$", l)
        if mm and mm.group(1) != "z9x":
            for v in re.split(r"[ :]", mm.group(3)):
                if v and (mm.group(2) != "RESTORE_MD5" or re.fullmatch(r"[0-9a-f]{32}", v)):
                    out.append((v, mm.group(1)))
    return out


def check(tsv, sh, ps, shc=None, psc=None):
    rows, bad = parse(tsv)
    ids, base = {}, {}
    prev = None
    for i, cells in rows:
        if not re.fullmatch(r"[a-z0-9_]+(\.win)?", i):
            bad.append("id %r: lower case letters, digits and '_' only (and .win)" % i)
        if i in ids:
            bad.append("id %s twice" % i)
        ids[i] = cells
        if i.endswith(".win") and prev != i[:-4]:
            bad.append("%s does not follow its row %s" % (i, i[:-4]))
        prev = i
        if not cells[0]:
            bad.append("%s: no English" % i)
        en = phs(cells[0])
        if en and en != {str(k) for k in range(1, len(en) + 1)}:
            bad.append("%s: the values are not {1} to {%d}: %s" % (i, len(en), sorted(en)))
        for lang, c in zip(LANGS, cells):
            if "\\" in re.sub(r"\\[nt\\]", "", c) or "\r" in c:
                bad.append("%s/%s: an escape other than \\n, \\t, \\\\" % (i, lang))
            if c and phs(c) != en:
                bad.append("%s/%s: values %s, the English has %s" % (i, lang, sorted(phs(c)), sorted(en)))
            if re.search("[\u2014\u2013;\uff1b\u061b]| - ", c):
                bad.append("%s/%s: a dash or a semicolon (plain sentences: a full stop or a comma)" % (i, lang))
            for w in set(re.findall(r"[a-z_]+", c.lower())):
                if hashlib.sha256(w.encode()).hexdigest()[:16] in BANNED:
                    bad.append("%s/%s: the word '%s' never goes into a public text" % (i, lang, w))
    shc = sh_calls(sh) if shc is None else shc
    psc = ps_calls(ps) if psc is None else psc
    for who, calls, win in (("lumen-install.sh", shc, False), ("lumen-install.ps1", psc, True)):
        for i, n, line in calls:
            if i not in ids or i.endswith(".win"):
                bad.append("%s:%d calls %s, which the catalog does not have" % (who, line, i))
                continue
            row = ids.get(i + ".win", ids[i]) if win else ids[i]
            want = len(phs(row[0]))
            if i not in SPECIAL and n != want:
                bad.append("%s:%d: %s with %d values, its text has %d" % (who, line, i, n, want))
    used_sh = {i for i, _, _ in shc}
    used_ps = {i for i, _, _ in psc}
    for i in ids:
        b = i[:-4] if i.endswith(".win") else i
        if i.endswith(".win") and b not in used_ps:
            bad.append("%s: the ps1 does not call %s" % (i, b))
        elif not i.endswith(".win") and b not in used_sh | used_ps:
            bad.append("%s: called by neither script" % i)
    for i in sorted(used_sh - used_ps - SH_ONLY):
        bad.append("%s: called by lumen-install.sh only (call it in the ps1 too, or list it in SH_ONLY)" % i)
    for i in sorted(used_ps - used_sh - PS_ONLY):
        bad.append("%s: called by lumen-install.ps1 only (call it in lumen-install.sh too, or list it in PS_ONLY)" % i)
    for i in sorted((SH_ONLY - (used_sh - used_ps)) | (PS_ONLY - (used_ps - used_sh))):
        bad.append("%s: listed as called by one script only, but it is not (take it out of the list)" % i)
    for who, src, pat in (("lumen-install.sh", sh, r"(?<![\w$./=-])t [\"']"), ("lumen-install.ps1", ps, r"(?<![\w$-])T [\"'@]")):
        left = [src.count("\n", 0, x.start()) + 1 for x in re.finditer(pat, src)
                if not src[src.rfind("\n", 0, x.start()) + 1:x.start()].lstrip().startswith("#")]
        if left:
            bad.append("%s still has inline en/ru texts at lines %s" % (who, left[:10]))
    helptext = sh.split("\nset -u\n", 1)[0].split("\n", 1)[-1]
    if re.search("[\u2014\u2013;]", helptext):
        bad.append("lumen-install.sh: its --help header has a dash or a semicolon")
    for who, src, pat in (("lumen-install.sh", sh, r"; \$\(m "), ("lumen-install.ps1", ps, r";\s*\"\s*\+\s*\(M ")):
        for x in re.finditer(pat, src):
            bad.append("%s:%d joins two texts with a semicolon" % (who, src.count("\n", 0, x.start()) + 1))
    for v, who in conf_values():   # the other projectors' values live only in lib/devices.conf
        if v.lower() in tsv.lower():
            bad.append("messages.tsv names '%s' (%s): take it from lib/devices.conf" % (v, who))
    if not re.search(r"^MSG_FILE=\$HERE/lib/messages\.tsv", sh, re.M) or not re.search(r"^msg_load$", sh, re.M):
        bad.append("lumen-install.sh does not load lib/messages.tsv at the start")
    if '$MsgFile = Join-Path (Join-Path $Here "lib") "messages.tsv"' not in ps or not re.search(r"^Msg-Load$", ps, re.M):
        bad.append("lumen-install.ps1 does not load lib\\messages.tsv at the start")
    return bad, len(rows), len(shc), len(psc)


# gawk (the awk of Fedora, Arch and of Debian or Ubuntu once gawk is installed) stops with a fatal error on a
# variable named like one of its built-in functions, so the joined lists of a message come out empty there
GAWK_BUILTINS = set("and or xor compl lshift rshift length index split sub gsub gensub substr match sprintf tolower toupper "
                    "int log exp sin cos sqrt atan2 rand srand system close fflush systime strftime mktime asort asorti "
                    "patsplit isarray typeof strtonum".split())


def awk_names(sh):  # every -v NAME= of an awk call, up to its program's opening quote
    return ["lumen-install.sh:%d: awk -v %s= is a gawk built-in name" % (sh.count("\n", 0, m.start()) + 1, v)
            for m in re.finditer(r"\bawk\b[^'\n]*", sh) for v in re.findall(r"-v ([A-Za-z_]\w*)=", m.group(0))
            if v in GAWK_BUILTINS]


def main(argv):
    tsv = (INST / "lib" / "messages.tsv").read_text(encoding="utf-8")
    sh = (INST / "lumen-install.sh").read_text(encoding="utf-8")
    ps = (INST / "lumen-install.ps1").read_text(encoding="utf-8-sig")
    shc, psc = sh_calls(sh), ps_calls(ps)
    bad, nrows, nsh, nps = check(tsv, sh, ps, shc, psc)
    bad += awk_names(sh)
    for b in bad:
        print("FAIL " + b)
    rows, _ = parse(tsv)
    filled = {l: sum(1 for _, c in rows if c[k]) for k, l in enumerate(LANGS)}
    if not bad:
        print("ok   messages.tsv: %d rows, %d calls in lumen-install.sh and %d in lumen-install.ps1 agree; translated rows: %s"
              % (nrows, nsh, nps, ", ".join("%s %d" % (l, n) for l, n in filled.items())))
    if "--mutants" in argv and not bad:
        lines = tsv.split("\n")
        rowline = [k for k, l in enumerate(lines) if l and not l.startswith("#") and not l.startswith("id\t")]
        killed = total = 0
        survived = []

        def run(name, t=tsv, s=sh, p=ps, sc=shc, pc=psc):
            nonlocal killed, total
            total += 1
            if check(t, s, p, sc, pc)[0]:
                killed += 1
            else:
                survived.append(name)
        for k in rowline:   # every row removed: a .win row only from a text the parity test reads
            i = lines[k].split("\t", 1)[0]
            if i.endswith(".win"):
                continue
            run("remove " + i, t="\n".join(lines[:k] + lines[k + 1:]))
        for k in rowline:   # a value out of the Russian text
            f = lines[k].split("\t")
            if phs(f[2]):
                f2 = list(f)
                f2[2] = f2[2].replace("{%s}" % sorted(phs(f2[2]))[0], "")
                run("value out of %s/ru" % f[0], t="\n".join(lines[:k] + ["\t".join(f2)] + lines[k + 1:]))
        for n, (i, v, line) in enumerate(shc[::7]):   # a call renamed, a call with one value less
            run("rename sh:%d" % line, sc=[(x if (x, y, z) != (i, v, line) else x + "_x", y, z) for x, y, z in shc])
            if v:
                run("value less sh:%d" % line, sc=[(x, y - 1 if (x, y, z) == (i, v, line) else y, z) for x, y, z in shc])
        for n, (i, v, line) in enumerate(psc[::7]):
            run("rename ps:%d" % line, pc=[(x if (x, y, z) != (i, v, line) else x + "_x", y, z) for x, y, z in psc])
        k = rowline[len(rowline) // 2]
        run("dash", t="\n".join(lines[:k] + [lines[k].replace("\t", "\t\u2014 ", 1)] + lines[k + 1:]))
        for mark in ("\u2013 ", "; ", "\uff1b", " - "):
            run("mark %r" % mark, t="\n".join(lines[:k] + [lines[k].replace("\t", "\t" + mark, 2)] + lines[k + 1:]))
        run("help semicolon", s=sh.replace("\n# ", "\n# a; ", 1))
        run("sh join", s=sh.replace(" $(m ", "; $(m ", 1))
        run("ps1 join", p=ps.replace(" + (M ", "; \" + (M ", 1))
        for b in survived[:20]:
            print("FAIL mutant survived: " + b)
        print("%s   mutants: %d of %d killed" % ("ok" if killed == total else "FAIL", killed, total))
        if killed != total:
            bad.append("mutants")
    print("installer messages: %s" % ("OK" if not bad else "%d failed" % len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
