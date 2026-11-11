#!/usr/bin/env python3
"""x50_apk_check.py: what one APK can do with the X50's gmpf HAL, read from its bytes with the Python standard
library only (the build host has no Android tool). verify_tree_x50.py uses it for the one APK of the X50 image that
may name the HAL (its HAL_APP: apps/X50EyeGuard); the host tests run it on the built APK and on mutants
(devices/g0111/test/x50_apk_check_test.sh).

  python3 -I x50_apk_check.py APK                       print the report
  python3 -I x50_apk_check.py APK --expect VERIFY_PY    and check it against HAL_APP of verify_tree_x50.py
        [--cert SHA256]                                 ... with another signer certificate (tests: the test key)
  exit 0 = report printed (and every expectation met), 1 = a problem, 2 = usage

What it reads:
- signers: the APK Signature Scheme v2 / v3 / v3.1 blocks, the sha256 of each signer's certificate (DER). The
  signatures themselves are verified by apksigner on the Mac when the APK is built (tools/devices/build_apps.sh);
  here the bytes of the image file must equal that APK, and the certificate must be the expected one.
- every classes*.dex: the gmpf descriptors among its strings (xgimi.hardware.gmpf.I..., Lxgimi/hardware/gmpf/I...,
  xgimi.hardware.gmpf@ of the HIDL), every invoke of android.os.IBinder.transact and of
  java.lang.reflect.Method.invoke (the class and method it sits in), and the call table of the transport class
  (tools/devices/check_aidl_hal.py rules 1-2: the class T with the one transact site declares enum T$Iface
  {NAME("descriptor")} and enum T$Call {NAME(Iface.X or null, code, "method")}): the arguments of every
  T$Iface / T$Call constructor call, read by following constants through the registers of the code.
- native code: any lib/ entry (a .so could reach the HAL around the dex).

problems(report, expect) -> [text]: the signer certificate of every scheme present is expect["cert"] (and v2 or v3
is present), the gmpf descriptors of the dex are exactly expect["ifaces"], no HIDL gmpf, exactly one transact site
and it is in the transport class, no Method.invoke, no native code, T$Call is constructed only in its own static
initialiser and its rows (service, code, method[, args]) are exactly expect["calls"] (service: the descriptor's last
part without the leading I, '-' for a meta call without interface; args: the t20 argument range, the Call's fourth
constructor String, which org.lumen.hal.AidlTransport enforces before the transact).
"""
import ast
import hashlib
import re
import struct
import sys
import zipfile

GMPF = (re.compile(r"^xgimi\.hardware\.gmpf\.I[A-Z]\w*$"), re.compile(r"^Lxgimi/hardware/gmpf/I[A-Z][\w$]*;$"))
GMPF_ANY = re.compile(r"xgimi[./]hardware[./]gmpf")
HIDL = re.compile(r"xgimi\.hardware\.gmpf@")
SCHEMES = {0x7109871A: "v2", 0xF05368C0: "v3", 0x1B93AD61: "v3.1"}


# ---------------------------------------------------------------- APK signing block
def _lp(b, o):
    """a uint32 length-prefixed field at o -> (bytes, next offset)"""
    n = struct.unpack_from("<I", b, o)[0]
    if o + 4 + n > len(b):
        raise ValueError("length-prefixed field runs past its block")
    return b[o + 4:o + 4 + n], o + 4 + n


def _seq(b):
    out, o = [], 0
    while o < len(b):
        x, o = _lp(b, o)
        out.append(x)
    return out


def signers(data):
    """{scheme: [sha256 of the first certificate of each signer]} of the APK signing block"""
    e = data.rfind(b"PK\x05\x06", max(0, len(data) - 65557))
    if e < 0:
        raise ValueError("no zip end of central directory")
    cd = struct.unpack_from("<I", data, e + 16)[0]
    if cd < 32 or data[cd - 16:cd] != b"APK Sig Block 42":
        return {}
    size = struct.unpack_from("<Q", data, cd - 24)[0]
    start = cd - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        raise ValueError("damaged APK signing block")
    out, o, end = {}, start + 8, cd - 24
    while o < end:
        n, i = struct.unpack_from("<QI", data, o)
        val = data[o + 12:o + 8 + n]
        o += 8 + n
        if i not in SCHEMES:
            continue
        certs = []
        for signer in _seq(_lp(val, 0)[0]):
            sd = _lp(signer, 0)[0]                 # signed data: digests, certificates, ...
            _, p = _lp(sd, 0)
            cl = _seq(_lp(sd, p)[0])
            if not cl:
                raise ValueError("%s signer without a certificate" % SCHEMES[i])
            certs.append(hashlib.sha256(cl[0]).hexdigest())
        out[SCHEMES[i]] = certs
    return out


# ---------------------------------------------------------------- dex
def _uleb(b, o):
    r = s = 0
    while True:
        x = b[o]
        o += 1
        r |= (x & 0x7F) << s
        s += 7
        if x < 0x80:
            return r, o


# instruction width in 16-bit code units by opcode (the Dalvik formats); payloads are handled apart
W = [1] * 256
for _r, _w in (((0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1A, 0x1C, 0x1F, 0x20, 0x22, 0x23, 0x29, 0xFE, 0xFF), 2),
               ((0x03, 0x06, 0x09, 0x14, 0x17, 0x1B, 0x24, 0x25, 0x26, 0x2A, 0x2B, 0x2C, 0xFC, 0xFD), 3),
               ((0x18,), 5), ((0xFA, 0xFB), 4)):
    for _o in _r:
        W[_o] = _w
for _a, _b, _w in ((0x2D, 0x3D, 2), (0x44, 0x6D, 2), (0x6E, 0x72, 3), (0x74, 0x78, 3), (0x90, 0xAF, 2),
                   (0xD0, 0xE2, 2)):
    for _o in range(_a, _b + 1):
        W[_o] = _w


class Dex:
    def __init__(self, b):
        if b[:4] != b"dex\n":
            raise ValueError("not a dex file")
        self.b = b
        u = lambda o: struct.unpack_from("<I", b, o)[0]  # noqa: E731
        ns, so, nt, to, npr, po, nf, fo, nm, mo, nc, co = (u(0x38 + 4 * k) for k in range(12))
        self.strings = []
        for k in range(ns):
            o = u(so + 4 * k)
            _, o = _uleb(b, o)
            self.strings.append(b[o:b.index(b"\0", o)].decode("utf-8", "replace"))
        self.types = [self.strings[u(to + 4 * k)] for k in range(nt)]
        self.protos = []
        for k in range(npr):
            off = u(po + 12 * k + 8)
            params = []
            if off:
                params = [self.types[struct.unpack_from("<H", b, off + 4 + 2 * j)[0]] for j in range(u(off))]
            self.protos.append(params)
        self.fields = [(self.types[struct.unpack_from("<H", b, fo + 8 * k)[0]], self.strings[u(fo + 8 * k + 4)])
                       for k in range(nf)]
        self.methods = [(self.types[struct.unpack_from("<H", b, mo + 8 * k)[0]],
                         struct.unpack_from("<H", b, mo + 8 * k + 2)[0], self.strings[u(mo + 8 * k + 4)])
                        for k in range(nm)]
        self.code = []   # (class, method name, code offset)
        for k in range(nc):
            cdo = u(co + 32 * k + 24)
            if not cdo:
                continue
            o = cdo
            sf, o = _uleb(b, o)
            inf, o = _uleb(b, o)
            dm, o = _uleb(b, o)
            vm, o = _uleb(b, o)
            for _ in range(sf + inf):
                _, o = _uleb(b, o)
                _, o = _uleb(b, o)
            for count in (dm, vm):
                idx = 0
                for _ in range(count):
                    d, o = _uleb(b, o)
                    _, o = _uleb(b, o)
                    c, o = _uleb(b, o)
                    idx += d
                    if c:
                        self.code.append((self.methods[idx][0], self.methods[idx][2], c))

    def invokes(self, off):
        """[(target class, target name, [argument values], proto index)] of the code item at off. A register's value
        is the last constant written to it in code order (int, ('str', s), ('field', class, name)), None after any
        other write: enough for the straight-line static initialiser of an enum, and a value that cannot be read
        never matches an allowed call (fail closed)."""
        b = self.b
        size = struct.unpack_from("<I", b, off + 12)[0]
        ins = struct.unpack_from("<%dH" % size, b, off + 16)
        regs, out, pc = {}, [], 0
        while pc < size:
            u0 = ins[pc]
            op = u0 & 0xFF
            if op == 0 and u0 in (0x100, 0x200, 0x300):     # switch / array payloads
                if u0 == 0x100:
                    pc += ins[pc + 1] * 2 + 4
                elif u0 == 0x200:
                    pc += ins[pc + 1] * 4 + 2
                else:
                    n = ins[pc + 2] | ins[pc + 3] << 16
                    pc += (n * ins[pc + 1] + 1) // 2 + 4
                continue
            a8, a4 = u0 >> 8, (u0 >> 8) & 0xF
            if op == 0x12:
                v = u0 >> 12
                regs[a4] = v - 16 if v > 7 else v
            elif op == 0x13:
                regs[a8] = struct.unpack("<h", struct.pack("<H", ins[pc + 1]))[0]
            elif op == 0x14:
                regs[a8] = struct.unpack("<i", struct.pack("<I", ins[pc + 1] | ins[pc + 2] << 16))[0]
            elif op == 0x15:
                regs[a8] = struct.unpack("<i", struct.pack("<I", ins[pc + 1] << 16))[0]
            elif op == 0x1A:
                regs[a8] = ("str", self.strings[ins[pc + 1]])
            elif op == 0x1B:
                regs[a8] = ("str", self.strings[ins[pc + 1] | ins[pc + 2] << 16])
            elif op in (0x60, 0x62, 0x63, 0x64, 0x65, 0x66):          # sget (not wide)
                regs[a8] = ("field",) + self.fields[ins[pc + 1]]
            elif op in (0x01, 0x07):
                regs[a4] = regs.get(u0 >> 12)
            elif op in (0x02, 0x08):
                regs[a8] = regs.get(ins[pc + 1])
            elif op in (0x03, 0x09):
                regs[ins[pc + 1]] = regs.get(ins[pc + 2])
            elif 0x6E <= op <= 0x72 or 0x74 <= op <= 0x78 or op in (0xFA, 0xFB):
                m = self.methods[ins[pc + 1]]
                if 0x6E <= op <= 0x72 or op == 0xFA:
                    c = ins[pc + 2]
                    r = [c & 0xF, c >> 4 & 0xF, c >> 8 & 0xF, c >> 12, a4][:u0 >> 12]
                else:
                    r = list(range(ins[pc + 2], ins[pc + 2] + a8))
                out.append((m[0], m[2], [regs.get(x) for x in r], m[1]))
            else:
                # any other write: its destination no longer holds a constant (wide writes: both registers)
                if op in (0x04, 0x05, 0x06, 0x0B, 0x16, 0x17, 0x18, 0x19, 0x45, 0x53, 0x61):
                    d = ins[pc + 1] if op == 0x06 else (a4 if op == 0x04 or op == 0x53 else a8)
                    regs.pop(d, None)
                    regs.pop(d + 1, None)
                elif op in (0x0A, 0x0C, 0x0D, 0x1C, 0x1F, 0x22, 0xFE, 0xFF) or 0x2D <= op <= 0x31 \
                        or 0x44 <= op <= 0x4A or 0x90 <= op <= 0xAF or 0xD8 <= op <= 0xE2:
                    regs.pop(a8, None)
                elif op in (0x20, 0x21, 0x23) or 0x52 <= op <= 0x58 or 0x7B <= op <= 0x8F or 0xB0 <= op <= 0xD7:
                    regs.pop(a4, None)
            pc += W[op]
        return out

    def handles(self):
        """[(class, name)] of every method handle of the dex (invoke-custom, const-method-handle)"""
        b = self.b
        mo = struct.unpack_from("<I", b, 0x34)[0]
        out = []
        for k in range(struct.unpack_from("<I", b, mo)[0]):
            t, _, n, o = struct.unpack_from("<HHII", b, mo + 4 + 12 * k)
            if t == 0x0008:
                for j in range(n):
                    kind, _, idx, _ = struct.unpack_from("<HHHH", b, o + 8 * j)
                    if kind >= 4:
                        out.append((self.methods[idx][0], self.methods[idx][2]))
        return out


# the shared transport of apps/core/lumen-hal (tools/devices/check_aidl_hal.py --module)
MODULE_TRANSPORT = "Lorg/lumen/hal/AidlTransport;"


def report(path):
    data = open(path, "rb").read()
    rep = {"sha256": hashlib.sha256(data).hexdigest(), "signers": signers(data), "dex": [], "gmpf": set(),
           "hidl": set(), "native": [], "transact": [], "reflect": [], "calls": [], "ifaces": {},
           "call_ctor_outside": [], "transport": None}
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        rep["native"] = [n for n in names if n.startswith("lib/") or n.endswith(".so")]
        dexes = [Dex(z.read(n)) for n in sorted(names) if re.match(r"^classes\d*\.dex$", n)]
        rep["dex"] = [n for n in sorted(names) if re.match(r"^classes\d*\.dex$", n)]
    sites = []
    for d in dexes:
        for s in d.strings:
            if HIDL.search(s):
                rep["hidl"].add(s)
            elif any(p.match(s) for p in GMPF):
                rep["gmpf"].add(s if s.startswith("xgimi.") else s[1:-1].replace("/", "."))
            elif GMPF_ANY.search(s):
                rep["gmpf"].add(s)
        for cls, name, off in d.code:
            for tc, tn, args, proto in d.invokes(off):
                sites.append((d, cls, name, tc, tn, args, proto))
    for d, cls, name, tc, tn, args, proto in sites:
        if tc == "Landroid/os/IBinder;" and tn == "transact":
            rep["transact"].append("%s.%s" % (cls, name))
        if tc == "Ljava/lang/reflect/Method;" and tn == "invoke":
            rep["reflect"].append("%s.%s" % (cls, name))
    for d in dexes:
        for tc, tn in d.handles():
            if tc == "Landroid/os/IBinder;" and tn == "transact":
                rep["transact"].append("method handle " + tn)
            if tc == "Ljava/lang/reflect/Method;" and tn == "invoke":
                rep["reflect"].append("method handle " + tn)
    if len(rep["transact"]) == 1:
        t = rep["transact"][0].rsplit(".", 1)[0]
        rep["transport"] = t
        base = t[:-1]
        if t == MODULE_TRANSPORT:
            # apps/core/lumen-hal: the transport is the shared AidlTransport, the allow list (enum Call, enum Iface) is
            # the one app class T with T$Call and T$Iface whose constants its own <clinit> builds
            owners = sorted({c[:-len("$Call;")] for _, c, n, tc, tn, _, _ in sites
                             if n == "<clinit>" and tn == "<init>" and c == tc and c.endswith("$Call;")
                             and (c[:-len("$Call;")] + "$Iface;") in {x[1] for x in sites}})
            if len(owners) == 1:
                base = owners[0]
                rep["allow_list"] = base + ";"
            else:
                rep["call_ctor_outside"].append("the shared transport %s, but %d allow-list classes (T$Call with T$Iface): %s"
                                                % (t, len(owners), owners))
        iface_ctor, call_ctor = base + "$Iface;", base + "$Call;"
        for d, cls, name, tc, tn, args, proto in sites:
            if tn != "<init>" or tc not in (iface_ctor, call_ctor) or cls == tc and name == "<init>":
                continue
            if cls != tc or name != "<clinit>":
                rep["call_ctor_outside"].append("%s.%s -> %s" % (cls, name, tc))
                continue
            params = d.protos[proto]
            if tc == iface_ctor:
                # (this, enum name, ordinal, descriptor)
                vals = [a[1] if isinstance(a, tuple) and a[0] == "str" else None for a in args]
                if len(args) == 4 and vals[1] and vals[3]:
                    rep["ifaces"][vals[1]] = vals[3]
                else:
                    rep["call_ctor_outside"].append("%s: an Iface constant that cannot be read %r" % (tc, args))
            else:
                # (this, enum name, ordinal, Iface or null, code, method) or, t20, with the argument range (ArgSpec) as a
                # fourth String: (..., method, args)
                spec = None
                if len(args) == 7 and len(params) == 6 and params[2] == iface_ctor and params[5] == "Ljava/lang/String;":
                    if not (isinstance(args[6], tuple) and args[6][0] == "str"):
                        rep["call_ctor_outside"].append("%s: a Call whose argument range cannot be read %r" % (tc, args))
                        continue
                    spec = args[6][1]
                elif len(args) != 6 or params[2] != iface_ctor:
                    rep["call_ctor_outside"].append("%s: a constructor %r that is not (Iface, int, String[, String])"
                                                    % (tc, params))
                    continue
                ifc, code, meth = args[3], args[4], args[5]
                if isinstance(ifc, tuple) and ifc[0] == "field" and ifc[1] == iface_ctor:
                    ifc = ifc[2]
                elif ifc == 0:
                    ifc = None
                else:
                    ifc = ("?", repr(ifc))
                if not isinstance(code, int) or not (isinstance(meth, tuple) and meth[0] == "str"):
                    rep["call_ctor_outside"].append("%s: a Call whose code or method cannot be read %r" % (tc, args))
                    continue
                rep["calls"].append((ifc, code, meth[1], spec))
    rows = []
    for ifc, code, meth, spec in rep["calls"]:
        if ifc is None:
            svc = "-"
        elif isinstance(ifc, str) and ifc in rep["ifaces"]:
            svc = rep["ifaces"][ifc].rsplit(".", 1)[-1][1:]
        else:
            svc = "?%s" % (ifc,)
        rows.append((svc, code & 0xFFFFFFFF, meth, spec))
    rep["rows"] = rows
    return rep


def problems(rep, expect):
    bad = []
    sig = rep["signers"]
    if not ({"v2", "v3"} & set(sig)):
        bad.append("not signed with APK Signature Scheme v2 or v3 (%s)" % (sorted(sig) or "no signing block"))
    for s, certs in sorted(sig.items()):
        if not certs or set(certs) != {expect["cert"]}:
            bad.append("%s signer certificate %s, not %s" % (s, ",".join(certs) or "none", expect["cert"]))
    if rep["gmpf"] != set(expect["ifaces"]):
        bad.append("names the gmpf interfaces %s, not exactly %s" % (sorted(rep["gmpf"]), sorted(expect["ifaces"])))
    if rep["hidl"]:
        bad.append("names the HIDL gmpf: %s" % sorted(rep["hidl"]))
    if rep["native"]:
        bad.append("carries native code: %s" % rep["native"])
    if len(rep["transact"]) != 1:
        bad.append("%d IBinder.transact sites, not exactly 1: %s" % (len(rep["transact"]), rep["transact"]))
    if rep["reflect"]:
        bad.append("calls Method.invoke: %s" % rep["reflect"])
    if rep["call_ctor_outside"]:
        bad.append("the call table is not only its enum constants: %s" % rep["call_ctor_outside"])
    if set(rep["ifaces"].values()) != set(expect["ifaces"]):
        bad.append("the transport's interfaces are %s, not %s" % (sorted(rep["ifaces"].values()), sorted(expect["ifaces"])))
    # a row of expect["calls"]: [service, code, method] or, with an argument range, [service, code, method, args]
    want = sorted((r[0], r[1] & 0xFFFFFFFF, r[2], r[3] if len(r) > 3 else None) for r in expect["calls"])
    if sorted(rep["rows"], key=str) != sorted(want, key=str):
        bad.append("the calls are %s, not exactly %s" % (sorted(rep["rows"], key=str), sorted(want, key=str)))
    return bad


def expect_from(verify_py, name="HAL_APP"):
    """HAL_APP (or, with name, another literal such as MENU_APP of t11) of verify_tree_x50.py, without running it"""
    for node in ast.parse(open(verify_py, encoding="utf-8").read()).body:
        if isinstance(node, ast.Assign) and [getattr(t, "id", None) for t in node.targets] == [name]:
            return ast.literal_eval(node.value)
    raise ValueError("%s has no %s" % (verify_py, name))


def main(a):
    if not a or a[0].startswith("-"):
        print((__doc__ or "").split("\n\n")[1])
        return 2
    apk, opts = a[0], dict(zip(a[1::2], a[2::2]))
    rep = report(apk)
    print("x50_apk_check: %s sha256 %s" % (apk, rep["sha256"]))
    for s, certs in sorted(rep["signers"].items()):
        print("  signer %s: %s" % (s, ", ".join(certs)))
    print("  dex: %s; native: %s" % (", ".join(rep["dex"]) or "none", ", ".join(rep["native"]) or "none"))
    print("  gmpf interfaces named: %s" % (", ".join(sorted(rep["gmpf"])) or "none"))
    print("  transact sites: %s; Method.invoke: %s" % (", ".join(rep["transact"]) or "none",
                                                        ", ".join(rep["reflect"]) or "none"))
    for r in rep["rows"]:
        print("  call %s\t0x%08X\t%s" % r[:3] + ("" if r[3] is None else "\targs:" + r[3]))
    if "--expect" not in opts:
        return 0
    exp = expect_from(opts["--expect"], opts.get("--name", "HAL_APP"))
    if "--cert" in opts:
        exp["cert"] = opts["--cert"]
    bad = problems(rep, exp)
    for b in bad:
        print("x50_apk_check: ERROR " + b)
    print("x50_apk_check: %s" % ("OK: %d calls" % len(rep["rows"]) if not bad else "FAILED (%d)" % len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
