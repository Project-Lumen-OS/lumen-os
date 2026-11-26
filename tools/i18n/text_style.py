#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""text_style.py: one spelling and one capitalisation rule for every English text people see (2026-11-24,
X50 live audit: "Colors for older eyes" next to "Wall colour adaptation", "Projection Brightness" next to sentence case).

    python3 tools/i18n/text_style.py [GSI_DIR]      exit 0 when every English text follows the rules, 1 otherwise

The English texts are every <string>, <plurals> item and <string-array> item of res/values/*.xml (the default
language) under apps/, overlay/ and devices/ that Android may translate (translatable="false" texts are names and
codes). The rules:
  1. US spelling: no British form of a word the apps use (colour, licence, centre, grey, behaviour, ...).
  2. Sentence case for labels (a text without a full stop, question or exclamation mark at its end, at most seven
     words): only the first word starts with a capital letter, every other capital belongs to a name of NAMES (Lumen
     OS, Google, Dolby Vision, a key or a mode the menus name) or is an abbreviation of ABBREVIATIONS (HDMI, HDR, 3D).
  3. A word in capitals is an abbreviation of ABBREVIATIONS (\"IRIS\" is no abbreviation: Iris).
  4. No explanation lines under menu rows (maintainer: no subtitles until he asks): lumen-menu MenuLabels.summary gives a
     line only to the rows of SUBTITLES (a key hint, a stock text, the frame cost warning), MenuLabels.tipText no text
     under a hint picture that only explains its row.
Build folders and references (@string/...) are skipped.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

BRITISH = {
    "colour": "color", "colours": "colors", "coloured": "colored", "colourful": "colorful", "licence": "license",
    "licences": "licenses", "centre": "center", "centred": "centered", "centres": "centers", "grey": "gray",
    "greyed": "grayed", "behaviour": "behavior", "behaviours": "behaviors", "favourite": "favorite",
    "favourites": "favorites", "organise": "organize", "organised": "organized", "recognise": "recognize",
    "recognised": "recognized", "optimise": "optimize", "optimised": "optimized", "optimisation": "optimization",
    "customise": "customize", "customised": "customized", "minimise": "minimize", "maximise": "maximize",
    "analyse": "analyze", "cancelled": "canceled", "cancelling": "canceling", "catalogue": "catalog",
    "dialogue": "dialog", "programme": "program", "metre": "meter", "metres": "meters", "neighbour": "neighbor",
    "labelled": "labeled", "initialise": "initialize", "initialised": "initialized", "synchronise": "synchronize",
    "personalise": "personalize", "prioritise": "prioritize", "summarise": "summarize", "authorise": "authorize",
    "authorised": "authorized", "visualise": "visualize", "normalise": "normalize", "whilst": "while",
    "amongst": "among", "defence": "defense", "judgement": "judgment", "ageing": "aging", "artefact": "artifact",
    "calibre": "caliber", "travelled": "traveled", "modelled": "modeled", "humour": "humor", "honour": "honor",
}
# names and their own capitals: products, brands, trademarks, the keys of the remote, the menu's named modes
NAMES = {
    "Lumen", "OS", "Home", "Play", "Google", "Android", "TV", "Gemini", "Alice", "Yandex", "YouTube", "Apple", "iPhone",
    "iPad", "Mac", "Windows", "Linux", "Miracast", "AirPlay", "DLNA", "Wi-Fi", "Bluetooth", "XGIMI", "Mate", "Dolby",
    "Vision", "Atmos", "Audio", "DTS", "IMAX", "HDR10+", "ISF", "Filmmaker",
    "Netflix", "Tencent", "Latin", "Personal", "Inter", "Onest", "Material", "Symbols", "Apache", "License", "SIL", "Open", "Font",
    "GitHub", "Telegram", "Spark", "Sparks", "Boost", "Turbo", "Office", "Performance", "Flat", "Ultra", "Hz",
    "Back", "OK", "Home", "Menu", "Accounts", "Paste", "Continue", "Install", "Download", "Downloads",
    "Keystone", "Auto", "Full", "Limited", "Gbit", "MaxCLL", "MaxFALL", "PJLink", "DLP-Link", "Signal", "Info",
    "Customize", "Store", "Files", "Installer", "Keyboard", "Setup", "Lab", "Win", "K", "NAS", "RS-232",
    "Max", "Pro", "Noir", "Titan", "TITAN", "Z9X", "X50", "RS30", "Play6", "HB", "ALLM", "VRR", "FRL", "LFE",
    "Harman", "Kardon", "Bilibili", "Kinopoisk", "Lampa", "Wink", "Okko", "Ivi", "VK", "Rutube", "Prime", "Video",
    "Disney", "Plus", "Kodi", "Plex", "Twitch", "Spotify", "MX", "VLC", "Projectivy", "Sony", "PlayStation", "Xbox",
    "Nintendo", "Switch", "PS5", "PS4", "Chromecast", "Fire", "Stick", "Apple TV", "Roku", "Steam", "Deck",
}
ABBREVIATIONS = {
    "3D", "2D", "4K", "8K", "HDMI", "HDR", "HDR10", "HLG", "SDR", "ARC", "EARC", "CEC", "USB", "LED", "DLP", "AI", "PQ",
    "MEMC", "RGB", "RGBW", "IR", "IP", "ID", "PIN", "QR", "OTA", "LAN", "WAN", "DNS", "SSID", "WPA", "WPA2", "WPA3",
    "MB", "GB", "KB", "TB", "FPS", "BT", "AC4", "DRC", "EQ", "UI", "PC", "APK", "LUT", "HDCP", "EDID", "VRR", "ALLM",
    "FRL", "TMDS", "YUV", "YCBCR", "ISF", "IMAX", "DTS", "XGIMI", "OS", "TV", "OK", "NAS", "SMB", "FTP", "URL", "HTTP",
    "HTTPS", "VPN", "PS", "CPU", "GPU", "RAM", "ROM", "AV", "HD", "UHD", "FHD", "MHL", "NFC", "GPS", "SIM", "SD",
    "TIF", "TCL", "LG", "DSP", "ADB", "IOT", "RS", "PJLINK", "ISO", "CIE", "BT2020", "DCI", "P3", "SRGB", "AM", "PM",
    "MAC", "ESN", "GSF", "PS5", "PS4", "FAQ", "CD", "DVD", "MP3", "MP4", "MKV", "AAC", "AC3", "PCM", "LPCM", "IMAX",
    "DLNA", "OSD", "FW", "ASR", "TTS", "API", "SDK", "JSON", "ASCII", "UTF", "LFE", "VK", "ATV", "GT", "EV", "DV",
    "LL", "ID", "TITAN", "HB", "ALS", "TOF", "SOC", "LSB", "MSB", "AIDL", "HAL", "SSD", "RMS", "DB", "KHZ", "MHZ",
}
# names of several words whose later words keep their capitals (trademarks and the names other systems give their pages)
PHRASES = ("Dolby Vision Bright", "Dolby Vision Dark", "Dolby Vision IQ", "IMAX Enhanced", "HDR Vivid", "ISF Day",
           "ISF Night", "Screen Mirroring", "Control Center", "Lumen OS Live")
ABBREVIATIONS |= {"HEVC", "AVC", "AOSP", "DRM", "TMDB", "PDIF", "DVI", "SIDEBAND", "FPGA", "GNU", "GPL", "LGPL", "ALAC",
                  "SSL", "MIT", "BSD"}
# texts that may keep their wording for now, each with the reason
EXEMPT = {
    ("apps/Z9xLineagePlatformOverlay/res/values/strings.xml", "lineageos_system_label"):
        "an overlay the Z9X image pins as a prebuilt: it changes with the next Z9X pin (owner OK), not here",
}
LABEL_WORDS = 7
WORD = re.compile(r"[A-Za-z0-9][A-Za-z0-9+'’-]*")


def english_files(root):
    for top in ("apps", "overlay", "devices"):
        for d, dirs, files in os.walk(os.path.join(root, top)):
            dirs[:] = [x for x in dirs if x not in ("build", "out", ".git")]
            if os.path.basename(d) != "values" or os.path.basename(os.path.dirname(d)) != "res":
                continue
            for f in sorted(files):
                if f.endswith(".xml"):
                    yield os.path.join(d, f)


def texts(path):
    """(name, text) of every translatable text of one values file."""
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        return
    for el in tree.getroot():
        if el.get("translatable") == "false":
            continue
        name = el.get("name", "?")
        items = [el] if el.tag == "string" else list(el) if el.tag in ("plurals", "string-array") else []
        for it in items:
            t = "".join(it.itertext()).strip()
            if t and not t.startswith("@"):
                yield name, t


def plain(t):
    t = re.sub(r"%(\d+\$)?[sdf]", " ", t)
    t = re.sub(r"\\[nt'\"]", " ", t)
    return re.sub(r"https?://\S+|[\w.-]+\.(com|org|io|ru)(/\S*)?", " ", t)


def problems(name, text):
    out = []
    t = plain(text)
    for w in re.findall(r"[A-Za-z]+", t):
        uk = BRITISH.get(w.lower())
        if uk:
            out.append("British spelling %r (US: %s)" % (w, uk))
    for ph in PHRASES:
        t = t.replace(ph, ph.split()[0] + " x" * (len(ph.split()) - 1))
    words = list(WORD.finditer(t))
    label = not re.search(r"[.?!…]\s*$", text.strip()) and len(words) <= LABEL_WORDS
    for i, m in enumerate(words):
        core = m.group(0).strip("'’-")
        parts = [x for x in core.split("-") if x]
        if len(core) >= 2 and all(x.isupper() or x.isdigit() for x in parts) and not core.isdigit():
            for x in parts:
                if len(x) >= 2 and x.isupper() and not any(ch.isdigit() for ch in x) \
                        and x not in ABBREVIATIONS and x not in NAMES:
                    out.append("%r in capitals is no abbreviation" % x)
            continue
        if not label or i == 0 or len(core) < 2 or not core[:1].isupper() or any(ch.isdigit() for ch in core):
            continue
        if re.search(r"[.:?!(“\"«»·>/]\s*$", t[:m.start()]):     # a new sentence, a quote, a part after a colon, a path
            continue
        if core not in NAMES and parts[0] not in NAMES:
            out.append("%r: a label is in sentence case" % core)
    return out


# the only rows with a line under them, each with why: the menu teaches a key, stock shows the text, or the row warns
SUBTITLES = {
    "lamp": "the keys to Boost (a hidden gesture is taught on the element itself)",
    "boost": "the stock Boost text of the model (boost.summary)",
    "signal.refresh": "the plain warning of a polling row that can cost frames (developer tools rule)",
}
TIP_TEXTS = {
    "focus_guide_front": "how to hold the remote for the focus (a key hint)",
    "focus_guide_side": "how to hold the remote for the focus (a key hint)",
}
LABELS = "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuLabels.java"


def cases(src, method):
    i = src.find("static int %s(String" % method)
    if i < 0:
        return None
    body = src[i:src.index("default:", i)]
    return set(re.findall(r'case "([a-z0-9_.]+)":', body))


def subtitles(root):
    path = os.path.join(root, LABELS)
    if not os.path.isfile(path):
        return []
    src = open(path, encoding="utf-8").read()
    bad = []
    for method, allowed in (("summary", SUBTITLES), ("tipText", TIP_TEXTS)):
        got = cases(src, method)
        if got is None:
            bad.append("%s: no MenuLabels.%s" % (LABELS, method))
            continue
        for row in sorted(got - set(allowed)):
            bad.append("%s %s(%s): a line that explains the row, menu rows get none" % (LABELS, method, row))
    return bad


def check(root):
    bad = subtitles(root)
    for p in english_files(root):
        rel = os.path.relpath(p, root)
        for name, t in texts(p):
            if (rel, name) in EXEMPT:
                continue
            for why in problems(name, t):
                bad.append("%s %s: %s (%s)" % (rel, name, why, t[:80]))
    return bad


def main(argv):
    root = argv[1] if len(argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
    bad = check(root)
    for b in bad:
        print("FAIL " + b)
    print(("ok   text style: every English text in US spelling and sentence case, no explanation lines" if not bad
           else "text style: %d problems" % len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
