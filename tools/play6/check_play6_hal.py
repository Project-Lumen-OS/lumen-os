#!/usr/bin/env python3
"""check_play6_hal.py: check H of the design notes (Notes A, B, C). The HAL guard of the Play 6
projector app: only the Play 6 gmpf clients talk to the vendor HAL, every code they can send is a reviewed Play 6
code (research/play6/gmpf_codes_play6.tsv, by name), nothing banned (Note A) is reachable, and the special values
of Note C can only leave from the members allowed to send them.

  check_play6_hal.py [APP_DIR]        APP_DIR: an assembled Play 6 projector tree; without it (or given the layer
  apps/Play6Projector, as tools/devices/check_hal.py passes) the tree of devices/dragon_healthy/app/
  Play6Projector.layers, written to a temp dir by play6_tree.py

The clients (docs/architecture: apps/core/lumen-gmpf): Play6Gmpf extends the shared GmpfBase over the Z9X HIDL
transport, Play6Gmpf2 extends Gmpf2Base over Play6Gmpf2Wire (the IGmpf2 identity guard). A shared call sends the code
of its operation (GmpfOp) in the device table hal/Play6Codes, whose rows are constants of the generated
hal/HalCodes.java (devices/dragon_healthy/hal/codes.tsv); an operation without a row sends nothing on the Play 6.
The Play 6 view of a client is the client plus the members of its shared body it does not override (an overridden
one counts where the override calls super.name( ).

Rules:
 1. Structure (Note B). Every file of package org.lumen.projector.hal is a Play 6 HAL file (HAL_FILES), a file of
    the module or a Z9X file of devices/dragon_healthy/app/Play6Projector.gmpf; no GmpfClient, Gmpf2Client or
    KstPoint anywhere. Outside hal/ (and the module's call layer org/lumen/hal) no file names the transport or the
    tables (INTERNAL), imports org.lumen.projector.hal.* or imports a hal member statically (the caller rules
    need qualified calls). No transact( outside HidlCaller and the vendor callbacks (Binder stubs), no
    HwBinder.getService outside HidlCaller. apps/common/projector/FILES lists nothing under hal/.
 2. Codes by name. Every HalCodes constant names its stock method in the TSV (GMPF_ BpHwGmpf, GMPF2_ BpHwGmpf2; the
    only IBase code is getHashChain 0x0f485348). Every row of Play6Codes.GMPF / GMPF2 maps an operation to a
    HalCodes constant of its own interface whose method is the operation's stock method (GmpfOp). A client names
    HalCodes of its own interface only (D2: Play6Gmpf GMPF_, Play6Gmpf2 and Gmpf2Vendor GMPF2_, Play6Gmpf2Wire
    the IBase code), no other hal file names HalCodes, and every send site of the Play 6 view names its code as
    key(OP), HalCodes.X or a local or parameter holding one (no number).
 3. Note A. No HalCodes constant names a banned method (BAN, by interface, code and name, and every name
    matching factory|aging|cali|McuUpgrade); with rule 2 no banned code can be sent.
 4. Note C, static. For each code of VALUE_RULES every send site of the Play 6 view belongs to a sender of the rule
    (its public member; a private helper is followed to its callers) or to a forbidden member. The wire arguments
    of a sender are evaluated (literal, constant, a value enum's .wire = every NAME(n) of the enum in the tree,
    checkRange(.., LO, HI), oneOf( guards, `c ? A : B`, booleans, Process.myPid(), the value just read with a
    code, a check*( call for a float) and lie inside the rule. A forbidden member (a shared body that would send
    another value) is called nowhere in the Play 6 code; a sender with a caller rule is called only from those
    files (and not from hal/); 148 / 152 senders call requireNuiReader( first. Every numeric value any Play 6 send
    site carries is provable this way (the old rule 5: a numeric parameter passes a check first).
 5. Note C, recorded. Every transaction of a VALUE_RULES code in the wire golden of the clients
    (apps/core/lumen-gmpf/test/wire/golden/dragon_healthy.txt; run.sh proves the clients still send exactly it)
    carries values inside the rule, and every rule is exercised there.
 6. The caller check of apps/check_hal_callers.py with the Play 6 clients and their shared bodies: every member
    that transacts has a caller outside hal/ (the forbidden members of rule 4 excepted: they may have none).
 7. The IGmpf2 identity guard (plan 2.1, D2: no bypass). Play6Gmpf2 hands a Play6Gmpf2Wire and Play6Codes.GMPF2 to
    Gmpf2Base and holds no other transport. In Play6Gmpf2Wire every transaction goes out on c(k) (the guard, then
    the code of the open vendor build) but the guard's own IBase read, c( starts with need(), only need() opens the
    guard or picks the vendor (through Gmpf2Vendor.of), drop() closes it for the next connection.
 8. The vendor code tables. Gmpf2Vendor holds exactly ACCEPTED, each hash
    equal to its IGmpf2 row of research/play6/hidl_hashes.txt; V1_7_116 has no table (its numbers are the HalCodes
    constants); every V1_1_67 row {HalCodes.GMPF2_X, N} names the same method as N in
    research/play6/vendor_v1167/gmpf_codes_play6.tsv, and every GMPF2 constant without a row names a method
    v1.1.67 does not have. IGmpf has no map: its v1.1.67 table equals v1.7.116, and the Note A methods absent on
    v1.7.116 are absent on v1.1.67 too.
Exit 0 = H green, 1 = red (every finding is printed), 2 = usage or a broken input file.
"""
import importlib.util
import os
import re
import struct
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import javascan  # noqa: E402
import play6_tree  # noqa: E402

G = os.path.dirname(os.path.dirname(HERE))
TSV = os.path.join(G, "research/play6/gmpf_codes_play6.tsv")
TSV_V1167 = os.path.join(G, "research/play6/vendor_v1167/gmpf_codes_play6.tsv")
HASHES = os.path.join(G, "research/play6/hidl_hashes.txt")
COMMON_FILES = os.path.join(G, "apps/common/projector/FILES")
Z9X_LIST = os.path.join(G, "devices/dragon_healthy/app/Play6Projector.gmpf")
CALLER_CHECK = os.path.join(G, "apps/check_hal_callers.py")
MODULE = os.path.join(G, "apps/core/lumen-gmpf/src")
WIRE_TEST = os.path.join(G, "apps/core/lumen-gmpf/test/wire")
GOLDEN = os.path.join(WIRE_TEST, "golden/dragon_healthy.txt")
STUB_PID = os.path.join(WIRE_TEST, "stubs/android/os/Process.java")

HAL = "src/org/lumen/projector/hal"
GMPF_PKG = "src/org/lumen/hal/gmpf"
HAL_FILES = {"HidlCaller", "HalCodes", "Play6Gmpf", "Play6Gmpf2", "Play6Gmpf2Wire", "Play6Codes", "Play6Rules",
             "Play6Values", "Play6KstPoint", "Play6PowerCallback", "Play6MusicPlayerCallback", "Gmpf2Identity",
             "Gmpf2Vendor", "IdentityException", "AbsentException", "PictureMode", "ColorTemp"}
INTERNAL = ("HidlCaller", "HidlWire", "Play6Gmpf2Wire", "Play6Codes", "Play6Rules", "HalCodes", "Gmpf2Identity",
            "Gmpf2Vendor", "GmpfWire", "GmpfCodes", "GmpfCaller", "HidlFocusCallback")
FORBIDDEN_FILES = {"GmpfClient.java", "Gmpf2Client.java", "KstPoint.java"}
CALLBACKS = {"Play6PowerCallback.java", "Play6MusicPlayerCallback.java", "HidlFocusCallback.java"}
# client -> (shared body, table, HalCodes prefix, interface)
VIEWS = {"Play6Gmpf": ("GmpfBase", "GMPF", "GMPF", "BpHwGmpf"),
         "Play6Gmpf2": ("Gmpf2Base", "GMPF2", "GMPF2", "BpHwGmpf2")}
OWN_PREFIX = {"Play6Gmpf.java": {"GMPF"}, "Play6Gmpf2.java": {"GMPF2"}, "Gmpf2Vendor.java": {"GMPF2"},
              "Play6Gmpf2Wire.java": {"IBASE"}, "Play6Codes.java": {"GMPF", "GMPF2"}}
PREFIX = {"GMPF": "BpHwGmpf", "GMPF2": "BpHwGmpf2", "IBASE": "IBase"}
IFACE = {"BpHwGmpf": "IGmpf", "BpHwGmpf2": "IGmpf2"}
IBASE_GET_HASH_CHAIN = 0x0f485348
ACCEPTED = (("V1_7_116", "v1.7.116"), ("V1_1_67", "v1.1.67"))
NUMERIC = {"int", "long", "short", "byte", "char", "float", "double", "Integer", "Long", "Short", "Byte", "Float",
           "Double"}

# Note A, by (interface, code, name). code None = the method is absent on the Play 6 (checked too).
BAN = [
    # Z9X-forbidden calls, translated by name
    ("BpHwGmpf", 301, "autoFocus", "Z9X-forbidden (whole method on the Play 6)"),
    ("BpHwGmpf", 453, "factoryControlMotor", "Z9X-forbidden"),
    ("BpHwGmpf", 583, "gyroscopeCorrection", "Z9X-forbidden"),
    ("BpHwGmpf", 194, "isPlayingHDRVideo", "Z9X-forbidden"),
    ("BpHwGmpf", 5, "getDolbyAc4De", "Z9X-forbidden"),
    ("BpHwGmpf", 6, "setDolbyAc4De", "Z9X-forbidden"),
    ("BpHwGmpf", 7, "getDolbySurroundVirtual", "Z9X-forbidden"),
    ("BpHwGmpf", 8, "setDolbySurroundVirtual", "Z9X-forbidden"),
    ("BpHwGmpf", 10, "setDolbyVolumeLeveler", "Z9X-forbidden"),
    ("BpHwGmpf", 11, "getRemoteVoiceTestResult", "Z9X-forbidden"),
    ("BpHwGmpf", 15, "startRemoteVoiceRecord", "Z9X-forbidden"),
    ("BpHwGmpf", 200, "getScreenZoomfactor", "Z9X-forbidden"),
    ("BpHwGmpf", 39, "enableAudioCertificationVersion", "Z9X-forbidden"),
    ("BpHwGmpf2", None, "cleanAllCalibrationData", "Z9X-forbidden (Z9X IGmpf2 76)"),
    ("BpHwGmpf2", 79, "getLightOffDelay", "Z9X-forbidden (Z9X IGmpf2 148)"),
    ("BpHwGmpf2", 78, "lightOffDelay", "neighbour of getLightOffDelay"),
    ("BpHwGmpf2", 80, "setLightOffDelay", "neighbour of getLightOffDelay"),
    ("BpHwGmpf2", 60, "setTiltShiftSpeed", "Z9X-forbidden (Z9X IGmpf2 87)"),
    ("BpHwGmpf2", 61, "setTiltShiftMove", "Z9X-forbidden (Z9X IGmpf2 88)"),
    ("BpHwGmpf2", 39, "correctKeystone", "Z9X IGmpf2 61, banned entirely on the Play 6"),
    # banned until their own review
    ("BpHwGmpf", 181, "correctKeystone", "until its own review"),
    ("BpHwGmpf", 158, "generateLogoByTrapezoid", "owner decision O4"),
    ("BpHwGmpf", 114, "uiHeartBeatDo", "vendor UI watchdog, until its own review"),
    ("BpHwGmpf", 115, "uiHeartBeatSet", "vendor UI watchdog, until its own review"),
    # Play 6 rules
    ("BpHwGmpf", 299, "disconnectProjectorFocusManager", "Play 6 rule"),
    ("BpHwGmpf", 400, "resetToDefaultEx", "Play 6 rule (reset)"),
    ("BpHwGmpf", 421, "resetToDefault", "Play 6 rule (reset)"),
    ("BpHwGmpf", 94, "setWBSampleData", "Play 6 rule (white balance)"),
    ("BpHwGmpf", 95, "setRequestWBInfo", "Play 6 rule (white balance)"),
    ("BpHwGmpf", 533, "dlpWhiteBalance", "Play 6 rule (white balance)"),
    ("BpHwGmpf", 556, "setWhiteBalanceFlag", "Play 6 rule (white balance)"),
    ("BpHwGmpf", 99, "setAutoColorCalibration", "Play 6 rule (auto-colour calibration)"),
    ("BpHwGmpf", 100, "setAutoColorDefault", "Play 6 rule (auto-colour calibration)"),
    ("BpHwGmpf", 101, "startAutoColorCalibrationCheck", "Play 6 rule (auto-colour calibration)"),
    ("BpHwGmpf", 102, "startAutoColorCalibration", "Play 6 rule (auto-colour calibration)"),
    ("BpHwGmpf", 155, "setALOnOff", "Play 6 rule"),
    ("BpHwGmpf", 80, "setALOnType", "Play 6 rule"),
    ("BpHwGmpf", 228, "getPowerMode", "power state"),
    ("BpHwGmpf", 229, "wakeUp", "power state"),
    ("BpHwGmpf", 230, "getBootReason", "power state"),
    ("BpHwGmpf", 231, "getTravelMode", "power state"),
    ("BpHwGmpf", 232, "setTravelMode", "power state"),
    ("BpHwGmpf", 233, "setStandbyStage", "power state"),
    ("BpHwGmpf", 234, "getStandbyStage", "power state"),
    ("BpHwGmpf", 241, "goToSleep", "power state"),
    ("BpHwGmpf", 422, "goToShutdown", "power state"),
    ("BpHwGmpf", 543, "setPowerOnMode", "power state"),
    ("BpHwGmpf2", 18, "setEnergyConMode", "power state"),
    ("BpHwGmpf2", 48, "setAutoEcoMode", "power state"),
    ("BpHwGmpf2", 49, "setCopowerDevicePairing", "power state"),
    ("BpHwGmpf2", 143, "setPantiltFunctionState", "Pantilt writer"),
    ("BpHwGmpf2", 144, "deletePantiltLocationInfo", "Pantilt writer"),
    ("BpHwGmpf2", 146, "savePantiltLocationInfo", "Pantilt writer"),
    ("BpHwGmpf2", 148, "setPantiltSpeed", "Pantilt writer"),
    ("BpHwGmpf2", 150, "pantiltElectricMove", "Pantilt writer"),
    ("BpHwGmpf2", 192, "MotionSensorReverseAdjustForFactory", "factory"),
    ("BpHwGmpf", 286, "kstUserCalibrate", "calibration"),
    ("BpHwGmpf", 434, "setFanMode", "Play 6 rule (fan)"),
    # not in the Play 6 clients at all
    ("BpHwGmpf", 272, "newAutoKst", "not in the Play 6 clients"),
    ("BpHwGmpf2", None, "setKstPrepare", "absent on the Play 6 IGmpf2"),
]
BAN_PATTERN = re.compile(r"factory|aging|cali|McuUpgrade", re.I)

GAME_PATH = r"(/game/|/[^/]*Game[^/]*\.java$)"
HDMI_PATH = r"/source/HdmiSource\.java$"
U = lambda lo, hi: set(range(lo, hi + 1))  # noqa: E731
B = "bool"
# Note C, by (interface, code): the stock name, one spec per wire argument, the senders (Play 6 view members; a
# list of specs narrows the rule for that sender) and the extra rules.
#   spec: set of ints | "bool" | "true" | "pid" (exactly Process.myPid()) | ("read", code) (the value just read
#         with that code in the same member) | "float_checked" (a float through a check*( call)
#   forbidden: shared members that would send other values; nothing in the Play 6 code calls them
#   callers: {sender: path regex} the only Play 6 files that may call it (never hal/)
VALUE_RULES = {
    ("BpHwGmpf", 300): dict(name="manualFocus", args=[{17, 1, 0, 2}], senders={"manualFocus": None}),
    ("BpHwGmpf", 307): dict(name="sendFocusStatus", args=[{44, 17}],
                            senders={"sendRemoteAutofocus": [{44}], "sendPowerOnUiReady": [{17}]},
                            forbidden={"sendAfUiOpen", "sendAfCardClosed", "sendAfStop"},
                            callers={"sendPowerOnUiReady": r"/power/UiReady17\.java$"}),
    # t15 (stock analysis hdmi_volume_game.md 3.3): never 4, the vendor refuses 667(0,4) in its independent game mode
    ("BpHwGmpf", 667): dict(name="setPictureMode", args=[{0}, {10, 1, 9, 7, 3, 25, 5}],
                            senders={"setPictureMode": None}, forbidden={"setAiScene"}),
    # t15 (stock analysis 4.1): 0 4:3, 1 16:9, 2 full screen, 4 original; 3 and 5+ are not sent
    ("BpHwGmpf", 665): dict(name="setAspectRatio", args=[{0, 1, 2, 4}], senders={"setAspectRatio": None}),
    ("BpHwGmpf", 648): dict(name="setGameModeOption", args=[("read", 647)], senders={"applyGameModeOption": None},
                            forbidden={"setGameModeOption"}, callers={"applyGameModeOption": GAME_PATH}),
    ("BpHwGmpf", 148): dict(name="setHumanDetectScreenOnOff", args=["true"], nui=True,
                            senders={"restoreEyeProtectionScreen": None}),
    ("BpHwGmpf", 152): dict(name="setHumanDetectOnOff", args=[B], nui=True, senders={"setEyeProtection": None}),
    ("BpHwGmpf", 573): dict(name="temporarySwitchTrigger", args=[{2}, B], senders={"setRealtimeAkTemporarily": None}),
    ("BpHwGmpf", 176): dict(name="setDlpLumensLevel", args=[U(1, 10)], senders={"setLampLevel": None}),
    ("BpHwGmpf", 172): dict(name="setProjectorPutMode", args=[U(0, 3)], senders={"setPutMode": None}),
    ("BpHwGmpf", 61): dict(name="setSoundeffect", args=[{3, 2, 1, 4, 12, 20}], senders={"setSoundEffect": None}),
    ("BpHwGmpf", 669): dict(name="setMemcLevel", args=[{0}, U(0, 3)], senders={"setMemcLevel": None}),
    ("BpHwGmpf", 689): dict(name="setSharpness", args=[{0}, U(0, 100)], senders={"setPq": None}),
    ("BpHwGmpf", 691): dict(name="setSaturation", args=[{0}, U(0, 100)], senders={"setPq": None}),
    ("BpHwGmpf", 693): dict(name="setBrightness", args=[{0}, U(0, 100)], senders={"setPq": None}),
    ("BpHwGmpf", 695): dict(name="setContrast", args=[{0}, U(0, 100)], senders={"setPq": None}),
    ("BpHwGmpf", 186): dict(name="getCorrectKeystoneFullPiont", args=[{0}], senders={"getKeystonePoints": None}),
    # design decision (2026-10-21, "as in stock"): 207(0) and 129(0, 0), only from the keystone reset
    ("BpHwGmpf", 207): dict(name="correctKeystoneReset", args=[{0}], senders={"resetKeystone": None},
                            forbidden={"resetKeystoneForTopSpeedGame"}),
    ("BpHwGmpf", 129): dict(name="switchKeystoneMode", args=[{0}, {0}], senders={"resetKeystone": None},
                            forbidden={"resetKeystoneForTopSpeedGame"}),
    ("BpHwGmpf", 587): dict(name="setAngTriggerAF", args=[B], senders={"setMoveAf": None}),
    ("BpHwGmpf2", 34): dict(name="setGameModeType", args=[{0, 1}], senders={"setGameModeType": None}),
    ("BpHwGmpf2", 35): dict(name="setGameModeState", args=[{0, 1}], senders={"setGameModeState": None}),
    ("BpHwGmpf2", 125): dict(name="setPushPullScreenState", args=[{0, 1}], senders={"setAdjustScreenOpen": None}),
    ("BpHwGmpf2", 64): dict(name="setPowerOnAFUseDistance", args=[B], senders={"setSmartAutofocus": None}),
    ("BpHwGmpf2", 214): dict(name="setAllmEnable", args=[B], senders={"setAllmAutoSwitch": None}),
    # t14 HDMI (stock analysis hdmi_stock.md 7): the input source with our own pid, 0 Android / 1 HDMI only, and the
    # full-panel display area of the debug fallback; both only from source/HdmiSource
    ("BpHwGmpf", 698): dict(name="switchInputSourceWithCallingPid", args=[{0, 1}, "pid"],
                            senders={"switchInputSource": None}, callers={"switchInputSource": HDMI_PATH}),
    ("BpHwGmpf", 659): dict(name="setDisplayArea", args=[{0}, {0}, {1920}, {1080}],
                            senders={"setFullDisplayArea": None}, callers={"setFullDisplayArea": HDMI_PATH}),
    # P3 item 21, digital zoom and shift (stock ZoomDisplacementWind), picture rotation (stock RotateWind)
    ("BpHwGmpf", 121): dict(name="zoomStart", args=[{0}], senders={"zoomSessionStart": None}),
    ("BpHwGmpf", 120): dict(name="zoomFinish", args=[{0}], senders={"zoomSessionFinish": None}),
    ("BpHwGmpf", 118): dict(name="setDigitalZoomStep", args=[U(-200, 200)], senders={"setDigitalZoomStep": None}),
    ("BpHwGmpf", 141): dict(name="translationKst", args=[{1, 2, 3, 4}, B], senders={"shiftPicture": None}),
    ("BpHwGmpf2", 138): dict(name="setScreenRotation", args=[{5, 6}, "float_checked"], senders={"rotateScreen": None}),
    # stock parity (2026-10-21, inventory B 4.2 / 4.4 / 4.7 and the dayao settings tree): the stock value sets only
    ("BpHwGmpf", 671): dict(name="set3DNR", args=[{0}, U(0, 4)], senders={"setNoiseReduction": None}),
    ("BpHwGmpf", 685): dict(name="setColorTemperature", args=[{0}, U(0, 3)], senders={"setColorTemperature": None}),
    ("BpHwGmpf", 683): dict(name="setColorTempRedGain", args=[{0}, U(0, 100)], senders={"setWb": None}),
    ("BpHwGmpf", 681): dict(name="setColorTempGreenGain", args=[{0}, U(0, 100)], senders={"setWb": None}),
    ("BpHwGmpf", 679): dict(name="setColorTempBlueGain", args=[{0}, U(0, 100)], senders={"setWb": None}),
    ("BpHwGmpf", 677): dict(name="setColorTempRedOffset", args=[{0}, U(0, 100)], senders={"setWb": None}),
    ("BpHwGmpf", 675): dict(name="setColorTempGreenOffset", args=[{0}, U(0, 100)], senders={"setWb": None}),
    ("BpHwGmpf", 673): dict(name="setColorTempBlueOffset", args=[{0}, U(0, 100)], senders={"setWb": None}),
    ("BpHwGmpf", 650): dict(name="setHdrEnable", args=[B], senders={"setHdrAuto": None}),
    ("BpHwGmpf", 655): dict(name="setUcdLevel", args=[{0}, U(0, 3)], senders={"setLocalContrast": None}),
    ("BpHwGmpf", 174): dict(name="setDlpLumensMode", args=[{0, 16, 12, 6}], senders={"setColorTemp": None}),
    ("BpHwGmpf", 658): dict(name="resetPqSettings", args=[{3}], senders={"resetCustomPicture": None}),
    ("BpHwGmpf", 635): dict(name="setHdmiRange", args=[{0, 1, 2}], senders={"setHdmiRange": None}),
    ("BpHwGmpf", 660): dict(name="setEdidVersion", args=[{1, 2}], senders={"setEdidVersion": None}),
    ("BpHwGmpf", 419): dict(name="enablePowerOnMusic", args=[B], senders={"setPowerOnMusic": None}),
    ("BpHwGmpf2", 69): dict(name="setSlideFunc", args=[{0, 1}], senders={"setSlideFunction": None}),
    # equalizer (2026-10-21): the SoC PEQ of the speaker path, inside Lumen's own limits
    ("BpHwGmpf", 50): dict(name="SetPEQ", args=[U(1, 13), U(-120, 60), U(50, 20000), U(5, 160)],
                           senders={"setPeqBand": None}),
    ("BpHwGmpf", 51): dict(name="SetPEQEnable", args=[B], senders={"setPeqEnabled": None}),
    ("BpHwGmpf2", 229): dict(name="setTvGammaLevel", args=[{0}, U(0, 8)], senders={"setGamma": None}),
    ("BpHwGmpf2", 231): dict(name="setTvDynamicContrastEnable", args=[{0}, B], senders={"setDynamicContrast": None}),
}

# shared members that read on a Play 6 row without a Play 6 caller (rule 6 tolerates them; they send no value)
SHARED_UNUSED = {"getPutMode": "the Play 6 shows the raw put mode (Play6Gmpf.getPutModeRaw), an unknown reply too"}

SEND_WIRE = re.compile(r"\b(wire|hidl|guard)\s*\.\s*(\w+)\s*\(")
NO_SEND = {"code", "name", "ready", "connected", "connect", "drop", "request", "need", "isOpen", "liveHashChain"}
OPS_RE = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*\(\s*\"(\w+)\"\s*\)")
INT_RE = re.compile(r"^-?(0[xX][0-9a-fA-F_]+|\d[\d_]*)[lL]?$")


class Findings:
    def __init__(self):
        self.errors = []

    def __call__(self, where, msg):
        self.errors.append("%s: %s" % (where, msg))


def load_tsv(path):
    tab, by_name = {}, {}
    for line in open(path, encoding="utf-8"):
        p = line.rstrip("\n").split("\t")
        if len(p) >= 3 and p[1].isdigit():
            tab[(p[0], int(p[1]))] = p[2]
            by_name[(p[0], p[2])] = int(p[1])
    return tab, by_name


def self_check(find, tab, by_name):
    """The tables above must name real Play 6 methods (a typo would silently allow something)."""
    for cls, code, name, _ in BAN:
        if code is None and (cls, name) in by_name:
            find("BAN", "%s %s is not absent: code %d" % (cls, name, by_name[(cls, name)]))
        elif code is not None and tab.get((cls, code)) != name:
            find("BAN", "%s %s %s: the TSV says %s" % (cls, code, name, tab.get((cls, code))))
    for (cls, code), r in VALUE_RULES.items():
        if tab.get((cls, code)) != r["name"]:
            find("VALUE_RULES", "%s %d %s: the TSV says %s" % (cls, code, r["name"], tab.get((cls, code))))


def strip_parens(e):
    e = e.strip()
    while True:
        m = re.match(r"^\((?:byte|int|short|long|char|float)\)\s*(.*)$", e, re.S)
        if m:
            e = m.group(1).strip()
            continue
        if e.startswith("(") and e.endswith(")") and javascan.matching(e, 0, "(", ")") == len(e) - 1:
            e = e[1:-1].strip()
            continue
        return e


def top_split(e, ch):
    """Index of the first ch at depth 0 of e, or -1."""
    d = 0
    for i, c in enumerate(e):
        if c in "([{":
            d += 1
        elif c in ")]}":
            d -= 1
        elif c == ch and d == 0:
            return i
    return -1


def call_args(m, open_idx):
    close = javascan.matching(m, open_idx, "(", ")")
    inner = m[open_idx + 1:close]
    return ([a.strip() for a in javascan.split_top(inner)] if inner.strip() else []), close + 1


class Member:
    def __init__(self, cls, name, params, body, start, mods, src):
        self.cls, self.name, self.body, self.start, self.mods, self.src = cls, name, body, start, mods, src
        self.params = []                                   # [(type, name)]
        for p in javascan.split_top(params) if params.strip() else []:
            toks = p.replace("final ", "").split()
            self.params.append((" ".join(toks[:-1]), toks[-1]))

    @property
    def key(self):
        return "%s.%s" % (self.cls, self.name)

    def ptype(self, n):
        return dict((b, a) for a, b in self.params).get(n)


HEAD = re.compile(r"^[ ]{4}((?:@\w+\s+)*(?:(?:public|protected|private|static|final|synchronized|abstract)\s+)*)"
                  r"(?:<[^>]+>\s+)?[\w.<>\[\], ?]+\s+(\w+)\s*\(([^)]*)\)[^;{=]*\{", re.M)


def members(path):
    """The top-level methods (and constructors) of the first class of path, on masked text."""
    src = javascan.mask(open(path, encoding="utf-8").read())
    cls = os.path.basename(path)[:-5]
    out = []
    for m in HEAD.finditer(src):
        if m.group(2) in ("if", "for", "while", "switch", "catch", "synchronized", "return", "new"):
            continue
        end = javascan.matching(src, m.end() - 1)
        out.append(Member(cls, m.group(2), m.group(3), src[m.end():end], m.end(), m.group(1), src))
    return out, src


def int_constants(src):
    out = {}
    for m in re.finditer(r"\bstatic\s+final\s+(?:int|long|short|byte)\s+([^;]+);", src):
        for d in javascan.split_top(m.group(1)):
            if "=" in d:
                n, v = d.split("=", 1)
                v = strip_parens(v)
                if INT_RE.match(v):
                    out[n.strip()] = int(v.replace("_", "").rstrip("lL"), 0)
    return out


class Tree:
    """The Play 6 HAL sources of one assembled tree."""

    def __init__(self, tree):
        self.tree = tree
        self.hal = os.path.join(tree, HAL)
        self.files = {}
        self.consts = {}
        for d in (self.hal, os.path.join(tree, GMPF_PKG)):
            for f in os.listdir(d):
                if f.endswith(".java"):
                    self.files[f[:-5]] = os.path.join(d, f)
        for name in ("GmpfBase", "Gmpf2Base", "Play6Gmpf", "Play6Gmpf2", "HalCodes"):
            src = javascan.mask(open(self.files[name], encoding="utf-8").read())
            c = int_constants(src)
            self.consts[name] = c
        self.enums = {}

    def enum_values(self, t):
        if t not in self.enums:
            vals = None
            p = self.files.get(t)
            if p:
                s = javascan.mask(open(p, encoding="utf-8").read())
                m = re.search(r"\benum\s+%s\b[^{]*\{([^;}]*)" % re.escape(t), s)
                if m:
                    vals = {}
                    for n, v in re.findall(r"([A-Z][A-Z0-9_]*)\s*\(\s*(-?\d+)", m.group(1)):
                        vals[n] = int(v)
            self.enums[t] = vals
        return self.enums[t]


class Eval:
    """The value set of a wire argument in a member of the Play 6 view (rule 4)."""

    def __init__(self, tree, consts, find, by=None):
        self.tree, self.consts, self.find, self.by = tree, consts, find, by or {}

    def const(self, n):
        n = n.split(".")[-1] if "." in n and n.split(".")[0][0].isupper() else n
        return self.consts.get(n)

    def value(self, e, m, env, depth=0):
        """A set of ints / True / False / "pid" / ("read", code) / "checked", or None (not provable)."""
        e = strip_parens(e)
        if depth > 6:
            return None
        q = top_split(e, "?")
        if q > 0:
            c = top_split(e[q + 1:], ":")
            if c < 0:
                return None
            a = self.value(e[q + 1:q + 1 + c], m, env, depth + 1)
            b = self.value(e[q + 2 + c:], m, env, depth + 1)
            return None if a is None or b is None else a | b
        if e in ("true", "false"):
            return {e == "true"}
        if INT_RE.match(e):
            return {int(e.replace("_", "").rstrip("lL"), 0)}
        if e.startswith("-"):
            v = self.value(e[1:], m, env, depth + 1)
            return None if v is None or any(not isinstance(x, int) or isinstance(x, bool) for x in v) else {-x for x in v}
        if re.match(r"^(?:android\.os\.)?Process\.myPid\(\)$", e):
            return {"pid"}
        mm = re.match(r"^checkRange\s*\(", e)
        if mm:
            args, _ = call_args(e, e.index("("))
            if len(args) == 4:
                lo, hi = self.value(args[2], m, env, depth + 1), self.value(args[3], m, env, depth + 1)
                if lo and hi and len(lo) == 1 and len(hi) == 1:
                    return U(next(iter(lo)), next(iter(hi)))
            return None
        mm = re.match(r"^([a-z]\w*)\s*\(", e)
        if mm and mm.group(1) in self.by and javascan.matching(e, mm.end() - 1, "(", ")") == len(e) - 1:
            args, _ = call_args(e, mm.end() - 1)
            for x in self.by[mm.group(1)]:
                r = re.match(r"^\s*return\s+([^;]+);\s*$", x.body)
                if len(x.params) == len(args) and helper(x) and r:
                    return self.value(r.group(1), x, {p[1]: (m, a, env) for p, a in zip(x.params, args)}, depth + 1)
        if re.match(r"^\w*[cC]heck\w*\s*\(", e):
            return {"checked"}
        mm = re.match(r"^([a-z]\w*)\.wire$", e)
        if mm:
            t = m.ptype(mm.group(1))
            vals = self.tree.enum_values(t) if t else None
            return set(vals.values()) if vals else None
        if re.match(r"^(?:[A-Z]\w*\.)?[A-Z][A-Z0-9_]*$", e):
            v = self.const(e)
            return None if v is None else {v}
        if re.match(r"^[a-z]\w*$", e):
            return self.variable(e, m, env, depth)
        return None

    def variable(self, n, m, env, depth):
        t = m.ptype(n)
        if t == "boolean":
            return {True, False} if n not in env else self.value(env[n][1], env[n][0], env[n][2], depth + 1)
        body = m.body
        decl = re.search(r"\b(?:final\s+)?(?:int|long|short|byte|float|Integer)\s+%s\s*=\s*([^;]+);" % re.escape(n), body)
        if decl and t is None:
            writes = len(re.findall(r"(?<![\w.])%s\s*(?:=(?!=)|[-+*/%%&|^]=|\+\+|--)" % re.escape(n), body))
            if writes != 1:
                return None
            init = strip_parens(decl.group(1))
            r = re.match(r"^(?:wire|hidl)\s*\.\s*ret\w*\s*\(\s*(HalCodes\.\w+|key\s*\(\s*[A-Z]\w*\s*\))\s*\)$", init)
            if r:
                return {("read", r.group(1))}
            return self.value(init, m, env, depth + 1)
        if t is None:
            return None
        guard = self.guard(n, m)
        if n in env:
            outer = self.value(env[n][1], env[n][0], env[n][2], depth + 1)
            if outer is None:
                return guard
            return outer if guard is None else {x for x in outer if x in guard}
        return guard

    def guard(self, n, m):
        """The values a numeric parameter is held to by a check statement of the member, or None."""
        g = re.search(r"\boneOf\s*\(\s*%s\s*,\s*([^;]*?)\)\s*\)\s*throw\b" % re.escape(n), m.body)
        if g:
            if re.match(r"^rules\(\)\s*\.\s*edidVersions\(\s*\)?$", g.group(1).strip()):
                return set(EDID_VALUES)
            vals = [self.value(a, m, {}) for a in javascan.split_top(g.group(1))]
            return None if any(v is None for v in vals) else set().union(*vals)
        g = re.search(r"(?<![\w.])checkRange\s*\(\s*[^,]+,\s*%s\s*,\s*([^,]+),\s*([^,)]+)\)\s*;" % re.escape(n), m.body)
        if g:
            lo, hi = self.value(g.group(1), m, {}), self.value(g.group(2), m, {})
            if lo and hi and len(lo) == 1 and len(hi) == 1:
                return U(next(iter(lo)), next(iter(hi)))
        return None


EDID_VALUES = []


def tree_of(arg):
    if arg and os.path.isfile(os.path.join(arg, HAL, "GmpfBase.java")):
        return os.path.abspath(arg)
    return play6_tree.default_app("check_play6_hal")


def run_caller_check(src):
    spec = importlib.util.spec_from_file_location("check_hal_callers_play6", CALLER_CHECK)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.CLIENTS = ("Play6Gmpf.java", "Play6Gmpf2.java")
    mod.SHARED = {"Play6Gmpf.java": mod.SHARED["GmpfClient.java"], "Play6Gmpf2.java": mod.SHARED["Gmpf2Client.java"]}
    # the Play 6 bans are rule 3 (by name and Play 6 code), its value rules rules 4 and 5
    mod.BANNED = {c: {} for c in mod.CLIENTS}
    mod.EXCEPTIONS, mod.ONLY_ARGS = {}, {}
    import contextlib
    import io
    buf, argv = io.StringIO(), sys.argv
    try:
        sys.argv = ["check_hal_callers.py", src]
        with contextlib.redirect_stdout(buf):
            rc = mod.main()
    finally:
        sys.argv = argv
    return rc, buf.getvalue().strip()


def helper(m):
    """A member only the clients call (private, or protected for the device clients): followed from its callers."""
    return "private" in m.mods or "protected" in m.mods


def calls(src, name):
    return re.search(r"(?:\.\s*%s\s*\(|::\s*%s\b)" % (name, name), src) is not None


def main():
    if len(sys.argv) > 2:
        print(__doc__)
        return 2
    t = Tree(tree_of(sys.argv[1] if len(sys.argv) == 2 else None))
    tree, hal = t.tree, t.hal
    find = Findings()
    tab, by_name = load_tsv(TSV)
    tab67, by_name67 = load_tsv(TSV_V1167)
    self_check(find, tab, by_name)
    banned = {(c, n): why for c, _, n, why in BAN}

    # ---- rule 1: structure
    z9x = {os.path.basename(l.split("#", 1)[0].strip())[:-5] for l in open(Z9X_LIST, encoding="utf-8")
           if l.split("#", 1)[0].strip()}
    module = {f[:-5] for f in os.listdir(os.path.join(MODULE, "org/lumen/projector/hal")) if f.endswith(".java")}
    for f in sorted(os.listdir(hal)):
        if f.endswith(".java") and f[:-5] not in HAL_FILES | z9x | module:
            find(HAL + "/" + f, "not a Play 6 HAL file (HAL_FILES, the module lumen-gmpf, Play6Projector.gmpf)")
    for name in sorted(HAL_FILES | {"GmpfBase", "Gmpf2Base", "HidlWire"}):
        if name not in t.files:
            find(HAL, "%s.java is missing" % name)
    internal = re.compile(r"\b(%s)\b" % "|".join(INTERNAL))
    outside = {}
    for d, _, files in os.walk(os.path.join(tree, "src")):
        for f in files:
            if not f.endswith(".java"):
                continue
            p = os.path.join(d, f)
            rel = os.path.relpath(p, tree)
            if f in FORBIDDEN_FILES:
                find(rel, "a Z9X client file inside the Play 6 tree")
            src = javascan.mask(open(p, encoding="utf-8").read())
            if re.search(r"\.\s*transact\s*\(|::\s*transact\b", src) and f != "HidlCaller.java" \
                    and not (f in CALLBACKS and rel.startswith(HAL + "/")):
                find(rel, "transacts itself (only HidlCaller.call may)")
            if re.search(r"\bHwBinder\s*(?:\.|::)\s*getService\b", src) and f != "HidlCaller.java":
                find(rel, "uses HwBinder.getService itself (only HidlCaller connects)")
            if re.search(r"\bimport\s+(?:static\s+)?org\.lumen\.projector\.hal\.\*", src):
                find(rel, "imports org.lumen.projector.hal.* (name each class)")
            if rel.startswith(HAL + "/") or rel.startswith("src/org/lumen/hal/"):
                continue
            outside[rel] = src
            for m in sorted(set(internal.findall(src))):
                find(rel, "uses %s (the transport and the tables stay inside the clients)" % m)
            for m in re.findall(r"\bimport\s+static\s+(org\.lumen\.projector\.hal\.[\w.]+)", src):
                find(rel, "import static %s (outside hal/ a client member is used qualified, so its callers "
                          "can be checked)" % m)
    for n, line in enumerate(open(COMMON_FILES, encoding="utf-8"), 1):
        if "projector/hal/" in line.split("#", 1)[0]:
            find("apps/common/projector/FILES:%d" % n, "lists a hal file (device HAL files are never shared)")

    # ---- rule 2: codes by name
    hc = {}
    for m in re.finditer(r"static final int (\w+) = (0x[0-9a-fA-F]+|\d+);\s*// (\w+)",
                         open(t.files["HalCodes"], encoding="utf-8").read()):
        name, code, meth = m.group(1), int(m.group(2), 0), m.group(3)
        cls = PREFIX.get(name.split("_", 1)[0]) if not name.startswith("GMPF2_") else "BpHwGmpf2"
        hc[name] = (cls, code, meth)
        if cls == "IBase":
            if meth != "getHashChain" or code != IBASE_GET_HASH_CHAIN:
                find("HalCodes." + name, "the only IBase code is getHashChain 0x%08x" % IBASE_GET_HASH_CHAIN)
        elif cls is None:
            find("HalCodes." + name, "no interface prefix (GMPF_, GMPF2_, IBASE_)")
        elif tab.get((cls, code)) != meth:
            find("HalCodes." + name, "%s %d is %s in the TSV, not %s" % (IFACE[cls], code, tab.get((cls, code), "absent"), meth))
    if len(hc) != len(t.consts["HalCodes"]):
        find("HalCodes", "%d constants without the stock method comment" % (len(t.consts["HalCodes"]) - len(hc)))
    ops = dict(OPS_RE.findall(open(t.files["GmpfOp"], encoding="utf-8").read()))
    codes_src = javascan.mask(open(t.files["Play6Codes"], encoding="utf-8").read())
    table = {}                                             # table -> {OP: HalCodes name}
    for tname, prefix in (("GMPF", "GMPF"), ("GMPF2", "GMPF2")):
        table[tname] = {}
        body = re.search(r"\b%s\s*=\s*new\s+GmpfCodes\s*<[^>]*>\s*\(\s*\)(.*?);" % tname, codes_src, re.S)
        if not body:
            find("Play6Codes", "no table %s" % tname)
            continue
        rows = re.findall(r"\.\s*put\s*\(\s*([A-Z][A-Z0-9_]*)\s*,\s*([^)]*)\)", body.group(1))
        for op, ref in rows:
            where = "Play6Codes.%s %s" % (tname, op)
            r = re.match(r"^HalCodes\.((%s)_[A-Z0-9_]+)$" % prefix, ref.strip())
            if not r or (prefix == "GMPF" and r.group(1).startswith("GMPF2_")):
                find(where, "%s is not a HalCodes.%s_ constant (each table its own interface)" % (ref.strip(), prefix))
                continue
            ref = r.group(1)
            if ref not in hc:
                find(where, "HalCodes.%s does not exist" % ref)
            elif ops.get(op) != hc[ref][2]:
                find(where, "the operation is stock %s, HalCodes.%s is %s" % (ops.get(op), ref, hc[ref][2]))
            if op in table[tname]:
                find(where, "two rows")
            table[tname][op] = ref
    for f in os.listdir(hal):
        if not f.endswith(".java") or f == "HalCodes.java":
            continue
        refs = set(re.findall(r"\bHalCodes\.([A-Z0-9_]+)", javascan.mask(open(os.path.join(hal, f), encoding="utf-8").read())))
        if refs and f not in OWN_PREFIX:
            find(HAL + "/" + f, "names HalCodes (only the clients, the table and the guard do)")
        for ref in sorted(refs):
            pre = "GMPF2" if ref.startswith("GMPF2_") else ref.split("_", 1)[0]
            if f in OWN_PREFIX and pre not in OWN_PREFIX[f]:
                find(HAL + "/" + f, "names HalCodes.%s: only %s codes go out over this interface (D2)"
                     % (ref, "/".join(sorted(OWN_PREFIX[f]))))

    # ---- rule 3: Note A
    for name, (cls, code, meth) in sorted(hc.items()):
        if cls in IFACE and ((cls, meth) in banned or BAN_PATTERN.search(meth)):
            find("HalCodes." + name, "%s %d %s is banned (Note A: %s)" % (IFACE[cls], code, meth,
                                                                         banned.get((cls, meth), "by name")))

    # ---- rule 4: Note C on the sources
    rules_src = javascan.mask(open(t.files["Play6Rules"], encoding="utf-8").read())
    edid = re.search(r"\bint\s*\[\]\s*EDID\s*=\s*\{([^}]*)\}", rules_src)
    EDID_VALUES[:] = [t.consts["Play6Gmpf"].get(x.strip().split(".")[-1]) for x in edid.group(1).split(",")] if edid else []
    if not edid or None in EDID_VALUES:
        find("Play6Rules", "no int[] EDID of Play6Gmpf constants")
    sites = []                                             # (entry member, (cls, code), [value sets], where, site offset)
    entries = {}
    unproven = []
    for client, (base, tname, prefix, cls) in sorted(VIEWS.items()):
        cm, _ = members(t.files[client])
        bm, _ = members(t.files[base])
        consts = dict(t.consts[base])
        consts.update(t.consts[client])
        consts.update({"HalCodes." + k: v for k, v in t.consts["HalCodes"].items()})
        over = {(m.name, len(m.params)) for m in cm}
        supered = {m.name for m in cm if re.search(r"\bsuper\s*\.\s*%s\s*\(" % m.name, m.body)}
        view = [m for m in cm] + [m for m in bm if (m.name, len(m.params)) not in over or m.name in supered]
        by = {}
        for m in view:
            by.setdefault(m.name, []).append(m)
        entries[client] = view
        ev = Eval(t, consts, find, by)

        def code_of(expr, m, env):
            """[(cls, code, label)] a code expression can name (empty: not on the Play 6), or None."""
            e = strip_parens(expr)
            r = re.match(r"^key\s*\(\s*(.+)\)$", e)
            if r:
                inner = strip_parens(r.group(1))
                h = re.match(r"^has\s*\(\s*([A-Z]\w*)\s*\)\s*\?\s*([A-Z]\w*)\s*:\s*([A-Z]\w*)$", inner)
                names = list(h.groups()[1:]) if h else [inner]
                out = []
                for op in names:
                    if re.match(r"^[a-z]\w*$", op):
                        if op in env:
                            sub = code_of("key(%s)" % env[op][1], env[op][0], env[op][2])
                            if sub is None:
                                return None
                            out += sub
                            continue
                        return None
                    if op not in ops:
                        return None
                    ref = table[tname].get(op)
                    if ref and ref in hc:
                        out.append((hc[ref][0], hc[ref][1], op))
                return out
            r = re.match(r"^HalCodes\.(\w+)$", e)
            if r:
                return [(hc[r.group(1)][0], hc[r.group(1)][1], r.group(1))] if r.group(1) in hc else None
            if re.match(r"^[a-z]\w*$", e):
                d = re.search(r"\b(?:Integer|int|K)\s+%s\s*=\s*([^;]+);" % e, m.body)
                if d:
                    return code_of(d.group(1), m, env)
                if e in env:
                    return code_of(env[e][1], env[e][0], env[e][2])
            return None

        def walk(m, env, entry, depth=0):
            """Every send site reached from member m (followed into private helpers and super calls)."""
            if depth > 4:
                return
            body = m.body
            for s in SEND_WIRE.finditer(body):
                obj, meth = s.group(1), s.group(2)
                if meth in NO_SEND or (obj == "guard" and meth != "retF32"):
                    continue
                args, _ = call_args(body, s.end() - 1)
                if not args:
                    continue
                where = "%s.%s" % (m.cls, m.name) + ("" if entry is m else " (from %s)" % entry.name)
                codes = code_of(args[0], m, env)
                if codes is None:
                    find(where, "%s.%s(%s, ..): the code is not key(OP), HalCodes.X or a local holding one"
                         % (obj, meth, args[0]))
                    continue
                if not codes:
                    continue                               # no Play 6 row: key() refuses, nothing is sent
                if obj == "hidl" and meth == "send":
                    pos = s.start()
                    vals = [w.group(2) for w in re.finditer(r"\bq\s*\.\s*(write\w+)\s*\(([^;]*)\)\s*;", body[:pos])
                            if w.group(1) != "writeInterfaceToken"]
                    kinds = [w.group(1) for w in re.finditer(r"\bq\s*\.\s*(write\w+)\s*\(", body[:pos])
                             if w.group(1) != "writeInterfaceToken"]
                    vargs = [(k, v) for k, v in zip(kinds, vals)]
                else:
                    vargs = [("arg", a) for a in args[1:]]
                values = []
                for kind, a in vargs:
                    if kind in ("writeBuffer", "writeStrongBinder") or "->" in a or re.search(r"\b(toBlob|cb|callback)\b", a):
                        values.append({"opaque"})
                        continue
                    v = ev.value(a, m, env)
                    typ = m.ptype(strip_parens(a)) or ""
                    if v is None and typ and typ not in NUMERIC and typ != "boolean":
                        v = {"opaque"}
                    values.append(v)
                    if v is None:
                        unproven.append((where, a))
                for c in codes:
                    sites.append((entry, c, values, where, s.start(), m))
            for c in re.finditer(r"(?<![\w.])(?:this\s*\.\s*)?(\w+)\s*\(", body):
                callee = c.group(1)
                if callee in by and callee != m.name and any(helper(x) for x in by[callee]):
                    args, _ = call_args(body, c.end() - 1)
                    for x in by[callee]:
                        if len(x.params) == len(args) and helper(x):
                            walk(x, {p[1]: (m, a, env) for p, a in zip(x.params, args)}, entry, depth + 1)
            for c in re.finditer(r"\bsuper\s*\.\s*(\w+)\s*\(", body):
                args, _ = call_args(body, c.end() - 1)
                for x in [b for b in bm if b.name == c.group(1) and len(b.params) == len(args)]:
                    walk(x, {p[1]: (m, a, env) for p, a in zip(x.params, args)}, entry, depth + 1)

        for m in view:
            if helper(m) or m.name == client:
                continue
            if m.cls == base and (m.name, len(m.params)) in over:
                continue                                   # reached through super from the override
            walk(m, {}, m)

    note_c = 0
    rule_of = {}
    for (cls, code), rule in VALUE_RULES.items():
        rule_of[(cls, code)] = rule
    seen_entries = set()
    for entry, (cls, code, label), values, where, pos, m in sites:
        rule = rule_of.get((cls, code))
        if rule is None:
            continue
        note_c += 1
        name = entry.name
        if name in rule.get("forbidden", set()):
            continue
        if name not in rule["senders"]:
            find(where, "sends %s %d %s (%s) but is no sender of its Note C rule: add it to VALUE_RULES with its "
                        "values, or forbid it" % (IFACE[cls], code, rule["name"], label))
            continue
        seen_entries.add((cls, code, name))
        spec = rule["senders"][name] or rule["args"]
        if len(values) != len(spec):
            find(where, "%s %d %s: %d wire argument(s), the rule has %d" % (IFACE[cls], code, rule["name"],
                                                                         len(values), len(spec)))
            continue
        for i, (v, s) in enumerate(zip(values, spec)):
            tag = "%s %d %s argument %d" % (IFACE[cls], code, rule["name"], i + 1)
            if v is None:
                continue                                    # reported as unproven below
            if isinstance(s, set):
                bad = {x for x in v if isinstance(x, bool) or not isinstance(x, int) or x not in s}
                if bad:
                    find(where, "%s: may send %s, allowed %s" % (tag, sorted(map(str, bad)), sorted(s)))
            elif s == B:
                if not v <= {True, False}:
                    find(where, "%s: must be a boolean, is %s" % (tag, v))
            elif s == "true":
                if v != {True}:
                    find(where, "%s: must be the literal true" % tag)
            elif s == "pid":
                if v != {"pid"}:
                    find(where, "%s: must be Process.myPid() (our own pid)" % tag)
            elif s == "float_checked":
                if v != {"checked"}:
                    find(where, "%s: must be passed through a check*( call" % tag)
            elif isinstance(s, tuple) and s[0] == "read":
                want = [k for k, (c2, n2, _) in hc.items() if (c2, n2) == (cls, s[1])]
                got = {x[1] for x in v if isinstance(x, tuple)}
                ok = bool(got) and all(g in ["HalCodes." + w for w in want] or
                                       (g.startswith("key") and table[VIEWS[entry.cls][1] if entry.cls in VIEWS else "GMPF"].get(
                                           re.sub(r"key\s*\(\s*(\w+)\s*\)", r"\1", g)) in want) for g in got) and len(v) == len(got)
                if not ok:
                    find(where, "%s: must be the value just read with %s %d in this member" % (tag, IFACE[cls], s[1]))
        if rule.get("nui") and "requireNuiReader(" not in m.body[:pos]:
            find(where, "%s %d %s: requireNuiReader( must come first (NUI fifo gate)" % (IFACE[cls], code, rule["name"]))
    for (cls, code), rule in sorted(VALUE_RULES.items()):
        if not ("GMPF2_" if cls == "BpHwGmpf2" else "GMPF_") or not any(
                (c2, n2) == (cls, code) for c2, n2, _ in hc.values()):
            continue
        for name in rule["senders"]:
            if (cls, code, name) not in seen_entries:
                find("VALUE_RULES %s %d" % (IFACE[cls], code), "sender %s sends nothing of it (stale rule)" % name)
        for name in sorted(rule.get("forbidden", set())):
            for rel, src in sorted(outside.items()):
                if calls(src, name):
                    find(rel, "calls %s: it would send %s %d outside the Play 6 rule" % (name, IFACE[cls], code))
        for name, rx in rule.get("callers", {}).items():
            for rel, src in sorted(outside.items()):
                if calls(src, name) and not re.search(rx, "/" + rel):
                    find(rel, "calls %s: only %s may (Note C)" % (name, rx if rx != "^$" else "the Play 6 clients"))
            for client in VIEWS:
                for m in entries[client]:
                    if m.name != name and m.name not in rule.get("forbidden", set()) \
                            and re.search(r"(?<![\w$])%s\s*\(|::\s*%s\b" % (name, name), m.body) \
                            and not (rx == "^$" and m.cls.startswith("Play6")):
                        find("%s.%s" % (m.cls, m.name), "calls %s: inside hal/ it is not handed on (Note C)" % name)
    for where, a in sorted(set(unproven)):
        find(where, "wire argument %s: value not provable (a constant, a value enum, checkRange / oneOf first)" % a)

    # ---- rule 5: Note C in the wire golden
    rule5 = check_golden(find, tab, tab67, {(c, n) for c, n, _ in hc.values()})

    # ---- rule 6: callers
    rc, caller_line = run_caller_check(os.path.join(tree, "src"))
    forbidden = {n for r in VALUE_RULES.values() for n in r.get("forbidden", set())} | set(SHARED_UNUSED)
    if rc != 0:
        left = [l for l in caller_line.split("\n")
                if not any(re.search(r"\.%s transacts but has no caller" % re.escape(n), l) for n in forbidden)
                and not l.startswith("check_hal_callers: FAILED")]
        if any("transacts but has no caller" in l or "banned" in l or "outside a recognised" in l for l in left):
            find("check_hal_callers", " | ".join(left))
        else:
            caller_line = ("OK but the Note C forbidden members and SHARED_UNUSED (%s), which have no caller"
                           % ", ".join(sorted(forbidden)))

    # ---- rule 7: the identity guard
    g2 = javascan.mask(open(t.files["Play6Gmpf2"], encoding="utf-8").read())
    if not re.search(r"\bthis\s*\(\s*new\s+Play6Gmpf2Wire\s*\(\s*\)\s*\)", g2) \
            or not re.search(r"\bsuper\s*\(\s*guard\s*,\s*Play6Codes\.GMPF2\s*,", g2):
        find("Play6Gmpf2", "does not hand its Play6Gmpf2Wire (the guard) and Play6Codes.GMPF2 to Gmpf2Base")
    if re.search(r"\b(HidlWire|HidlCaller|hidl)\b", g2):
        find("Play6Gmpf2", "holds another transport than the guard")
    if re.search(r"\b(HidlWire|HidlCaller|hidl)\b", javascan.mask(open(t.files["Gmpf2Base"], encoding="utf-8").read())):
        find("Gmpf2Base", "holds a transport of its own (it sends through the wire it is given only)")
    wm, wsrc = members(t.files["Play6Gmpf2Wire"])
    nguarded = 0
    for m in wm:
        for s in re.finditer(r"\bhidl\s*\.\s*(\w+)\s*\(", m.body):
            if s.group(1) in NO_SEND:
                continue
            args, _ = call_args(m.body, s.end() - 1)
            first = args[0].replace(" ", "") if args else ""
            if m.name == "readHashChain" and first == "HalCodes.IBASE_GET_HASH_CHAIN":
                continue
            if first not in ("c(k)",):
                find("Play6Gmpf2Wire.%s" % m.name, "hidl.%s(%s, ..) does not go out on c(k) (guard and vendor table)"
                     % (s.group(1), first))
            else:
                nguarded += 1
        if m.name != "need" and re.search(r"\bidentity\s*=\s*Gmpf2Identity\.OPEN|\bvendor\s*=", m.body):
            find("Play6Gmpf2Wire.%s" % m.name, "opens the guard or picks the vendor (only need())")
    by_w = {m.name: m for m in wm}
    if "c" not in by_w or not by_w["c"].body.strip().startswith("need();"):
        find("Play6Gmpf2Wire.c", "does not start with need()")
    if "need" not in by_w or not re.search(r"\bGmpf2Vendor\.of\s*\(", by_w["need"].body):
        find("Play6Gmpf2Wire.need", "does not select the vendor through Gmpf2Vendor.of(")
    if "drop" not in by_w or not re.search(r"\bidentity\s*=\s*Gmpf2Identity\.UNKNOWN", by_w["drop"].body):
        find("Play6Gmpf2Wire.drop", "does not reset the guard for the next connection")
    if re.search(r"SystemProperties|getprop|System\.getProperty", wsrc):
        find("Play6Gmpf2Wire", "reads a property (no switch may open the guard)")

    # ---- rule 8: the vendor code tables
    rule8 = check_vendors(find, t, hc, tab, tab67, by_name67)

    for e in find.errors:
        print("check_play6_hal: ERROR " + e)
    print("check_play6_hal: rule 1: %d hal files, %d Play 6 files outside hal/" % (
        len([f for f in os.listdir(hal) if f.endswith(".java")]), len(outside)))
    print("check_play6_hal: rules 2-3: %d HalCodes constants, %d + %d table rows, %d send sites in the Play 6 view"
          % (len(hc), len(table.get("GMPF", {})), len(table.get("GMPF2", {})), len(sites)))
    print("check_play6_hal: rule 4: %d Note C codes, %d of their send sites" % (len(VALUE_RULES), note_c))
    print("check_play6_hal: rule 5: " + rule5)
    print("check_play6_hal: rule 6: " + caller_line.replace("\n", " | "))
    print("check_play6_hal: rule 7: Play6Gmpf2Wire: %d transactions on c(k)" % nguarded)
    print("check_play6_hal: rule 8: " + rule8)
    if find.errors:
        print("check_play6_hal: FAILED (%d)" % len(find.errors))
        return 1
    print("check_play6_hal: OK")
    return 0


def golden_value(tok):
    kind, _, v = tok.partition(":")
    if kind in ("i32", "i8", "u8", "i64"):
        return int(v)
    if kind == "b":
        return v == "true"
    if kind == "f":
        return ("float", struct.unpack(">f", struct.pack(">i", int(v)))[0])
    return (kind, v)


def check_golden(find, tab, tab67, named):
    if not os.path.isfile(GOLDEN):
        find("rule 5", "no wire golden %s" % os.path.relpath(GOLDEN, G))
        return "no golden"
    pid = int(re.search(r"return\s+(\d+)\s*;", open(STUB_PID, encoding="utf-8").read()).group(1))
    by = {(cls, r["name"]): (code, r) for (cls, code), r in VALUE_RULES.items()}
    hit = {}
    vendor = "v1.7.116"
    n = 0
    for ln, line in enumerate(open(GOLDEN, encoding="utf-8"), 1):
        if line.startswith("# pass"):
            vendor = "v1.1.67" if "v1.1.67" in line else "v1.7.116"
        elif line.startswith("# scripted"):
            vendor = "any"
        m = re.match(r"^  TX \S+::(IGmpf2?) (\d+) f\d+ tok:(\S+)(.*)$", line)
        if not m or m.group(3).startswith("android.hidl"):
            continue
        cls = "BpHwGmpf2" if m.group(1) == "IGmpf2" else "BpHwGmpf"
        code = int(m.group(2))
        tabs = [tab] if cls == "BpHwGmpf" or vendor == "v1.7.116" else [tab67] if vendor == "v1.1.67" else [tab, tab67]
        names = {x.get((cls, code)) for x in tabs} - {None}
        args = [golden_value(x) for x in m.group(4).split()]
        for name in names:
            if (cls, name) not in by:
                continue
            rcode, rule = by[(cls, name)]
            if vendor == "any" and len(names) > 1:
                continue                                   # a scripted IGmpf2 code of either build: not decidable
            n += 1
            hit[(cls, rcode)] = hit.get((cls, rcode), 0) + 1
            where = "golden:%d %s %d %s" % (ln, IFACE[cls], rcode, name)
            if len(args) != len(rule["args"]):
                find(where, "%d wire arguments, the rule has %d" % (len(args), len(rule["args"])))
                continue
            for i, (v, s) in enumerate(zip(args, rule["args"])):
                ok = ((isinstance(s, set) and not isinstance(v, bool) and v in s) or
                      (s == B and isinstance(v, bool)) or (s == "true" and v is True) or
                      (s == "pid" and v == pid) or (isinstance(s, tuple) and s[0] == "read" and isinstance(v, int)) or
                      (s == "float_checked" and isinstance(v, tuple) and v[0] == "float" and 0 < v[1] <= 45))
                if not ok:
                    find(where, "argument %d is %r, outside the rule" % (i + 1, v))
    for key, rule in sorted(VALUE_RULES.items()):
        if key not in hit and key in named:
            find("rule 5", "%s %d %s is never sent in the wire golden (the rule is not exercised)"
                 % (IFACE[key[0]], key[1], rule["name"]))
    return "%d Note C transactions of the wire golden, %d of %d rules exercised (%d without a HalCodes constant)" % (
        n, len(hit), len(VALUE_RULES), len(set(VALUE_RULES) - named))


def check_vendors(find, t, hc, tab, tab67, by_name67):
    src = open(t.files["Gmpf2Vendor"], encoding="utf-8").read()
    rows = {}
    for line in open(HASHES, encoding="utf-8"):
        p = line.split()
        if len(p) >= 4 and p[1].endswith("::IGmpf2"):
            rows[p[0]] = p[3]
    consts = re.findall(r"^\s{4}(V\w+)\(\"([\w.]+)\",\s*\"([0-9a-f]{64})\"", src, re.M)
    if [(c, v) for c, v, _ in consts] != list(ACCEPTED):
        find("Gmpf2Vendor", "holds %s, expected %s" % ([(c, v) for c, v, _ in consts], list(ACCEPTED)))
    for c, v, h in consts:
        if v not in rows:
            find("Gmpf2Vendor." + c, "research/play6/hidl_hashes.txt has no %s IGmpf2 row" % v)
        elif rows[v] != h:
            find("Gmpf2Vendor." + c, "hash %s, hidl_hashes.txt %s IGmpf2 is %s" % (h, v, rows[v]))
    if not re.search(r"^\s{4}V1_7_116\([^\n]*,\s*null\s*\)\s*,", src, re.M):
        find("Gmpf2Vendor.V1_7_116", "must have no table (its numbers are the HalCodes constants)")
    mapped = {}
    for ref, n in re.findall(r"\{\s*HalCodes\.(GMPF2_[A-Z0-9_]+)\s*,\s*(\d+)\s*\}", src):
        meth = hc.get(ref, (None, None, None))[2]
        if ref in mapped:
            find("Gmpf2Vendor.V1_1_67", "two rows for %s" % ref)
        mapped[ref] = int(n)
        if tab67.get(("BpHwGmpf2", int(n))) != meth:
            find("Gmpf2Vendor.V1_1_67 " + ref, "%s maps to %s, which is %s on v1.1.67" % (meth, n, tab67.get(("BpHwGmpf2", int(n)))))
    if len(re.findall(r"\{\s*HalCodes\.", src)) != len(mapped):
        find("Gmpf2Vendor.V1_1_67", "a row that is not {HalCodes.GMPF2_X, N}")
    for ref, (cls, code, meth) in sorted(hc.items()):
        if cls == "BpHwGmpf2" and ref not in mapped and ("BpHwGmpf2", meth) in by_name67:
            find("Gmpf2Vendor.V1_1_67", "no row for %s (%s is %d on v1.1.67)" % (ref, meth, by_name67[("BpHwGmpf2", meth)]))
    absent = sorted(meth for ref, (cls, code, meth) in hc.items() if cls == "BpHwGmpf2" and ref not in mapped)
    diff = [k for k in set(tab) | set(tab67) if k[0] == "BpHwGmpf" and tab.get(k) != tab67.get(k)]
    if diff:
        find("vendor v1.1.67", "the IGmpf table differs from v1.7.116 (%d codes): Play6Gmpf has no code map" % len(diff))
    for cls, code, name, _ in BAN:
        if code is None and (cls, name) in by_name67:
            find("vendor v1.1.67", "BAN %s %s is absent on v1.7.116 but present on v1.1.67: review" % (cls, name))
    return "%d vendor builds = hidl_hashes.txt, %d v1.1.67 rows proven by name, absent on v1.1.67: %s" % (
        len(consts), len(mapped), ", ".join(absent) or "none")


if __name__ == "__main__":
    sys.exit(main())
