#!/usr/bin/env python3
"""The two Z9X build.prop files of a release (the Mac staging step, before tools/build/build.py): the tail that
tools/lumen_v1.sh prep appends (the snippets of this tree, the OTA props with the build id and MANIFEST, the variant
lines, ro.lumen.dsu_install=1) in place of the tail the base tar carries, and the About line. Writes
OUTDIR/system/build.prop and OUTDIR/system/product/etc/build.prop and prints a unified diff.
  z9x_props.py G OLD_SYSTEM_BUILD_PROP OLD_PRODUCT_BUILD_PROP OUTDIR BID MANIFEST
"""
import difflib, os, re, sys
G, old_sys, old_prod, out, bid = sys.argv[1:6]
VER = re.search(r'^VER=(\S+)$', open(f'{G}/tools/lumen_v1.sh').read(), re.M).group(1)
V1, OTAIMG, UIRES = f'{G}/overlay/v1', f'{G}/tools/ota/image', f'{G}/overlay/v1/z9x_uires'
rd = lambda p: open(p).read().rstrip('\n')   # bash "$(cat f)" drops trailing newlines
BUILD_ID = f'lumen-{VER}-{bid}'
MANIFEST = sys.argv[6]
ota = rd(f'{OTAIMG}/ota_props.txt')
ota, n1 = re.subn(r'^ro\.z9x\.build_id=lumen-%s-<DATE>$' % re.escape(VER), f'ro.z9x.build_id={BUILD_ID}', ota, flags=re.M)
ota = ota.replace('<DATE>', bid[:8])
ota, n2 = re.subn(r'^ro\.z9x\.ota\.manifest_url=.*/update-stable\.json$', f'ro.z9x.ota.manifest_url={MANIFEST}', ota, flags=re.M)
assert n1 == 1 and n2 == 1, (n1, n2)
pub = ('\n# ---- public image (tools/lumen_v1.sh VARIANT=public, tools/ota/image/HANDOFF.md section 2)\nro.z9x.variant=public'
       '\n# ---- no-Google edition (tools/lumen_v1.sh GMS=0)\nro.z9x.gms=0'
       '\n# ---- install from the projector (apps/LumenDsuInstall, image/INTEGRATION.md): engine on\nro.lumen.dsu_install=1')
sys_tail = rd(f'{V1}/build_prop_v1.txt') + '\n# end of file'
prod_tail = (rd(f'{V1}/product_prop_v1.txt') + '\n' + rd(f'{UIRES}/product_prop.txt')
             + '\n# ---- OTA (tools/ota/image/ota_props.txt, L-OTA)\n' + ota + pub + '\n# end of file')
def retail(old, start, tail):
    s = open(old).read()
    i = s.index('\n' + start + '\n') + 1          # the first line of the appended snippet
    assert s.endswith('# end of file\n'), old
    assert s.count('\n' + start + '\n') == 1
    return s[:i] + tail + '\n'
os.makedirs(f'{out}/system/product/etc', exist_ok=True)
ns = retail(old_sys, '# Lumen OS 1.0 (appended to system/build.prop by lumen_v1.sh)', sys_tail)
bp = re.search(r'^ro\.build\.id=(.*)$', ns, re.M).group(1)
ns, n3 = re.subn(r'^ro\.build\.display\.id=.*$', f'ro.build.display.id=Lumen OS {VER} ({bp}, {bid})', ns, flags=re.M)
ns, n4 = re.subn(r'^ro\.lineage\.display\.version=.*$', f'ro.lineage.display.version={VER} (kmuradoff)', ns, flags=re.M)
assert n3 == 1 and n4 == 1
np_ = retail(old_prod, '# Lumen OS 1.0 product defaults (appended to system/product/etc/build.prop by lumen_v1.sh).', prod_tail)
assert '\\' not in ns + np_
open(f'{out}/system/build.prop', 'w').write(ns)
open(f'{out}/system/product/etc/build.prop', 'w').write(np_)
for a, b, n in ((old_sys, ns, 'system/build.prop'), (old_prod, np_, 'system/product/etc/build.prop')):
    sys.stdout.writelines(difflib.unified_diff(open(a).read().splitlines(True), b.splitlines(True), n, n, n=0))
