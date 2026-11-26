#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# assemble_app_test.sh: tests of tools/devices/assemble_app.py.
#
#   sh tools/devices/test/assemble_app_test.sh
#
# Every device recipe of the registry (devices/*/app/*.layers) must assemble green; then broken recipes, written
# into a scratch dir (so no fork table applies to them), must be red: a drop that removes nothing, a path given
# by two bases, a differing top file without a fork row (untracked fork), a byte-identical top file without
# 'identical allow' (pointless copy), an unknown directive, --out inside a layer. --compare must be red against a
# folder that misses one file. Modules (apps/core, tools/arch/README.md): a clean module-only and module-plus-base
# recipe; red for a shadowed or dropped module file, a missing dependency, a wrong name, an unprefixed resource or file,
# a class outside the module's package, no module.conf. The tree is only read.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/assemble_test.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
R=$G
A() { python3 -B "$G/tools/devices/assemble_app.py" --root "$R" "$@"; }
expect() { # name want(0|1) pattern args...
  n=$1 want=$2 pat=$3; shift 3
  A "$@" > "$W/out" 2>&1; got=$?
  if [ "$got" = "$want" ] && { [ -z "$pat" ] || grep -q -- "$pat" "$W/out"; }; then
    PASS=$((PASS + 1)); echo "ok   $n: $(tail -1 "$W/out")"
  else
    FAIL=$((FAIL + 1)); echo "FAIL $n (exit $got, want $want, pattern '$pat')"; sed 's/^/     /' "$W/out"
  fi
}

for r in "$G"/devices/*/app/*.layers; do
  [ -f "$r" ] || continue
  expect "recipe_${r#$G/}" 0 'files (bases' "$r"
done
# a family recipe (devices/_family/<f>/app) assembles for every model that sees it
for c in $(cd "$G/devices" && ls -d */ | tr -d / | grep -v '^_'); do
  [ -f "$G/devices/$c/device.conf" ] || continue
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --recipes "$c" | while IFS=$(printf '\t') read -r app rel; do
    case $rel in devices/_family/*) echo "$c $app" ;; esac
  done
done > "$W/family_recipes"
while read -r c app; do
  expect "family_${c}_$app" 0 'files (bases' --device "$c" --app "$app"
done < "$W/family_recipes"
[ -s "$W/family_recipes" ] || { echo "FAIL no family recipe found"; FAIL=$((FAIL + 1)); }

# a small two-layer app in the scratch dir: base B (a, b, c/d), top T
mkdir -p "$W/B/c" "$W/T"
echo a > "$W/B/a.txt"; echo b > "$W/B/b.txt"; echo d > "$W/B/c/d.txt"
echo own > "$W/T/own.txt"
recipe() { printf '%s\n' "app Test" "base ${W#/}/B" "top ${W#/}/T" "$@" > "$W/r.layers"; }
# paths in a recipe are relative to --root: use / as root for the scratch layers
R=/

recipe
expect clean 0 '3 files\|4 files' "$W/r.layers"
recipe "drop nothing.txt"
expect drop_removes_nothing 1 'removes nothing' "$W/r.layers"
recipe "drop c/"
expect drop_folder 0 '' "$W/r.layers"
printf '%s\n' "app Test" "base ${W#/}/B" "file ${W#/}/B/a.txt a.txt" "top ${W#/}/T" > "$W/r.layers"
expect two_bases 1 'given by two bases' "$W/r.layers"
echo changed > "$W/T/b.txt"
recipe
expect untracked_fork 1 'untracked fork' "$W/r.layers"
rm "$W/T/b.txt"; cp "$W/B/a.txt" "$W/T/a.txt"
recipe
expect pointless_copy 1 'pointless copy' "$W/r.layers"
recipe "identical allow"
expect copy_allowed 0 '1 copy' "$W/r.layers"
rm "$W/T/a.txt"
recipe "frobnicate x"
expect unknown_directive 1 'cannot read' "$W/r.layers"
recipe
expect out_inside_layer 1 'overlaps the layer' "$W/r.layers" --out "$W/T/x"
mkdir -p "$W/cmp"; cp "$W/B/a.txt" "$W/B/b.txt" "$W/T/own.txt" "$W/cmp/"
expect compare_missing_file 1 'COMPARE FAILED' "$W/r.layers" --compare "$W/cmp"
mkdir -p "$W/cmp/c"; cp "$W/B/c/d.txt" "$W/cmp/c/"
expect compare_equal 0 'COMPARE OK' "$W/r.layers" --compare "$W/cmp"
expect out_written 0 '' "$W/r.layers" --out "$W/o"

# features (rule 9): the optional parts of a layer, named by B.features beside it; a recipe takes a part only by name
printf '%s\n' "# optional parts" "extra b.txt" "deep c/" > "$W/B.features"
recipe
expect feature_left_out 0 ' 2 files' "$W/r.layers"
recipe "feature extra"
expect feature_taken 0 ' 3 files' "$W/r.layers"
recipe "feature extra deep"
expect feature_both 0 ' 4 files' "$W/r.layers"
recipe "feature nosuch"
expect feature_unknown 1 'named by no feature table' "$W/r.layers"
recipe "feature extra" "drop b.txt"
expect feature_dropped 1 'leave its feature out' "$W/r.layers"
printf '%s\n' "extra b.txt" "gone missing.txt" > "$W/B.features"
recipe
expect feature_row_stale 1 'matches no file' "$W/r.layers"
printf '%s\n' "extra b.txt" "other b.txt" > "$W/B.features"
recipe "feature extra"
expect feature_twice 1 'one at most' "$W/r.layers"
printf '%s\n' "extra b.txt" > "$W/B.features"
printf '%s\n' "a.txt" "b.txt" > "$W/B.list"
printf '%s\n' "app Test" "base ${W#/}/B ${W#/}/B.list" "top ${W#/}/T" > "$W/r.layers"
expect feature_listed 1 'which the recipe does not name' "$W/r.layers"
printf '%s\n' "app Test" "base ${W#/}/B" "feature extra" > "$W/r.layers"
expect no_top 0 ' 3 files' "$W/r.layers"
rm -f "$W/B.features" "$W/B.list"
[ -f "$W/o/c/d.txt" ] && [ -f "$W/o/own.txt" ] && PASS=$((PASS + 1)) || { FAIL=$((FAIL + 1)); echo "FAIL out_written: files missing"; }

# modules (tools/arch/README.md): a module M (module.conf, a class in its package, a prefixed string), used next to B
mkdir -p "$W/M/lumen-x/src/org/lumen/x" "$W/M/lumen-x/res/values" "$W/T2"
printf 'name=lumen-x\npackage=org.lumen.x\n' > "$W/M/lumen-x/module.conf"
echo 'package org.lumen.x; public final class A {}' > "$W/M/lumen-x/src/org/lumen/x/A.java"
printf '<resources><string name="lumen_ok">OK</string></resources>\n' > "$W/M/lumen-x/res/values/lumen_strings.xml"
echo notes > "$W/M/lumen-x/README.md"
echo own > "$W/T2/own.txt"
mrecipe() { printf '%s\n' "app Test" "module ${W#/}/M/lumen-x" "top ${W#/}/T2" "$@" > "$W/r.layers"; }
mrecipe
expect module_only 0 '3 files' "$W/r.layers"
recipe "module ${W#/}/M/lumen-x"
expect module_with_base 0 '6 files' "$W/r.layers"
mkdir -p "$W/T2/src/org/lumen/x"; echo 'package org.lumen.x; final class A {}' > "$W/T2/src/org/lumen/x/A.java"
mrecipe
expect module_shadowed 1 'modules are closed' "$W/r.layers"
rm -r "$W/T2/src"
mrecipe "drop src/org/lumen/x/A.java"
expect module_dropped 1 'modules are closed' "$W/r.layers"
printf 'name=lumen-x\npackage=org.lumen.x\ndepends=lumen-y\n' > "$W/M/lumen-x/module.conf"
mrecipe
expect module_missing_dep 1 'does not name' "$W/r.layers"
printf 'name=lumen-z\npackage=org.lumen.x\n' > "$W/M/lumen-x/module.conf"
mrecipe
expect module_name_mismatch 1 'the folder is lumen-x' "$W/r.layers"
printf 'name=lumen-x\npackage=org.lumen.x\n' > "$W/M/lumen-x/module.conf"
printf '<resources><string name="ok">OK</string></resources>\n' > "$W/M/lumen-x/res/values/lumen_more.xml"
mrecipe
expect module_unprefixed_res 1 'not prefixed lumen_' "$W/r.layers"
rm "$W/M/lumen-x/res/values/lumen_more.xml"
# a sub-package of the module's own package: red unless packages= names it
mkdir -p "$W/M/lumen-x/src/org/lumen/x/utils"; echo 'package org.lumen.x.utils; public final class U {}' > "$W/M/lumen-x/src/org/lumen/x/utils/U.java"
mrecipe
expect module_subpackage_unlisted 1 'outside its package folders' "$W/r.layers"
printf 'name=lumen-x\npackage=org.lumen.x\npackages=org.lumen.x.utils\n' > "$W/M/lumen-x/module.conf"
mrecipe
expect module_subpackage_listed 0 '4 files' "$W/r.layers"
printf 'name=lumen-x\npackage=org.lumen.x\n' > "$W/M/lumen-x/module.conf"
rm -r "$W/M/lumen-x/src/org/lumen/x/utils"
mkdir -p "$W/M/lumen-x/src/org/other"; echo 'package org.other; class B {}' > "$W/M/lumen-x/src/org/other/B.java"
mrecipe
expect module_outside_package 1 'outside its package folder' "$W/r.layers"
rm -r "$W/M/lumen-x/src/org/other"
mkdir -p "$W/M/lumen-x/res/drawable"; echo '<shape/>' > "$W/M/lumen-x/res/drawable/icon.xml"
mrecipe
expect module_unprefixed_file 1 'is not named lumen_' "$W/r.layers"
rm -r "$W/M/lumen-x/res/drawable"
rm "$W/M/lumen-x/module.conf"
mrecipe
expect module_no_conf 1 'has no module.conf' "$W/r.layers"

echo "assemble_app_test: $PASS passed, $FAIL failed"
[ $FAIL = 0 ]
