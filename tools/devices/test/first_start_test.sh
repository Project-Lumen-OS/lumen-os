#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# first_start_test.sh: the one first-start setting of every image (devices/_image/first_start_prop.txt, owner
# 2026-11-13: English, the time and time zone from the network, no fixed zone) and its tools: the repository lint (no
# descriptor, recipe, prop file, init file or overlay sets a language or time zone of its own), the build.py rule (every
# variant of every descriptor checks its image), first_start.py apply and the image check (Android 14 property order
# with a hostile vendor, init files, overlays) on scratch trees and a tar, the X50 prop check and the Z9X props, and
# Lumen Setup: its step order (the remote first, the language next, as the stock XGIMI wizard pairs the remote right
# after its welcome animation and has no language page) and the language of its first screen (FirstLocale, on the
# host with javac: the builders have it).
#   bash tools/devices/test/first_start_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/first_start.XXXXXX") || exit 2
trap 'rm -rf "$W"' EXIT
python3 -B - "$G" "$W" <<'EOF'
import io, os, shutil, subprocess, sys, tarfile, zipfile
G, W = sys.argv[1], sys.argv[2]
sys.path[:0] = [os.path.join(G, "devices", "_image", "checks"), os.path.join(G, "tools", "build")]
import first_start as fs
import build
PROP = os.path.join(G, fs.SELF[0])
TOOL = os.path.join(G, fs.SELF[1])
bad = []


def ok(name, cond, detail=""):
    print(("ok   " if cond else "FAIL ") + name + ("" if cond else "  | " + str(detail)[-400:]))
    if not cond:
        bad.append(name)


def run(*a):
    r = subprocess.run([sys.executable, "-B"] + list(a), capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


# ---- the decision and the repository
want = dict(fs.read_prop(PROP))
ok("decision_english", want.get("ro.product.locale") == "en-US", want)
ok("decision_no_fixed_zone", want.get("persist.sys.timezone") == "", want)
ok("decision_vendor_pair_emptied", all(want.get(k) == "" for k in
                                      ("persist.sys.locale", "persist.sys.language", "persist.sys.country",
                                       "persist.sys.localevar")), want)
rc, out = run(TOOL, "lint", G)
ok("lint_repository", rc == 0, out)
ok("lint_build_py_check", build.first_start_lint(G) == [], build.first_start_lint(G))
devs = sorted(c for c in os.listdir(os.path.join(G, "devices")) if os.path.isfile(os.path.join(G, "devices", c, "build.conf")))
ok("every_model_has_a_descriptor", {"horse_popular", "goat_elegant", "dragon_healthy", "g0111", "g0100", "g0155"} <= set(devs), devs)
for c in devs:
    d = build.descriptor(G, c)
    files = build.stage_files(G, d)
    ok("stages_common_%s" % c, all(f in files for f in build.COMMON_FILES), files[:8])
    plans = 0
    for name, args in build.variants(d):
        for s in d.raw.get("samples", "").split():
            try:
                v, steps = build.plan_variant(d, s, args, G)
            except build.RecipeError:
                continue
            plans += 1
            ok("checks_image_%s_%s_%s" % (c, name, s), build.has_first_start_check(steps))
            stripped = [st for st in steps if st[2][:2] != ["first_start.py", "check"]]
            ok("refused_without_check_%s_%s_%s" % (c, name, s), not build.has_first_start_check(stripped))
    ok("planned_%s" % c, plans > 0)

# ---- scratch trees
BLOCK = fs.block_lines(PROP)
HOSTILE_RC = "on boot\n    setprop persist.sys.timezone Europe/Moscow\n"


def tree(name, product, rcs=None, overlays=None, system_ext="persist.sys.language=en\npersist.sys.country=US\n"):
    s = os.path.join(W, name, "system")
    for d in ("product/etc", "system_ext/etc", "etc/init", "product/overlay"):
        os.makedirs(os.path.join(s, d), exist_ok=True)
    open(os.path.join(s, "build.prop"), "w").write("ro.product.locale=en-US\nro.build.id=AP2A\n# end of file\n")
    open(os.path.join(s, "system_ext/etc/build.prop"), "w").write(system_ext)
    open(os.path.join(s, fs.PRODUCT), "w").write(product)
    for n, t in (rcs or {}).items():
        open(os.path.join(s, "etc/init", n), "w").write(t)
    for n, arsc in (overlays or {}).items():
        with zipfile.ZipFile(os.path.join(s, "product/overlay", n), "w") as z:
            z.writestr("AndroidManifest.xml", b"x")
            z.writestr("resources.arsc", arsc)
    return s


BASE_PRODUCT = "ro.product.product.model=X\nro.product.locale=en-US\npersist.sys.timezone=Europe/Moscow\nro.lmk.x=1\n"
s = tree("good", BASE_PRODUCT, {"z9x_setup.rc": 'on load_bpf_programs && property:persist.z9x.firstrun=1\n'
                                                '    setprop persist.sys.locale ""\n    setprop persist.sys.timezone ""\n'},
         {"Ok.apk": b"\x02\x00config_x\x00"})
rc, out = run(TOOL, "apply", PROP, s)
prod = open(os.path.join(s, fs.PRODUCT)).read()
ok("apply_appends_block", rc == 0 and prod.endswith("\n".join(BLOCK) + "\n"), out)
ok("apply_drops_base_lines", "Europe/Moscow" not in prod and "ro.lmk.x=1" in prod
   and [l for l in prod.split("\n") if l.startswith("ro.product.locale=")] == ["ro.product.locale=en-US"], prod)
rc, out = run(TOOL, "apply", PROP, s)
ok("apply_refuses_twice", rc != 0 and "already" in out, out)
rc, out = run(TOOL, "check", PROP, s)
ok("check_good_tree", rc == 0 and "locale en-US" in out, out)

s = tree("no_block", BASE_PRODUCT)
rc, out = run(TOOL, "check", PROP, s)
ok("check_refuses_missing_block", rc != 0 and "not there as one block" in out, out)
ok("check_vendor_wins_without_block", "persist.sys.language resolves to 'zh'" in out
   and "zygote's locale with the stock XGIMI vendor is 'zh-CN'" in out, out)
# only the vendor's legacy pair emptied is not enough: a vendor persist.sys.locale would win (any-vendor rule)
s = tree("pair_only", "persist.sys.language=\npersist.sys.country=\nro.product.locale=en-US\n")
rc, out = run(TOOL, "check", PROP, s)
ok("check_any_vendor_rule", rc != 0 and "persist.sys.locale resolves to 'vendor-value'" in out, out)

s = tree("later_line", BASE_PRODUCT)
run(TOOL, "apply", PROP, s)
open(os.path.join(s, fs.PRODUCT), "a").write("persist.sys.timezone=Europe/Moscow\n")
rc, out = run(TOOL, "check", PROP, s)
ok("check_refuses_line_after_block", rc != 0 and "outside the first-start block" in out and "resolves to 'Europe/Moscow'" in out, out)

s = tree("import_after", BASE_PRODUCT)
run(TOOL, "apply", PROP, s)
open(os.path.join(s, fs.PRODUCT), "a").write("import /product/etc/more.prop\n")
rc, out = run(TOOL, "check", PROP, s)
ok("check_refuses_import_after_block", rc != 0 and "imports another file" in out, out)

s = tree("rc_own_zone", BASE_PRODUCT, {"x.rc": HOSTILE_RC})
run(TOOL, "apply", PROP, s)
rc, out = run(TOOL, "check", PROP, s)
ok("check_refuses_rc_own_zone", rc != 0 and "x.rc:2 sets persist.sys.timezone" in out, out)

s = tree("sh_auto_time", BASE_PRODUCT, {"y.sh": "#!/system/bin/sh\nsettings put global auto_time_zone 0\n"})
run(TOOL, "apply", PROP, s)
rc, out = run(TOOL, "check", PROP, s)
ok("check_refuses_auto_time_write", rc != 0 and "automatic time settings" in out, out)

s = tree("overlay_auto_time", BASE_PRODUCT, overlays={"Bad.apk": b"\x02\x00def_auto_time_zone\x00"})
run(TOOL, "apply", PROP, s)
rc, out = run(TOOL, "check", PROP, s)
ok("check_refuses_auto_time_overlay", rc != 0 and "Bad.apk" in out, out)
s = tree("overlay_auto_time16", BASE_PRODUCT, overlays={"Bad16.apk": "def_auto_time".encode("utf-16-le")})
run(TOOL, "apply", PROP, s)
rc, out = run(TOOL, "check", PROP, s)
ok("check_refuses_auto_time_overlay_utf16", rc != 0 and "Bad16.apk" in out, out)

# the vendor's legacy pair beats ro.product.locale when nothing empties it (the RS30's Chinese first start)
p = {"ro.product.locale": "en-US", "persist.sys.language": "zh", "persist.sys.country": "CN"}
ok("read_locale_vendor_pair_wins", fs.read_locale(p) == "zh-CN", fs.read_locale(p))
ok("read_locale_user_choice_wins", fs.read_locale(dict(p, **{"persist.sys.locale": "ru-RU"})) == "ru-RU")
ok("read_locale_defaults", fs.read_locale({}) == "en-US", fs.read_locale({}))

# a system tar (the Z9X recipe): members system/...
def tar_of(sysdir, path):
    with tarfile.open(path, "w") as t:
        t.add(sysdir, arcname="system")
    return path


rc, out = run(TOOL, "check", PROP, "--tar", tar_of(os.path.join(W, "good", "system"), os.path.join(W, "good.tar")))
ok("check_good_tar", rc == 0, out)
rc, out = run(TOOL, "check", PROP, "--tar", tar_of(os.path.join(W, "rc_own_zone", "system"), os.path.join(W, "bad.tar")))
ok("check_refuses_bad_tar", rc != 0 and "etc/init/x.rc:2" in out, out)

# ---- the repository lint on a scratch tree
R = os.path.join(W, "repo")
os.makedirs(os.path.join(R, "devices", "_image", "checks"))
shutil.copy(PROP, os.path.join(R, fs.SELF[0]))
shutil.copy(TOOL, os.path.join(R, fs.SELF[1]))


def put(rel, text):
    p = os.path.join(R, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w").write(text)


put("devices/m1/image/m1_prop.txt", "# comment ro.product.locale=zh-CN\nro.lumen.device=m1\n")
put("devices/m1/image/m1.rc", 'on boot\n    setprop persist.sys.locale ""\n')
put("devices/_baseline/m1/props_added.txt", "ro.product.locale=en-US\n")
put("overlay/v65/z9x_setup.rc", "    setprop persist.sys.timezone Europe/Moscow\n")
put("apps/FooOverlay/res/values/config.xml", '<bool name="config_x">true</bool>\n')
ok("lint_clean_scratch", fs.lint(R)[0] == [], fs.lint(R)[0])
for name, rel, text, word in (
        ("lint_prop_file", "devices/m2/image/m2_prop.txt", "ro.product.locale=ru-RU\n", "sets ro.product.locale"),
        ("lint_x50_edit_add", "devices/m2/image/m2_edits.txt", "[product]\n+persist.sys.timezone=Europe/Moscow\n",
         "sets persist.sys.timezone"),
        ("lint_x50_edit_delete", "devices/m2/image/m2_edits2.txt", "[product]\n-persist.sys.language\n",
         "sets persist.sys.language"),
        ("lint_rc_value", "overlay/m2/m2.rc", "on boot\n    setprop persist.sys.timezone Europe/Moscow\n",
         "sets persist.sys.timezone"),
        ("lint_steps_row", "devices/m2/image/steps.tsv", "*\tline\t{sys}/product/etc/build.prop\tpersist.sys.locale=en-US\n",
         "names persist.sys.locale=en-US"),
        ("lint_python_literal", "devices/m2/image/m2_props.py", "p += ['ro.product.locale=ru-RU']\n",
         "writes ro.product.locale"),
        ("lint_overlay_default", "apps/BarOverlay/res/values/defaults.xml", '<bool name="def_auto_time_zone">false</bool>\n',
         "def_auto_time_zone"),
        ("lint_conf_vendor_legacy", "devices/m2/build.conf", "ro.product.locale.region=CN\n", "sets ro.product.locale.region"),
        ("lint_settings_write", "tools/ota/image/x.sh", "settings put global auto_time 0\n", "automatic time settings")):
    put(rel, text)
    errs = fs.lint(R)[0]
    ok(name, any(rel in e and word in e for e in errs), errs)
    os.unlink(os.path.join(R, rel))

# ---- the X50 prop check (verify_tree_x50.py): base + edits + model, then first_start.py apply
X = os.path.join(W, "x50")
os.makedirs(os.path.join(X, "base/product/etc"))
open(os.path.join(X, "base/build.prop"), "w").write("ro.product.system.model=Z9X\nro.build.id=A\nro.lineage.display.version=1.0.9 (kmuradoff)\n")
open(os.path.join(X, "base/product/etc/build.prop"), "w").write(BASE_PRODUCT)
open(os.path.join(X, "edits.txt"), "w").write("[system]\nro.product.system.model=X50\n[product]\n+ro.lumen.device=g0111\n+ro.lumen.version=t1\n")
open(os.path.join(X, "model.txt"), "w").write("ro.xgimi.product.name=Product_dianapro\n")
shutil.copytree(os.path.join(X, "base"), os.path.join(X, "new"))
XP = os.path.join(G, "devices", "g0111", "image", "x50_props.py")
args = [os.path.join(X, "edits.txt"), os.path.join(X, "model.txt")]
rc, out = run(XP, "apply", *args, os.path.join(X, "new/build.prop"), os.path.join(X, "new/product/etc/build.prop"), "t1", "202611130000")
rc2, out2 = run(TOOL, "apply", PROP, os.path.join(X, "new"))
chk = args + [os.path.join(X, "base/build.prop"), os.path.join(X, "base/product/etc/build.prop"),
              os.path.join(X, "new/build.prop"), os.path.join(X, "new/product/etc/build.prop"), "t1", "202611130000"]
rc3, out3 = run(XP, "check", *chk, PROP)
ok("x50_props_check_with_first_start", rc == 0 and rc2 == 0 and rc3 == 0, out + out2 + out3)
rc4, out4 = run(XP, "check", *chk)
ok("x50_props_check_refuses_without_it", rc4 != 0, out4)
sys.path.insert(0, os.path.dirname(XP))
import x50_props
ok("x50_model_rule_drops_locale", not x50_props.model_rule("ro.product.locale=en-US")
   and not x50_props.model_rule("ro.product.locale.language=zh") and x50_props.model_rule("ro.xgimi.default.aipq=film")
   and not x50_props.model_rule("ro.localization.locale_filter=^(?!en-XA$)"))

# ---- the Z9X product build.prop (z9x_props.py, the Mac staging step) ends with the block, and the tar checks
Z = os.path.join(W, "z9x")
os.makedirs(Z)
open(os.path.join(Z, "sys.prop"), "w").write(
    "ro.build.id=AP2A\nro.build.display.id=old\nro.lineage.display.version=old\nro.product.locale=en-US\n"
    "# Lumen OS 1.0 (appended to system/build.prop by lumen_v1.sh)\nold=1\n# end of file\n")
open(os.path.join(Z, "prod.prop"), "w").write(
    "ro.product.product.model=XGIMI Z9X\n# Lumen OS 1.0 product defaults (appended to system/product/etc/build.prop by lumen_v1.sh).\n"
    "ro.product.locale=en-US\npersist.sys.timezone=Europe/Moscow\n# end of file\n")
rc, out = run(os.path.join(G, "devices", "horse_popular", "image", "z9x_props.py"), G, os.path.join(Z, "sys.prop"),
              os.path.join(Z, "prod.prop"), os.path.join(Z, "out"), "20261113anp",
              "https://github.com/Project-Lumen-OS/lumen-os/releases/download/z9x/update-public-nogms.json")
zp = open(os.path.join(Z, "out/system/product/etc/build.prop")).read() if rc == 0 else ""
ok("z9x_props_block_last", rc == 0 and zp.endswith("\n".join(BLOCK) + "\n# end of file\n") and "Europe/Moscow" not in zp, out)
os.makedirs(os.path.join(Z, "out/system/system_ext/etc"), exist_ok=True)
open(os.path.join(Z, "out/system/system_ext/etc/build.prop"), "w").write("ro.adb.secure=1\n")
rc, out = run(TOOL, "check", PROP, "--tar", tar_of(os.path.join(Z, "out/system"), os.path.join(W, "z9x.tar")))
ok("z9x_props_tar_checks", rc == 0, out)

# ---- the shared stock-data block of the Z9X family empties, it sets nothing of its own
rcs = open(os.path.join(G, "overlay/v1/z9x_setup.rc")).read()
ok("z9x_setup_rc_empties", 'setprop persist.sys.locale ""' in rcs and 'setprop persist.sys.timezone ""' in rcs
   and "Europe/Moscow" not in rcs.split("on load_bpf_programs && property:persist.z9x.firstrun=1")[1], rcs[-400:])

# ---- Lumen Setup: the order of the steps and the language of the first screen
import re
SETUP = os.path.join(G, "apps", "LumenSetup")
FL = os.path.join(SETUP, "src", "org", "lumen", "setup", "FirstLocale.java")
JT = os.path.join(W, "jt")
os.makedirs(os.path.join(JT, "src"))
open(os.path.join(JT, "src", "FirstLocaleTest.java"), "w").write(r"""
import org.lumen.setup.FirstLocale;
public class FirstLocaleTest {
    static int bad = 0;
    static void eq(String name, String got, String want) {
        boolean ok = want == null ? got == null : want.equals(got);
        System.out.println((ok ? "ok   " : "FAIL ") + "setup_first_locale_" + name + (ok ? "" : "  | got " + got + " want " + want));
        if (!ok) bad++;
    }
    public static void main(String[] a) {
        eq("fresh_russian_data", FirstLocale.toApply(null, "ru-RU", "en-US"), "en-US");
        eq("fresh_vendor_chinese", FirstLocale.toApply(null, "zh-CN", "en-US"), "en-US");
        eq("fresh_empty_step", FirstLocale.toApply("", "ru-RU", "en-US"), "en-US");
        eq("fresh_already_default", FirstLocale.toApply(null, "en-US", "en-US"), null);
        eq("fresh_case_and_underscore", FirstLocale.toApply(null, "en_us", "en-US"), null);
        eq("fresh_trimmed", FirstLocale.toApply(null, "de-DE", " en-US "), "en-US");
        eq("step_shown_keeps_user", FirstLocale.toApply("remote", "ru-RU", "en-US"), null);
        eq("later_step_keeps_user", FirstLocale.toApply("network", "zh-CN", "en-US"), null);
        eq("no_product_locale_no_own_language", FirstLocale.toApply(null, "ru-RU", ""), null);
        eq("null_product_locale", FirstLocale.toApply(null, "ru-RU", null), null);
        eq("null_current", FirstLocale.toApply(null, null, "en-US"), "en-US");
        System.exit(bad == 0 ? 0 : 1);
    }
}
""")
javac = shutil.which("javac")
if javac is None:
    ok("setup_first_locale_javac", False, "no javac (run the suite on a builder)")
else:
    r = subprocess.run([javac, "-encoding", "UTF-8", "-nowarn", "-d", os.path.join(JT, "out"), FL,
                        os.path.join(JT, "src", "FirstLocaleTest.java")], capture_output=True, text=True)
    ok("setup_first_locale_compiles", r.returncode == 0, r.stderr)
    if r.returncode == 0:
        r = subprocess.run([shutil.which("java") or "java", "-cp", os.path.join(JT, "out"), "FirstLocaleTest"],
                           capture_output=True, text=True)
        print(r.stdout.rstrip())
        ok("setup_first_locale_cases", r.returncode == 0, r.stdout + r.stderr)
fl = open(FL).read()
ok("setup_first_locale_no_own_language", not re.search(r'"[a-z]{2,3}[-_][A-Za-z]{2}"', fl), "a language tag literal in FirstLocale.java")


def method(src, sig):
    i = src.index(sig)
    j = src.index("{", i)
    depth, k = 0, j
    while True:
        if src[k] == "{":
            depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0:
                return src[j:k + 1]
        k += 1


# every SetupActivity an image can get: the base and any layer on top of it (devices/*/app/*.layers "top")
acts = [os.path.join(SETUP, "src", "org", "lumen", "setup", "SetupActivity.java")]
import os, sys  # noqa: E401
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import assemble_app  # noqa: E402  (every recipe a model builds, its own or its family's)
for c in devs:
    for f in sorted(set(assemble_app.recipes(G, c).values())):
        if f.endswith(".layers"):
            for line in open(f):
                p = line.split()
                if len(p) == 2 and p[0] == "top":
                    a = os.path.join(G, p[1], "src", "org", "lumen", "setup", "SetupActivity.java")
                    if os.path.isfile(a) and a not in acts:
                        acts.append(a)
for a in acts:
    rel = os.path.relpath(a, G)
    src = open(a).read()
    create = method(src, "protected void onCreate(")
    start = method(src, "private void startSteps(")
    order = re.findall(r"mSteps\.add\(new (\w+)\(", start)
    ok("setup_order_remote_then_language %s" % rel, order[:2] == ["RemoteStep", "WelcomeStep"], order)
    gate = create.find("FirstLocale.toApply(state.step()")
    ok("setup_first_locale_gate %s" % rel, gate >= 0 and 'Sys.prop("ro.product.locale"' in create
       and "setLocale(Locale.forLanguageTag(first), this::startSteps)" in create, create[-900:])
    calls = [m.start() for m in re.finditer(r"\bstartSteps\(\);", create)]
    ok("setup_steps_after_gate %s" % rel, calls and all(c > gate for c in calls), calls)
    outside = src.replace(start, "")
    ok("setup_full_steps_only_in_startSteps %s" % rel,
       re.findall(r"mSteps\.add\(new (\w+)\(", outside) == ["LauncherStep"],
       re.findall(r"mSteps\.add\(new (\w+)\(", outside))
    ok("setup_late_start_resumes %s" % rel, "startAfterFirstFrame();" in start and "mResumed" in start, start[-400:])
# the first screen has a text in the default (English) resources for every string it shows
rs = open(os.path.join(SETUP, "src", "org", "lumen", "setup", "steps", "RemoteStep.java")).read()
used = sorted(set(re.findall(r"R\.string\.(\w+)", rs)))
vals = "".join(open(os.path.join(SETUP, "res", "values", f)).read()
               for f in os.listdir(os.path.join(SETUP, "res", "values")) if f.endswith(".xml"))
miss = [n for n in used if '<string name="%s"' % n not in vals]
ok("setup_remote_step_english_strings", used and not miss, miss)

print("first_start_test: %d failed" % len(bad))
sys.exit(1 if bad else 0)
EOF
rc=$?
echo "first_start_test: $([ $rc = 0 ] && echo PASS || echo FAIL)"
exit $rc
