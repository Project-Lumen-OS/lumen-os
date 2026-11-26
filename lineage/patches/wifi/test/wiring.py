#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""wiring.py: the patched SupplicantP2pIfaceHalAidlImpl.java sends each AIDL V3 P2P call only through
SupplicantP2pV3Calls and falls back to the V2 call (lineage/patches/wifi/0001). Run by test/run.sh:

  python3 wiring.py PATCHED_SupplicantP2pIfaceHalAidlImpl.java

A source contract, checked on the real patched file: the class itself has its own host test, the calls on the unit
are proven live (the trial check in docs/casting/WIFI_P2P_HAL.md).
"""
import re
import sys

V3 = {"FIND": "findWithParams", "CONNECT": "connectWithParams", "EXT_LISTEN": "configureExtListenWithParams"}
errors = []


def flat(s):
    return re.sub(r"\s+", " ", s)


def body(src, signature):
    """The body of the one method whose declaration matches the regex, braces balanced."""
    m = [x for x in re.finditer(signature, src)]
    if len(m) != 1:
        errors.append(f"{len(m)} declarations match {signature!r}")
        return ""
    i = src.index("{", m[0].end() - 1)
    depth = 0
    for j in range(i, len(src)):
        depth += {"{": 1, "}": -1}.get(src[j], 0)
        if depth == 0:
            return flat(src[i:j + 1])
    errors.append(f"unbalanced {signature!r}")
    return ""


def order(what, text, *parts):
    """Every part occurs in text, in this order."""
    pos = 0
    for p in parts:
        k = text.find(p, pos)
        if k < 0:
            errors.append(f"{what}: missing (in order) {p!r}")
            return
        pos = k + len(p)


def catch_remote(text):
    """The text of the first catch (RemoteException e) block of a method body."""
    k = text.find("catch (RemoteException e) {")
    if k < 0:
        return ""
    depth = 0
    for j in range(text.index("{", k), len(text)):
        depth += {"{": 1, "}": -1}.get(text[j], 0)
        if depth == 0:
            return text[k:j + 1]
    return ""


def main(path):
    src = open(path).read()
    f = flat(src)
    if re.search(r"getCachedServiceVersion\(\) *(>= *3|< *3|> *2)", src):
        errors.append("a V3 call is still gated by the reported version number")
    if "private SupplicantP2pV3Calls mV3Calls = SupplicantP2pV3Calls.none();" not in f:
        errors.append("mV3Calls does not start with no V3 call")
    for key, name in V3.items():
        n = len(re.findall(r"mISupplicantP2pIface\." + name + r"\(", src))
        if n != 1:
            errors.append(f"{name}: {n} call sites on the interface (one, inside its own gated method)")
        if f.count(f"SupplicantP2pV3Calls.{key}") < 2:
            errors.append(f"{name}: not both gated and dropped by SupplicantP2pV3Calls.{key}")

    setup = body(src, r"public boolean setupIface\(@NonNull String ifaceName\)")
    order("setupIface", setup, "mISupplicantP2pIface = iface; mV3Calls = v3CallsOf(iface);", "registerCallback(")

    v3of = body(src, r"private SupplicantP2pV3Calls v3CallsOf\(@NonNull ISupplicantP2pIface iface\)")
    order("v3CallsOf", v3of, "int version = getCachedServiceVersion();", "String hash = null;",
          "if (version >= 3) {", "hash = iface.getInterfaceHash();", "catch (RemoteException e)",
          "SupplicantP2pV3Calls.of(version, hash)", "return calls;")
    if "supplicantServiceDiedHandler" in v3of or "handleRemoteException" in v3of:
        errors.append("v3CallsOf: a failed hash read must not drop the interface")

    find = body(src, r"public boolean findWithParams\(WifiP2pDiscoveryConfig config, int timeout\)")
    order("findWithParams gate", find, "if (!mV3Calls.has(SupplicantP2pV3Calls.FIND)) {",
          "return find(config.getScanType(), config.getFrequencyMhz(), timeout);", "}",
          "mISupplicantP2pIface.findWithParams(halInfo);")
    order("findWithParams unimplemented", catch_remote(find),
          "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, e.getMessage())) {",
          "return find(config.getScanType(), config.getFrequencyMhz(), timeout);", "}",
          "handleRemoteException(e, methodStr);")

    cwp = body(src, r"private String connectWithParams\(boolean joinExistingGroup, byte\[\] peerAddress,")
    order("connectWithParams unimplemented", catch_remote(cwp),
          "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.CONNECT, e.getMessage())) {",
          "return null;", "}", "handleRemoteException(e, methodStr);")
    con = body(src, r"public String connect\(WifiP2pConfig config, boolean joinExistingGroup\)")
    order("connect", con, "if (mV3Calls.has(SupplicantP2pV3Calls.CONNECT)) {", "String pin = connectWithParams(",
          "if (pin != null || mV3Calls.has(SupplicantP2pV3Calls.CONNECT)) { return pin; }", "}",
          "return mISupplicantP2pIface.connect(", "handleRemoteException(e, methodStr);")

    lwp = body(src, r"private boolean configureExtListenWithParams\(int periodInMillis, int intervalInMillis,")
    order("configureExtListenWithParams unimplemented", catch_remote(lwp),
          "if (mV3Calls.dropIfUnimplemented(SupplicantP2pV3Calls.EXT_LISTEN, e.getMessage())) {",
          "return false;", "}", "handleRemoteException(e, methodStr);")
    lis = body(src, r"public boolean configureExtListen\(boolean enable, int periodInMillis, int intervalInMillis,")
    order("configureExtListen", lis, "if (mV3Calls.has(SupplicantP2pV3Calls.EXT_LISTEN)) {",
          "boolean done = configureExtListenWithParams(",
          "if (done || mV3Calls.has(SupplicantP2pV3Calls.EXT_LISTEN)) { return done; }", "}",
          "mISupplicantP2pIface.configureExtListen(periodInMillis, intervalInMillis);")

    for e in errors:
        print("FAIL " + e)
    if errors:
        return 1
    print("wiring OK (3 V3 calls gated by hash, unimplemented falls back to V2, interface kept)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
