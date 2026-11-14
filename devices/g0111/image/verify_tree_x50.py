#!/usr/bin/env python3
"""verify_tree_x50.py: the X50 test tree (or image, mounted) against the Z9X 1.0.9 base image (mounted), entry by
entry: type, owner, group, mode (not of symlinks), file content (sha256), link target, SELinux label, file capability. Run as root
by compose_x50.sh (step 7 on the staged tree, step 9 on the mounted image):

  python3 -I verify_tree_x50.py BASE NEW IN V BID

IN is the composer's input folder (remove.txt, x50_prop.txt, x50_model_prop.txt, x50_props.py, x50_netadb.rc,
x50_blobs.sh, x50_blobs.rc, x50_blobs_allow.txt, x50_nui.rc, blobs_stock_dsu.sh, x50_apk_check.py and stage/, the APKs built and signed
on the Mac).
Allowed differences, and nothing else:
  removed   every path of remove.txt with everything below it;
  changed   system/build.prop and system/product/etc/build.prop (exactly the base files with the edits of
            x50_prop.txt and the lines of x50_model_prop.txt, x50_props.py check), system/etc/security/otacerts.zip
            (a zip without any certificate: no OTA payload verifies on the X50 test image); owner, mode and
            label of all three as in the base;
  added     system/etc/init/x50_netadb.rc, x50_blobs.rc, x50_nui.rc and system/etc/z9x/x50_blobs.sh,
            x50_blobs_allow.txt, blobs_stock_dsu.sh (the DSU-trial reader x50_blobs.sh sources), each byte-identical to its IN file (root:root, 0644, 0755 for the script,
            system_file), and two 0-byte placeholders the X50 blobs reader binds the user's own files over:
            system/bin/nativeui (root:shell 0755 nativeui_main_exec, the stock vendor_file_contexts label) and
            system/lib64/xgimi.hardware.gmpf-V1-ndk.so (root:root 0644 system_lib_file); the eye-follow fail-safe
            system/app/X50EyeGuard/ (root:root 0755 system_file) with X50EyeGuard.apk (root:root 0644
            system_file), byte-identical to IN/stage/X50EyeGuard.apk.
Then, on NEW: no file has the sha256 of a file of the blob sets (the base's system/etc/z9x/blobs_allow.txt and
IN/x50_blobs_allow.txt, the eye-protection drawer included: the MediaTek / XGIMI files an image never carries); no
APK, jar, dex or ELF names the X50's AIDL gmpf
(xgimi.hardware.gmpf.I..., Lxgimi/hardware/gmpf/I...) or the Z9X HIDL gmpf (xgimi.hardware.gmpf@), with one
exception, HAL_APP below: the X50EyeGuard APK, and only when it is the staged file, signed (v2/v3) by the g0111
platform certificate, names no gmpf interface but DisplayManager, has no native code and no Method.invoke, one
IBinder.transact site, and its call table is exactly the eight calls of HAL_APP (x50_apk_check.py reads the dex);
no Z9X updater APK; no reader of the vendor's native-UI fifo (/data/vendor/tmp/nuififo): the Z9X z9x_nui ACKs without
drawing, and on the X50 the human-shape eye-protection mask is drawn by the stock nativeui, so an ACK-only reader
would tell the vendor the person is masked. The only /system/etc files that may
name the fifo are x50_blobs.sh and x50_nui.rc (checked byte for byte above), which only remove it while no reader
runs. Every ART, dexopt and zygote property of the two build.prop files is the base's, and
ro.dalvik.vm.enable_uffd_gc matches the GC the boot image and services.odex were compiled for (oat key
concurrent-copying: false = the userfaultfd GC, which needs enable_uffd_gc=true). Directory sizes and times are
ignored.
Prints a summary and every violation; exit 0 = OK, 1 = violations.
"""
import hashlib
import os
import re
import stat
import subprocess
import sys
import zipfile

base, new, IN, V, BID = sys.argv[1:6]
# 2026-11-11: the X50 model of the image (g0111 X50 Ultra, the default; g0100 X50 Ultra Max) and its two prop files
# (IN names; compose_x50.sh passes what it applied); a release-shaped V (1.0.10-beta1) is the PUBLIC variant: no
# network adb, no test adb props, no x50_netadb.rc
C = sys.argv[6] if len(sys.argv) > 6 else "g0111"
PROPF = sys.argv[7] if len(sys.argv) > 7 else "x50_prop.txt"
MPROPF = sys.argv[8] if len(sys.argv) > 8 else "x50_model_prop.txt"
X50_MODELS = {"g0111": ("G0111", "x50"), "g0100": ("G0100", "x50max")}
if C not in X50_MODELS:
    sys.exit("verify_tree_x50: %s is not an X50 model (g0111, g0100)" % C)
MODEL, PREFIX = X50_MODELS[C]
PUBLIC = re.fullmatch(r"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}(-(beta|rc)[0-9]{1,2})?", V) is not None
if not PUBLIC and not re.fullmatch(r"t[0-9]{1,2}", V):
    sys.exit("verify_tree_x50: variant %r is neither tN nor a release version" % V)
WANT_PROPF = {("g0111", False): "x50_prop.txt", ("g0111", True): "x50_prop_pub.txt", ("g0100", True): "x50max_prop_pub.txt"}
WANT_MPROPF = {"g0111": "x50_model_prop.txt", "g0100": "x50max_model_prop.txt"}
if WANT_PROPF.get((C, PUBLIC)) != PROPF or WANT_MPROPF[C] != MPROPF:
    sys.exit("verify_tree_x50: %s %s takes %s + %s, not %s + %s" % (C, "public" if PUBLIC else "test",
             WANT_PROPF.get((C, PUBLIC)), WANT_MPROPF[C], PROPF, MPROPF))
BASE_ROOT, NEW_ROOT = base, new   # the loops below reuse the names base / new
bad = []
sys.path.insert(0, IN)
sys.dont_write_bytecode = True   # IN belongs to the build user; this script runs as root
import x50_apk_check  # noqa: E402  (IN/x50_apk_check.py, staged with this script)
import x50_power_keys  # noqa: E402  (IN/x50_power_keys.py, staged with this script)
import x50_diag  # noqa: E402  (IN/x50_diag.py, staged with this script)
import x50_perm_pkgs  # noqa: E402  (IN/x50_perm_pkgs.py + IN/apkinfo.py, staged with this script)

# The one APK of the X50 image that may name the gmpf HAL (maintainer's decision 2026-11-07, X50 work approved;
# the design notes): the eye-follow fail-safe apps/X50EyeGuard, built and signed on the
# Mac with the g0111 platform key (tools/devices/build_apps.sh g0111 build/g0111/stage) and staged by
# tools/image/compose.sh (stage_dir). "calls" = devices/g0111/research/allowed_x50eyeguard.tsv (service, code,
# method; '-' for the version and hash guard; no setter of the user's eye-protection settings, gsi-research/
# the design notes), "cert" = devices/g0111/certs/platform.x509.pem (sha256 of the DER):
# devices/g0111/test/x50_apk_check_test.sh holds both to their sources.
HAL_APP = {
    "path": "system/app/X50EyeGuard/X50EyeGuard.apk",
    "staged": "stage/X50EyeGuard.apk",
    "cert": "3a741e26026295ba510b6135b98294198013e271b8916db50a80a45be6fd76d2",
    "ifaces": ["xgimi.hardware.gmpf.IDisplayManager", "xgimi.hardware.gmpf.IPowerManager",
               "xgimi.hardware.gmpf.ISystemManager"],
    "calls": [["-", 0x00FFFFFF, "getInterfaceVersion"],
              ["-", 0x00FFFFFE, "getInterfaceHash"],
              ["DisplayManager", 127, "getHumanDetectMode"],
              ["DisplayManager", 62, "getHumanDetectOnOff"],
              ["SystemManager", 32, "setBootAnimState"],
              ["SystemManager", 33, "getBootAnimState"],
              ["SystemManager", 36, "setSystemUIState"],
              ["PowerManager", 2, "goToBootCompleted"]],
}


# The second APK that may name the gmpf HAL (t11): the gear-key menu apps/X50Projector, built and signed like
# X50EyeGuard. "calls" = devices/g0111/research/allowed_x50projector.tsv row for row (service, code, method: the X50
# codes found by method name; t12: 43 rows on 7 interfaces, the eye-protection switch 61 and mode 126 only as the
# stock user rows; t14: + MotionDetectionManager 25 getMotionAttitude with the stock angle dialog's 24 / 15, x50-hdmi's
# 18 HDMI / sound rows and the picture page 33 / 34, 172 / 174, 113 / 114 = 70 (t15: contrast enhancement 166 / 167 gone,
# the X50 settings tree has no such node; t17: power-on AF 4 / 5 and the AF-when-moved getter MotionDetectionManager 5
# gone, the X50 tree has only smart AF = 85 + MotionDetectionManager 4: 67; t18: the projection Auto radio reads
# MotionDetectionManager 9 getAutoReverse and sends 8 true: 68; t20, decision 2026-11-10: + the keystone reset's
# 11 / 63 / 88, the light level 6 / 7 / 152 and the zoom and lens shift screen's 15 calls = 89, each t20 row with its
# argument range as a 4th field, 11 / 88 / 3 unbanned for this app only; core-menu-x50game: + the stock game
# menu's 12 calls = 101, each with its range); no 101 / 66, no vendor
# heartbeat 99 / 100, no laser / ambient light getter, no other BANNED call). It stays a literal
# because x50_apk_check.expect_from reads it with ast (compose's pre-check and the tests use this one copy);
# devices/g0111/test/x50_menu_apk_check_test.sh refuses any difference from the tsv and from the GmpfIface list of the app.
MENU_APP = {
    "path": "system/priv-app/X50Projector/X50Projector.apk",
    "staged": "stage/X50Projector.apk",
    "cert": "3a741e26026295ba510b6135b98294198013e271b8916db50a80a45be6fd76d2",
    "ifaces": ["xgimi.hardware.gmpf.IDisplayManager", "xgimi.hardware.gmpf.IGmAudioManager",
               "xgimi.hardware.gmpf.IGmFactoryManager", "xgimi.hardware.gmpf.IGmTvManager",
               "xgimi.hardware.gmpf.IMotionDetectionManager",
               "xgimi.hardware.gmpf.IProjectorFocusManager", "xgimi.hardware.gmpf.ISystemManager"],
    "calls": [["-", 0x00FFFFFF, "getInterfaceVersion"],
              ["-", 0x00FFFFFE, "getInterfaceHash"],
              ["DisplayManager", 38, "correctKeystoneReset"],
              ["DisplayManager", 84, "getCurrentKeystoneMode"],
              ["DisplayManager", 85, "switchKeystoneMode"],
              ["DisplayManager", 62, "getHumanDetectOnOff"],
              ["DisplayManager", 127, "getHumanDetectMode"],
              ["ProjectorFocusManager", 2, "manualFocus"],
              ["ProjectorFocusManager", 8, "sendFocusStatus"],
              ["ProjectorFocusManager", 27, "autoKst"],
              ["ProjectorFocusManager", 33, "setProjScreenAdp"],
              ["ProjectorFocusManager", 34, "getProjScreenAdp"],
              ["ProjectorFocusManager", 35, "setProjCurtainAdp"],
              ["ProjectorFocusManager", 36, "getProjCurtainAdp"],
              ["ProjectorFocusManager", 85, "setPowerOnAFUseDistance"],
              ["ProjectorFocusManager", 86, "getPowerOnAFUseDistance"],
              ["MotionDetectionManager", 4, "setAngTriggerAF"],
              ["MotionDetectionManager", 6, "setAccTriggerAK"],
              ["MotionDetectionManager", 7, "getAccTriggerAK"],
              ["GmFactoryManager", 45, "savePowerOnAKFlag"],
              ["GmFactoryManager", 46, "getPowerOnAKFlag"],
              ["DisplayManager", 61, "setHumanDetectOnOff", "0..1"],
              ["DisplayManager", 126, "setHumanDetectMode", "0..1"],
              ["DisplayManager", 2, "setProjectorPutMode"],
              ["DisplayManager", 3, "getProjectorPutMode"],
              ["DisplayManager", 15, "setCorrectKeystoneFullPiont"],
              ["DisplayManager", 16, "getCorrectKeystoneFullPiont"],
              ["DisplayManager", 64, "checkTrapezoidCoordinate"],
              ["MotionDetectionManager", 8, "setAutoReverse"],
              ["MotionDetectionManager", 9, "getAutoReverse"],
              ["MotionDetectionManager", 25, "getMotionAttitude"],
              ["SystemManager", 140, "setPushPullScreenOnOff"],
              ["GmTvManager", 5, "setContrast"],
              ["GmTvManager", 6, "getContrast"],
              ["GmTvManager", 7, "setBrightness"],
              ["GmTvManager", 8, "getBrightness"],
              ["GmTvManager", 9, "setSaturation"],
              ["GmTvManager", 10, "getSaturation"],
              ["GmTvManager", 11, "setSharpness"],
              ["GmTvManager", 12, "getSharpness"],
              ["GmAudioManager", 6, "setSoundeffect"],
              ["GmAudioManager", 7, "getSoundeffect"],
              # x50-hdmi
              ["GmTvManager", 3, "switchInputSource"],
              ["GmTvManager", 4, "getCurrentInputSource"],
              ["GmTvManager", 39, "getEdidVersion"],
              ["GmTvManager", 40, "setEdidVersion"],
              ["GmTvManager", 63, "setHdmiRange"],
              ["GmTvManager", 64, "getHdmiRange"],
              ["GmTvManager", 65, "setEARCEnable"],
              ["GmTvManager", 66, "getEARCEnableState"],
              ["GmTvManager", 79, "getEARCStatus"],
              ["GmTvManager", 129, "setAllmEnable"],
              ["GmTvManager", 130, "getAllmEnableState"],
              ["GmAudioManager", 2, "setAudioOutput"],
              ["GmAudioManager", 3, "getAudioOutput"],
              ["DisplayManager", 76, "setGameModeOption"],
              ["DisplayManager", 77, "getGameModeOption"],
              ["DisplayManager", 146, "setGameModeState"],
              ["DisplayManager", 147, "setGameModeType"],
              ["DisplayManager", 148, "getGameModeProp"],
              ["MotionDetectionManager", 15, "temporarySwitchTrigger"],
              ["MotionDetectionManager", 24, "setMotionACalEnable"],
              ["GmTvManager", 33, "setPictureMode"],
              ["GmTvManager", 34, "getPictureMode"],
              ["DisplayManager", 172, "setDynamicBlackType"],
              ["DisplayManager", 174, "getDynamicBlackType"],
              ["GmTvManager", 113, "setTvDynamicContrastEnable"],
              ["GmTvManager", 114, "getTvDynamicContrastEnable"],
              # t20 (decision 2026-11-10): the whole keystone reset, the light level, the zoom and lens shift
              # screen; each with its argument range (4th field = the Call's ArgSpec, enforced by the transport);
              # 11, 88 and 3 only through devices/g0111/research/unbanned_x50projector.tsv
              ["DisplayManager", 11, "correctKeystone", "50"],
              ["DisplayManager", 63, "zoomControl", "5,10;11,10;12,10;13,10;18,1"],
              ["ProjectorFocusManager", 88, "setTiltShiftMove", "1..5,f:0;13,f:0"],
              ["DisplayManager", 6, "setDlpLumensLevel", "1..10"],
              ["DisplayManager", 7, "getDlpLumensLevel", "-"],
              ["DisplayManager", 152, "getLensDispMode", "-"],
              ["DisplayManager", 93, "zoomStart", "0"],
              ["DisplayManager", 94, "zoomFinish", "0"],
              ["DisplayManager", 95, "setOpticalZoomStep", "0..64"],
              ["DisplayManager", 96, "setDigitalZoomStep", "-200..200"],
              ["DisplayManager", 97, "getZoomStepRange", "-"],
              ["DisplayManager", 98, "getCurrentZoomStep2", "-"],
              ["DisplayManager", 185, "setKstPrepare", "0..1,1..2"],
              ["DisplayManager", 189, "SetOpticalZoomLimitStep", "-16..0"],
              ["DisplayManager", 190, "GetCurrentZoomLimitStep", "-"],
              ["ProjectorFocusManager", 3, "autoFocus", "47"],
              ["ProjectorFocusManager", 94, "getShiftProfessionalMode", "-"],
              ["ProjectorFocusManager", 104, "setTiltShiftKstAlgInfo", "-"],
              ["ProjectorFocusManager", 105, "setTiltShiftCompensation", "-"],
              ["ProjectorFocusManager", 112, "getUitiltShiftSwithStatus", "0..1"],
              ["SystemManager", 147, "setPushPullScreenState", "0..1"],
              # core-menu-x50game: the stock X50 game menu's calls, each with
              # its argument range; gated in GameSequences (getter in range first, every write read back)
              ["GmTvManager", 145, "setVRRModeEnable", "0..1,1..3"],
              ["GmTvManager", 146, "getVRREnableState", "1..3"],
              ["GmTvManager", 147, "setGamePictureMode", "0..4,1..3"],
              ["GmTvManager", 148, "getGamePictureMode", "1..3"],
              ["GmTvManager", 149, "setDarkBoostOnoff", "0..1,0..4,1..3"],
              ["GmTvManager", 150, "getDarkBoostState", "0..4,1..3"],
              ["GmTvManager", 120, "setAisrType", "0..1"],
              ["GmTvManager", 121, "getAisrType", "-"],
              ["GmTvManager", 122, "getVideoInfo", "-"],
              ["GmAudioManager", 75, "setGamingSoundEffectMode", "190..194"],
              ["GmAudioManager", 76, "getGamingSoundEffectMode", "-"],
              ["DisplayManager", 179, "getDispOutputFps", "-"]],
}


def rel_walk(top):
    out = {}
    for d, dirs, files in os.walk(top):
        for n in dirs + files:
            p = os.path.join(d, n)
            out[os.path.relpath(p, top)] = p
    return out


def xattr(p, name):
    try:
        return os.getxattr(p, name, follow_symlinks=False)
    except OSError:
        return None


def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def meta(p, content=True):
    st = os.lstat(p)
    m = {"type": stat.S_IFMT(st.st_mode), "uid": st.st_uid, "gid": st.st_gid, "mode": stat.S_IMODE(st.st_mode),
         "label": xattr(p, "security.selinux"), "cap": xattr(p, "security.capability")}
    if stat.S_ISLNK(st.st_mode):
        # Linux ignores a symlink's permission bits and cannot set them (rsync, ext4 staging): links are compared by
        # target, owner and label only
        del m["mode"]
        m["link"] = os.readlink(p)
    elif stat.S_ISREG(st.st_mode) and content:
        m["sha"] = sha(p)
    return m


def lines(path):
    out = []
    for raw in open(path, encoding="utf-8"):
        s = raw.rstrip("\n")
        if s.strip() and not s.startswith("#"):
            out.append(s.split("\t", 1)[0])
    return out


B = rel_walk(base)
N = rel_walk(new)
REMOVE = lines(os.path.join(IN, "remove.txt"))
removed = set()
for r in REMOVE:
    if r not in B:
        bad.append("remove.txt names %s, which is not in the base" % r)
    removed |= {k for k in B if k == r or k.startswith(r + "/")}
# the X50 power keys: these key layouts are the base's with exactly the edit of x50_power_keys.py (checked below)
KL = "system/product/usr/keylayout"
DIAG = "system/etc/xgimi/z9x_diag.sh"
CHANGED = {"system/build.prop", "system/product/etc/build.prop", "system/etc/security/otacerts.zip", DIAG} \
    | {KL + "/" + n for n in x50_power_keys.TABLE}
# t12: the platform set on the g0111 keys (devices/g0111/image/sign_x50_base.py, compose_x50.sh step 3b): every row of
# IN/stage/base_signed/base_signed.tsv is a base file (its base sha256) replaced by the signed one (its signed
# sha256) at the same path, or a .capex replaced by its signed .apex (maintainer, mode, label of the .capex)
SIGNED = {}
for raw in open(os.path.join(IN, "stage", "base_signed", "base_signed.tsv"), encoding="utf-8"):
    if raw.startswith("#") or not raw.strip():
        continue
    f = raw.rstrip("\n").split("\t")
    if len(f) != 5 or f[1] not in ("apk", "apex", "mac_permissions"):
        bad.append("base_signed.tsv: bad row %r" % raw)
        continue
    SIGNED[f[0]] = (f[1], f[2], f[3], f[4])
SIGNED_NEW = {}   # signed .apex path -> the .capex it replaces
for k, (kind, old, new, rep) in SIGNED.items():
    if rep == k:
        CHANGED.add(k)
    elif kind == "apex" and rep.endswith(".capex") and k == rep[:-len(".capex")] + ".apex":
        removed.add(rep)
        SIGNED_NEW[k] = rep
    else:
        bad.append("base_signed.tsv: %s replaces %s" % (k, rep))
for must in ("system/framework/framework-res.apk", "system/etc/selinux/plat_mac_permissions.xml",
             "system/apex/com.android.permission.apex"):
    if must not in SIGNED:
        bad.append("base_signed.tsv lacks %s: not the g0111 platform set" % must)
# t12 (t11 review P0.3): Lumen Home of this tree, built and signed with the g0111 platform key on the Mac
# (devices/g0111/image/build_home_x50.sh, staged as IN/stage/Z9xHome.apk), in place of the base's; its base oat files go
HOME_APP = {"path": "system/priv-app/Z9xHome/Z9xHome.apk", "staged": "stage/Z9xHome.apk"}
HOME_OAT = "system/priv-app/Z9xHome/oat"
CHANGED.add(HOME_APP["path"])
removed |= {k for k in B if k == HOME_OAT or k.startswith(HOME_OAT + "/")}
# t17 (t16 live BUG C): the HDMI input app of this tree (devices/g0111/app/X50TvInput.layers: apps/Z9xTvInput plus the
# X50 source picker look), built and signed with the g0111 platform key on the Mac (tools/devices/build_apps.sh g0111,
# staged as IN/stage/X50TvInput.apk), in place of the base's; its base oat files go
TVIN_APP = {"path": "system/app/Z9xTvInput/Z9xTvInput.apk", "staged": "stage/X50TvInput.apk"}
TVIN_OAT = "system/app/Z9xTvInput/oat"
CHANGED.add(TVIN_APP["path"])
removed |= {k for k in B if k == TVIN_OAT or k.startswith(TVIN_OAT + "/")}
SYSF = b"u:object_r:system_file:s0\x00"
# the platform certificate of the model (devices/<C>/certs/platform.x509.pem, sha256 of the DER): HAL_APP and MENU_APP
# above carry the g0111 one as a literal (x50_apk_check.expect_from reads it); the g0100 image is checked against its own
PLATFORM_CERTS = {"g0111": "3a741e26026295ba510b6135b98294198013e271b8916db50a80a45be6fd76d2",
                  "g0100": "9eaca977d74fd750c0ed1f6d4e89579cb93dcc2354eae83d60f9d4694da6ab4d"}
HAL_APP["cert"] = MENU_APP["cert"] = PLATFORM_CERTS[C]
# path: (uid, gid, mode, label, the IN file it is a copy of, None for a 0-byte placeholder, or DIR for a folder)
DIR = "/"
ADDED = {"system/etc/init/x50_netadb.rc": (0, 0, 0o644, SYSF, "x50_netadb.rc"),
         "system/etc/init/x50_blobs.rc": (0, 0, 0o644, SYSF, "x50_blobs.rc"),
         "system/etc/init/x50_nui.rc": (0, 0, 0o644, SYSF, "x50_nui.rc"),
         "system/etc/z9x/x50_blobs.sh": (0, 0, 0o755, SYSF, "x50_blobs.sh"),
         "system/etc/z9x/x50_blobs_allow.txt": (0, 0, 0o644, SYSF, "x50_blobs_allow.txt"),
         "system/etc/z9x/blobs_stock_dsu.sh": (0, 0, 0o644, SYSF, "blobs_stock_dsu.sh"),
         "system/bin/nativeui": (0, 2000, 0o755, b"u:object_r:nativeui_main_exec:s0\x00", None),
         "system/lib64/xgimi.hardware.gmpf-V1-ndk.so": (0, 0, 0o644, b"u:object_r:system_lib_file:s0\x00", None),
         os.path.dirname(HAL_APP["path"]): (0, 0, 0o755, SYSF, DIR),
         HAL_APP["path"]: (0, 0, 0o644, SYSF, HAL_APP["staged"]),
         # the X50 power-key RRO (apps/X50PowerOverlay: resources only, no dex), the staged bytes
         "system/product/overlay/X50PowerOverlay.apk": (0, 0, 0o644, SYSF, "stage/X50PowerOverlay.apk"),
         # install from a DSU try (t8, apps/LumenDsuInstall): the app, its init rc (gated, checked below), its script
         "system/priv-app/LumenDsuInstall": (0, 0, 0o755, SYSF, DIR),
         "system/priv-app/LumenDsuInstall/LumenDsuInstall.apk": (0, 0, 0o644, SYSF, "stage/LumenDsuInstall.apk"),
         "system/etc/init/lumen_dsu_install.rc": (0, 0, 0o644, SYSF, "lumen_dsu_install.rc"),
         "system/etc/lumen": (0, 0, 0o755, SYSF, DIR),
         "system/etc/lumen/lumen_dsu_install.sh": (0, 0, 0o755, SYSF, "lumen_dsu_install.sh"),
         # the projector menu with the remote pairing (t11, apps/X50Projector; t10 had the pairing alone as X50Remote):
         # priv-app (privileged permissions with privapp control off) and its runtime Bluetooth permissions; the second
         # HAL app, MENU_APP below
         os.path.dirname(MENU_APP["path"]): (0, 0, 0o755, SYSF, DIR),
         MENU_APP["path"]: (0, 0, 0o644, SYSF, MENU_APP["staged"]),
         "system/product/etc/default-permissions/x50-projector.xml": (0, 0, 0o644, SYSF, "x50-projector.xml")}
# the public variant (a release version, 2026-11-11): no network adb, so no x50_netadb.rc (an extra file is refused)
if PUBLIC:
    del ADDED["system/etc/init/x50_netadb.rc"]
EMPTY = hashlib.sha256(b"").hexdigest()

for k in sorted(removed):
    if k in N:
        bad.append("removed path still there: " + k)
for k in sorted(set(N) - set(B)):
    if k not in ADDED and k not in SIGNED_NEW:
        bad.append("added path not allowed: " + k)
for k, rep in sorted(SIGNED_NEW.items()):
    if k not in N or rep not in B:
        bad.append("signed APEX %s (for %s) missing" % (k, rep))
        continue
    mb, mn = meta(B[rep], False), meta(N[k], False)
    if mb != mn:
        bad.append("signed APEX %s does not keep the owner, mode and label of %s: %s / %s" % (k, rep, mn, mb))
for k, (uid, gid, mode, label, src) in ADDED.items():
    if k in B:
        bad.append("added %s is already in the base" % k)
    if k not in N:
        bad.append("missing added file " + k)
        continue
    m = meta(N[k])
    kind = stat.S_IFDIR if src == DIR else stat.S_IFREG
    if (m["type"], m["uid"], m["gid"], m["mode"], m["label"]) != (kind, uid, gid, mode, label):
        bad.append("added %s has %s, not %d:%d %o %r" % (k, m, uid, gid, mode, label))
    if src == DIR:
        continue
    if m.get("sha") != (sha(os.path.join(IN, src)) if src else EMPTY):
        bad.append("added %s is not %s" % (k, "the staged " + src if src else "a 0-byte placeholder"))
same = 0
for k in sorted(set(B) - removed):
    if k not in N:
        bad.append("base path missing: " + k)
        continue
    mb, mn = meta(B[k], k not in CHANGED), meta(N[k], k not in CHANGED)
    if mb != mn:
        diff = sorted(x for x in set(mb) | set(mn) if mb.get(x) != mn.get(x))
        bad.append("differs from the base: %s (%s)" % (k, ", ".join(diff)))
    elif k not in CHANGED:
        same += 1
for k in sorted(CHANGED):
    if k in N and k in B and stat.S_ISREG(os.lstat(N[k]).st_mode) and sha(N[k]) == sha(B[k]):
        bad.append("expected a change, but the file is the base's: " + k)

# install from a DSU try: every trigger of its rc needs ro.gsid.image_running=1, its services are disabled oneshots
# that run only the root script, and no build.prop of the image opens the X50 owner switch
DSU_RC = "system/etc/init/lumen_dsu_install.rc"
if DSU_RC in N:
    rc = open(N[DSU_RC], encoding="utf-8").read().splitlines()
    for ln in rc:
        if ln.startswith("on ") and not re.fullmatch(r"on property:sys\.lumen\.dsu\.request=(check|install|reboot) && property:ro\.gsid\.image_running=1", ln):
            bad.append("lumen_dsu_install.rc: trigger without ro.gsid.image_running=1: " + ln)
    svc = [ln for ln in rc if ln.startswith("service ")]
    if len(svc) != 3 or any(not ln.endswith(" /system/bin/sh /system/etc/lumen/lumen_dsu_install.sh " + m)
                            for ln, m in zip(svc, ("check", "install", "reboot"))):
        bad.append("lumen_dsu_install.rc: not the three services of apps/LumenDsuInstall/image: %r" % svc)
    if sum(1 for ln in rc if ln.strip() == "disabled") != 3:
        bad.append("lumen_dsu_install.rc: every service must be disabled")
# test image log buffers (t17, t16 live BUG B): the image's Android 14 logd ignores every persist.logd.size* /
# ro.logd.size* property (LogSize.cpp reads them only with ro.hardware.type=automotive) and sets 256 KiB at each Init(),
# at its start and at every logd --reinit; so x50_netadb.rc sizes the buffers with logd's setLogSize command (logcat -G)
# from three one-shot services once the boot has completed, and nothing in the image may run a logd --reinit after that
# (it would set 256 KiB again). Checked here: the exact block and services, no size property and no reinit of our own,
# every start of init.rc's logd-reinit outside a boot-completed trigger, the logd gate itself (the reason) and the
# policy that gives the services a domain allowed to talk to logd (init refuses a service without one, even permissive)
NR = "system/etc/init/x50_netadb.rc"
LOGSIZE = [("main", "16M"), ("system", "4M"), ("crash", "1M")]
nbad_logs = len(bad)
if NR in N:
    nr = [ln for ln in open(N[NR], encoding="utf-8").read().splitlines() if not ln.lstrip().startswith("#")]
    want = ["on property:sys.boot_completed=1"] + ["    start x50-logsize-" + buf for buf, _ in LOGSIZE]
    at = [i for i, ln in enumerate(nr) if ln == want[1]]
    if len(at) != 1 or nr[at[0] - 1:at[0] - 1 + len(want)] != want:
        bad.append(NR + ": the log buffers must be sized in one 'on property:sys.boot_completed=1' block: " + " / ".join(w.strip() for w in want))
    for buf, size in LOGSIZE:
        svc = "service x50-logsize-%s /system/bin/logcat -b %s -G %s" % (buf, buf, size)
        if svc not in nr or nr[nr.index(svc) + 1:nr.index(svc) + 5] != ["    oneshot", "    disabled", "    user logd", "    group log"]:
            bad.append(NR + ": %r must be the one-shot, disabled logcat -G as user logd / group log" % svc)
        elif nr[nr.index(svc) + 5:nr.index(svc) + 6] not in ([], [""]):
            bad.append(NR + ": %r takes no other option (no seclabel: init's logcat_exec -> logpersist transition)" % svc)
    if sum(1 for ln in nr if ln.startswith("service ")) != 3 or any(ln.startswith("service ") and "x50-logsize-" not in ln for ln in nr):
        bad.append(NR + ": the only services are the three x50-logsize ones (adbd is the base's)")
    if any(re.match(r"\s*setprop\s+(persist|ro)\.logd\.size", ln) for ln in nr):
        bad.append(NR + ": no logd size property (Android 14 logd ignores them; t14 / t15 relied on them)")
    if any("--reinit" in ln or re.match(r"\s*start\s+(x50-)?logd-reinit\b", ln) for ln in nr):
        bad.append(NR + ": no logd --reinit (it sets the 256 KiB default again, after logcat -G too)")
    if any(ln.startswith("on property:persist.logd.size") for ln in nr):
        bad.append(NR + ": no trigger on the vendor's log size value (it ran too early in t14)")
    if any(re.match(r"\s*(stop |setprop (persist\.)?log\.tag)", ln) for ln in nr):
        bad.append(NR + ": must not stop a service or silence a log tag")
    # order: no rc of the image starts a logd reinit from a boot-completed trigger (after our logcat -G)
    for rc in sorted(k for k in N if k.startswith("system/etc/init/") and k.endswith(".rc")):
        trig = None
        for ln in open(N[rc], encoding="utf-8", errors="replace").read().splitlines():
            if ln.lstrip().startswith("#"):
                continue
            if ln.startswith("on "):
                trig = ln
            elif ln.startswith("service ") or ln.startswith("import "):
                trig = None
            elif trig and re.match(r"\s*(start\s+\S*logd-reinit|exec\b.*logd\s+--reinit)", ln) and "boot_completed" in trig:
                bad.append("%s: %r under %r would undo the log buffer sizes" % (rc, ln.strip(), trig))
    lg = "system/bin/logd"
    lgb = open(N[lg], "rb").read() if lg in N else b""
    if b"automotive" not in lgb or b"persist.logd.size." not in lgb:
        bad.append(lg + ": not the Android 14 logd whose size properties are gated (recheck x50_netadb.rc's logcat -G reason)")
    cil = "system/etc/selinux/plat_sepolicy.cil"
    cl = open(N[cil], encoding="utf-8", errors="replace").read() if cil in N else ""
    for rule in ("(typetransition init logcat_exec process logpersist)", "(allow init logpersist (process (transition)))",
                 "(allow logpersist logd (unix_stream_socket (connectto)))"):
        if rule not in cl:
            bad.append(cil + ": %s missing: the x50-logsize services would have no domain or no logd access" % rule)
    fc = "system/etc/selinux/plat_file_contexts"
    if fc in N and not re.search(r"(?m)^/system/bin/logcat\s+(--\s+)?u:object_r:logcat_exec:s0$", open(N[fc], encoding="utf-8").read()):
        bad.append(fc + ": /system/bin/logcat is not logcat_exec")
    if len(bad) == nbad_logs:
        print("verify_tree_x50: log buffers via logcat -G at boot completed (logd %s gates its size properties)" % lg)
# the projector menu's default permissions (the remote pairing): its own package, BLUETOOTH_SCAN and BLUETOOTH_CONNECT, nothing else
RP = "system/product/etc/default-permissions/x50-projector.xml"
if RP in N:
    xp = open(N[RP], encoding="utf-8").read()
    if re.findall(r'package="([^"]+)"', xp) != ["org.lumen.x50.projector"] or sorted(re.findall(r'<permission name="([^"]+)"', xp)) \
            != ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"]:
        bad.append(RP + ": not exactly BLUETOOTH_SCAN + BLUETOOTH_CONNECT for org.lumen.x50.projector")
for bp in ("system/build.prop", "system/product/etc/build.prop"):
    if bp in N and re.search(rb"(?m)^ro\.lumen\.dsu_install\.x50_write=", open(N[bp], "rb").read()):
        bad.append(bp + " sets the owner switch ro.lumen.dsu_install.x50_write (only the owner sets it, on the unit)")

# the two build.prop files: exactly the edits
r = subprocess.run([sys.executable, "-I", os.path.join(IN, "x50_props.py"), "check", os.path.join(IN, PROPF),
                    os.path.join(IN, MPROPF), B["system/build.prop"],
                    B["system/product/etc/build.prop"], N["system/build.prop"], N["system/product/etc/build.prop"],
                    V, BID], capture_output=True, text=True)
print(r.stdout.strip())
if r.returncode:
    bad.append("build.prop edits: " + (r.stdout + r.stderr).strip())
props = {}
for raw in open(N["system/product/etc/build.prop"], encoding="utf-8"):
    if "=" in raw and not raw.startswith("#"):
        props.setdefault(raw.split("=", 1)[0], raw.rstrip("\n").split("=", 1)[1])
want = {"ro.lumen.device": C, "ro.lumen.model": MODEL, "ro.lumen.family": "horse_popular",
        "ro.z9x.device": "x50", "ro.z9x.ota.gate": "0", "ro.z9x.rescue": "0", "ro.z9x.version_code": "0",
        "persist.vendor.xgimi.hd_server": "2"}
for k, v in want.items():
    if props.get(k) != v:
        bad.append("product build.prop: %s=%r, not %r" % (k, props.get(k), v))
for k in ("ro.z9x.ota.manifest_url", "ro.product.ab_ota_partitions"):
    if k in props:
        bad.append("product build.prop still has " + k)
if not props.get("ro.z9x.build_id", "").startswith("lumen-%s-%s-" % (PREFIX, V)):
    bad.append("ro.z9x.build_id %r is not a lumen-%s-%s build id" % (props.get("ro.z9x.build_id"), PREFIX, V))
if props.get("ro.lumen.version") != "%s-%s" % (PREFIX, V):
    bad.append("ro.lumen.version %r is not %s-%s" % (props.get("ro.lumen.version"), PREFIX, V))
sysprops = {}
for raw in open(N["system/build.prop"], encoding="utf-8"):
    if "=" in raw and not raw.startswith("#"):
        sysprops.setdefault(raw.split("=", 1)[0], raw.rstrip("\n").split("=", 1)[1])
if PUBLIC:
    # the public variant: adb only as the base (Lumen OS 1.0.9) has it, never open on the network
    for k in ("service.adb.tcp.port", "persist.adb.tcp.port"):
        if k in props or k in sysprops:
            bad.append("public image sets %s" % k)
    if props.get("ro.adb.secure", sysprops.get("ro.adb.secure", "1")) == "0":
        bad.append("public image sets ro.adb.secure=0")
    if "test" in sysprops.get("ro.build.display.id", "").split(", ")[-1]:
        bad.append("public image is called a test build: %r" % sysprops.get("ro.build.display.id"))
    if any(k.endswith("adb_keys") or k.endswith("/x50_netadb.rc") for k in N):
        bad.append("public image carries adb keys or x50_netadb.rc")
    print("verify_tree_x50: public variant %s-%s (%s): no network adb, no adb keys" % (PREFIX, V, MODEL))

# ART (t4): the image keeps the base's ART boot image and system_server odex, compiled
# for the userfaultfd GC (oat key concurrent-copying=false), and ro.dalvik.vm.enable_uffd_gc must say so: on Android
# 14, odrefresh takes the boot image on /system only while that property equals what the kernel offers
# (odr_common.cc CheckBuildUserfaultFdGc; the X50 kernel has CONFIG_USERFAULTFD=y, compose_x50.sh step 0), else it
# compiles the whole boot classpath before zygote at the first boot (Play 6 t4; the vendor's PWM wait). The stock
# X50's false belongs to its own boot image (concurrent-copying=true). So no ART, dexopt or zygote property of the
# two build.prop files may differ from the base (x50_prop.txt and x50_model_prop.txt name none), and the property
# must match the GC every boot image file was compiled for.
ART_KEY = re.compile(r"^(ro\.)?dalvik\.vm\.|^pm\.dexopt\.|^ro\.zygote$|^persist\.sys\.dalvik\.")


def prop_map(path):
    out = {}
    for raw in open(path, encoding="utf-8", errors="replace"):
        s = raw.strip()
        if s and not s.startswith("#") and "=" in s:
            k, v = s.split("=", 1)
            out[k.strip()] = v.strip()   # a later line wins, as in init
    return out


def oat_value(path, key):
    with open(path, "rb") as f:
        data = f.read(1 << 20)   # the oat header and its key-value store are at the start of .rodata
    i = data.find(key + b"\x00")
    if i < 0:
        return None
    j = data.find(b"\x00", i + len(key) + 1)
    return data[i + len(key) + 1:j].decode("ascii", "replace") if j > 0 else None


for f in ("system/build.prop", "system/product/etc/build.prop"):
    pb = {k: v for k, v in prop_map(B[f]).items() if ART_KEY.search(k)}
    pn = {k: v for k, v in prop_map(N[f]).items() if ART_KEY.search(k)}
    if pb != pn:
        bad.append("%s: ART / dexopt / zygote properties differ from the base: %s"
                   % (f, ", ".join("%s %r -> %r" % (k, pb.get(k), pn.get(k)) for k in sorted(set(pb) | set(pn))
                                    if pb.get(k) != pn.get(k))))
uffd = prop_map(N["system/product/etc/build.prop"]).get("ro.dalvik.vm.enable_uffd_gc",
                                                        prop_map(N["system/build.prop"]).get("ro.dalvik.vm.enable_uffd_gc"))
ccs = {}
for f in ("system/framework/arm64/boot.oat", "system/framework/arm/boot.oat", "system/framework/oat/arm64/services.odex"):
    cc = oat_value(N[f], b"concurrent-copying") if f in N else None
    ccs[f] = cc
    if cc not in ("true", "false"):
        bad.append("%s: no concurrent-copying key in its oat header (%r)" % (f, cc))
    elif (cc == "false") != (uffd == "true"):
        bad.append("%s is compiled for the %s GC (concurrent-copying=%s), but ro.dalvik.vm.enable_uffd_gc=%s: odrefresh "
                   "would compile the boot classpath at the first boot" % (f, "userfaultfd" if cc == "false" else
                                                                         "read-barrier", cc, uffd))
print("verify_tree_x50: ART ro.dalvik.vm.enable_uffd_gc=%s, boot image and services.odex concurrent-copying=%s"
      % (uffd, "/".join(sorted({str(v) for v in ccs.values()}))))

# the power keys (devices/g0111/README.md, "Power key"): every user power key is the system POWER again, nothing else
# in the key layouts changed
r = subprocess.run([sys.executable, "-I", os.path.join(IN, "x50_power_keys.py"), "check", B[KL], N[KL]],
                   capture_output=True, text=True)
print(r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "x50_power_keys: no output")
if r.returncode:
    bad.append("key layouts: " + (r.stdout + r.stderr).strip())
try:
    with zipfile.ZipFile(N["system/product/overlay/X50PowerOverlay.apk"]) as z:
        if any(n.endswith(".dex") or n.endswith(".so") for n in z.namelist()):
            bad.append("X50PowerOverlay.apk carries code")
except Exception as e:
    bad.append("X50PowerOverlay.apk: %s" % e)

# otacerts.zip without a certificate
try:
    z = zipfile.ZipFile(N["system/etc/security/otacerts.zip"])
    if z.namelist():
        bad.append("otacerts.zip holds %s" % z.namelist())
except Exception as e:
    bad.append("otacerts.zip: %s" % e)

# the blob sets never in the image: the Z9X one of the base (the X50 stock copies of those files have the same
# sha256, README.md) and the X50 one (the same nine plus the eye-protection drawer and its library)
blob_sha = set()
for allow in (B["system/etc/z9x/blobs_allow.txt"], os.path.join(IN, "x50_blobs_allow.txt")):
    for raw in open(allow, encoding="utf-8"):
        m = re.match(r"^([0-9a-f]{64}) ", raw)
        if m:
            blob_sha.add(m.group(1))
        blob_sha |= set(re.findall(r"from=([0-9a-f]{64})", raw))
        blob_sha |= set(re.findall(r"alt=v[0-9.]+:([0-9a-f]{64})", raw))   # another stock firmware's copy (v1.2.54)
if len(blob_sha) < 13:
    bad.append("the two blobs allow-lists give only %d sha256" % len(blob_sha))
for raw in open(os.path.join(IN, "x50_blobs_allow.txt"), encoding="utf-8"):
    if raw.startswith("set=") and not raw.startswith("set=x50-"):
        bad.append("x50_blobs_allow.txt has the set id %s" % raw.strip())
if sum(1 for raw in open(os.path.join(IN, "x50_blobs_allow.txt"), encoding="utf-8")
       if re.match(r"^[0-9a-f]{64} ", raw) and " need=eye" in raw) != 2:
    bad.append("x50_blobs_allow.txt does not have exactly the two need=eye lines (nativeui and its library)")

# t12: the platform set is on the g0111 keys. Every signed file is exactly the one sign_x50_base.py wrote (signed
# sha256) over exactly the base file it read (base sha256); framework-res is signed by the g0111 platform certificate
# alone; no APK or APEX of the image is signed by the Z9X release platform certificate (a Z9X-signed X50 image, or one
# whose framework kept the Z9X key, is refused); each re-signed APEX container is signed by its g0111 APEX certificate
# (devices/g0111/certs/apex/FINGERPRINTS.txt).
Z9X_PLATFORM_CERT = "43690bdf8027f77c41b365c1b83da1cf340d030db98a4524575dbc6417ecb320"
G0111_APEX_CERTS = {"com.android.configinfrastructure": "edcaf7ee339b4df7e534df31c3a0dccd193a90678869f3cf1b4e99222de2d432",
                    "com.android.devicelock": "f92ee26e19cca9bc25a069e6b482c3ccc6f702a120c7155774eed3bad83d89a5",
                    "com.android.ondevicepersonalization": "7970d61b1aa500cdeaab47ae183cc9a21a4784f0a22fc332e83f69ad3ddde3ae",
                    "com.android.permission": "b8d1cb48fa89bbeed2a376fd7353f6ac1cab0927feee1cf05baca7a25ad025e3"}
# the X50 Ultra Max's own APEX keys (devices/g0100/certs/apex/FINGERPRINTS.txt, 2026-11-11)
G0100_APEX_CERTS = {"com.android.configinfrastructure": "fc1714b5aa699a147127f3f7c67b8e1ee0658bbeb2cd4664f7af7ae550750879",
                    "com.android.devicelock": "7a6b5f0349b8b9bf703cfcf4c7494a0db2db3c1faea1c2c9ed67dcbd37f74dbd",
                    "com.android.ondevicepersonalization": "e0ef9027decff61e899fb04f4977516af9af8b0c2e3aa1f51398ac9efe9a944a",
                    "com.android.permission": "fce3622616f0378c4bb70f6a455453f6c7c0d4ec88d5bb832b28e9c9a8345795"}
G0111_APEX_CERTS = {"g0111": G0111_APEX_CERTS, "g0100": G0100_APEX_CERTS}[C]   # the model's own set (the name stays)
for k, (kind, old, new, rep) in sorted(SIGNED.items()):
    if rep in B and sha(B[rep]) != old:
        bad.append("base_signed.tsv: %s is not the base file the Mac signed" % rep)
    if k in (HOME_APP["path"], TVIN_APP["path"]):
        continue   # replaced by this tree's Home / TV input below
    if k not in N or sha(N[k]) != new:
        bad.append("%s is not the file sign_x50_base.py signed" % k)
    elif kind == "apex":
        mod = os.path.basename(k)[:-len(".apex")]
        certs = x50_apk_check.signers(open(N[k], "rb").read())
        if mod not in G0111_APEX_CERTS or not certs or any(set(c) != {G0111_APEX_CERTS.get(mod)} for c in certs.values()):
            bad.append("%s: container not signed by its g0111 APEX certificate (%s)" % (k, certs))
hp = HOME_APP["path"]
if hp not in N or sha(N[hp]) != sha(os.path.join(IN, HOME_APP["staged"])):
    bad.append("%s is not IN/%s (devices/g0111/image/build_home_x50.sh)" % (hp, HOME_APP["staged"]))
else:
    hs = x50_apk_check.signers(open(N[hp], "rb").read())
    if not hs or any(set(c) != {HAL_APP["cert"]} for c in hs.values()):
        bad.append("%s is not signed by the g0111 platform certificate alone: %s" % (hp, hs))
    elif b"org.lumen.x50.projector" not in zipfile.ZipFile(N[hp]).read("classes.dex"):
        bad.append("%s does not name org.lumen.x50.projector (not the device-aware Home)" % hp)
    else:
        print("verify_tree_x50: Home %s sha256 %s on the g0111 key, oat removed" % (hp, sha(N[hp])))
tp = TVIN_APP["path"]
if tp not in N or sha(N[tp]) != sha(os.path.join(IN, TVIN_APP["staged"])):
    bad.append("%s is not IN/%s (tools/devices/build_apps.sh g0111)" % (tp, TVIN_APP["staged"]))
else:
    import apkinfo  # noqa: E402  (IN/apkinfo.py, staged with this script)
    with zipfile.ZipFile(N[tp]) as z:
        tv_pkg = apkinfo.manifest_facts(z.read("AndroidManifest.xml")).get("package")
        tv_dex = z.read("classes.dex")
    ts = x50_apk_check.signers(open(N[tp], "rb").read())
    if not ts or any(set(c) != {HAL_APP["cert"]} for c in ts.values()):
        bad.append("%s is not signed by the g0111 platform certificate alone: %s" % (tp, ts))
    elif tv_pkg != "org.lumen.tvinput":
        bad.append("%s is package %r, not org.lumen.tvinput (the base app's package)" % (tp, tv_pkg))
    elif b"Lorg/lumen/ui/TvFocus;" not in tv_dex or b"Lorg/lumen/tvinput/InputPickerActivity;" not in tv_dex:
        # arch phase 2: the X50 layer's picker uses the lumen-ui module's TvFocus (org.lumen.ui); the base Z9X app
        # (frozen, no module) has neither it nor any other TvFocus
        bad.append("%s has no org.lumen.ui.TvFocus picker (not the X50 source picker of X50TvInput.layers)" % tp)
    else:
        print("verify_tree_x50: TV input %s sha256 %s on the g0111 key, oat removed" % (tp, sha(N[tp])))
fw = "system/framework/framework-res.apk"
fw_signers = x50_apk_check.signers(open(N[fw], "rb").read()) if fw in N else {}
if not fw_signers or any(set(c) != {HAL_APP["cert"]} for c in fw_signers.values()):
    bad.append("%s is not signed by the g0111 platform certificate alone: %s" % (fw, fw_signers))
z9x_signed = []
n_signed = 0
for k, p in sorted(N.items()):
    if k.endswith((".apk", ".apex", ".capex")) and stat.S_ISREG(os.lstat(p).st_mode):
        try:
            s = x50_apk_check.signers(open(p, "rb").read())
        except Exception as e:
            s = {}
            if k.endswith(".apk"):
                bad.append("%s: signing block unreadable (%s)" % (k, e))
        n_signed += 1
        if any(Z9X_PLATFORM_CERT in c for c in s.values()):
            z9x_signed.append(k)
for k in z9x_signed:
    bad.append("signed by the Z9X release platform certificate: " + k)
print("verify_tree_x50: platform set g0111 (%d signed files of base_signed.tsv), framework-res %s, %d APK/APEX signers "
      "read, %d on the Z9X platform key" % (len(SIGNED), HAL_APP["cert"][:16], n_signed, len(z9x_signed)))

# t12: the boot logger is exactly the X50 edit of the base's (service xgimi_middle, FaceCutoutBin silenced)
if DIAG in B and DIAG in N:
    bad.extend(x50_diag.check(B[DIAG], N[DIAG]))
else:
    bad.append("%s missing in the base or the image" % DIAG)

# t12 (t11 review P0.1d): every default-permissions / privapp / sysconfig file that is new or changed against the base
# names only packages of APKs in the image (a merged or renamed app must not leave a file granting nothing)
perm_bad, perm_sum = x50_perm_pkgs.problems(NEW_ROOT, BASE_ROOT)
bad.extend(perm_bad)
print("verify_tree_x50: " + perm_sum)

PAT = [re.compile(rb"xgimi\.hardware\.gmpf\.I[A-Z]"), re.compile(rb"Lxgimi/hardware/gmpf/I[A-Z]"),
       re.compile(rb"xgimi\.hardware\.gmpf@")]
hits = []
nfiles = 0
hal_app_ok = None
menu_app_ok = None
for k, p in sorted(N.items()):
    st = os.lstat(p)
    if not stat.S_ISREG(st.st_mode):
        continue
    nfiles += 1
    if st.st_size and sha(p) in blob_sha:
        bad.append("a file of the blob set is in the image: " + k)
    name = k.lower()
    if k == HAL_APP["path"]:
        # the exception: this APK may name the HAL, with exactly HAL_APP's interface, signer and calls (its bytes are
        # the staged file's, checked with ADDED above)
        try:
            rep = x50_apk_check.report(p)
            errs = x50_apk_check.problems(rep, HAL_APP)
        except Exception as e:  # a damaged APK is refused, never skipped
            rep, errs = None, ["cannot be read: %s" % e]
        for e in errs:
            bad.append("%s (the one HAL app): %s" % (k, e))
        hal_app_ok = not errs and rep
        continue
    if k == MENU_APP["path"]:
        # the second exception (t11): the projector menu, with exactly MENU_APP's interfaces, signer and calls
        try:
            rep = x50_apk_check.report(p)
            errs = x50_apk_check.problems(rep, MENU_APP)
        except Exception as e:  # a damaged APK is refused, never skipped
            rep, errs = None, ["cannot be read: %s" % e]
        for e in errs:
            bad.append("%s (the menu HAL app): %s" % (k, e))
        menu_app_ok = not errs and rep
        continue
    if name.endswith((".apk", ".jar", ".apex", ".capex")):
        try:
            with zipfile.ZipFile(p) as z:
                for zi in z.infolist():
                    if zi.filename.endswith(".dex") or zi.filename.endswith(".so"):
                        data = z.read(zi)
                        if any(x.search(data) for x in PAT):
                            hits.append("%s!%s" % (k, zi.filename))
        except zipfile.BadZipFile:
            pass
    if st.st_size < (64 << 20):
        with open(p, "rb") as f:
            head = f.read(4)
            if head == b"\x7fELF" or name.endswith((".dex", ".odex", ".vdex", ".oat", ".art", ".so")):
                f.seek(0)
                data = f.read()
                if any(x.search(data) for x in PAT):
                    hits.append(k)
    if os.path.basename(k).startswith("Z9xUpdater"):
        bad.append("a Z9X updater file is in the image: " + k)
for h in hits:
    bad.append("names the gmpf HAL: " + h)
if hal_app_ok:
    print("verify_tree_x50: HAL app %s sha256 %s, signer %s, calls %s" % (
        HAL_APP["path"], hal_app_ok["sha256"], HAL_APP["cert"][:16],
        ", ".join("%s %d %s" % r[:3] for r in sorted(hal_app_ok["rows"], key=str))))
elif hal_app_ok is None:
    bad.append("the HAL app %s is not in the image" % HAL_APP["path"])
if menu_app_ok:
    print("verify_tree_x50: menu HAL app %s sha256 %s, signer %s, %d calls" % (
        MENU_APP["path"], menu_app_ok["sha256"], MENU_APP["cert"][:16], len(menu_app_ok["rows"])))
elif menu_app_ok is None:
    bad.append("the menu HAL app %s is not in the image" % MENU_APP["path"])

# the X50 human-shape mask is drawn by the stock nativeui: no reader may answer the vendor's native-UI fifo without
# drawing, so neither the Z9X ACK reader nor anything else that names the
# fifo, except the X50 blobs reader and x50_nui.rc (byte-identical to their inputs, checked above), which only
# remove it while no reader runs
FIFO_OK = {"system/etc/z9x/x50_blobs.sh", "system/etc/init/x50_nui.rc"}
for k in ("system/etc/init/z9x_nui.rc", "system/etc/xgimi/z9x_nui.sh"):
    if k in N:
        bad.append("the Z9X ACK-only fifo reader %s is in the image" % k)
for k, p in sorted(N.items()):
    if k.startswith("system/etc/") and k not in FIFO_OK and stat.S_ISREG(os.lstat(p).st_mode) \
            and os.lstat(p).st_size < (1 << 20):
        if b"nuififo" in open(p, "rb").read():
            bad.append("%s names the native-UI fifo" % k)

print("verify_tree_x50: base %d entries, new %d; removed %d, changed %d, added %d, identical %d; %d files scanned"
      % (len(B), len(N), len(removed), len(CHANGED), len(ADDED), same, nfiles))
for b in bad:
    print("verify_tree_x50: ERROR " + b)
print("verify_tree_x50: %s" % ("OK" if not bad else "FAILED (%d)" % len(bad)))
sys.exit(1 if bad else 0)
