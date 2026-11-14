# SPDX-License-Identifier: Apache-2.0
# Lumen OS installer for the XGIMI Z9X and the XGIMI Play 6 - Windows (PowerShell 5.1+). BETA: not yet tested on
# Windows. Same steps and the same safety rules as lumen-install.sh (macOS/Linux):
#
#   lumen-install.cmd [command] [-Image FILE] [-Serial S] [-KeepData] [-Wipe format|factory-reset]
#                     [-Lang en|ru] [-Blobs partition|embedded] [-NoBlobs] [-UnsignedImage] [-Zip FILE] [-Remove]
#                     [-Ip ADDRESS[:PORT]] [-Device z9x|play6|ID] [-Path FOLDER] [-GappsZip FILE] [-PlatformTools DIR]
#                     [-ErofsUtils DIR]
#   commands: (none = continue) check | backup | blobs | vbmeta | flash | verify | gsf | gapps | status | reset
#   XGIMI Play 6 (-Ip: its Wi-Fi address; it has no USB debugging): (none) | check | backup | flash | verify |
#   stick -Path E:\ | gapps | gsf | status | reset. 'flash' writes system in fastboot mode over the A-to-A
#   cable, waits for Lumen OS, then asks Android for its factory reset (XGIMI's recovery erases the data).
#   gapps -Zip FILE: Lumen OS without Google + Google services from YOUR OWN MindTheGapps download (the guide
#   names the one accepted file), in their own partition z9x_gapps_<slot> (Play 6: lumen_gapps), then the data
#   wipe (-KeepData: no wipe). gapps -Remove: takes them out (Z9X also from fastboot mode). gapps: their state.
#   -Blobs: published default partition; the maintainer's PRIVATE image needs -Blobs embedded.
#   -NoBlobs: a public image without the codec and audio files (skips 'blobs': NO SOUND, no protected video).
#   -UnsignedImage: your own build without a signed SHA256SUMS (asks you to type UNSIGNED).
#   -Image: required when several lumen-os-*-system.img lie next to the installer (with and without Google).
#   gsf: only when Google services are there (built in, or added with 'gapps').
#   -GappsZip FILE: the full install (no command) with Google services in one pass and ONE data wipe (not with
#   -KeepData or -Wipe factory-reset). XGIMI Z9X: the Google services partition is built on this computer (erofs,
#   tools\erofs-utils) before the restart, then ONE fastbootd visit writes system, the Google services and wipes the
#   data; no USB debugging or Rooted debugging needed after it. Needs tools\erofs-utils\windows-x64 (not in this
#   installer yet: until then -GappsZip stops before anything is written; install without it, then gapps -Zip FILE).
#   XGIMI Play 6: Lumen OS starts once on the old data, the Google apps are filled in as root.
#   -ErofsUtils DIR: mkfs.erofs.exe, fsck.erofs.exe, dump.erofs.exe of erofs-utils 1.9.4 in DIR instead of
#   tools\erofs-utils (the image built must still be bit for bit the checked one, lib\gapps_images.txt).
#
# Writes only system_<slot>, z9x_blobs_<slot>, z9x_gapps_<slot> (-GappsZip: create/resize/flash of the erofs image
# built here; 'gapps': create/resize/format/delete, filled over adb root), and formats userdata/metadata/cache. Deletes only *-cow
# leftovers and XGIMI's unused product_<slot>/system_ext_<slot>. NEVER vbmeta (your manual step), boot, vendor,
# persist, xgimi*, misc, frp, the other slot; never 'fastboot -w' or 'fastboot erase'.
# A Windows USB driver for the projector's adb/fastboot interface may be needed (Google USB Driver): when the
# projector does not show up in fastboot mode, the installer looks for a USB device without a driver and says how.
# adb and fastboot: never the ones on PATH. The first run downloads Google's Platform-Tools (pinned version and
# sha256) into tools\platform-tools next to this script; -PlatformTools DIR uses the adb.exe and fastboot.exe in DIR
# instead (35 or newer), for a computer without internet access. Own adb server on port 5038; time limits on every call.
# XGIMI Play 6: only system is written (Fb-Allowed), and 'gapps' creates, resizes or deletes lumen_gapps (formatted
# and filled on the projector over adb root, lib\play6\gapps_fill.sh); the data wipe is Android's own factory reset.
# Never vendor, boot, recovery, MBOOT, env, calibration or factory data. Its model, firmware list and image name:
# lib\devices.conf.
# Projectors with FLOW=ab-fastbootd in lib\devices.conf (-Device ID; their model codes, firmware, slots and image name
# are there; guide: docs/install/<GUIDE>/<language>.md). This computer talks to their system over the network
# (-Ip ADDRESS; on stock, Developer options > USB debugging opens it); fastbootd is reached over a USB A-to-A cable in
# the projector's USB port, plugged in when the installer asks. Commands: (none) = check, backup (asked), vbmeta
# (asked), blobs, flash, continued where it stopped | check (read only) | backup | vbmeta (shows the one manual step:
# Google's vbmeta.img, both slots; never run by the installer) | blobs (the MediaTek / XGIMI files of the profile's
# allow-list, from YOUR projector's own system) | flash (one fastbootd visit: z9x_blobs_<slot>, system_<slot>, then
# 'fastboot format' of userdata and metadata with the file system of the projector's stock fstab: ext4 on the HB,
# f2fs (userdata with casefold and projid) on the X50, the profile's WIPE_FS; the full data wipe; no -KeepData) | verify (after the setup, with
# debugging on again) | gapps -Zip FILE (a profile with GAPPS_ALLOW: the Google services add-on image built on this computer, erofs,
# written into z9x_gapps_<slot> in one fastbootd visit, then the format of userdata and metadata) | gapps -Remove |
# gapps (its state) | gsf (the GSF ID, adb root) | status | reset (-Device ID reset forgets only that projector's
# progress). -GappsZip FILE with the full install: the same add-on in the install's own visit, after system and before
# the wipe. It writes only system_<slot> and z9x_blobs_<slot> (create, resize, flash) of the running slot (a or b, the
# one fastbootd reports) when the profile's SLOTS has it, z9x_gapps_<slot> of that slot (create, resize, flash,
# delete; only with GAPPS_ALLOW), and formats userdata and metadata, only while no update is staged or merging
# (snapshot-update-status none). Never vbmeta, boot, vendor, the KEEP partitions, the other
# slot, --slot, set_active, a delete of anything but z9x_gapps_<slot>, erase or -w (Fb-Allowed). stick: not yet.
param([string]$Command = "", [string]$Image = "", [string]$Serial = "", [switch]$KeepData,
      [string]$Wipe = "format", [string]$Lang = "", [string]$Blobs = "", [switch]$NoBlobs, [switch]$UnsignedImage,
      [string]$Zip = "", [switch]$Remove, [string]$Ip = "", [string]$Device = "", [string]$Path = "",
      [string]$GappsZip = "", [string]$PlatformTools = "", [string]$ErofsUtils = "")
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Here = $PSScriptRoot
$StateDir = if ($env:LUMEN_STATE_DIR) { $env:LUMEN_STATE_DIR } else { Join-Path $HOME ".lumen-installer" }
$BackupRoot = if ($env:LUMEN_BACKUP_DIR) { $env:LUMEN_BACKUP_DIR } else { Join-Path $HOME "Lumen-backup" }
$GuideBase = "https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install"   # one guide per projector and language
$GoogleGsi = "https://source.android.com/docs/core/tests/vts/gsi#flashing-gsis"
$BlobSize = 4194304
if (-not $Lang) {   # the language of the messages (lib\messages.tsv): from the Windows display language, or -Lang
  $cu = Get-Culture; $two = $cu.TwoLetterISOLanguageName
  $Lang = if ($two -eq "ru") { "ru" } elseif ($two -eq "zh") { if ($cu.Name -match '^zh-(TW|HK|MO|Hant)') { "zh-TW" } else { "zh-CN" } } `
          elseif (@("ar", "be", "de", "es", "fr", "it", "ja", "kk", "ko", "pl", "pt", "tr", "uk") -contains $two) { $two } else { "en" }
}
$MsgFile = Join-Path (Join-Path $Here "lib") "messages.tsv"   # every text the user reads, by id, in each language
function Msg-Load {  # the texts of -Lang (lib\messages.tsv; English where a text has no translation; the .win rows win)
  $script:Msg = @{}; $win = @{}; $c = -1
  if (-not (Test-Path -LiteralPath $MsgFile)) { Write-Host "lib\messages.tsv is missing: download the installer again. Nothing was done."; exit 1 }
  foreach ($l in [System.IO.File]::ReadAllLines($MsgFile, [System.Text.Encoding]::UTF8)) {
    if ($l -eq "" -or $l.StartsWith("#")) { continue }
    $f = $l.Split([char]9)
    if ($c -lt 0) { $c = [array]::IndexOf($f, $Lang); if ($c -lt 1) { $c = 1 }; continue }
    $s = if ($c -lt $f.Count -and $f[$c] -ne "") { $f[$c] } else { $f[1] }
    $s = [regex]::Replace($s, '\\(.)', { param($x) switch -CaseSensitive ($x.Groups[1].Value) { "n" { "`n" } "t" { "`t" } default { $x.Groups[1].Value } } })
    if ($f[0].EndsWith(".win")) { $win[$f[0].Substring(0, $f[0].Length - 4)] = $s } else { $script:Msg[$f[0]] = $s }
  }
  foreach ($k in $win.Keys) { $script:Msg[$k] = $win[$k] }
}
function M([string]$Id) {  # the text of Id in -Lang, {1}, {2}, ... filled in with the values that follow
  $s = $script:Msg[$Id]; if ($null -eq $s) { $s = "[$Id]" }
  $v = $args
  return [regex]::Replace($s, '\{([1-9][0-9]?)\}', { param($x) $i = [int]$x.Groups[1].Value; if ($i -le $v.Count) { "$($v[$i - 1])" } else { $x.Value } })
}
Msg-Load
$conf = @{}
$rc = Join-Path $Here "release.conf"
if (Test-Path $rc) { Get-Content $rc | Where-Object { $_ -match "^\s*([A-Z_]+)=(.*)$" } | ForEach-Object { $null = $_ -match "^\s*([A-Z_]+)=(.*)$"; $conf[$Matches[1]] = $Matches[2].Trim() } }
if ($NoBlobs) { $Blobs = "none" }
if (-not $Blobs -and $env:BLOBS) { $Blobs = $env:BLOBS }      # an explicit environment value wins over release.conf
if (-not $Blobs) { $Blobs = if ($conf["BLOBS"]) { $conf["BLOBS"] } else { "partition" } }
if (@("partition", "embedded", "none") -notcontains $Blobs) { Write-Host "-Blobs partition|embedded|none (-NoBlobs)"; exit 2 }
if (@("format", "factory-reset") -notcontains $Wipe) { Write-Host "-Wipe format|factory-reset"; exit 2 }
if ($Command -ne "gapps" -and ($Zip -or $Remove)) { Write-Host "-Zip / -Remove belong to 'gapps'"; exit 2 }
# -Device: a projector of lib\devices.conf (read here, before the functions; without the file Load-Profile stops)
$dc = Join-Path (Join-Path $Here "lib") "devices.conf"
$IdsTop = @(if (Test-Path -LiteralPath $dc) { Get-Content -LiteralPath $dc -Encoding UTF8 | ForEach-Object { if ("$_" -cmatch '^([a-z0-9]+)\.NAME=') { $Matches[1] } } })
if ($Device -and (Test-Path -LiteralPath $dc) -and $IdsTop -notcontains $Device) { Write-Host ("-Device " + ($IdsTop -join "|")); exit 2 }
$RelDevice = if ($conf["DEVICE"]) { $conf["DEVICE"] } else { "" }   # the projector this download's image is for (none: z9x)
$script:Projector = ""        # the projector: an id of lib\devices.conf (Resolve-Device), its FLOW picks the steps
$script:SD = $StateDir        # where the device's progress is kept (the Z9X: $StateDir\state, as always)
$script:AdbT = ""             # XGIMI Play 6: adb over Wi-Fi, ADDR:PORT ($script:Serial is then the fastboot serial)
$script:ImgGlob = "lumen-os-*-system.img"
$script:EraseNow = $false; $script:WipedNow = $false

function Say($m) { Write-Host ("{0} {1}" -f (Get-Date -Format HH:mm:ss), $m) }
function Ok($m) { Say "  + $m" }
function Warn($m) { Say "  ! $m" }
function Die($m) { Say ((M stop) + ": $m"); Pt-Cleanup; exit 1 }
function Ask($q) {
  $a = Read-Host ("$q [" + (M yes_no) + "]")
  if (@("y","yes","Yes","YES","д","да","Да","ДА") -contains $a) { return $true }
  return ($a -and (@((M answer_yes) -split " ") -contains $a))   # the words of the language of the messages
}
function Sha256($f) { (Get-FileHash $f -Algorithm SHA256).Hash.ToLower() }
# identity of the file on disk (size, last write time): a cached check is only valid for exactly that file
function File-Key($f) { $i = Get-Item -LiteralPath $f; "{0}:{1}" -f $i.Length, $i.LastWriteTimeUtc.Ticks }

function State-Get($k) {
  $f = Join-Path $script:SD "state"
  if (-not (Test-Path $f)) { return "" }
  $l = Get-Content $f | Where-Object { $_ -like "$k=*" } | Select-Object -Last 1
  if ($l) { return $l.Substring($k.Length + 1) } else { return "" }
}
function State-Set($k, $v) {
  New-Item -ItemType Directory -Force -Path $script:SD | Out-Null
  $f = Join-Path $script:SD "state"
  $lines = @(); if (Test-Path $f) { $lines = @(Get-Content $f | Where-Object { $_ -notlike "$k=*" }) }
  $lines += "$k=$v"
  Set-Content -Path $f -Value $lines -Encoding UTF8
}

function Adb { $t = if ($script:AdbT) { $script:AdbT } else { $script:Serial }; Pt-Adb -s $t @args 2>$null }   # AdbT: never set on the Z9X
function Prop($p) { ((Adb shell getprop $p) | Out-String).Trim() }
function Fb { Pt-Fastboot -s $script:Serial @args 2>&1 | ForEach-Object { "$_" } }

# ------------------------------------------------------------------ adb and fastboot (own copy, time limits)
# Never the adb/fastboot on PATH: Pt-Setup downloads Google's Platform-Tools $PtVer into tools\platform-tools next
# to this script (sha256 pinned below; a platform-tools_r$PtVer-win.zip already in tools\ is checked and used
# without a download) or takes -PlatformTools DIR. Its adb server runs on its own port ($AdbPort), and only that
# server is stopped at the end. Every call runs through Pt-Call with a time limit: a call that hangs is stopped.
# The functions are Pt-Adb and Pt-Fastboot, never 'adb'/'fastboot': PowerShell names ignore case, and a function
# 'adb' (or the helper 'Adb') would be called instead of adb.exe.
$PtVer = "35.0.2"
$PtSha = @{ win = "2975a3eac0b19182748d64195375ad056986561d994fffbdc64332a516300bb9"
            darwin = "1820078db90bf21628d257ff052528af1c61bb48f754b3555648f5652fa35d78"
            linux = "acfdcccb123a8718c46c46c059b2f621140194e5ec1ac9d81715be3d6ab6cd0a" }
$PtUrl = "https://dl.google.com/android/repository"
$AdbPort = "5038"     # the installer's own adb server (adb's default is 5037)
$script:OnWindows = ($PSVersionTable.PSEdition -ne "Core") -or $IsWindows
$script:PtExt = if ($script:OnWindows) { ".exe" } else { "" }
$script:PtDir = ""; $script:PtServer = $false; $script:PtAdbV = ""; $script:PtFbV = ""; $script:PnpBefore = @()
function Pt-Adb { Pt-Call "adb" $args }
function Pt-Fastboot { Pt-Call "fastboot" $args }
function Pt-Watch {  # the watchdog: a timer that stops our adb/fastboot process when its time is up
  if ("LumenWatch" -as [type]) { return }
  Add-Type -TypeDefinition @"
using System;
using System.Diagnostics;
using System.Threading;
public static class LumenWatch {
  static Timer timer;
  static volatile bool fired;
  static string path;
  static DateTime since;
  public static void Arm(string exe, int seconds) {
    fired = false; path = exe; since = DateTime.Now.AddSeconds(-2);
    timer = new Timer(Fire, null, seconds * 1000L, Timeout.Infinite);
  }
  public static bool Disarm() {
    Timer t = timer; timer = null;
    if (t != null) t.Dispose();
    return fired;
  }
  static void Fire(object o) {
    fired = true;
    string name = System.IO.Path.GetFileNameWithoutExtension(path);
    foreach (Process p in Process.GetProcessesByName(name)) {
      try {
        if (p.StartTime < since) continue;   // started before this call: another program's, or our server
        string f = null;
        try { f = p.MainModule.FileName; } catch { }
        bool win = Environment.OSVersion.Platform == PlatformID.Win32NT;   // elsewhere: only the tests of this script
        if (f == null || !win || String.Equals(f, path, StringComparison.OrdinalIgnoreCase)) p.Kill();
      } catch { }
    }
  }
}
"@
}
function Pt-Cap($s) { if ($env:LUMEN_TIMEOUT_MAX -and [int]$s -gt [int]$env:LUMEN_TIMEOUT_MAX) { return [int]$env:LUMEN_TIMEOUT_MAX }; return [int]$s }   # tests only
function Pt-Sub($a) {  # the command word after the options (-s SERIAL, --fs-options=...)
  for ($i = 0; $i -lt $a.Count; $i++) {
    $w = "$($a[$i])"
    if ($w -eq "-s" -or $w -eq "-t") { $i++; continue }
    if ($w -eq "--version") { return "version" }
    if ($w.StartsWith("-")) { continue }
    return $w
  }
  return ""
}
function Pt-Limit($tool, $a) {  # seconds the call may take (generous: only a hang reaches it)
  $sub = Pt-Sub $a
  switch -Wildcard ("$tool $sub") {
    "* version" { return 20 }
    "adb devices" { return 20 }
    "adb start-server" { return 20 }
    "adb kill-server" { return 20 }
    "adb get-state" { return 20 }
    "adb get-serialno" { return 20 }
    "adb disconnect" { return 20 }
    "adb mdns" { return 20 }
    "adb connect" { return 30 }
    "adb root" { return 60 }
    "adb unroot" { return 60 }
    "adb reboot" { return 60 }
    "adb reconnect" { return 60 }
    "adb wait-for-*" { return 180 }
    "adb pull" { return 7200 }
    "adb shell" { return 1800 }
    "adb push" { return 1800 }
    "adb install*" { return 1800 }
    "fastboot devices" { return 20 }
    "fastboot getvar" { return 60 }
    "fastboot reboot*" { return 180 }
    "fastboot flash" {   # at least 2 MB/s from the disk to the projector (USB 2.0 does about 30)
      $f = "$($a[$a.Count - 1])"; $len = 0
      if (Test-Path -LiteralPath $f) { $len = (Get-Item -LiteralPath $f).Length }
      return [int](300 + $len / 2MB)
    }
  }
  return 600
}
function Pt-Call($tool, $a) {  # adb|fastboot, its arguments: our own copy, within the time limit of that call
  if (-not $script:PtDir) { Die "internal: $tool before the Platform-Tools check" }
  $a = @($a)
  $secs = Pt-Cap (Pt-Limit $tool $a)
  $exe = Join-Path $script:PtDir "$tool$($script:PtExt)"
  [LumenWatch]::Arm($exe, $secs)
  try { & $exe @a } finally { $fired = [LumenWatch]::Disarm() }
  if (-not $fired) { return }
  if ($tool -eq "fastboot" -and (Pt-Sub $a) -eq "flash") {   # the caller says what that means for the projector
    Warn (M fastboot_did_not_finish_s "$a" "$secs")
    $global:LASTEXITCODE = 124
    return
  }
  $hint = if ($tool -eq "fastboot") { Fb-Driver-Hint } else { "" }
  Die ((M did_not_answer_within_s "$tool" "$a" "$secs") + $hint)
}
function Pt-Files {  # what the installer runs or fastboot needs (Windows: and adb's USB libraries)
  $f = @("adb", "fastboot", "mke2fs", "make_f2fs") | ForEach-Object { "$_$($script:PtExt)" }
  if ($script:OnWindows) { $f += @("AdbWinApi.dll", "AdbWinUsbApi.dll") }
  return $f
}
function Pt-Marker-Ok($dir, $sha) {  # our checked unpack: zip sha256, and the sha256 of each file it runs
  $m = Join-Path $dir ".lumen-platform-tools"
  if (-not (Test-Path -LiteralPath $m)) { return $false }
  $lines = @(Get-Content -LiteralPath $m)
  if ($lines -notcontains "zip $sha") { return $false }
  foreach ($f in (Pt-Files)) {
    $want = $lines | Where-Object { $_ -like "* $f" } | ForEach-Object { ($_ -split " ")[0] } | Select-Object -First 1
    $p = Join-Path $dir $f
    if (-not $want -or -not (Test-Path -LiteralPath $p) -or (Sha256 $p) -ne $want) { return $false }
  }
  return $true
}
function Pt-Fetch($zip, $sha) {  # the pinned zip into tools\ (kept: a later run checks it again instead of downloading)
  $dir = Join-Path $Here "tools"; $url = "$PtUrl/$zip"; $dst = Join-Path $dir $zip; $part = "$dst.part"
  try { New-Item -ItemType Directory -Force -Path $dir -ErrorAction Stop | Out-Null; $w = Join-Path $dir ".write-test"; [IO.File]::WriteAllText($w, ""); [IO.File]::Delete($w) }
  catch { Die (M cannot_write_copy_installer_folder "$dir") }
  if ((Test-Path -LiteralPath $dst) -and (Sha256 $dst) -eq $sha) { return }
  Say (M downloading_googles_android_sdk "$PtVer" "$url")
  Remove-Item -LiteralPath $part -Force -ErrorAction SilentlyContinue
  $curl = if ($script:OnWindows) { Join-Path $env:SystemRoot "System32\curl.exe" } else { "/usr/bin/curl" }
  if (Test-Path -LiteralPath $curl) {
    # Windows' curl checks certificate revocation, which often cannot be reached (seen 2026-10-29): skip only that
    # check; the certificate itself is still checked, and the zip must match the pinned sha256 anyway
    # (each argument written out: PowerShell 5.1 mangled a splatted "--ssl-no-revoke" into "-:", 2026-10-29)
    if ($script:OnWindows) { & $curl -fsSL --ssl-no-revoke --retry 3 --connect-timeout 20 --speed-limit 1024 --speed-time 60 -o $part $url 2>$null }
    else { & $curl -fsSL --retry 3 --connect-timeout 20 --speed-limit 1024 --speed-time 60 -o $part $url 2>$null }
    if ($LASTEXITCODE -ne 0) { Remove-Item -LiteralPath $part -Force -ErrorAction SilentlyContinue }
  }
  if (-not (Test-Path -LiteralPath $part)) {   # no curl, or it failed: PowerShell's own download
    try {
      [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
      $pp = $ProgressPreference; $ProgressPreference = "SilentlyContinue"
      Invoke-WebRequest -Uri $url -OutFile $part -UseBasicParsing -TimeoutSec 300 -ErrorAction Stop
      $ProgressPreference = $pp
    } catch { }
  }
  if (-not (Test-Path -LiteralPath $part)) { Die (M download_failed_no_internet "$url" "$dir") }
  if ((Sha256 $part) -ne $sha) { Remove-Item -LiteralPath $part -Force; Die (M downloaded_not_googles_file_sha256 "$zip") }
  Move-Item -LiteralPath $part -Destination $dst -Force
}
function Pt-Unpack($zip, $sha) {  # zip -> tools\platform-tools, with the marker that lets later runs reuse it
  $tools = Join-Path $Here "tools"; $new = Join-Path $tools ".platform-tools.new"; $dir = Join-Path $tools "platform-tools"
  Remove-Item -LiteralPath $new -Recurse -Force -ErrorAction SilentlyContinue
  try { Expand-Archive -LiteralPath (Join-Path $tools $zip) -DestinationPath $new -Force -ErrorAction Stop }
  catch { Remove-Item -LiteralPath $new -Recurse -Force -ErrorAction SilentlyContinue; Die (M cannot_unpack_nothing_done "$zip" "$($_.Exception.Message)") }
  $pt = Join-Path $new "platform-tools"
  if ($script:OnWindows) { Get-ChildItem -LiteralPath $pt -Recurse -File | Unblock-File -ErrorAction SilentlyContinue }
  else { foreach ($f in (Pt-Files)) { if (Test-Path -LiteralPath (Join-Path $pt $f)) { & chmod 755 (Join-Path $pt $f) } } }
  $m = @("zip $sha") + @(Pt-Files | Where-Object { Test-Path -LiteralPath (Join-Path $pt $_) } | ForEach-Object { "$(Sha256 (Join-Path $pt $_)) $_" })
  Set-Content -LiteralPath (Join-Path $pt ".lumen-platform-tools") -Value $m -Encoding ASCII
  Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue
  Move-Item -LiteralPath $pt -Destination $dir
  Remove-Item -LiteralPath $new -Recurse -Force -ErrorAction SilentlyContinue
}
function Pt-Major($text) { if ("$text" -match "(?m)^(Version|fastboot version) (\d+)\.") { return [int]$Matches[2] }; return 0 }
function Pt-Run-Quiet($exe, $a, $secs) {  # our tool outside Pt-Call (setup, cleanup): output as text; $null = it hung
  [LumenWatch]::Arm($exe, (Pt-Cap $secs))
  try { $o = (& $exe @a 2>&1 | ForEach-Object { "$_" }) -join "`n" } finally { $fired = [LumenWatch]::Disarm() }
  if ($fired) { return $null }
  return $o
}
function Pt-Setup {  # adb and fastboot of our own, checked; our own adb server started (it answers within seconds)
  Pt-Watch
  $os = if ($script:OnWindows) { "win" } elseif ($IsMacOS) { "darwin" } else { "linux" }
  $pt = if ($PlatformTools) { $PlatformTools } else { $env:LUMEN_PLATFORM_TOOLS }
  if ($pt) {
    $dir = (Resolve-Path -LiteralPath $pt -ErrorAction SilentlyContinue).ProviderPath
    if (-not $dir -or -not (Test-Path -LiteralPath (Join-Path $dir "adb$($script:PtExt)")) -or -not (Test-Path -LiteralPath (Join-Path $dir "fastboot$($script:PtExt)"))) {
      Die (M platform_tools_no_adb_fastboot "$pt")
    }
    $src = $dir
  } else {
    $sha = $PtSha[$os]; $zip = "platform-tools_r$PtVer-$os.zip"; $dir = Join-Path (Join-Path $Here "tools") "platform-tools"
    if (-not (Pt-Marker-Ok $dir $sha)) {
      Pt-Fetch $zip $sha; Pt-Unpack $zip $sha
      if (-not (Pt-Marker-Ok $dir $sha)) { Die "internal: the unpacked Platform-Tools do not check out" }
    }
    $src = "tools\platform-tools, " + (M checked)
  }
  $env:ANDROID_ADB_SERVER_PORT = $AdbPort
  Remove-Item Env:ADB_SERVER_SOCKET -ErrorAction SilentlyContinue
  $adb = Join-Path $dir "adb$($script:PtExt)"; $fb = Join-Path $dir "fastboot$($script:PtExt)"
  $av = Pt-Run-Quiet $adb @("version") 20; $fv = Pt-Run-Quiet $fb @("--version") 20
  if ((Pt-Major $av) -lt 35 -or (Pt-Major $fv) -lt 35) { Die (M adb_fastboot_too_old_not "$dir") }
  $script:PtAdbV = (("$av" -split "`n")[0] -split " ")[-1].Trim(); $script:PtFbV = ((("$fv" -split "`n")[0]) -split " ")[2].Trim()
  $script:PtDir = $dir
  if ($script:OnWindows) { $script:PnpBefore = @(Usb-Problem-Ids) }
  $script:PtServer = $true
  $s = Pt-Run-Quiet $adb @("start-server") 20
  if ($null -eq $s) { Die (M adb_did_not_start_within "$AdbPort") }
  if ($LASTEXITCODE -ne 0) { Die (M adb_did_not_start_port "$AdbPort") }
  Ok ("Platform-Tools $(($script:PtFbV -split '-')[0]) ($src), " + (M own_adb_server_port) + " $AdbPort")
  Slow-Drive-Warn $Here
  if ($script:Image) { Slow-Drive-Warn (Split-Path -Parent ([IO.Path]::GetFullPath($script:Image))) }
}
function Pt-Cleanup {  # only our own adb server, never another program's
  if (-not $script:PtServer) { return }
  $script:PtServer = $false
  $null = Pt-Run-Quiet (Join-Path $script:PtDir "adb$($script:PtExt)") @("kill-server") 10
}
function Other-Adb-Hint {  # adb.exe of another program still runs: on Windows it keeps the projector's USB to itself
  $mine = Join-Path $script:PtDir "adb$($script:PtExt)"
  $other = @(Get-Process -Name adb -ErrorAction SilentlyContinue | Where-Object { $p = $null; try { $p = $_.Path } catch { }; -not $p -or $p -ne $mine })
  if ($other.Count -eq 0) { return "" }
  return " " + (M another_programs_adb_running_x "$($other.Count)")
}
function Usb-Problem-Ids {  # Windows: USB devices that have no working driver (Device Manager's yellow mark)
  try { return @(Get-PnpDevice -PresentOnly -ErrorAction Stop | Where-Object { $_.Status -ne "OK" -and $_.InstanceId -like "USB\*" } | ForEach-Object { $_.InstanceId }) }
  catch {
    try { return @(& pnputil /enum-devices /problem 2>$null | Where-Object { $_ -match "^\s*Instance ID:\s+(USB\\\S+)" } | ForEach-Object { $Matches[1] }) } catch { return @() }
  }
}
function Fb-Driver-Hint {  # Windows: the projector is on the cable in fastboot mode, but Windows has no driver for it
  if (-not $script:OnWindows) { return "" }
  $new = @(Usb-Problem-Ids | Where-Object { $script:PnpBefore -notcontains $_ })
  $named = @()
  try { $named = @(Get-PnpDevice -PresentOnly -ErrorAction Stop | Where-Object { $_.Status -ne "OK" -and ($_.InstanceId -match "VID_18D1|VID_0E8D" -or "$($_.FriendlyName)" -match "Android|fastboot|ADB") }) } catch { }
  if ($new.Count -eq 0 -and $named.Count -eq 0) { return "" }
  return " " + (M windows_sees_usb_device_without)
}
function Slow-Drive-Warn($folder) {  # Windows: the installer or the image on a USB stick, card or network drive
  if (-not $script:OnWindows -or -not $folder) { return }
  try {
    $full = [IO.Path]::GetFullPath($folder)
    $slow = $full.StartsWith("\\")
    if (-not $slow) {
      $root = [IO.Path]::GetPathRoot($full); $d = New-Object IO.DriveInfo($root)
      $slow = @("Removable", "Network", "CDRom") -contains "$($d.DriveType)"
      if (-not $slow) { try { $slow = ((Get-Partition -DriveLetter $root.Substring(0, 1) -ErrorAction Stop | Get-Disk -ErrorAction Stop).BusType -eq "USB") } catch { } }
    }
    if ($slow) { Warn (M usb_stick_card_external_network "$full") }
  } catch { }
}

function Fb-Allowed($act, $part) {
  # XGIMI Play 6: system, and the Google services add-on partition lumen_gapps ('gapps': create, resize, delete;
  # it is formatted and filled on the projector). Single slot; never format, erase or another flash.
  # misc: only the 2 KiB erase request for XGIMI's recovery (P6-Bcb-Write checks its bytes before the write).
  if ($script:Prof["FLOW"] -ceq "play6-wifi") {
    if ("${act}:$part" -ceq "flash:misc" -and $script:P6BcbOk) { return $true }
    return (@("flash:system", "create:lumen_gapps", "resize:lumen_gapps", "delete:lumen_gapps") -ccontains "${act}:$part")
  }
  # FLOW=ab-fastbootd: system and z9x_blobs of the slot Abf-Fbd-Check found running ($script:AbfS, one of SLOTS), the
  # Google services add-on z9x_gapps of that slot when the profile has GAPPS_ALLOW (the only delete), and the
  # format of userdata and metadata when WIPE_PARTS names them. No erase, vbmeta, KEEP partition or other slot.
  if ($script:Prof["FLOW"] -ceq "ab-fastbootd") {
    $sx = $script:AbfS
    if (-not $sx -or (@("$($script:Prof['SLOTS'])" -split " ") -cnotcontains $sx.TrimStart('_'))) { return $false }
    if (@("flash:system$sx", "create:z9x_blobs$sx", "resize:z9x_blobs$sx", "flash:z9x_blobs$sx") -ccontains "${act}:$part") { return $true }
    if ($script:Prof["GAPPS_ALLOW"] -and (@("create:z9x_gapps$sx", "resize:z9x_gapps$sx", "flash:z9x_gapps$sx", "delete:z9x_gapps$sx") -ccontains "${act}:$part")) { return $true }
    # the format: never while an update is staged or merging (the last getvar all)
    if ((@("format:userdata", "format:metadata") -ccontains "${act}:$part") -and (@("$($script:Prof['WIPE_PARTS'])" -split " ") -ccontains $part) -and
        (Gv-Val $script:AbfGv "snapshot-update-status") -ceq "none") { return $true }
    return $false
  }
  $s = State-Get slot
  $ok = @("flash:system", "flash:z9x_blobs$s", "create:z9x_blobs$s", "resize:z9x_blobs$s",
          "create:z9x_gapps$s", "resize:z9x_gapps$s", "format:z9x_gapps$s", "delete:z9x_gapps$s", "flash:z9x_gapps$s",
          "delete:product$s", "delete:system_ext$s", "format:userdata", "format:metadata", "format:cache")
  return ($ok -contains "${act}:$part") -or ($act -eq "delete" -and $part -like "*-cow")
}
function Fb-Do($act, $part, $arg) {
  if (-not (Fb-Allowed $act $part)) { Die "internal: $act $part is not on the allow-list" }
  switch ($act) {
    "flash"  { Fb flash $part $arg | Out-Host }
    "create" { Fb create-logical-partition $part $arg | Out-Host }
    "resize" { Fb resize-logical-partition $part $arg | Out-Host }
    "delete" { Fb delete-logical-partition $part | Out-Host }
    "format" {
      # FLOW=ab-fastbootd: fastbootd says raw, so the type is given: the profile's WIPE_FS, the file system its
      # stock fstab mounts there (ext4 on the HB, f2fs on the X50); f2fs as the Z9X way below
      if ($script:Prof["FLOW"] -ceq "ab-fastbootd" -and $script:Prof["WIPE_FS"] -ceq "f2fs") {
        if ($part -eq "userdata") { Fb "--fs-options=casefold,projid" format:f2fs userdata | Out-Host }
        else { Fb format:f2fs $part | Out-Host }   # metadata
      }
      elseif ($script:Prof["FLOW"] -ceq "ab-fastbootd") { Fb format:ext4 $part | Out-Host }
      elseif ($part -eq "userdata") { Fb "--fs-options=casefold,projid" format:f2fs userdata | Out-Host }
      elseif ($part -eq "metadata") { Fb format:f2fs metadata | Out-Host }
      else { Fb format:ext4 $part | Out-Host }  # cache, z9x_gapps_<slot>
    }
  }
  return ($LASTEXITCODE -eq 0)
}

function Pick-Device {
  $list = @(Pt-Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] })
  if ($script:Serial) {
    if ($list -contains $script:Serial) { return }
    if (-not $script:SerialFromState) { Die (M device_not_connected_over_adb "$($script:Serial)") }
    $script:Serial = ""
  }
  if ($list.Count -ne 1) {
    Pt-Adb devices
    Die ((M exactly_one_projector_must "$($list.Count)") + (Other-Adb-Hint))
  }
  $script:Serial = $list[0]
}

function Find-Image {
  if ($script:Image) { if (-not (Test-Path $script:Image)) { Die (M no_image "$($script:Image)") }; Image-Owner; return }
  $g = $script:ImgGlob
  $c = @(Get-ChildItem -Path (Join-Path $Here $g), (Join-Path (Split-Path -Parent $Here) $g) -ErrorAction SilentlyContinue)
  if ($c.Count -lt 1) { Die (M no_found_use_image_file "$g") }
  # never pick one of several by name order: the editions (with and without Google) differ
  if ($c.Count -gt 1) { Die (M several_images_found_without) }
  $script:Image = $c[0].FullName
  Image-Owner
}

# a Z9X never takes an image named for another projector (lumen-os-play6-... matches lumen-os-*-system.img): the
# Play 6 token anywhere in the name, the token of a FLOW=ab-fastbootd projector after lumen-os-
function Image-Owner {
  if ($script:Prof["FLOW"] -cne "z9x-usb") { return }
  $leaf = Split-Path -Leaf $script:Image
  foreach ($id in @(Profile-Ids)) {
    $tok = Devconf-Get $id "IMAGE_TOKEN"
    if (-not $tok) { continue }
    $fl = Profile-Flow $id
    if (($fl -ceq "play6-wifi" -and $leaf -like "*$tok*") -or ($fl -ceq "ab-fastbootd" -and $leaf -clike "lumen-os-$tok-*")) {
      Die (M image_not_xgimi_z9x_nothing "$(Dev-Name $id)")
    }
  }
}

# the edition of the image (1.0.1): 0 = Lumen OS without Google (-nogms- in the name, ro.z9x.gms=0), 1 = with
# Google services. With dump.erofs on this computer the image's own product build.prop must agree.
function Image-Edition {
  $name = Split-Path -Leaf $script:Image
  $g = if ($name -like "*-nogms-*") { "0" } else { "1" }
  if (Get-Command dump.erofs -ErrorAction SilentlyContinue) {
    $bp = @(& dump.erofs --cat --path=/system/product/etc/build.prop $script:Image 2>$null)
    if ($LASTEXITCODE -eq 0 -and $bp.Count -gt 0) {
      $p = ($bp | Where-Object { $_ -like "ro.z9x.gms=*" } | Select-Object -Last 1)
      $p = if ($p) { $p.Substring(11).Trim() } else { "" }
      if (($g -eq "0" -and $p -ne "0") -or ($g -eq "1" -and $p -eq "0")) {
        Die (M file_name_image_disagree_about "$name" "$p")
      }
      # whose image it is: Lumen OS that names another projector of lib\devices.conf (its LUMEN_PROP, e.g. the X50
      # Ultra, which runs the same XGIMI product image) or another device id than the Z9X's is never written here
      foreach ($l in (Devconf-Lines)) {
        if ($l -cnotmatch '^([a-z0-9]+)\.LUMEN_PROP=([^ ]+) ([^ ]+)$') { continue }
        $id = $Matches[1]; $lp = $Matches[2]; $lv = $Matches[3]
        $v = ($bp | Where-Object { $_.StartsWith("$lp=", [StringComparison]::Ordinal) } | Select-Object -Last 1)
        if ($v -and $v.Substring($lp.Length + 1).Trim() -ceq $lv) { Die (M image_not_xgimi_z9x_nothing "$(Dev-Name $id)") }
      }
      $d = ($bp | Where-Object { $_ -like "ro.z9x.device=*" } | Select-Object -Last 1)
      $d = if ($d) { $d.Substring(14).Trim() } else { "" }
      if ($d -and $d -cne "z9x") { Die (M not_lumen_os_xgimi_z9x "$name" "$d") }
    }
  }
  State-Set image_gms $g
  if ($g -eq "0") { Ok (M edition_lumen_os_without_google) } else { Ok (M edition_google_services) }
}

# keep-data repairs never switch editions: Play and Google services updates in /data would be orphaned and
# Google accounts would survive. dev_gms comes from Check-Device (0, 1, or stock).
function Edition-Guard {
  $d = State-Get dev_gms; $i = State-Get image_gms
  if ($d -eq "0" -or $d -eq "1") {
    if ($d -eq $i) { return }
    if ($d -eq "0") { Die (M projector_runs_lumen_os_without) }
    Die (M projector_runs_lumen_os_google)
  }
}

function Check-Tools {  # Pt-Setup ran: adb and fastboot of our own, 35 or newer
  if ($script:Prof["FLOW"] -ceq "z9x-usb" -and -not $KeepData -and $Wipe -eq "format") {
    if (-not ((Test-Path (Join-Path $script:PtDir "make_f2fs$($script:PtExt)")) -and (Test-Path (Join-Path $script:PtDir "mke2fs$($script:PtExt)")))) {
      Die (M make_f2fs_mke2fs_missing_next "$($script:PtDir)")
    }
  }
  # FLOW=ab-fastbootd: 'fastboot format:<WIPE_FS>' makes the file system with the tool next to fastboot
  if ($script:Prof["FLOW"] -ceq "ab-fastbootd" -and $script:Prof["WIPE_FS"] -ceq "f2fs") {
    if (-not (Test-Path -LiteralPath (Join-Path $script:PtDir "make_f2fs$($script:PtExt)"))) {
      Die (M make_f2fs_missing_next_fastboot "$($script:PtDir)")
    }
  }
  elseif ($script:Prof["FLOW"] -ceq "ab-fastbootd" -and -not (Test-Path -LiteralPath (Join-Path $script:PtDir "mke2fs$($script:PtExt)"))) {
    Die (M mke2fs_missing_next_fastboot_use "$($script:PtDir)")
  }
  Ok "adb $($script:PtAdbV), fastboot $($script:PtFbV)"
}

function Verify-Sums($sums) {
  # fails closed: a missing signature or certificate stops the install (only -UnsignedImage skips it)
  $sig = "$sums.sig"; $cert = Join-Path $Here ($script:Prof["CERT"] -replace '/', '\')
  if (-not ((Test-Path $sums) -and (Test-Path $sig) -and (Test-Path $cert))) { return $false }
  $c = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2 -ArgumentList $cert
  $rsa = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPublicKey($c)
  $ok = $rsa.VerifyData([IO.File]::ReadAllBytes($sums), [IO.File]::ReadAllBytes($sig),
        [System.Security.Cryptography.HashAlgorithmName]::SHA256, [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
  if (-not $ok) { Die (M sha256sums_not_signed_lumen_os) }
  Ok (M sha256sums_signature_lumen_os)
  return $true
}

function Check-Image([switch]$Force) {
  Find-Image
  $name = Split-Path -Leaf $script:Image
  $dir = Split-Path -Parent $script:Image
  if ($name -match "PRIVATE" -and $Blobs -ne "embedded") { Die (M private_image_codec_files_inside "$name") }
  if ($Blobs -eq "none") { Warn (M no_blobs_no_sound_no) }
  $sums = Join-Path $dir "SHA256SUMS"
  if (-not (Verify-Sums $sums)) {
    if (-not $UnsignedImage) {
      if (-not (Test-Path $sums)) { Die (M no_sha256sums_next_image_cannot) }
      Die (M no_sha256sums_sig_installer)
    }
    Warn (M unsigned_image_image_not)
    if ((State-Get unsigned_ok) -ne "${name}:$(File-Key $script:Image)") {
      if ((Read-Host (M type_unsigned_install_anyway "$name")) -cne "UNSIGNED") { Die (M cancelled_nothing_written) }
      State-Set unsigned_ok "${name}:$(File-Key $script:Image)"
    }
    if (-not (Test-Path $sums)) {
      $sums = $null
      foreach ($f in @(Get-ChildItem -Path (Join-Path $dir "SHA256SUMS*") -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*.sig" })) {
        if (Get-Content $f.FullName | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))$" }) { $sums = $f.FullName; break }
      }
    }
    if (-not $sums) { Warn (M unsigned_image_without_any); Image-Edition; return }
  }
  $want = (Get-Content $sums | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))$" } | ForEach-Object { ($_ -split "\s+")[0] }) | Select-Object -First 1
  if (-not $want) { Die (M not_listed "$name" "$(Split-Path -Leaf $sums)") }
  # the cache only spares a second hash in 'check' for exactly the same file; Cmd-Flash always re-hashes
  if ($Force -or (State-Get image_ok) -ne "${want}:$(File-Key $script:Image)") {
    Say (M checking_image_checksum_about)
    if ((Sha256 $script:Image) -ne $want.ToLower()) { State-Set image_ok none; Die (M checksum_mismatch_image_damaged) }
    State-Set image_ok "${want}:$(File-Key $script:Image)"
  }
  Ok "$name sha256 $($want.Substring(0,16))..."
  Image-Edition
}

function Check-Device {
  Pick-Device
  $code = Prop ro.boot.xgimi.modelname; $board = Prop ro.boot.hardware
  $vend = Prop ro.vendor.build.version.incremental; $slot = Prop ro.boot.slot_suffix; $sys = Prop ro.z9x.version
  if ($code -ne $script:Prof["MODEL"] -or $board -ne $script:Prof["BOARD"]) { Die ((M not_xgimi_z9x_model_board "$code" "$board") + (Other-Hint $code $board)) }
  if (@(Abf-List VENDOR_OK) -notcontains $vend) { Die (M xgimi_firmware_not_supported_v6 "$vend") }
  State-Set serial $script:Serial; State-Set slot $slot
  if ($sys) { $g = Prop ro.z9x.gms; State-Set dev_gms $(if ($g) { $g } else { "1" }) } else { State-Set dev_gms stock }
  if (-not $sys) { $sys = M xgimi_stock }
  Ok "XGIMI Z9X $($script:Serial), $(M firmware) $vend, $(M slot) $($slot.TrimStart('_')), $(M system): $sys"
}

function Cmd-Check { Check-Tools; Check-Device; Check-Image; Ok (M check_passed_nothing_written); State-Set step_check done }

function Cmd-Backup {
  $pre = ""
  if ($script:Prof["FLOW"] -ceq "play6-wifi") { if (-not (Play6-Adb 60)) { Die (P6-NoConn) }; $pre = "$($script:Projector)-" }
  elseif (Abf-On) { if (-not (Abf-Adb 60)) { Die (P6-NoConn) }; $pre = "$($script:Projector)-" }
  else { Pick-Device }
  $d = Join-Path $BackupRoot ($pre + (Get-Date -Format "yyyyMMdd-HHmm"))
  New-Item -ItemType Directory -Force -Path (Join-Path $d "apps"), (Join-Path $d "sdcard"), (Join-Path $d "settings") | Out-Null
  Say (M backup "$d")
  if ($script:Prof["FLOW"] -cne "z9x-usb") { Say (M over_wi_fi_takes_about) }
  $n = 0
  foreach ($p in @(Adb shell pm list packages -3 | ForEach-Object { "$_".Trim() -replace "^package:", "" } | Where-Object { $_ })) {
    foreach ($path in @(Adb shell pm path $p | ForEach-Object { "$_".Trim() -replace "^package:", "" } | Where-Object { $_ })) {
      $t = Join-Path (Join-Path $d "apps") $p; New-Item -ItemType Directory -Force -Path $t | Out-Null
      Adb pull $path $t | Out-Null; if ($LASTEXITCODE -eq 0) { $n++ }
    }
  }
  Ok (M apk_files_apps_app_data "$n")
  foreach ($p in @("Download","Movies","Music","Pictures","DCIM","Documents")) { Adb pull "/sdcard/$p" (Join-Path $d "sdcard") | Out-Null }
  foreach ($p in @("global","secure","system")) { Adb shell settings list $p | Set-Content -Encoding UTF8 (Join-Path (Join-Path $d "settings") "$p.txt") }
  State-Set backup $d; State-Set step_backup done
  Ok (M backup_done)
}

# minimal POSIX ustar writer (deterministic: sorted, mtime 0, uid/gid 0), padded to 4 MiB
function Write-Ustar($root, $out) {
  $fs = [IO.File]::Create($out)
  $enc = [Text.Encoding]::ASCII
  $items = @(Get-ChildItem -Recurse -Path $root | ForEach-Object {
    $rel = $_.FullName.Substring($root.Length).TrimStart('\','/') -replace '\\','/'
    [pscustomobject]@{ Rel = $(if ($_.PSIsContainer) { "$rel/" } else { $rel }); Dir = $_.PSIsContainer; Full = $_.FullName }
  } | Sort-Object { $_.Rel } -Culture "")
  foreach ($it in $items) {
    $data = if ($it.Dir) { [byte[]]@() } else { [IO.File]::ReadAllBytes($it.Full) }
    $h = New-Object byte[] 512
    $put = { param($s, $off, $len) $b = $enc.GetBytes($s); [Array]::Copy($b, 0, $h, $off, [Math]::Min($b.Length, $len)) }
    & $put $it.Rel 0 100
    & $put $(if ($it.Dir) { "0000755" } else { "0000644" }) 100 8
    & $put "0000000" 108 8; & $put "0000000" 116 8
    & $put ([Convert]::ToString($data.Length, 8).PadLeft(11, '0')) 124 12
    & $put "00000000000" 136 12
    & $put "        " 148 8
    $h[156] = if ($it.Dir) { [byte][char]'5' } else { [byte][char]'0' }
    & $put "ustar" 257 6; & $put "00" 263 2; & $put "root" 265 32; & $put "root" 297 32
    $sum = 0; foreach ($x in $h) { $sum += $x }
    & $put ([Convert]::ToString($sum, 8).PadLeft(6, '0')) 148 6; $h[154] = 0; $h[155] = 32
    $fs.Write($h, 0, 512)
    if ($data.Length -gt 0) { $fs.Write($data, 0, $data.Length); $pad = (512 - ($data.Length % 512)) % 512; if ($pad) { $fs.Write((New-Object byte[] $pad), 0, $pad) } }
  }
  $fs.Write((New-Object byte[] 1024), 0, 1024)
  if ($fs.Length -gt $BlobSize) { $fs.Close(); Die "blobs tar larger than $([int64]$BlobSize / 1048576) MiB" }
  $fs.SetLength($BlobSize); $fs.Close()
}

# blobs_allow.txt from=<sha256>:<'N,Md' commands>: the stock original of a file (XGIMI's audio policy) is
# turned into the listed file by deleting those lines (same bytes as sed on macOS/Linux), then checked.
function Remove-Lines($path, $cmds) {
  $del = @{}
  foreach ($c in ($cmds -split ";")) {
    if ($c -notmatch '^(\d+)(,(\d+))?d$') { Die "internal: bad from= command '$c'" }
    $a = [int]$Matches[1]; $b = if ($Matches[3]) { [int]$Matches[3] } else { $a }
    for ($i = $a; $i -le $b; $i++) { $del[$i] = $true }
  }
  $bytes = [IO.File]::ReadAllBytes($path)
  $out = New-Object IO.MemoryStream
  $ln = 1; $start = 0
  for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq 10) {
      if (-not $del.ContainsKey($ln)) { $out.Write($bytes, $start, $i - $start + 1) }
      $ln++; $start = $i + 1
    }
  }
  if ($start -lt $bytes.Length -and -not $del.ContainsKey($ln)) { $out.Write($bytes, $start, $bytes.Length - $start) }
  [IO.File]::WriteAllBytes($path, $out.ToArray())
}

function Cmd-Blobs {
  if ($Blobs -eq "none") { Warn (M no_blobs_codec_audio_files); State-Set step_blobs skip; return }
  if ($Blobs -ne "partition") { Ok (M image_carries_codec_files_itself_2); State-Set step_blobs skip; return }
  Pick-Device
  $allow = Join-Path $Here "lib\blobs_allow.txt"
  $w = Join-Path $StateDir "blobs"; Remove-Item -Recurse -Force $w -ErrorAction SilentlyContinue
  $x = Join-Path $w "x"; New-Item -ItemType Directory -Force -Path $x | Out-Null
  $set = ((Get-Content $allow | Where-Object { $_ -like "set=*" } | Select-Object -First 1) -replace "^set=", "")
  $manifest = @("set=$set"); $n = 0
  foreach ($line in Get-Content $allow) {
    if (-not $line -or $line.StartsWith("#") -or $line.StartsWith("set=")) { continue }
    $f = $line -split "\s+"; $h = $f[0]; $rel = $f[1]; $src = $f[2]
    $from = ""; foreach ($o in ($f | Select-Object -Skip 3)) { if ($o -like "from=*") { $from = $o.Substring(5) } }
    $dst = Join-Path $x ($rel -replace '/', '\')
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dst) | Out-Null
    $got = ""
    foreach ($p in @($src, "/system/$rel")) {
      Adb pull $p $dst | Out-Null
      if ($LASTEXITCODE -eq 0 -and (Test-Path $dst)) {
        $got = Sha256 $dst; if ($got -eq $h) { break }
        if ($from -and $got -eq ($from -split ":", 2)[0]) {
          Remove-Lines $dst ($from -split ":", 2)[1]
          $got = Sha256 $dst; if ($got -eq $h) { break }
        }
      }
    }
    if ($got -ne $h) { Die (M projector_not_match_allow_list_4 "$rel") }
    $manifest += "$rel $h $((Get-Item $dst).Length)"; $n++
  }
  [IO.File]::WriteAllText((Join-Path $x "MANIFEST"), (($manifest -join "`n") + "`n"))
  $img = Join-Path $w "z9x_blobs.img"
  Write-Ustar $x $img
  New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null
  $out = Join-Path $BackupRoot "blobs-$($script:Serial).img"; Copy-Item $img $out -Force
  State-Set blobs_img $out; State-Set step_blobs done
  Ok (M codec_audio_files_projector_set "$n" "$set" "$out")
}

function Cmd-Vbmeta {
  $g = P6-Guide
  Write-Host ""
  Write-Host (M one_manual_step_vbmeta_yourself_2)
  Write-Host "  Google: $GoogleGsi"
  Write-Host "  $(M guide): $g#vbmeta"
  if (Ask (M have_done_vbmeta_step_did)) { State-Set step_vbmeta done }
  else { Die (M vbmeta_step_first_run_installer) }
}

function Try-Root {
  if ((Prop ro.debuggable) -ne "1") { return $false }
  if (((Adb shell id -u) | Out-String).Trim() -eq "0") { return $true }
  Adb root | Out-Null; Start-Sleep 3; Adb wait-for-device | Out-Null
  return (((Adb shell id -u) | Out-String).Trim() -eq "0")
}
function Root-Setting-Wait {  # -GappsZip: Developer options > Rooted debugging is off; $true = the user turned it on, try again
  # (LineageOS adbd asks its adb_root service first: "ADB Root access is disabled by system setting"; never bypassed)
  Write-Host ""
  Write-Host (M root_debugging_off_projector)
  return (Ask (M rooted_debugging_now))
}
function Root-Setting-Stop {  # the user did not turn it on now: the next run continues at this step
  Die (M turn_rooted_debugging_settings)
}
function Calib-Snapshot($tag) {
  $f = Join-Path $StateDir "calib_$tag.txt"
  $lines = foreach ($p in @("persist","xgimidatabase","xgimisps","xgimicri","project_id","tvcertificate")) {
    ((Adb shell "[ -e /dev/block/by-name/$p ] && sha256sum /dev/block/by-name/$p") | Out-String).Trim()
  }
  Set-Content -Path $f -Value ($lines | Where-Object { $_ }) -Encoding UTF8
  Ok (M calibration_fingerprint_saved_read "$tag")
}

function Cmd-Flash {
  Check-Tools; Check-Device; Check-Image -Force
  if ($KeepData) { Edition-Guard }
  if ((State-Get step_vbmeta) -ne "done") { Cmd-Vbmeta }
  if ($Blobs -eq "partition" -and (State-Get step_blobs) -ne "done") { Cmd-Blobs }
  if ($GappsZip) { Z9x-Gapps-Prepare }   # everything slow before fastbootd (its ~2 minute limit)
  $S = State-Get slot
  $blobImg = if ($Blobs -eq "partition") { State-Get blobs_img } else { "" }
  $sl = $S.TrimStart('_')
  if ($KeepData) {
    Write-Host (M writing_lumen_os_slot_without "$sl" "$S")
    if ((Read-Host (M type_write_continue)) -cne "WRITE") { Die (M cancelled_nothing_written) }
  }
  else {
    Write-Host (M writing_lumen_os_slot_erasing "$sl" "$S")
    if ($GappsZip) {
      $gmb = [math]::Ceiling($script:GiSize / 1MB)
      Write-Host (M gapps_zip_same_session_also "$($script:GzName)" "$S" "$gmb")
    }
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_written) }
    $script:EraseNow = $true
  }
  # -GappsZip: the add-on of this write; any other write forgets what an earlier one-pass install left saved
  if ($GappsZip -or (State-Get gapps_plan) -or (State-Get step_gapps_part) -or (State-Get step_gapps_fill) -or (State-Get step_gapps_flash)) {
    State-Set gapps_plan $script:GzSha; State-Set gapps_img $script:GiSha
    State-Set step_gapps_part ""; State-Set step_gapps_fill ""; State-Set step_gapps_flash ""
    if ($GappsZip) { State-Set step_wipe "" }
  }
  if (Try-Root) { Calib-Snapshot before }
  Say (M restarting_fastbootd_about_30_s)
  Adb reboot fastboot | Out-Null
  $seen = $false
  for ($i = 0; $i -lt 45; $i++) { if ((Pt-Fastboot devices) -match "^$([regex]::Escape($script:Serial))\s") { $seen = $true; break }; Start-Sleep 2 }
  if (-not $seen) { Die ((M projector_did_not_appear_fastboot) + (Fb-Driver-Hint)) }
  $gv = Fb getvar all
  function Val($k) { $l = $gv | Where-Object { $_ -like "(bootloader) ${k}:*" } | Select-Object -First 1; if ($l) { ($l.Substring(("(bootloader) ${k}:").Length)).Trim() } else { "" } }
  if ((Val is-userspace) -ne "yes") { Fb reboot | Out-Null; Die (M not_fastbootd) }
  if ((Val product) -ne $script:Prof["FB_PRODUCT"]) { Fb reboot | Out-Null; Die (M fastbootd_not_z9x_one) }
  if ((Val unlocked) -ne "yes") { Fb reboot | Out-Null; Die (M bootloader_locked) }
  if ((Val current-slot) -ne $S.TrimStart('_')) { Fb reboot | Out-Null; Die "slot mismatch" }
  if ((Val snapshot-update-status) -ne "none") { Fb reboot | Out-Null; Die (M update_still_being_finished_let) }
  $cows = $gv | ForEach-Object { if ($_ -match "partition-size:([a-z0-9_]+-cow):") { $Matches[1] } } | Sort-Object -Unique
  foreach ($p in $cows) { Say ((M removing_update_leftover) + " $p"); if (-not (Fb-Do delete $p)) { Fb reboot | Out-Null; Die "delete $p" } }
  foreach ($p in @("product$S", "system_ext$S")) { if ($gv -match "partition-size:${p}:") { Say ((M removing_unused_xgimi_partition) + " $p"); $null = Fb-Do delete $p } }
  if (-not $GappsZip -and $gv -match "partition-size:z9x_gapps${S}:") { Say (M z9x_gapps_google_services_added_2 "$S") }
  Say (M writing_system_about_40_s)
  if (-not (Fb-Do flash system $script:Image)) { Fb reboot | Out-Null; Die (M system_not_written_projector_still) }
  State-Set step_flash_system done
  if ($blobImg) {
    if ($gv -match "partition-size:z9x_blobs${S}:") { $null = Fb-Do resize "z9x_blobs$S" $BlobSize } else { $null = Fb-Do create "z9x_blobs$S" $BlobSize }
    if (Fb-Do flash "z9x_blobs$S" $blobImg) { Ok "z9x_blobs$S" } else { Warn (M codec_audio_files_not_written) }
  }
  if ($GappsZip) { if (-not (Z9x-Gapps-Write $S)) { State-Set gapps_plan "" } }
  if (-not $KeepData) {
    if ($Wipe -eq "format") {
      Say (M wiping_data_format_userdata)
      if (-not (Fb-Do format userdata)) { Fb reboot | Out-Null; Die (M wipe_failed_settings_device_reset) }
      $null = Fb-Do format metadata
      if ($gv -match "partition-type:cache:") { $null = Fb-Do format cache }
      State-Set step_wipe done
    } else { State-Set step_wipe factory-reset }
  }
  Fb reboot | Out-Null
  State-Set step_flash done
  if ((State-Get step_gapps_flash) -eq "done") { Ok (M written_first_start_takes_2); return }
  Ok (M written_first_start_takes_2_2)
  if ($Wipe -eq "factory-reset" -and -not $KeepData) { Say (M now_projector_settings_device_3) }
}

function Cmd-Verify {
  Say (M waiting_projector_finish_starting)
  for ($i = 0; $i -lt 150; $i++) {
    $l = @(Pt-Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] })
    if ($l.Count -ge 1) { if (-not $script:Serial) { $script:Serial = $l[0] }; if ((Prop sys.boot_completed) -eq "1") { break } }
    Start-Sleep 2
  }
  if ((Prop sys.boot_completed) -ne "1") { Warn (M no_usb_connection_started_system_2); return }
  $nogms = (Prop ro.z9x.gms) -eq "0"
  $v = Prop ro.z9x.version; if ($v) { Ok ("Lumen OS $v" + $(if ($nogms) { ", " + (M without_google) } else { "" })) } else { Warn "ro.z9x.version" }
  if ($nogms -and (Prop sys.z9x.gapps) -eq "ok") {
    Ok (M google_services_added_gapps)
  } elseif ($nogms) {
    $gp = @(Adb shell pm list packages | ForEach-Object { "$_".Trim() -replace "^package:", "" } | Where-Object { $_ -match '^(com\.google\.|com\.mtg\.|com\.android\.vending$)' } | Select-Object -First 3)
    if ($gp.Count -eq 0) { Ok (M no_google_package) } else { Warn ((M google_packages_lumen_os_without) + ": " + ($gp -join " ")) }
  }
  if ((Prop ro.z9x.keys) -eq "release") { Ok (M release_keys) } else { Warn "ro.z9x.keys" }
  if ((Prop init.svc.gmpf_main) -eq "running") { Ok (M projector_service_running) } else { Warn "gmpf_main" }
  if ($Blobs -eq "partition") { if ((Prop sys.z9x.blobs) -eq "ok") { Ok "blobs ok" } else { Warn "sys.z9x.blobs=$(Prop sys.z9x.blobs) (no sound / protected video until 'blobs' + 'flash -KeepData')" } }
  if ((Test-Path (Join-Path $StateDir "calib_before.txt")) -and (Try-Root)) {
    Calib-Snapshot after
    # XGIMI's own services write these partitions at every start (all five hashes change across a plain reboot),
    # so a changed hash proves nothing; the guarantee is the allow-list: the installer never writes them.
    Ok (M calibration_partitions_never "$StateDir")
    Adb unroot | Out-Null
  }
  State-Set step_verify done
}

# waiting for fastbootd: Lumen OS 1.0.1+ goes there by itself after 3 starts in a row that did not complete. There
# is no adb then ('gapps -Remove' waits for it there). XGIMI's fastbootd leaves by itself after about 2 minutes,
# so everything slow happens before the wait.
function Fbd-Find($secs) {
  for ($i = 0; $i -lt $secs; $i += 2) {
    $list = @(Pt-Fastboot devices 2>$null | ForEach-Object { ("$_".Trim() -split "\s+")[0] } | Where-Object { $_ })
    if ($script:Serial -and ($list -contains $script:Serial)) { return $true }
    if ($list.Count -eq 1 -and (-not $script:Serial -or $script:SerialFromState)) {
      if ($script:Serial) { Warn (M using_saved_serial "$($list[0])" "$($script:Serial)") }
      $script:Serial = $list[0]; return $true
    }
    if ($list.Count -gt 1) { Die (M several_devices_fastboot_mode) }
    if ($i -gt 0 -and ($i % 120) -eq 0) { Say (M still_waiting_fastboot_mode) }
    Start-Sleep 2
  }
  return $false
}

function Cmd-Gsf {
  Pick-Device
  if (-not (Prop ro.z9x.version)) { Die (M projector_not_running_lumen_os) }
  # Lumen OS without Google with the user's add-on ('gapps') has GSF too: the package decides, not ro.z9x.gms
  if (-not ((Adb shell pm path com.google.android.gsf) | Out-String).Trim()) {
    Ok (M lumen_os_has_no_google)
    if ((Prop ro.z9x.gms) -eq "0") { Say (M add_them_bash_lumen_install) }
    return
  }
  Adb root | Out-Null; Start-Sleep 3; Adb wait-for-device | Out-Null
  $id = ((Adb shell "sqlite3 /data/data/com.google.android.gsf/databases/gservices.db `"select value from main where name='android_id';`"") | Out-String).Trim()
  Adb unroot | Out-Null
  if (-not $id) { Die (M no_gsf_id_yet_connect) }
  Write-Host "GSF ID: $id"
  Write-Host (M register_https_www_google_com_2)
}

# gapps (Lumen OS without Google + the user's own Google services; see lumen-install.sh, section 'gapps'):
# -Zip FILE (only a MindTheGapps zip whose sha256 is in lib\gapps_allow.txt) goes into the logical partition
# z9x_gapps_<slot>: created / resized and formatted in fastbootd (room in super checked first), filled over
# adb root by lib\gapps_fill.sh, checked by the image's z9x_gapps.sh, then the data wipe of 'flash' (not with
# -KeepData; -Wipe factory-reset leaves it to the projector). -Remove deletes the partition (also from fastboot
# mode, waited for there when the system no longer starts) with the same wipe. 'gapps' alone shows the state.
$GappsDir = "/data/local/tmp/lumen-gapps"
# the add-on's allow-list (the same file as the image's /system/etc/z9x/gapps_allow.txt) and the checked add-on images:
# the Z9X ones here; a FLOW profile names its own (GAPPS_ALLOW, GAPPS_IMAGES, Load-Profile)
$script:GappsAllowF = Join-Path $Here "lib\gapps_allow.txt"; $script:GappsAllowRel = "lib\gapps_allow.txt"
$script:GappsImagesF = Join-Path $Here "lib\gapps_images.txt"; $script:GappsImagesRel = "lib\gapps_images.txt"
function Gapps-Line($sha) { $af = if ($script:GappsAllowF) { $script:GappsAllowF } else { Join-Path $Here "lib\gapps_allow.txt" }; Get-Content $af | Where-Object { $_ -like "zip=$sha *" } | Select-Object -First 1 }
function Gapps-Field($line, $key) { foreach ($w in ("$line" -split " ")) { if ($w.StartsWith("$key=")) { return $w.Substring($key.Length + 1) } }; return "" }
function Gv-Size($gv, $name) {
  $l = $gv | Where-Object { $_ -like "(bootloader) partition-size:${name}:*" } | Select-Object -First 1
  if (-not $l) { return $null }
  return [Convert]::ToInt64(($l.Substring(("(bootloader) partition-size:${name}:").Length)).Trim(), 16)
}
function Super-Free($gv) {  # bytes of super no logical partition uses: each rounded up to 1 MiB, 2 MiB for metadata
  $sup = Gv-Size $gv "super"
  if ($null -eq $sup) { return [int64]0 }
  $used = [int64]0
  $names = @($gv | ForEach-Object { if ("$_" -match "^\(bootloader\) is-logical:([^:]+):yes") { $Matches[1] } } | Sort-Object -Unique)
  foreach ($n in $names) { $sz = Gv-Size $gv $n; if ($null -ne $sz) { $used += [int64][math]::Ceiling($sz / 1MB) * 1MB } }
  return [int64]($sup - $used - 2MB)
}
function Fbd-Check($slot) {  # the fastbootd checks of 'flash'; returns 'getvar all', sets $script:FbdSlot
  $gv = Fb getvar all
  function V($k) { $l = $gv | Where-Object { $_ -like "(bootloader) ${k}:*" } | Select-Object -First 1; if ($l) { ($l.Substring(("(bootloader) ${k}:").Length)).Trim() } else { "" } }
  if ((V is-userspace) -ne "yes") { Fb reboot | Out-Null; Die (M not_fastbootd) }
  if ((V product) -ne $script:Prof["FB_PRODUCT"]) { Fb reboot | Out-Null; Die (M fastbootd_not_z9x_one) }
  if ((V unlocked) -ne "yes") { Fb reboot | Out-Null; Die (M bootloader_locked) }
  $cs = V current-slot
  if (@("a", "b") -notcontains $cs) { Fb reboot | Out-Null; Die "current-slot '$cs'" }
  if ($slot -and $cs -ne $slot.TrimStart('_')) { Fb reboot | Out-Null; Die "slot mismatch" }
  $script:FbdSlot = "_$cs"
  if ((V snapshot-update-status) -ne "none") { Fb reboot | Out-Null; Die (M lumen_os_update_still_being_2) }
  return $gv
}
function Fbd-Enter($slot) {
  Say (M restarting_fastbootd_about_30_s)
  Adb reboot fastboot | Out-Null
  $seen = $false
  for ($i = 0; $i -lt 45; $i++) { if ((Pt-Fastboot devices) -match "^$([regex]::Escape($script:Serial))\s") { $seen = $true; break }; Start-Sleep 2 }
  if (-not $seen) { Die ((M projector_did_not_appear_fastboot) + (Fb-Driver-Hint)) }
  return (Fbd-Check $slot)
}
function Fb-Wipe($gv) {  # the data wipe of 'flash', in fastbootd
  Say (M wiping_data_format_userdata)
  if (-not (Fb-Do format userdata)) { Fb reboot | Out-Null; Die (M wipe_failed_projector_settings) }
  $null = Fb-Do format metadata
  if ($gv -match "partition-type:cache:") { $null = Fb-Do format cache }
  State-Set step_wipe done
}
function Boot-Wait {  # up to 5 min for the projector back over adb with sys.boot_completed=1
  for ($i = 0; $i -lt 150; $i++) {
    $l = @(Pt-Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] })
    if (($l -contains $script:Serial) -and ((Prop sys.boot_completed) -eq "1")) { return $true }
    Start-Sleep 2
  }
  return $false
}
function Gapps-Sh { ((Adb shell sh "$GappsDir/fill.sh" @args) | Out-String).Trim() }
function Gapps-Push-Fill {
  # the device's sh needs LF line ends whatever a Windows checkout made of the file
  New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
  $tmp = Join-Path $StateDir "gapps_fill.sh"
  [IO.File]::WriteAllText($tmp, ([IO.File]::ReadAllText((Join-Path $Here "lib\gapps_fill.sh")) -replace "`r", ""))
  Adb shell rm -rf $GappsDir | Out-Null
  Adb shell mkdir -p $GappsDir | Out-Null
  Adb push $tmp "$GappsDir/fill.sh" | Out-Null
  if ($LASTEXITCODE -ne 0) { Die "adb push lib\gapps_fill.sh" }
}

function Gapps-Install {
  if (-not (Test-Path -LiteralPath $Zip)) { Die (M no_file "$Zip") }
  $zip = (Resolve-Path -LiteralPath $Zip).Path
  Check-Tools
  $dir = $script:PtDir
  if (-not (Test-Path (Join-Path $dir "mke2fs$($script:PtExt)"))) { Die (M mke2fs_missing_next_fastboot_use_2 "$dir") }
  $zname = Split-Path -Leaf $zip
  Say (M checking "$zname")
  $zsha = Sha256 $zip
  $zl = Gapps-Line $zsha
  if (-not $zl) { Die ((M not_mindthegapps_file_installer "$zname" "$($zsha.Substring(0,16))") + ". " + (M nothing_written)) }
  $name = ("$zl" -split " ")[1]; $part = [int64](Gapps-Field $zl "part"); $mb = [int64][math]::Floor($part / 1MB)
  Ok "$name, sha256 $($zsha.Substring(0,16))..."
  Check-Device
  $slot = State-Get slot; $p = "z9x_gapps$slot"; $sl = $slot.TrimStart('_')
  $dg = State-Get dev_gms
  if ($dg -eq "1") { Die (M projector_runs_lumen_os_google_2) }
  if ($dg -ne "0") { Die (M projector_not_run_lumen_os) }
  # the same add-on, on: writing it again would only cost the Google apps their data (one start without them)
  $have = Prop sys.z9x.gapps
  if ($have -eq "ok" -and (Prop sys.z9x.gapps.zip) -eq $zsha) {
    Ok (M these_google_services_already "$name")
    return
  }
  if (-not (Try-Root)) { Die (M adb_root_not_possible_system) }
  $al = @((Adb shell cat /system/etc/z9x/gapps_allow.txt) | ForEach-Object { "$_".TrimEnd("`r") })
  if (-not ($al | Where-Object { $_ -like "zip=$zsha *" })) { Die (M lumen_os_projector_not_accept) }
  if ($KeepData -or $Wipe -eq "factory-reset") {
    Write-Host (M adding_google_services_slot "$sl" "$p" "$mb" "$name")
    if ($KeepData) {
      Say (M keep_data_sign_google_later)
      if ($have -and $have -ne "none") { Say (M google_services_already_installed) }
    }
    if ((Read-Host (M type_write_continue)) -cne "WRITE") { Die (M cancelled_nothing_written) }
  } else {
    Write-Host (M adding_google_services_slot_2 "$sl" "$p" "$mb" "$name")
    if ((State-Get step_backup) -ne "done" -and (Ask (M make_backup_apps_files_first))) { Cmd-Backup }
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_written) }
  }
  # 1. the fill script's own checks on the projector, before any partition is touched
  Gapps-Push-Fill
  $o = Gapps-Sh pre $zsha (Get-Item -LiteralPath $zip).Length
  Adb shell rm -rf $GappsDir | Out-Null
  if (-not $o.StartsWith("OK pre ")) { Die ((M projector_not_ready) + ": " + ($o -replace "^FAIL ", "") + ". " + (M nothing_written)) }
  # 2. fastbootd: the partition
  $gv = Fbd-Enter $slot
  $free = Super-Free $gv; $cur = [int64]0
  $have = Gv-Size $gv $p; if ($null -ne $have) { $cur = $have }
  $avail = [int64][math]::Floor(($free + $cur) / 1MB)
  if (($free + $cur) -lt $part) { Fb reboot | Out-Null; Die (M not_enough_free_space_super "$avail" "$mb") }
  Say (M creating_mb_mb_super_stay "$p" "$mb" "$($avail - $mb)")
  $made = if ($cur -gt 0) { Fb-Do resize $p $part } else { Fb-Do create $p $part }
  if (-not $made) { Fb reboot | Out-Null; Die (M could_not_created_nothing_written "$p") }
  if (-not (Fb-Do format $p)) { $null = Fb-Do delete $p; Fb reboot | Out-Null; Die (M could_not_formatted_removed_again "$p") }
  Fb reboot | Out-Null
  # 3. filled over adb as root, checked by the image's own script
  Say (M waiting_projector_start_up_5)
  if (-not (Boot-Wait)) { Die (M projector_did_not_come_back "$p") }
  if (-not (Try-Root)) { Die "adb root" }
  Gapps-Push-Fill
  Say (M copying_projector_about_minute "$name")
  Adb push $zip "$GappsDir/gapps.zip" | Out-Null
  if ($LASTEXITCODE -ne 0) { Adb shell rm -rf $GappsDir | Out-Null; Die (M adb_push_zip_failed_still "$p") }
  Say (M copying_google_apps_checking_each "$p")
  $o = Gapps-Sh fill $zsha
  if (-not $o.StartsWith("OK fill ")) { Adb shell rm -rf $GappsDir | Out-Null; Die ((M google_services_were_not_added) + ": " + ($o -replace "^FAIL ", "") + ". " + (M lumen_os_starts_without_them)) }
  $c = ((Adb shell sh /system/etc/z9x/z9x_gapps.sh check) | Out-String).Trim()
  Adb shell rm -rf $GappsDir /metadata/z9x_gapps | Out-Null
  if (-not $c.StartsWith("ok: ")) { Die ((M projectors_own_check_failed "$p") + ": $c. " + (M lumen_os_starts_without_google)) }
  Ok "${p}: $($c.Substring(4))"
  State-Set gapps $name
  # 4. the wipe, so that the first start sets up Google services
  if ($KeepData) {
    Adb reboot | Out-Null
    Say (M restarting)
    if ((Boot-Wait) -and (Prop sys.z9x.gapps) -eq "ok") { Ok (M google_services_sign_settings) }
    else { Warn (M projector_did_not_report_google "$(Prop sys.z9x.gapps)") }
  } elseif ($Wipe -eq "factory-reset") {
    Adb reboot | Out-Null
    State-Set step_wipe factory-reset
    Say (M now_projector_settings_device_2)
  } else {
    $gv = Fbd-Enter $slot
    Fb-Wipe $gv
    Fb reboot | Out-Null
    Ok (M done_first_start_takes_2)
  }
  State-Set step_gapps done
}

function Gapps-Remove {
  Check-Tools
  $list = @(Pt-Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
  $viaAdb = $list.Count -gt 0
  if ($viaAdb) { Check-Device }
  if ($KeepData -or $Wipe -eq "factory-reset") {
    Write-Host (M removing_google_services_added)
    if ((Read-Host (M type_write_continue)) -cne "WRITE") { Die (M cancelled_nothing_written) }
  } else {
    Write-Host (M removing_google_services_added_2)
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_written) }
  }
  if ($viaAdb) { $gv = Fbd-Enter (State-Get slot) }
  else {
    Say (M no_projector_over_adb_waiting)
    if (-not (Fbd-Find 900)) { Die ((M projector_did_not_appear_fastboot_2) + (Fb-Driver-Hint)) }
    $gv = Fbd-Check ""
    State-Set serial $script:Serial; State-Set slot $script:FbdSlot
  }
  $S = $script:FbdSlot; $p = "z9x_gapps$S"; $removed = $false
  if ($gv -match "partition-size:${p}:") {
    if (-not (Fb-Do delete $p)) { Fb reboot | Out-Null; Die (M could_not_deleted_nothing_changed "$p") }
    $removed = $true; Ok (M deleted "$p")
  } else { Ok (M slot_has_no_nothing_remove "$($S.TrimStart('_'))" "$p") }
  if ($removed -and -not $KeepData -and $Wipe -eq "format") { Fb-Wipe $gv }
  Fb reboot | Out-Null
  State-Set gapps ""
  if ($removed -and -not $KeepData -and $Wipe -eq "factory-reset") { Say (M now_projector_settings_device) }
  Ok (M projector_starts_lumen_os_without)
}

function Gapps-Status {
  Pick-Device
  if (-not (Prop ro.z9x.version)) { Die (M projector_not_running_lumen_os) }
  $v = Prop sys.z9x.gapps; $z = Prop sys.z9x.gapps.zip
  if ($v -eq "ok") { Ok ((M google_services_added) + " (" + ("$(Gapps-Line $z)" -split " ")[1] + ")") }
  elseif ($v -eq "skip") { Ok (M lumen_os_has_google_services) }
  elseif (-not $v -or $v -eq "none") { Say (M no_google_services_added_add) }
  elseif ($v -eq "bad:slow") { Warn (M last_start_check_google_services) }
  elseif ($v -eq "off") {
    Warn (M google_services_were_turned_off)
    if (Ask (M turn_them_again_projector_restarts)) {
      if (-not (Try-Root)) { Die "adb root" }
      Adb shell rm -rf /metadata/z9x_gapps | Out-Null
      Adb reboot | Out-Null
      Ok (M turned_projector_restarts)
    }
  }
  else { Warn (M google_services_added_cannot_used "$v") }
}

function Cmd-Gapps {
  if ($Zip -and $Remove) { Die "gapps: -Zip or -Remove, not both" }
  if ($Remove) { Gapps-Remove } elseif ($Zip) { Gapps-Install } else { Gapps-Status }
}

# ------------------------------------------------------------------ XGIMI Z9X: Lumen OS and Google services in one pass (-GappsZip FILE)
# See lumen-install.sh, same section: ONE fastbootd visit and ONE wipe (decision 2026-10-30). Before anything is
# written: the zip, the image (without Google, able to take an erofs add-on of this zip: read with our dump.erofs),
# then the add-on image built HERE (Gi-Build: our own tar writer with owner root, modes 0755/0644 and the SELinux
# label on every entry, mkfs.erofs of tools\erofs-utils, fsck.erofs, every file read back, the image's sha256 against
# lib\gapps_images.txt). One ERASE question, then one fastbootd session: system (+ z9x_blobs), z9x_gapps_<slot>
# created or resized to the image and flashed (Z9x-Gapps-Write), the wipe, reboot. No room or a failed write: the
# partition is deleted and the install finishes without Google. fastbootd gone during the write: nothing erased,
# the same command again writes everything. No adb root, no start on the old data.
# State: gapps_plan, gapps_img, step_gapps_flash (done | skipped:<why>), step_wipe. An older installer's install that
# waits on the old data for its fill (step_gapps_part=done, no step_wipe): Z9x-Gapps-Finish flashes the add-on in
# fastbootd and wipes there.
$script:GzSha = ""; $script:GzName = ""; $script:GzPart = [int64]0; $script:GzZip = ""
$EuVer = "1.9.4"
$script:EuDir = ""; $script:EuErr = ""
$script:GiImg = ""; $script:GiSha = ""; $script:GiSize = [int64]0; $script:GiReady = $false
$GiLabel = "u:object_r:system_file:s0"; $GiMtime = [int64]1230768000

function Eu-Plat { if ([Environment]::Is64BitOperatingSystem) { return "windows-x64" }; return "" }
function Eu-Run($exe, [string[]]$argv, $outFile) {  # one of our erofs programs; its stdout into outFile as bytes ($null: dropped); exit code
  $psi = New-Object Diagnostics.ProcessStartInfo
  $psi.FileName = $exe; $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
  $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
  $psi.Arguments = (@($argv | ForEach-Object { '"' + ("$_" -replace '"', '\"') + '"' }) -join " ")
  $psi.EnvironmentVariables["CYGWIN"] = "nodosfilewarning"
  try { $p = [Diagnostics.Process]::Start($psi) } catch { $script:EuErr = "$_"; return 255 }
  $err = $p.StandardError.ReadToEndAsync()
  if ($outFile) { $fs = [IO.File]::Create($outFile); $p.StandardOutput.BaseStream.CopyTo($fs); $fs.Close() }
  else { $null = $p.StandardOutput.ReadToEnd() }
  $p.WaitForExit()
  $script:EuErr = $err.Result
  return $p.ExitCode
}
function Eu-Version($dir) {
  $tmp = Join-Path $StateDir "eu-version.txt"
  if ((Eu-Run (Join-Path $dir "mkfs.erofs.exe") @("--version") $tmp) -ne 0) { return "" }
  $v = ((Get-Content -LiteralPath $tmp -TotalCount 1) -split " ")[-1]; Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
  return "$v".Trim()
}
function Eu-Setup {  # mkfs/fsck/dump.erofs: our own (tools\erofs-utils), checked against lib\erofs_utils.txt, run from a copy in the state folder
  if ($script:EuDir) { return }
  New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
  if ($ErofsUtils) {
    $d = (Resolve-Path -LiteralPath $ErofsUtils -ErrorAction SilentlyContinue).Path
    if (-not $d -or -not (Test-Path (Join-Path $d "mkfs.erofs.exe")) -or -not (Test-Path (Join-Path $d "fsck.erofs.exe")) -or -not (Test-Path (Join-Path $d "dump.erofs.exe"))) {
      Die (M erofs_utils_no_mkfs_erofs "$ErofsUtils")
    }
    $v = Eu-Version $d
    if ($v -ne $EuVer) { Die (M erofs_utils_erofs_utils_installer "$ErofsUtils" "$v" "$EuVer") }
    $script:EuDir = $d; Ok "erofs-utils $EuVer (-ErofsUtils $d)"; return
  }
  $plat = Eu-Plat; $src = Join-Path (Join-Path $Here "tools") "erofs-utils"; $sums = Join-Path $Here "lib\erofs_utils.txt"
  $lines = @(); if (Test-Path -LiteralPath $sums) { $lines = @(Get-Content -LiteralPath $sums | Where-Object { $_ -match "^[0-9a-f]{64}  $plat/" }) }
  if (-not $plat -or -not ($lines | Where-Object { $_ -like "*  $plat/mkfs.erofs.exe" }) -or -not (Test-Path -LiteralPath (Join-Path $src $plat))) {
    Die (M installer_has_no_erofs_utils_2 "$env:PROCESSOR_ARCHITECTURE")
  }
  $d = Join-Path (Join-Path $StateDir "erofs-utils-$EuVer") $plat
  New-Item -ItemType Directory -Force -Path $d | Out-Null
  foreach ($l in $lines) {   # every listed file of this platform (the programs and the DLLs they need)
    $want = $l.Substring(0, 64); $rel = $l.Substring(66); $f = Split-Path -Leaf $rel
    $dst = Join-Path $d $f
    if ((Test-Path -LiteralPath $dst) -and (Sha256 $dst) -eq $want) { continue }
    $from = Join-Path $src ($rel -replace "/", "\")
    if (-not (Test-Path -LiteralPath $from) -or (Sha256 $from) -ne $want) { Die (M tools_erofs_utils_missing_damaged "$($rel -replace '/', '\')") }
    [IO.File]::WriteAllBytes($dst, [IO.File]::ReadAllBytes($from))   # a plain copy: no 'downloaded from the internet' mark
    if ((Sha256 $dst) -ne $want) { Die "internal: $dst" }
  }
  if ((Eu-Version $d) -ne $EuVer) { Die (M mkfs_erofs_tools_erofs_utils "$plat") }
  $script:EuDir = $d
  Ok "erofs-utils $EuVer (tools\erofs-utils, $(M checked))"
}

# the tar for mkfs.erofs --tar=f, written here (as lumen-install.sh gi_tar): every entry a PAX header with its SELinux
# label and a ustar header with owner root (0/0), the fixed time, mode 0755 (directories) or 0644 (files)
function Gi-Hdr($fs, $name, $mode, $size, $type) {
  if ($name.Length -gt 99) { Die "internal: tar name longer than 99 characters: $name" }
  $enc = [Text.Encoding]::ASCII
  $h = New-Object byte[] 512
  $put = { param($s, $off) $b = $enc.GetBytes($s); [Array]::Copy($b, 0, $h, $off, $b.Length) }
  & $put $name 0; & $put $mode 100; & $put "0000000" 108; & $put "0000000" 116
  & $put ([Convert]::ToString([int64]$size, 8).PadLeft(11, '0')) 124
  & $put ([Convert]::ToString($GiMtime, 8).PadLeft(11, '0')) 136
  & $put "        " 148; & $put $type 156
  & $put "ustar" 257; & $put "00" 263; & $put "root" 265; & $put "root" 297; & $put "0000000" 329; & $put "0000000" 337
  $sum = 0; foreach ($x in $h) { $sum += $x }
  & $put ([Convert]::ToString($sum, 8).PadLeft(6, '0')) 148; $h[154] = 0; $h[155] = 32
  $fs.Write($h, 0, 512)
}
function Gi-Entry($fs, $name, $mode, $size, $type) {
  $body = "SCHILY.xattr.security.selinux=$GiLabel"
  $l = $body.Length; $n = $l + 4; $n = $l + 3 + "$n".Length; $n = $l + 3 + "$n".Length   # the record counts its own digits
  Gi-Hdr $fs "././@PaxHeader" "0000644" $n "x"
  $rec = [byte[]]([Text.Encoding]::ASCII.GetBytes("$n $body") + [byte[]](0, 10))
  $fs.Write($rec, 0, $rec.Length); $fs.Write((New-Object byte[] (512 - $n)), 0, 512 - $n)
  Gi-Hdr $fs $name $mode $size $type
}
function Gi-Pad($fs, [int64]$size) { $pad = [int]((512 - ($size % 512)) % 512); if ($pad) { $fs.Write((New-Object byte[] $pad), 0, $pad) } }
function Gi-Lines($sha) {  # the zip's file lines of lib\gapps_allow.txt as "sha256 size path"
  $out = @(); $on = $false
  $af = if ($script:GappsAllowF) { $script:GappsAllowF } else { Join-Path $Here "lib\gapps_allow.txt" }
  foreach ($raw in (Get-Content $af)) {
    $w = @("$raw".Trim() -split "\s+")
    if ($w[0] -like "zip=*") { $on = ($w[0] -eq "zip=$sha"); continue }
    if ($on -and $w.Count -eq 3 -and -not $w[0].StartsWith("#")) { $out += ($w -join " ") }
  }
  return ,$out
}
function Gi-Tar($zipPath, $out) {  # the add-on's tree as a tar, laid out exactly as lib\gapps_fill.sh fills the ext4 partition; "" or why not
  $lines = Gi-Lines $script:GzSha
  if ($lines.Count -eq 0) { Die "internal: no files for $($script:GzName) in $($script:GappsAllowRel)" }
  $dirs = New-Object 'System.Collections.Generic.HashSet[string]'
  foreach ($ln in $lines) { $p = ($ln -split " ")[2]; while ($p.Contains("/")) { $p = $p.Substring(0, $p.LastIndexOf("/")); $null = $dirs.Add($p) } }
  $dl = [string[]]@($dirs); [Array]::Sort($dl, [StringComparer]::Ordinal)
  $files = [string[]]@($lines | ForEach-Object { ($_ -split " ")[2] }); [Array]::Sort($files, [StringComparer]::Ordinal)
  $byPath = @{}; foreach ($ln in $lines) { $w = $ln -split " "; $byPath[$w[2]] = $w }
  $zl = Gapps-Line $script:GzSha
  $mf = [Text.Encoding]::ASCII.GetBytes("# Lumen OS: Google services add-on (lumen-install gapps). z9x_gapps.sh checks it at every start`n# against /system/etc/z9x/gapps_allow.txt: do not edit.`n" +
    ((("$zl" -split " ")[0..1]) -join " ") + "`n" + ($lines -join "`n") + "`n")
  [IO.File]::WriteAllBytes((Join-Path (Split-Path -Parent $out) "MANIFEST"), $mf)
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $z = [IO.Compression.ZipFile]::OpenRead($zipPath)
  $fs = [IO.File]::Create($out)
  $tmp = Join-Path (Split-Path -Parent $out) "file"
  try {
    Gi-Entry $fs "./" "0000755" 0 "5"   # the image root: without this entry it would be 0777 and unlabelled
    foreach ($d in $dl) { Gi-Entry $fs "$d/" "0000755" 0 "5" }
    foreach ($p in $files) {
      if ($p -notmatch '^[A-Za-z0-9_@+.-]+(/[A-Za-z0-9_@+.-]+)+$' -or $p -match '(^|/)\.\.(/|$)') { return "bad path $p" }
      $e = $z.GetEntry("system/$p"); if (-not $e) { return "no system/$p in the zip" }
      [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $tmp, $true)
      $w = $byPath[$p]
      if ((Sha256 $tmp) -ne $w[0] -or (Get-Item -LiteralPath $tmp).Length -ne [int64]$w[1]) { return "sha256 of $p" }
      Gi-Entry $fs $p "0000644" ([int64]$w[1]) "0"
      $in = [IO.File]::OpenRead($tmp); $in.CopyTo($fs); $in.Close(); Gi-Pad $fs ([int64]$w[1])
    }
    Gi-Entry $fs "MANIFEST" "0000644" $mf.Length "0"; $fs.Write($mf, 0, $mf.Length); Gi-Pad $fs $mf.Length
    $fs.Write((New-Object byte[] 1024), 0, 1024)
  } finally { $fs.Close(); $z.Dispose(); Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue }
  return ""
}
function Gi-Cat($img, $path, $out) { return ((Eu-Run (Join-Path $script:EuDir "dump.erofs.exe") @("--cat", "--path=$path", $img) $out) -eq 0) }
function Gi-Image-Ok {  # the Lumen OS image to be written can use an erofs add-on of this zip (read with our dump.erofs)
  $tmp = Join-Path $StateDir "image-file.txt"; $leaf = Split-Path -Leaf $script:Image
  $ok1 = (Gi-Cat $script:Image "/system/etc/z9x/z9x_gapps.sh" $tmp) -and (@(Get-Content -LiteralPath $tmp) | Where-Object { $_ -match '^# lumen-gapps-fs:.* erofs' })
  if (-not $ok1) { Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue; Die (M cannot_take_google_services_one "$leaf") }
  $ok2 = (Gi-Cat $script:Image "/system/etc/z9x/gapps_allow.txt" $tmp) -and (@(Get-Content -LiteralPath $tmp) | Where-Object { $_ -like "zip=$($script:GzSha) *" })
  Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
  if (-not $ok2) { Die (M not_accept_not_gapps_allow "$leaf" "$($script:GzName)") }
  Ok (M image_takes_google_services_add)
}
function Gi-Build {  # the add-on image in the state folder, built and checked; sets GiImg, GiSha, GiSize
  $nw = M nothing_written
  $ref = $null; $imf = $script:GappsImagesF; $imr = $script:GappsImagesRel
  if (Test-Path -LiteralPath $imf) { $ref = Get-Content -LiteralPath $imf | Where-Object { $w = @("$_".Trim() -split "\s+"); $w.Count -eq 5 -and $w[0] -eq $script:GzSha -and $w[1] -eq $EuVer -and $w[2] -eq "plain" } | Select-Object -First 1 }
  if (-not $ref) { Die (M names_no_checked_add_image_2 "$imr" "$($script:GzName)" "$EuVer" "$nw") }
  $w = @("$ref".Trim() -split "\s+"); $script:GiSha = $w[3]; $script:GiSize = [int64]$w[4]
  $mb = [math]::Ceiling($script:GiSize / 1MB)
  $wd = Join-Path $StateDir "gapps"; $img = Join-Path $wd "z9x_gapps-$($script:GzSha.Substring(0, 16)).img"
  $script:GiImg = $img
  if ((Test-Path -LiteralPath $img) -and (Get-Item -LiteralPath $img).Length -eq $script:GiSize -and (Sha256 $img) -eq $script:GiSha) {
    Ok ((M google_services_add_image_built) + " ($mb $(M mb))"); return
  }
  Remove-Item -Recurse -Force -LiteralPath $wd -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $wd | Out-Null
  $free = (New-Object IO.DriveInfo ([IO.Path]::GetPathRoot((Resolve-Path -LiteralPath $wd).Path))).AvailableFreeSpace
  $needB = $script:GiSize * 2 + 64MB
  if ($free -lt $needB) { Die (M not_enough_free_space_computer_2 "$([math]::Ceiling($needB / 1MB))" "$wd" "$nw") }
  Say (M building_google_services_add_image "$($script:GzName)")
  $tar = Join-Path $wd "gapps.tar"
  $why = Gi-Tar $script:GzZip $tar
  if ($why) { Remove-Item -LiteralPath $tar -ErrorAction SilentlyContinue; Die (M zip_not_hold_expected_files_2 "$why" "$nw") }
  $part = "$img.part"
  $rc = Eu-Run (Join-Path $script:EuDir "mkfs.erofs.exe") @("-b4096", "-E", "noinline_data", "-T$GiMtime", "-U", "00000000-0000-0000-0000-000000000000", "--all-root", "--workers=1", "-Lz9x_gapps", "--tar=f", $part, $tar) $null
  Remove-Item -LiteralPath $tar -ErrorAction SilentlyContinue
  if ($rc -ne 0) { Remove-Item -LiteralPath $part -ErrorAction SilentlyContinue; Die ((M mkfs_erofs_failed) + ": $($script:EuErr.Trim()). $nw") }
  if ((Eu-Run (Join-Path $script:EuDir "fsck.erofs.exe") @($part) $null) -ne 0) { Remove-Item -LiteralPath $part -ErrorAction SilentlyContinue; Die "fsck.erofs: $($script:EuErr.Trim()). $nw" }
  # every file read back out of the image
  $tmp = Join-Path $wd "readback"
  $lines = Gi-Lines $script:GzSha
  foreach ($ln in ($lines + @("MANIFEST"))) {
    if ($ln -eq "MANIFEST") { $p = "MANIFEST"; $want = Sha256 (Join-Path $wd "MANIFEST") } else { $x = $ln -split " "; $p = $x[2]; $want = $x[0] }
    if (-not (Gi-Cat $part "/$p" $tmp) -or (Sha256 $tmp) -ne $want) {
      Remove-Item -LiteralPath $part, $tmp -ErrorAction SilentlyContinue
      Die (M read_back_add_image_differs_2 "$p" "$nw")
    }
  }
  Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
  $got = Sha256 $part
  if ($got -ne $script:GiSha -or (Get-Item -LiteralPath $part).Length -ne $script:GiSize) {
    Remove-Item -LiteralPath $part -ErrorAction SilentlyContinue
    Die (M add_image_built_here_sha256_2 "$($got.Substring(0, 16))" "$($script:GiSha.Substring(0, 16))" "$nw")
  }
  Move-Item -LiteralPath $part -Destination $img -Force
  Ok ((M google_services_add_image) + ": $($lines.Count) $(M files_checked), $mb $(M mb), sha256 $($script:GiSha.Substring(0, 16))...")
}

function Z9x-Gapps-Plan {  # -GappsZip, before anything else: the zip, and that the install saved here can take it
  if (-not (Test-Path -LiteralPath $GappsZip)) { Die (M no_file "$GappsZip") }
  $script:GzZip = (Resolve-Path -LiteralPath $GappsZip).Path
  $zname = Split-Path -Leaf $script:GzZip
  Say (M checking "$zname")
  $script:GzSha = Sha256 $script:GzZip
  $zl = Gapps-Line $script:GzSha
  if (-not $zl) { Die ((M not_mindthegapps_file_installer "$zname" "$($script:GzSha.Substring(0,16))") + ". " + (M nothing_written)) }
  $script:GzName = ("$zl" -split " ")[1]; $script:GzPart = [int64](Gapps-Field $zl "part")
  Ok "$($script:GzName), sha256 $($script:GzSha.Substring(0,16))..."
  $plan = State-Get gapps_plan
  if ((State-Get step_flash) -eq "done") {
    # an older installer's install waiting for its fill (Z9x-Gapps-Finish completes it), or this one-pass install done
    $legacy = ((State-Get step_gapps_part) -eq "done" -and -not (State-Get step_wipe))
    if (-not $legacy -and (State-Get step_gapps_flash) -ne "done" -and (State-Get step_gapps_fill) -ne "done") {
      Die (M lumen_os_installed_here_without)
    }
    if ($plan -and $plan -ne $script:GzSha) { Die (M install_started_another) }
  }
}
function Z9x-Gapps-Prepare {  # before the ERASE question: erofs-utils, the image takes the add-on, the add-on image built
  if ($script:GiReady) { return }
  if (-not $script:Image -or -not (Test-Path -LiteralPath $script:Image)) { Find-Image }
  if ((State-Get image_gms) -ne "0") { Die (M image_has_google_services_built) }
  Eu-Setup
  Gi-Image-Ok
  Gi-Build
  $script:GiReady = $true
}
function Gapps-Zipcheck($f) {  # the zip on this computer against the allow-list (lumen-install.sh gapps_zipcheck); sets GzZip, GzSha, GzName, GzPart
  if (-not (Test-Path -LiteralPath $f)) { Die (M no_file "$f") }
  $script:GzZip = (Resolve-Path -LiteralPath $f).Path
  $zname = Split-Path -Leaf $script:GzZip
  Say (M checking "$zname")
  $script:GzSha = Sha256 $script:GzZip
  $zl = Gapps-Line $script:GzSha
  if (-not $zl) { Die ((M not_mindthegapps_file_installer "$zname" "$($script:GzSha.Substring(0,16))") + ". " + (M nothing_written)) }
  $script:GzName = ("$zl" -split " ")[1]; $script:GzPart = [int64](Gapps-Field $zl "part")
  Ok "$($script:GzName), sha256 $($script:GzSha.Substring(0,16))..."
}
function Z9x-In-Fastboot {
  if (-not $script:Serial) { return $false }
  return (@(Pt-Fastboot devices 2>$null | ForEach-Object { ("$_".Trim() -split "\s+")[0] }) -ccontains $script:Serial)
}
function Z9x-Gapps-Write($S) {  # fastbootd after system (+ z9x_blobs): z9x_gapps_<slot> sized to the image and flashed;
  # $false = left out (warned; the partition is deleted, so the system starts plainly without Google)
  $p = "z9x_gapps$S"
  $need = [int64]([math]::Ceiling($script:GiSize / 1MB) * 1MB)
  $why = M install_goes_without_google
  $gv = Fb getvar all   # system and z9x_blobs have their new sizes now
  $free = Super-Free $gv; $cur = [int64]0
  $have = Gv-Size $gv $p; if ($null -ne $have) { $cur = $have }
  if (($free + $cur) -lt $need) {
    if ($cur -gt 0) { $null = Fb-Do delete $p }
    Warn ((M not_enough_free_space_super_4 "$([math]::Floor(($free + $cur) / 1MB))" "$([math]::Floor($need / 1MB))") + $why)
    State-Set step_gapps_flash "skipped:room"; return $false
  }
  Say (M writing_google_services_mb_about "$p" "$([math]::Floor($need / 1MB))")
  $made = if ($cur -gt 0) { Fb-Do resize $p $need } else { Fb-Do create $p $need }
  if (-not $made) { $null = Fb-Do delete $p; Warn ((M could_not_created_3 "$p") + $why); State-Set step_gapps_flash "skipped:create"; return $false }
  if (-not (Fb-Do flash $p $script:GiImg)) {
    # fastbootd gone (its time limit, the cable): nothing is erased, the projector starts on the old data with USB
    # debugging, and the same command writes everything again in one visit
    if (-not (Z9x-In-Fastboot)) { Die (M fastbootd_ended_while_being "$p") }
    $null = Fb-Do delete $p
    Warn ((M could_not_written_removed_again_3 "$p") + $why)
    State-Set step_gapps_flash "skipped:flash"; return $false
  }
  State-Set step_gapps_flash done; State-Set gapps $script:GzName; State-Set gapps_img $script:GiSha
  Ok "${p}: $($script:GzName)"
  return $true
}
function Z9x-Gapps-Finish {  # an install of an older installer that waits on the old data for its fill: the add-on flashed
  # as erofs in fastbootd (with -GappsZip), then the one data wipe there
  $slot = State-Get slot
  if (-not $GappsZip) {
    Say (M install_started_google_services)
    if (-not (Ask (M finish_install_without_google))) { Die (M run_same_command_again_gapps) }
    State-Set gapps_plan ""
  } else { Check-Image -Force; Z9x-Gapps-Prepare }
  if (-not $script:EraseNow) {
    Write-Host ""
    Write-Host (M lumen_os_written_next_installer)
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_erased) }
    $script:EraseNow = $true
  }
  $gv = if (Z9x-In-Fastboot) { Fbd-Check $slot } else { Fbd-Enter $slot }
  if ($GappsZip) { if (-not (Z9x-Gapps-Write $slot)) { State-Set gapps_plan "" } }
  Fb-Wipe $gv
  Fb reboot | Out-Null
  State-Set step_gapps_part ""; State-Set step_gapps_fill ""
  if ((State-Get step_gapps_flash) -eq "done") { Ok (M data_wiped_first_start_takes) }
  else { Ok (M data_wiped_first_start_takes_2) }
}

# ------------------------------------------------------------------ projector profiles (lib\devices.conf)
# What a projector IS comes from lib\devices.conf: data only, checked line by line here and never run. What the
# installer DOES (its steps, and Fb-Allowed) stays in this script, so that file cannot widen what is written. The
# Z9X runs on the constants at the top and does not need lib\devices.conf (it only names other projectors from it).
$DevConf = Join-Path $Here "lib\devices.conf"
$PKeys = @("NAME", "MODEL", "BOARD", "FB_PRODUCT", "VENDOR_OK", "CERT", "IMAGE_TOKEN", "IMAGE_FS", "VERSION_PROP", "LUMEN_PROP",
           "LOGICAL", "SUPER_RESERVE", "GUIDE", "RESTORE_BIN", "RESTORE_MD5", "RESTORE_URL", "FLOW", "SLOTS", "KEEP", "WIPE_PARTS",
           "SYSTEM_MAX", "BLOBS_ALLOW", "GAPPS_ALLOW", "GAPPS_IMAGES", "BLOBS_SIZE", "FAMILY", "SLOT_CHECK",
           "WIPE_FS", "MODELS_OK", "PRODUCT_NAMES", "DEVICE", "MODEL_CONFIG", "MODELS_NOT_YET", "MODELS_CHECK_ONLY", "ABI")
$TodoKeys = @("VENDOR_OK", "LOGICAL", "KEEP", "SYSTEM_MAX", "BLOBS_ALLOW", "BLOBS_SIZE", "RESTORE_BIN")   # the only keys that may say TODO
$script:Todo = ""   # the keys of the profile whose value is TODO: not finished, nothing is written (Abf-Todo-Stop)
$script:AbfModel = ""   # the model code Abf-Identity found (one of MODELS_OK, else MODEL)
$script:Prof = @{}
function Devconf-Lines { if (Test-Path -LiteralPath $DevConf) { @(Get-Content -LiteralPath $DevConf -Encoding UTF8) } else { @() } }
function Devconf-Check {  # stops on a missing file, a broken line, an unknown key or a key given twice
  if (-not (Test-Path -LiteralPath $DevConf)) { Die "internal: lib\devices.conf is missing: re-download the installer" }
  $seen = @{}; $n = 0
  foreach ($l in (Devconf-Lines)) {
    $n++
    if ($l -eq "" -or $l.StartsWith("#")) { continue }
    $good = $false
    if ($l -cmatch '^([a-z0-9]+)\.([A-Z][A-Z0-9_]*)=[A-Za-z0-9 ._:/,-]*$') {
      $k = "$($Matches[1]).$($Matches[2])"
      if (($PKeys -ccontains $Matches[2]) -and -not $seen.ContainsKey($k)) { $seen[$k] = $true; $good = $true }
    }
    if (-not $good) { Die "internal: lib\devices.conf line $n is not valid (unknown key, a key given twice, or characters that are not allowed): re-download the installer" }
  }
}
function Devconf-Get($id, $key) {  # id KEY -> value ("" without lib\devices.conf)
  $pre = "$id.$key="
  foreach ($l in (Devconf-Lines)) { if ($l.StartsWith($pre, [StringComparison]::Ordinal)) { return $l.Substring($pre.Length) } }
  return ""
}
function Is-Todo($v) { return "$v".StartsWith("TODO", [StringComparison]::Ordinal) }   # a profile value that is not known yet (TODO, TODO:<why>)
function Devconf-Todo($id) {  # id -> the keys of that profile whose value is TODO, space-separated ("": finished)
  $pre = "$id."; $out = @()
  foreach ($l in (Devconf-Lines)) {
    if (-not $l.StartsWith($pre, [StringComparison]::Ordinal)) { continue }
    $k = $l.Substring($pre.Length); $e = $k.IndexOf("=")
    if ($e -gt 0 -and $k.Substring($e + 1).StartsWith("TODO", [StringComparison]::Ordinal)) { $out += $k.Substring(0, $e) }
  }
  return ($out -join " ")
}
function Dev-Name($id) {  # id -> the name shown to the user
  $n = Devconf-Get $id "NAME"
  if ($n) { return $n }
  return $id
}
function Profile-Ids { return @(Devconf-Lines | ForEach-Object { if ("$_" -cmatch '^([a-z0-9]+)\.NAME=') { $Matches[1] } }) }   # in file order
function Profile-Flow($id) { if (-not $id) { return "" }; return (Devconf-Get "$id".ToLower() "FLOW") }   # id -> its FLOW
function Flow-Id($flow) {  # flow -> the first projector of lib\devices.conf with that FLOW
  foreach ($id in @(Profile-Ids)) { if ((Devconf-Get $id "FLOW") -ceq $flow) { return $id } }
  return ""
}
function State-Dir-Of($id) {  # id -> the folder of its saved progress (lib\flows.conf STATE)
  if ((Flow-Get (Profile-Flow $id) "STATE") -ceq "root") { return $StateDir }
  return (Join-Path $StateDir "$id".ToLower())
}
function Not-Yet($c) { $nm = P6-Name; Die (M not_available_yet_nothing_done "$c" "$nm") }   # a command or option this projector has not yet
$FlowConf = Join-Path (Join-Path $Here "lib") "flows.conf"
function Flowconf-Lines { if (Test-Path -LiteralPath $FlowConf) { @(Get-Content -LiteralPath $FlowConf -Encoding UTF8) } else { @() } }
function Flowconf-Check {  # lib\flows.conf: every line flow.KEY=value with a known key, no key twice, a known step name
  if (-not (Test-Path -LiteralPath $FlowConf)) { Die "internal: lib\flows.conf is missing: re-download the installer" }
  $seen = @{}; $n = 0
  foreach ($l in (Flowconf-Lines)) {
    $n++
    if ($l -eq "" -or $l.StartsWith("#")) { continue }
    $good = $l -cmatch '^([a-z0-9-]+\.(KEYS|STATE|START|TOOLS|NOT_NEEDED|NOT_YET|NEEDS|CMD_[A-Z]+))=[A-Za-z0-9 ._:/,-]*$'
    if ($good) { $k = $Matches[1]; $good = -not $seen.ContainsKey($k); $seen[$k] = $true }
    if ($good -and $l -cmatch '\.(START|CMD_[A-Z]+)=') { $good = $l -cmatch '=(cmd|stick|play6|abf)_[a-z0-9_]+( [0-9]+)?$' }
    if (-not $good) { Die "internal: lib\flows.conf line $n is not valid: re-download the installer" }
  }
}
function Flow-Get($flow, $key) {  # flow KEY -> value ("" without lib\flows.conf)
  if (-not $flow) { return "" }
  $pre = "$flow.$key="
  foreach ($l in (Flowconf-Lines)) { if ($l.StartsWith($pre, [StringComparison]::Ordinal)) { return $l.Substring($pre.Length) } }
  return ""
}
function Profile-Of($model, $board) {  # model board -> the id of the profile that has both ("": unknown); a profile's
  # model codes are its MODEL, the models of its MODELS_OK and its MODELS_NOT_YET
  $mo = @{}; $bo = @{}; $ex = @{}; $order = @()
  foreach ($l in (Devconf-Lines)) {
    if ($l -cmatch '^([a-z0-9]+)\.MODEL=(.*)$') { $mo[$Matches[1]] = $Matches[2]; $order += $Matches[1] }
    elseif ($l -cmatch '^([a-z0-9]+)\.BOARD=(.*)$') { $bo[$Matches[1]] = $Matches[2] }
    elseif ($l -cmatch '^([a-z0-9]+)\.(MODELS_OK|MODELS_NOT_YET)=(.*)$') {
      $i = $Matches[1]; if (-not $ex.ContainsKey($i)) { $ex[$i] = @() }
      foreach ($e in @($Matches[3] -split ' ' | Where-Object { $_ })) { $ex[$i] += ($e -split ':', 2)[0] }
    }
  }
  foreach ($i in $order) { if (($mo[$i] -ceq $model -or ($ex.ContainsKey($i) -and $ex[$i] -ccontains $model)) -and $bo[$i] -ceq $board) { return $i } }
  return ""
}
function Join-And($items) {  # "a, b and c" (M and)
  $items = @($items)
  if ($items.Count -le 1) { return ($items -join "") }
  return (($items[0..($items.Count - 2)] -join ", ") + " " + (M and) + " " + $items[-1])
}
function Profiles-Named {  # "XGIMI Z9X (model) and XGIMI Play 6 (...)": every projector of lib\devices.conf whose flow is
  # not ab-fastbootd (a FLOW=ab-fastbootd one is named by its own path, Abf-Other-Hint)
  $nm = @{}; $mo = @{}; $fl = @{}; $order = @()
  foreach ($l in (Devconf-Lines)) {
    if ($l -cmatch '^([a-z0-9]+)\.NAME=(.*)$') { $nm[$Matches[1]] = $Matches[2]; $order += $Matches[1] }
    elseif ($l -cmatch '^([a-z0-9]+)\.MODEL=(.*)$') { $mo[$Matches[1]] = $Matches[2] }
    elseif ($l -cmatch '^([a-z0-9]+)\.FLOW=ab-fastbootd$') { $fl[$Matches[1]] = $true }
  }
  return (Join-And @($order | Where-Object { -not $fl.ContainsKey($_) } | ForEach-Object { "$($nm[$_]) ($($mo[$_]))" }))
}
function Abf-Table-Check($id) {  # MODELS_OK and its companion keys are one consistent table, else an internal STOP
  # MODELS_OK: 'G0001:v1.2.3,v1.3.0 G0002:v1.3.0', every model once, its first model is MODEL, every
  # firmware in VENDOR_OK and every firmware of VENDOR_OK some model's; PRODUCT_NAMES: 'G0001:Product_x ...' for
  # exactly those models; DEVICE: one word; MODEL_CONFIG: a folder under /mnt/vendor/; MODELS_NOT_YET: model codes
  # not in MODELS_OK; MODELS_CHECK_ONLY: models of MODELS_OK, each once
  $bad = "internal: lib\devices.conf: $id.MODELS_OK"
  $P = $script:Prof
  if (-not $P["MODELS_OK"] -or -not $P["PRODUCT_NAMES"] -or -not $P["DEVICE"] -or -not $P["MODEL_CONFIG"]) { Die $bad }
  $vok = @("$($P['VENDOR_OK'])" -split ' ' | Where-Object { $_ })
  $mods = @(); $fws = @(); $first = ""
  foreach ($e in @("$($P['MODELS_OK'])" -split ' ' | Where-Object { $_ })) {
    $m = ($e -split ':', 2)[0]; $l = if ($e.Contains(":")) { ($e -split ':', 2)[1] } else { $e }
    if ($m -cnotmatch '^G[0-9]{4}$') { Die $bad }
    if (-not $l -or $l.Contains(":") -or $l.StartsWith(",") -or $l.EndsWith(",") -or $l.Contains(",,")) { Die $bad }
    if ($mods -ccontains $m) { Die $bad }
    if (-not $first) { $first = $m }
    $mods += $m
    foreach ($v in @($l -split ',')) {
      if ($v -cnotmatch '^v[0-9][v0-9.]*$') { Die $bad }
      if ($vok -cnotcontains $v) { Die "$bad (${m}:$v is not in VENDOR_OK)" }
      $fws += $v
    }
  }
  if ($first -cne $P["MODEL"]) { Die "$bad (its first model is not MODEL)" }
  foreach ($v in $vok) { if ($fws -cnotcontains $v) { Die "$bad (VENDOR_OK $v is no model's)" } }
  $n = 0
  foreach ($e in @("$($P['PRODUCT_NAMES'])" -split ' ' | Where-Object { $_ })) {
    $m = ($e -split ':', 2)[0]; $v = if ($e.Contains(":")) { ($e -split ':', 2)[1] } else { "" }
    if ("${m}:$v" -cne $e -or -not $v) { Die "internal: lib\devices.conf: $id.PRODUCT_NAMES" }
    if ($mods -cnotcontains $m) { Die "internal: lib\devices.conf: $id.PRODUCT_NAMES" }
    $n++
  }
  if ($n -ne $mods.Count) { Die "internal: lib\devices.conf: $id.PRODUCT_NAMES" }
  if ("$($P['DEVICE'])".Contains(" ")) { Die "internal: lib\devices.conf: $id.DEVICE" }
  $mc = "$($P['MODEL_CONFIG'])"
  if ($mc -cnotmatch '^/mnt/vendor/.+' -or $mc.Contains("..") -or $mc.Contains(" ") -or $mc.EndsWith("/")) { Die "internal: lib\devices.conf: $id.MODEL_CONFIG" }
  foreach ($m in @("$($P['MODELS_NOT_YET'])" -split ' ' | Where-Object { $_ })) {
    if ($m -cnotmatch '^G[0-9]{4}$' -or $mods -ccontains $m) { Die "internal: lib\devices.conf: $id.MODELS_NOT_YET" }
  }
  $co = @()
  foreach ($m in @("$($P['MODELS_CHECK_ONLY'])" -split ' ' | Where-Object { $_ })) {
    if ($mods -cnotcontains $m -or $co -ccontains $m) { Die "internal: lib\devices.conf: $id.MODELS_CHECK_ONLY" }
    $co += $m
  }
}
function Pair-Get($list, $key) {  # "K:V K2:V2 ..." K -> V ($null: no such K)
  foreach ($e in @("$list" -split ' ' | Where-Object { $_ })) { if (($e -split ':', 2)[0] -ceq $key) { return ($e -split ':', 2)[1] } }
  return $null
}
function Abf-Is-Model($model) {  # a model code of this profile (one of MODELS_OK, else MODEL)
  if (-not $model) { return $false }
  if (-not $script:Prof["MODELS_OK"]) { return ($model -ceq $script:Prof["MODEL"]) }
  return ($null -ne (Pair-Get $script:Prof["MODELS_OK"] $model))
}
function Abf-Model-Fw($model) {  # model -> the firmware it may run (its MODELS_OK entry, else VENDOR_OK)
  if ($script:Prof["MODELS_OK"]) { return @("$(Pair-Get $script:Prof['MODELS_OK'] $model)" -split ',' | Where-Object { $_ }) }
  return @(Abf-List VENDOR_OK)
}
function Abf-Check-Only {  # $true = the model Abf-Identity found is one of MODELS_CHECK_ONLY: 'check' and 'blobs', no write yet
  return [bool]($script:AbfModel -and (@("$($script:Prof['MODELS_CHECK_ONLY'])" -split ' ') -ccontains $script:AbfModel))
}
function Abf-Live-Gate {  # every write ('flash', 'all', 'gapps -Zip', 'gapps -Remove') once Abf-Device ran: a model of
  # MODELS_CHECK_ONLY is refused before anything is written. It mirrors the live switch of the installers that run on
  # the projector itself (a check and a dry run only, until one live test on that model)
  if (-not (Abf-Check-Only)) { return }
  $am = $script:AbfModel; $nm = P6-Name
  Die (M support_testing_lumen_os_goes "$am" "$nm")
}
function Abf-Fw-Names($fws) {  # @("v1.2.3", "v1.3.0") -> "V1.2.3 and V1.3.0"
  return (Join-And @(@($fws) | ForEach-Object { "V" + "$_".Substring(1) }))
}
function Abf-Matrix {  # "G0001 (V1.2.3, V1.3.0) and G0002 (V1.3.0)": the models and the firmware of each
  return (Join-And @(@("$($script:Prof['MODELS_OK'])" -split ' ' | Where-Object { $_ }) | ForEach-Object {
    $m = ($_ -split ':', 2)[0]; $f = @(($_ -split ':', 2)[1] -split ',' | ForEach-Object { "V" + "$_".Substring(1) }) -join ", "
    "$m ($f)" }))
}
function Load-Profile($id) {  # id: $script:Prof (NAME, MODEL, ...) of that projector
  Devconf-Check
  if (-not (Devconf-Get $id "NAME")) { Die "internal: lib\devices.conf has no projector '$id': re-download the installer" }
  $script:Prof = @{}
  foreach ($k in $PKeys) { $script:Prof[$k] = Devconf-Get $id $k }
  # FAMILY: the fastbootd 'product' of the projector whose XGIMI product image this one shares (never its own
  # FB_PRODUCT as well; that projector names its own and has no FAMILY): MODEL and BOARD tell the two apart
  if ($script:Prof["FAMILY"]) {
    $fam = $script:Prof["FAMILY"]
    if ($script:Prof["FB_PRODUCT"] -or $fam -ceq $id -or (Devconf-Get $fam "FAMILY") -or (Devconf-Get $fam "MODEL") -ceq $script:Prof["MODEL"]) { Die "internal: lib\devices.conf: $id.FAMILY" }
    $script:Prof["FB_PRODUCT"] = Devconf-Get $fam "FB_PRODUCT"
    if (-not $script:Prof["FB_PRODUCT"]) { Die "internal: lib\devices.conf: $id.FAMILY" }
  }
  # FLOW: one of lib\flows.conf, whose KEYS this profile must have
  Flowconf-Check
  $keys = @("$(Flow-Get $script:Prof['FLOW'] 'KEYS')" -split ' ' | Where-Object { $_ })
  if (-not $keys) { Die "internal: lib\devices.conf: $id.FLOW=$($script:Prof['FLOW'])" }
  foreach ($k in $keys) {
    if (-not $script:Prof[$k]) { Die "internal: lib\devices.conf: '$id' has no ${k}: re-download the installer" }
  }
  $abf = $script:Prof["FLOW"] -ceq "ab-fastbootd"
  # TODO (lib\devices.conf header): only with FLOW=ab-fastbootd and only for the facts a port may still lack
  # ($TodoKeys). Such a profile is not finished: their checks below are skipped, Abf-Main and Abf-Check stop it
  $script:Todo = Devconf-Todo $id
  foreach ($k in @("$($script:Todo)" -split ' ' | Where-Object { $_ })) {
    if ($TodoKeys -cnotcontains $k -or -not $abf) { Die "internal: lib\devices.conf: $id.$k=TODO" }
  }
  # ABI (optional, with FLOW=ab-fastbootd): the userspace of its stock vendor, checked in the image (Abf-Check-Image)
  if ($script:Prof["ABI"] -and (-not $abf -or @("arm32", "arm64") -cnotcontains $script:Prof["ABI"])) { Die "internal: lib\devices.conf: $id.ABI" }
  if ($script:Prof["SUPER_RESERVE"] -and $script:Prof["SUPER_RESERVE"] -notmatch '^[0-9]+$') { Die "internal: lib\devices.conf: $id.SUPER_RESERVE" }
  if ($script:Prof["LUMEN_PROP"] -and $script:Prof["LUMEN_PROP"] -notmatch ' ') { Die "internal: lib\devices.conf: $id.LUMEN_PROP" }
  # SLOT_CHECK (optional): only 'yes', only with FLOW=ab-fastbootd, and only with RESTORE_BIN (its texts name the way back)
  if ($script:Prof["SLOT_CHECK"] -and ($script:Prof["SLOT_CHECK"] -cne "yes" -or -not $abf -or -not $script:Prof["RESTORE_BIN"] -or (Is-Todo $script:Prof["RESTORE_BIN"]))) { Die "internal: lib\devices.conf: $id.SLOT_CHECK" }
  # WIPE_FS (optional, only with FLOW=ab-fastbootd): ext4 or f2fs, as the projector's stock fstab mounts userdata and metadata
  if ($script:Prof["WIPE_FS"] -and (-not $abf -or @("ext4", "f2fs") -cnotcontains $script:Prof["WIPE_FS"])) { Die "internal: lib\devices.conf: $id.WIPE_FS" }
  # the model x firmware table (optional, only with FLOW=ab-fastbootd)
  if ("$($script:Prof['MODELS_OK'])$($script:Prof['PRODUCT_NAMES'])$($script:Prof['DEVICE'])$($script:Prof['MODEL_CONFIG'])$($script:Prof['MODELS_NOT_YET'])") {
    if (-not $abf) { Die "internal: lib\devices.conf: $id.MODELS_OK" }
    Abf-Table-Check $id
  }
  if (-not $abf) { return }
  if (-not $script:Prof["WIPE_FS"]) { $script:Prof["WIPE_FS"] = "ext4" }
  # FLOW=ab-fastbootd: every one of its keys; what they may name is narrowed here (Fb-Allowed narrows it again)
  foreach ($k in @("SLOTS", "KEEP", "WIPE_PARTS", "SYSTEM_MAX", "BLOBS_ALLOW")) {
    if (-not $script:Prof[$k]) { Die "internal: lib\devices.conf: '$id' has no ${k}: re-download the installer" }
  }
  if (-not (Is-Todo $script:Prof["SYSTEM_MAX"]) -and $script:Prof["SYSTEM_MAX"] -notmatch '^[0-9]+$') { Die "internal: lib\devices.conf: $id.SYSTEM_MAX" }
  foreach ($k in (Abf-List SLOTS)) { if (@("a", "b") -cnotcontains $k) { Die "internal: lib\devices.conf: $id.SLOTS" } }
  foreach ($k in (Abf-List WIPE_PARTS)) { if (@("userdata", "metadata") -cnotcontains $k) { Die "internal: lib\devices.conf: $id.WIPE_PARTS" } }
  if (-not (Is-Todo $script:Prof["BLOBS_ALLOW"])) {
    if ($script:Prof["BLOBS_ALLOW"] -cnotmatch '^lib/.*\.txt$') { Die "internal: lib\devices.conf: $id.BLOBS_ALLOW" }
    if ($script:Prof["BLOBS_ALLOW"].Contains("..")) { Die "internal: lib\devices.conf: $id.BLOBS_ALLOW" }
  }
  # its z9x_blobs partition (optional): bytes, whole MiB from 4 to 64; without it the 4 MiB of $BlobSize
  if ($script:Prof["BLOBS_SIZE"] -and -not (Is-Todo $script:Prof["BLOBS_SIZE"])) {
    $bs = "$($script:Prof['BLOBS_SIZE'])"
    if ($bs -notmatch '^[0-9]+$' -or ([int64]$bs % 1048576) -ne 0 -or [int64]$bs -lt 4194304 -or [int64]$bs -gt 67108864) { Die "internal: lib\devices.conf: $id.BLOBS_SIZE" }
    $script:BlobSize = [int64]$bs
  }
  if ($script:Prof["IMAGE_FS"] -cne "erofs") { Die "internal: lib\devices.conf: $id.IMAGE_FS (ab-fastbootd: erofs)" }
  # the Google services add-on: both lists or neither (then gapps, gsf and -GappsZip are not there for it)
  if ($script:Prof["GAPPS_ALLOW"] -or $script:Prof["GAPPS_IMAGES"]) {
    foreach ($k in @("GAPPS_ALLOW", "GAPPS_IMAGES")) {
      if ("$($script:Prof[$k])" -cnotmatch '^lib/.*\.txt$' -or "$($script:Prof[$k])".Contains("..")) { Die "internal: lib\devices.conf: $id.$k" }
    }
    $script:GappsAllowF = Abf-Path $script:Prof["GAPPS_ALLOW"]; $script:GappsAllowRel = $script:Prof["GAPPS_ALLOW"] -replace '/', '\'
    $script:GappsImagesF = Abf-Path $script:Prof["GAPPS_IMAGES"]; $script:GappsImagesRel = $script:Prof["GAPPS_IMAGES"] -replace '/', '\'
  }
}
function Other-Hint($model, $board) {  # the Z9X check met another projector this installer knows: its way in
  $id = Profile-Of $model $board
  $fl = Profile-Flow $id
  if ($fl -ceq "play6-wifi") { return " " + (M installs_over_wi_fi_run "$(Dev-Name $id)") }
  if ($fl -ceq "ab-fastbootd") { return (Abf-Other-Hint $model $board) }
  return ""
}
function P6-Unfinished {  # a Play 6 install is saved that was checked and has not reached the wipe yet
  $f = Join-Path (State-Dir-Of (Flow-Id play6-wifi)) "state"
  if (-not (Test-Path -LiteralPath $f)) { return $false }
  $c = @(Get-Content -LiteralPath $f)
  return (($c -contains "step_check=done") -and -not ($c | Where-Object { $_ -like "step_wipe=?*" }))
}
function Z9x-Unfinished {
  $f = Join-Path (State-Dir-Of (Flow-Id z9x-usb)) "state"
  if (-not (Test-Path -LiteralPath $f)) { return $false }
  $c = @(Get-Content -LiteralPath $f)
  return (($c -contains "step_check=done") -and -not ($c -contains "step_flash=done"))
}
function Resolve-Device {  # the projector: -Device, else -Ip or 'stick' (Play 6), this download, an unfinished own build
  Devconf-Check
  if ($RelDevice -and @(Profile-Ids) -notcontains $RelDevice) { Die "internal: release.conf DEVICE=${RelDevice}: re-download the installer" }
  $usb = Flow-Id z9x-usb; $wifi = Flow-Id play6-wifi
  # A download names its projector (release.conf DEVICE, none = the Z9X), so a Z9X download behaves exactly as it
  # always did, also on a computer that keeps an unfinished Play 6 install. Only your own builds
  # (-UnsignedImage, not bound to a download) let an unfinished install name the projector.
  # A download for a FLOW=ab-fastbootd projector means it with -Ip and 'stick' too (never the Play 6).
  $script:Projector = $usb
  if ($Device) { $script:Projector = $Device }
  elseif ((Profile-Flow $RelDevice) -ceq "ab-fastbootd") { $script:Projector = $RelDevice }
  elseif ($Ip -or $Command -eq "stick") { $script:Projector = $wifi }
  elseif ($RelDevice) { $script:Projector = $RelDevice }
  elseif ($UnsignedImage -and (P6-Unfinished)) {
    if (Z9x-Unfinished) { Die (M two_unfinished_installs_saved "$(Dev-Name $wifi)") }
    $script:Projector = $wifi
  }
  $script:Projector = "$($script:Projector)".ToLower()
  if ((Profile-Flow $script:Projector) -ceq "z9x-usb" -and $Ip) { Die (M ip_xgimi_z9x_installs_over "$(Dev-Name $wifi)") }
  Load-Profile $script:Projector
  $script:SD = State-Dir-Of $script:Projector
  if ($script:Prof["IMAGE_TOKEN"]) { $script:ImgGlob = "lumen-os-$($script:Prof['IMAGE_TOKEN'])-*-system.img" }
}
function Release-Guard {  # the image of this download belongs to one projector (release.conf DEVICE; none = the Z9X)
  $rel = if ($RelDevice) { "$RelDevice".ToLower() } else { Flow-Id z9x-usb }
  if ($rel -eq $script:Projector) { return }
  if ($UnsignedImage) {
    Warn (M installer_folder_came_release_own "$(Dev-Name $rel)")
    return
  }
  Die (M download_installer_not_download "$(Dev-Name $rel)" "$(Dev-Name $script:Projector)")
}

# ------------------------------------------------------------------ XGIMI Play 6 (see lumen-install.sh, section 'XGIMI Play 6')
# adb runs over Wi-Fi only ($script:AdbT = ADDR:PORT): the projector's one USB-A port is a host port under Android.
# fastbootd is reached with 'adb reboot fastboot' sent over Wi-Fi and a USB A-to-A cable in that port; the projector
# does not start while that cable is in, so it goes in only for the write. Steps: check (with the pre-checks: Wi-Fi
# chip, serial, firmware, power, the USB port; logged in $StateDir\play6\install.log) -> backup (asked) -> write
# (fastbootd: system, then the erase request for XGIMI's recovery in misc, the same 2 KiB that Android's factory reset
# writes there) -> boot_fresh (the recovery erases userdata and cache BEFORE the first start, so that first start is a
# light one on clean data) -> verify (optional, later). The earlier way stays as the fallback, and for -KeepData and
# installs begun by an older installer: boot1 (first start on the old data) -> wipe (Android's own factory reset as
# root over adb). Progress: $StateDir\play6\state.
$P6App = "org.lumen.projector"                      # the projector app of the image (1.0.8 and later)
$P6AppOld = "org.z9x.projector"                    # its name up to 1.0.7 (status of an older install)
$P6Tuner = "com.zacharee1.systemuituner"          # installed on stock to turn debugging on; a wipe removes it
$P6Marker = "/data/local/tmp/lumen-install-wipe"  # written before the wipe; a real wipe removes it
# the erase request (struct bootloader_message, 2048 bytes): "boot-recovery" at 0, the arguments at 64, zeros elsewhere
# (lumen-install.sh, P6_BCB_ARGS: what Android's factory reset writes at offset 0 of misc, without --locale)
$P6BcbArgs = "recovery`n--wipe_data`n--reason=lumen-install`n"
$P6BcbSha = "aa5a031e5dcedc6346b920fd73cc9ccf623ec0a795f31c246f77fd6c26ef16fc"
$P6MinBattery = 50                                 # percent, before any write and before the erase
# no double quotes in these: PowerShell 5.1 does not pass them to adb.exe intact
$P6UsbCmd = 'for d in /sys/bus/usb/devices/*; do [ -f $d/idVendor ] || continue; echo ${d##*/} $(cat $d/idVendor):$(cat $d/idProduct) $(cat $d/*:1.0/bInterfaceClass 2>/dev/null | head -n 1); done'
$P6PwrCmd = "logcat -d -v brief 2>/dev/null | grep -E 'POWER_BATTERY_LEVEL|IsAdapterPowered|AdapterOut|AdapterInsert|P6Battery' | tail -n 50"
$P6MntCmd = "grep ' /mnt/media_rw/' /proc/mounts"
# the vendor's own AIC8800 driver (the last firmware of VENDOR_OK: its wlan_dongle_detect loads it when dongle.cfg maps the chip)
$P6AicCmd = 'grep -c ''^[^#]*:368b:8d90:'' /vendor/lib/firmware/dongle.cfg'
$script:P6BcbOk = $false    # set only while P6-Bcb-Write writes its checked erase request into misc
$script:P6PreDone = $false  # the pre-checks of this run are done
function P6-LProp { return ($script:Prof["LUMEN_PROP"] -split " ", 2)[0] }
function P6-LVal { return ($script:Prof["LUMEN_PROP"] -split " ", 2)[1] }
function P6-Name { return $script:Prof["NAME"] }
function P6-Guide { $l = if ($Lang -eq "ru") { "ru" } else { "en" }; return "$GuideBase/$($script:Prof['GUIDE'])/$l.md" }
$script:P6St = ""   # what 'adb get-state' said last in Wifi-Connect (unauthorized: the allow prompt was not accepted)
function P6-NoConn($secs) {  # why Wifi-Connect gave up, as the user can act on it
  $nm = P6-Name; $t = $script:AdbT
  if (-not $secs -or $secs -eq 60) { $w = (M within_minute) } else { $w = (M within_minutes "$([int]($secs / 60))") }
  if ($script:P6St -match "unauthorized") {
    return (M projector_did_not_allow_computer "$t" "$w")
  }
  return (M no_adb_connection_over_wi "$t" "$w" "$nm")
}
function P6-State { return ((@(Pt-Adb -s $script:AdbT get-state 2>&1 | ForEach-Object { "$_" }) -join "`n").Trim()) }
function P6-Alive {  # $true = the projector answers on a NEW connection (an old one may still say 'device' after a restart)
  Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
  Pt-Adb connect $script:AdbT 2>&1 | Out-Null
  return ((P6-State) -eq "device")
}
$P6NoMark = "lumen-install-no-check-file"
function P6-Marker {  # the wipe's check file: 'there' (no wipe since it was written), 'gone' (erased), '' (could not read)
  # Every attempt writes the file before it saves the token and before it asks for the reset, so a file that is
  # there, whatever token it holds, means that no wipe happened after the last request.
  $m = ((Adb shell "cat $P6Marker 2>/dev/null || echo $P6NoMark") | Out-String).Trim()
  if ($m -ceq $P6NoMark) { return "gone" }
  if ($m.StartsWith("lumen-")) { return "there" }
  return ""
}
function P6-Discover($ser) {  # ADDR:PORT of the one projector that 'adb mdns services' lists (with that serial), or ""
  $pre = if ($ser) { "adb-$ser-" } else { "" }
  $found = @(Pt-Adb mdns services 2>$null | ForEach-Object {
    $f = @("$_".Trim() -split "\s+")
    if ($f.Count -ge 3 -and $f[0] -like "adb-*" -and $f[1] -match '^_adb\._tcp\.?$' -and (-not $pre -or $f[0].StartsWith($pre))) { $f[-1] }
  } | Sort-Object -Unique)
  if ($found.Count -eq 1) { return $found[0] } else { return "" }
}
function Wifi-Connect($secs) {  # adb to $script:AdbT until it answers as 'device' ($true) or the time is up ($false)
  # The time counts both the pauses and the real clock (adb connect and get-state can take seconds each on a
  # network that drops packets), so "a minute" stays about a minute.
  $said = $false; $sw = [Diagnostics.Stopwatch]::StartNew()
  for ($i = 0; ; $i += 5) {
    Pt-Adb connect $script:AdbT 2>&1 | Out-Null
    $st = P6-State
    $script:P6St = $st
    if ($st -eq "device") { return $true }
    if ($st -match "unauthorized") {
      if (-not $said) { Say (M projector_allow_debugging_computer); $said = $true }
    } elseif ($st -match "offline") { Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null }
    if ($i -ge $secs -or $sw.Elapsed.TotalSeconds -ge $secs) { return $false }
    # the address may have changed (DHCP): the projector's mDNS name carries its serial (only that projector, also
    # with -Ip: P6-Identity checks the serial again)
    if ($script:Serial -and ($i % 30) -eq 25) {
      $a = P6-Discover $script:Serial
      if ($a -and $a -ne $script:AdbT) { Say (M projector_answers_new_address "$a"); $script:AdbT = $a }
    }
    Start-Sleep 5
  }
}
function P6-Identity {  # the device at AdbT: this projector model, and the saved one (DHCP may give its address away)
  $code = Prop ro.boot.xgimi.modelname; $board = Prop ro.boot.hardware; $ser = Prop ro.serialno; $t = $script:AdbT
  if ($code -cne $script:Prof["MODEL"] -or $board -cne $script:Prof["BOARD"]) {
    Pt-Adb disconnect $t 2>&1 | Out-Null
    if ((Profile-Flow (Profile-Of $code $board)) -ceq "z9x-usb") { Die (M device_xgimi_z9x_installs_over "$t") }
    Die (M projector_not_supported_model "$code" "$board" "$(Profiles-Named)")
  }
  if (-not $ser) { Die (M projector_reports_no_serial_number "$t") }
  if ($script:Serial -and $script:Serial -cne $ser) {
    Pt-Adb disconnect $t 2>&1 | Out-Null
    $saved = $script:Serial
    if ($script:SerialFromState) { Die (M projector_has_serial_but_install "$t" "$ser" "$saved") }
    Die (M projector_has_serial_not_serial "$t" "$ser" "$saved")
  }
  $script:Serial = $ser
}
function P6-Usb-Hint {  # no address for a Play 6: a Z9X on the USB cable means the wrong download or the wrong option
  $l = @(Pt-Adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] } | Where-Object { $_ -notmatch ":" })
  if ($l.Count -ne 1) { return }
  $code = ((Pt-Adb -s $l[0] shell getprop ro.boot.xgimi.modelname 2>$null) | Out-String).Trim()
  $board = ((Pt-Adb -s $l[0] shell getprop ro.boot.hardware 2>$null) | Out-String).Trim()
  if ((Profile-Flow (Profile-Of $code $board)) -ceq "z9x-usb") {
    $nm = P6-Name
    if ((Profile-Flow $RelDevice) -ceq "play6-wifi") { Die (M projector_usb_cable_xgimi_z9x "$nm") }
    Die (M projector_usb_cable_xgimi_z9x_2)
  }
}
function Play6-Adb($secs) {  # adb over Wi-Fi to the Play 6 (-Ip, the saved address, or mDNS) and who it is; $false = no answer
  if (-not $script:AdbT) {
    $a = State-Get ip
    if (-not $a) {
      $a = P6-Discover ""
      if (-not $a -or -not (Ask (M projector_answers_yours_one "$a"))) {
        P6-Usb-Hint
        $nm = P6-Name
        Die (M give_projectors_wi_fi_address "$nm")
      }
    }
    $script:AdbT = $a
  }
  Say (M connecting_over_wi_fi "$($script:AdbT)")
  if (-not (Wifi-Connect $secs)) { return $false }
  P6-Identity
  return $true
}
function P6-Fw-List { return (Join-And @($script:Prof["VENDOR_OK"] -split " " | ForEach-Object { "V" + $_.Substring(1) })) }
function P6-Fw-First { return ("V" + ($script:Prof["VENDOR_OK"] -split " ")[0].Substring(1)) }
function P6-Fw-Last { return ("V" + @($script:Prof["VENDOR_OK"] -split " ")[-1].Substring(1)) }   # the newest (VENDOR_OK is oldest first)
function P6-Restore-Md5($v) {  # VERSION: md5 of its .bin (RESTORE_MD5 md5:version)
  foreach ($e in @("$($script:Prof["RESTORE_MD5"])" -split " ")) { $kv = $e -split ":", 2; if ($kv.Count -eq 2 -and $kv[1] -ceq $v) { return $kv[0] } }
  return ""
}
function P6-Restore-Url($v) {  # VERSION: the official URL of its .bin (RESTORE_URL version:url)
  foreach ($e in @("$($script:Prof["RESTORE_URL"])" -split " ")) { if ($e.StartsWith("$($v):", [StringComparison]::Ordinal)) { return $e.Substring($v.Length + 1) } }
  return ""
}
function P6-Fw-Back($chip, $pre) {  # the stock .bin that brings this unit back (lumen-install.sh p6_fw_back)
  if ($pre -ceq "XHF" -or "$chip" -clike "368b:*") { return (P6-Fw-Last) }
  $v = State-Get vendor
  if ($v -and @($script:Prof["VENDOR_OK"] -split " ") -ccontains $v) { return ("V" + $v.Substring(1)) }
  return (P6-Fw-First)
}
function P6-Vendor-Aic($fw) {  # $true = the running vendor drives the AIC chip itself (lumen-install.sh p6_vendor_aic)
  $n = (@(((Adb shell $P6AicCmd) | Out-String).Trim() -split "`r?`n") | Select-Object -Last 1)
  $n = "$n".Trim()
  if ($n -notmatch '^[0-9]+$') { return (("V" + "$fw".TrimStart('v')) -cne (P6-Fw-First)) }
  return ([int]$n -gt 0)
}
function Play6-Device {  # connected: firmware, single slot, stock or Lumen OS; saves serial, address, firmware
  $nm = P6-Name
  $vend = Prop ro.vendor.build.version.incremental; $slot = Prop ro.boot.slot_suffix; $lum = Prop (P6-LProp)
  if (@($script:Prof["VENDOR_OK"] -split " ") -cnotcontains $vend) {
    $all = P6-Fw-List; $first = P6-Fw-First
    Die (M xgimi_firmware_not_supported_lumen "$vend" "$nm" "$all" "$first")
  }
  if ($slot) { Die (M projector_has_second_system_slot "$slot" "$nm") }
  if (-not $lum) {
    State-Set dev_lumen 0; State-Set dev_version ""; $ver = M xgimi_stock
    # a downloaded XGIMI update arms the next restart; the installer's own restart into fastbootd does not run it
    $up = Prop xgimi.ota.update.onboot
    if (-not $up) { $up = ((Adb shell settings get system xgimi_ota_update) | Out-String).Trim() }
    if ($up -eq "1" -or $up -eq "2") { Warn (M downloaded_xgimi_update_waits_next) }
  } elseif ($lum -ceq (P6-LVal)) { $v = Prop $script:Prof['VERSION_PROP']; State-Set dev_lumen 1; State-Set dev_version $v; $ver = "Lumen OS $v" }
  else { Die (M projector_runs_lumen_os_another "$lum") }
  State-Set serial $script:Serial; State-Set ip $script:AdbT; State-Set vendor $vend
  Ok "$nm $($script:Serial) ($($script:AdbT)), $(M firmware) $vend, $(M system): $ver"
}
# ------------------------------------------------------------------ XGIMI Play 6: pre-checks, log, erase request, help
function P6-LogPath { return (Join-Path $script:SD "install.log") }
function P6-Log($m) {
  try {
    New-Item -ItemType Directory -Force -Path $script:SD | Out-Null
    Add-Content -LiteralPath (P6-LogPath) -Value ("{0} {1}" -f (Get-Date -Format "yyyyMMdd-HHmmss"), $m) -Encoding UTF8
  } catch { }
}
function P6-Chip-Name($id) {  # vid:pid -> the Wi-Fi and Bluetooth chip it is, or ""
  if ($id -ceq "0e8d:7663") { return "MediaTek MT7663 ($id)" }
  if ($id -ceq "0e8d:7961") { return "MediaTek MT7961 ($id)" }
  if ($id -clike "0e8d:*") { return "MediaTek ($id)" }
  if ($id -ceq "368b:8d90" -or $id -ceq "368b:8d91") { return "AIC8800 ($id)" }
  return ""
}
function P6-Usb-Parse($text) {  # "<name> <vid>:<pid> <class>" lines -> Chip, ChipId, Stick, Other (lumen-install.sh p6_usb_parse)
  $r = @{ Chip = ""; ChipId = ""; Stick = $false; Other = "" }
  foreach ($l in @("$text" -split "`r?`n")) {
    $f = @($l.Trim() -split "\s+")
    if ($f.Count -lt 2 -or -not $f[1]) { continue }
    $id = $f[1]; $cls = if ($f.Count -ge 3) { $f[2] } else { "" }
    if ($id -clike "1d6b:*") { continue }   # root hubs
    $n = P6-Chip-Name $id
    if ($n) { $r.Chip = $n; $r.ChipId = $id; continue }
    if ($cls -ceq "08") { $r.Stick = $true } else { $r.Other = ("$($r.Other) $id").Trim() }   # 08: mass storage
  }
  return $r
}
function P6-Power-Parse($text) {  # XGIMI's power log -> Lvl (percent), Ad ("1" adapter, "0" battery), "" when it does not tell
  $r = @{ Lvl = ""; Ad = "" }
  foreach ($l in @("$text" -split "`r?`n")) {
    if ($l -cmatch 'POWER_BATTERY_LEVEL[^0-9]*([0-9]+)') { $r.Lvl = $Matches[1] }
    elseif ($l -cmatch 'P6Battery: read level=([0-9]+)') { $r.Lvl = $Matches[1] }
    if ($l -cmatch 'IsAdapterPowered:1|AdapterInsert|adapter=true') { $r.Ad = "1" }
    if ($l -cmatch 'IsAdapterPowered:0|AdapterOut|adapter=false') { $r.Ad = "0" }
  }
  return $r
}
function P6-Usb-Scan { $script:P6Usb = ((Adb shell $P6UsbCmd) | Out-String).Trim(); $script:P6U = P6-Usb-Parse $script:P6Usb }
function P6-Prechecks {  # before any write, on the running system over adb: which unit, its power and its USB port (logged)
  if ($script:P6PreDone) { return }
  $nm = P6-Name; $min = $P6MinBattery; $fw1 = P6-Fw-First
  $ser = "$($script:Serial)"; $pre = if ($ser.Length -ge 3) { $ser.Substring(0, 3) } else { $ser }
  $model = Prop ro.boot.xgimi.modelname; $fw = Prop ro.vendor.build.version.incremental; $disp = Prop ro.build.display.id
  P6-Usb-Scan; $u = $script:P6U
  $pw = P6-Power-Parse ((Adb shell $P6PwrCmd) | Out-String); $lvl = $pw.Lvl; $ad = $pw.Ad
  $fs = ""
  foreach ($l in @(((Adb shell $P6MntCmd) | Out-String) -split "`r?`n")) { $f = @($l.Trim() -split "\s+"); if ($f.Count -ge 3) { $fs = $f[2]; break } }
  State-Set chip $(if ($u.ChipId) { $u.ChipId } else { "unknown" }); State-Set serial_prefix $pre
  $dt = if ($disp) { " ($disp)" } else { "" }
  P6-Log ("pre-check: serial $ser ($pre), model $model, firmware $fw$dt, chip $(if ($u.Chip) { $u.Chip } else { 'unknown' }), battery $(if ($lvl) { $lvl } else { '?' }), adapter $(if ($ad) { $ad } else { '?' }), usb [" + (($script:P6Usb -split "`r?`n") -join ";") + "], stick $(if ($fs) { $fs } else { '-' })")
  Ok "$(M unit): $(M serial) $ser ($pre), $(M model) $model, $(M firmware) $fw$dt"
  if ($u.Chip) { Ok "$(M wi_fi_bluetooth_chip): $($u.Chip)" }
  else { Warn ((M wi_fi_bluetooth_chip_not) + " (" + ((@($script:P6Usb -split "`r?`n") | ForEach-Object { $f = @($_.Trim() -split "\s+"); if ($f.Count -ge 2) { $f[1] } }) -join " ") + ")") }
  # the AIC chip runs only on XGIMI's own AIC driver (the last firmware of VENDOR_OK): Lumen OS brings none (lumen-install.sh p6_prechecks)
  if ($u.ChipId -clike "368b:*" -and -not (P6-Vendor-Aic $fw)) {
    P6-Log "pre-check: refused, AIC chip $($u.ChipId) on firmware $fw without XGIMI's AIC driver"
    $fwl = P6-Fw-Last
    Die (M aic_chip_firmware_no_driver "$nm" "$($u.Chip)" "$fw" "$fwl" "$(P6-Restore-Url $fwl)" "$(P6-Restore-Md5 $fwl)")
  }
  # power: an erase or a write cut off by an empty battery leaves the projector without a system
  if ($lvl -and [int]$lvl -lt $min) {
    P6-Log "pre-check: refused, battery $lvl"
    Die (M battery_charge_least_power_adapter "$lvl" "$min")
  }
  if ($ad -eq "0") {
    P6-Log "pre-check: refused, on battery"
    Die (M projector_runs_battery_connect)
  }
  if ($lvl -and $ad -eq "1") { Ok (M battery_power_adapter_connected "$lvl") }
  else {
    Say ((M installer_could_not_read_battery) + $(if ($lvl) { " ($lvl %)" } else { "" }))
    if (-not (Ask (M power_adapter_connected_projector "$min"))) { Die (M connect_power_adapter_charge "$min") }
    P6-Log "pre-check: power confirmed by the user"
  }
  # the newer batch: the first-batch stock .bin kills its Wi-Fi and Bluetooth
  if ($pre -ceq "XHF" -or $u.ChipId -clike "368b:*") {
    Write-Host ""
    $fwl = P6-Fw-Last
    Write-Host (M newer_batch_serial_xhf_aic "$nm" "$fw1" "$fwl" "$(P6-Restore-Url $fwl)" "$(P6-Restore-Md5 $fwl)")
    if (-not (Ask (M install_lumen_os_projector))) { Die (M cancelled_nothing_written) }
    P6-Log "pre-check: newer batch confirmed by the user"
  }
  # what is in the USB port at power-on matters: a FAT32 stick is good, a USB Ethernet adapter or an A-to-A cable is not
  if ($u.Other) {
    $o = $u.Other
    Warn (M usb_device_not_usb_stick "$o" "$nm")
    if (Ask (M device_projectors_only_connection)) {
      Say (M keep_now_when_installer_asks)
      P6-Log "pre-check: other USB device kept as the network: $o"
    } else {
      $null = Read-Host (M take_out_projectors_usb_port)
      P6-Usb-Scan
      if ($script:P6U.Other) { Die (M usb_device_still_projectors_usb "$($script:P6U.Other)") }
      Ok (M no_other_device_usb_port)
      P6-Log "pre-check: other USB device removed"
    }
  }
  if ($u.Stick -and $fs -and $fs -cne "vfat") { Warn (M usb_stick_projector_not_fat32 "$fs" "$nm") }
  Say (M every_power_during_install_keep)
  $script:P6PreDone = $true
}
function P6-Noboot-Help {  # the projector did not come back after a start: what to do, and the way back for its batch
  $nm = P6-Name; $fw1 = P6-Fw-First; $chip = State-Get chip; $pre = State-Get serial_prefix; $lp = P6-LogPath
  P6-Log "no start reported (serial $pre, chip $(if ($chip) { $chip } else { '?' }))"
  Write-Host ""
  Write-Host (M if_projector_not_come_back_2)
  Write-Host (M n1_usb_port_only_fat32)
  Write-Host (M n2_pull_power_adapter_wait)
  Write-Host (M n3_note_what_see_xgimi)
  Write-Host (M n4_find_cause_we_need "$lp")
  Write-Host (M n5_back_stock_erases_everything "$nm")
  $c = if ($chip) { $chip } else { "?" }
  $fwl = P6-Fw-Last
  if ($pre -ceq "XHF" -or $chip -clike "368b:*") { Write-Host (M projector_serial_chip_newer_batch "$pre" "$c" "$fw1" "$fwl" "$(P6-Restore-Url $fwl)" "$(P6-Restore-Md5 $fwl)") }
  elseif ($chip -clike "0e8d:*") { Write-Host (M projector_serial_mediatek_chip_bin "$pre" "$chip" "$(P6-Fw-Back $chip $pre)") }
  else { Write-Host (M serial_xhn_mediatek_wi_fi "$(P6-Fw-Back '' $pre)" "$fw1" "$fwl") }
  Write-Host ""
}
function P6-Marker-Write {  # the check file for 'verify' (a real wipe removes /data/local/tmp); $false = not written
  $tok = "lumen-$(Get-Date -Format 'yyyyMMddHHmmss')-$PID"
  Adb shell "echo $tok > $P6Marker" | Out-Null
  if (((Adb shell cat $P6Marker) | Out-String).Trim() -cne $tok) { return $false }
  State-Set wipe_marker $tok
  return $true
}
function P6-Bcb-Bytes {  # the erase request, 2048 bytes (see $P6BcbArgs)
  $b = New-Object byte[] 2048
  $c = [Text.Encoding]::ASCII.GetBytes("boot-recovery"); [Array]::Copy($c, 0, $b, 0, $c.Length)
  $a = [Text.Encoding]::ASCII.GetBytes($P6BcbArgs); [Array]::Copy($a, 0, $b, 64, $a.Length)
  return ,$b
}
function P6-Bcb-Write {  # fastbootd, system written: the erase request into misc; $false = not written (then the earlier way)
  New-Item -ItemType Directory -Force -Path $script:SD | Out-Null
  $f = Join-Path $script:SD "bcb.bin"
  [IO.File]::WriteAllBytes($f, (P6-Bcb-Bytes))
  if ((Get-Item -LiteralPath $f).Length -ne 2048 -or (Sha256 $f) -ne $P6BcbSha) { Remove-Item -LiteralPath $f -Force -ErrorAction SilentlyContinue; P6-Fb-Stop "internal: the erase request is not the known one." }
  Say (M writing_erase_request_xgimis)
  $script:P6BcbOk = $true
  try { $ok = Fb-Do flash misc $f } finally { $script:P6BcbOk = $false }
  Remove-Item -LiteralPath $f -Force -ErrorAction SilentlyContinue
  if ($ok) { State-Set step_bcb done; P6-Log "erase request written"; return $true }
  State-Set step_bcb failed; P6-Log "erase request refused by fastbootd"
  Warn (M fastboot_mode_did_not_take)
  return $false
}
function P6-Ne {  # "Nothing was erased." for the earlier way; "" after the erase before the first start
  if ((State-Get step_bcb) -eq "done") { return "" }
  return (M nothing_erased)
}
function Play6-Boot-Fresh {  # the start after the write: XGIMI's recovery erases the data, then the first, light start of Lumen OS
  if (-not $script:AdbT) { $script:AdbT = State-Get ip }
  Write-Host ""
  Write-Host (M projector_now_starts_xgimis)
  P6-Log "light first start: waiting for the user"
  if (-not (Ask (M wait_lumen_os_projector_first))) {
    P6-Log "light first start: not seen"
    P6-Noboot-Help
    Die (M when_lumen_os_shows_run)
  }
  # erased: the projector is off Wi-Fi now. Still answering with the old data: the recovery did not take the request.
  Say (M checking_old_data_gone_up)
  if ($script:AdbT -and (Wifi-Connect 60)) {
    P6-Identity
    $mk = P6-Marker
    if ($mk -eq "there") {
      P6-Log "light first start: the old data is still there"
      Warn (M projector_still_has_old_data)
      # from here on the earlier way: its messages ("Nothing was erased.") and a resumed run go straight to Boot1
      State-Set step_bcb ignored
      Play6-Boot1
      return
    } elseif ($mk -eq "gone") { Ok (M data_erased_check_file_written); State-Set step_wipe done }
    else { Warn (M check_file_could_not_read); State-Set step_wipe requested }
  } else {
    Ok (M projector_off_wi_fi_data)
    State-Set step_wipe requested
    $script:WipedNow = $true
  }
  P6-Log "light first start: done (step_wipe=$(State-Get step_wipe))"
  State-Set step_boot1 done
}
function P6-Gapps-Fresh-Fill {  # one pass after the light first start: debugging on again, the Google apps in, then one more reset
  $nm = P6-Name
  if (-not $GappsZip) { P6-Gapps-Fill-Step; return }   # asks whether to finish without Google
  Write-Host ""
  Write-Host (M lumen_os_runs_clean_data_2)
  Write-Host (M n1_settings_network_internet_join)
  Write-Host (M n2_settings_device_preferences)
  Write-Host (M n3_settings_device_preferences "$nm")
  Write-Host (M when_projector_asks_allow_usb)
  $null = Read-Host (M press_enter_when_done)
  if (-not $script:AdbT) { $script:AdbT = State-Get ip }
  if (-not (Wifi-Connect 600)) { Die ((P6-NoConn 600) + " " + (M run_same_command_again_continues)) }
  # the fill is followed by Android's factory reset (Play6-Install): the first start after it sets Google up.
  # The check file of the erase before the first start must not count for that reset.
  State-Set step_wipe ""; State-Set wipe_marker ""; $script:EraseNow = $true
  P6-Gapps-Fill-Step
}
function Read-Hex($f, $off, $n) {  # n bytes of file f at off, as lower-case hex ("" when the file is shorter)
  $fs = [IO.File]::OpenRead($f)
  try {
    if ($fs.Length -lt ($off + $n)) { return "" }
    $null = $fs.Seek($off, [IO.SeekOrigin]::Begin)
    $b = New-Object byte[] $n
    $null = $fs.Read($b, 0, $n)
    return (($b | ForEach-Object { $_.ToString("x2") }) -join "")
  } finally { $fs.Close() }
}
function Verify-Sig($sums, $sig, $cert) {  # SHA256SUMS.sig made with the key of this certificate
  $c = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2 -ArgumentList $cert
  $rsa = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPublicKey($c)
  return $rsa.VerifyData([IO.File]::ReadAllBytes($sums), [IO.File]::ReadAllBytes($sig),
         [System.Security.Cryptography.HashAlgorithmName]::SHA256, [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
}
function Play6-Check-Image([switch]$Force) {  # name, file system, size, signature, checksum, content
  Find-Image
  $img = $script:Image; $name = Split-Path -Leaf $img; $dir = Split-Path -Parent $img; $nm = P6-Name; $tok = $script:Prof["IMAGE_TOKEN"]
  $sums = Join-Path $dir "SHA256SUMS"; $sig = "$sums.sig"; $cert = Join-Path $Here ($script:Prof["CERT"] -replace '/', '\')
  if (-not $name.Contains($tok)) { Die (M not_image_name_must_contain "$name" "$nm" "$tok") }
  if ($name -cmatch "PRIVATE") { Die (M private_image_never_installed "$name" "$nm") }
  $nv = ""
  if (-not $UnsignedImage) {
    if ($name -notlike "*-nogms-*") { Die (M not_release_file_has_lumen "$name" "$nm") }
    # the release version is in the name, and the signed SHA256SUMS lists the image under that name: a file of another
    # release (signed with the same key) next to this installer stops here, before anything is written
    if (-not $conf["VERSION"]) { Die "internal: release.conf has no VERSION: re-download the installer of the $nm release" }
    if ($name -cmatch "^lumen-os-$([regex]::Escape($tok))-(.+)-nogms-system\.img$") { $nv = $Matches[1] }
    if (-not $nv) { Die (M not_named_like_release_file "$name" "$tok") }
    if ($nv -cne $conf["VERSION"]) { Die (M lumen_os_but_installer_download "$name" "$nv" "$($conf['VERSION'])" "$nm") }
  }
  # the file system, read without any tool: raw ext4 has 53 ef at byte 1080
  if ($script:Prof["IMAGE_FS"] -eq "ext4" -and (Read-Hex $img 1080 2) -ne "53ef") {
    if ((Read-Hex $img 1024 4) -eq "e2e1f5e0") { Die (M xgimi_z9x_image_erofs_not "$name" "$nm") }
    if ((Read-Hex $img 0 4) -eq "3aff26ed") { Die (M sparse_image_not_release_file "$name") }
    Die (M not_ext4_system_image_nothing "$name")
  }
  if ((Get-Item -LiteralPath $img).Length % 4096 -ne 0) { Die (M has_size_not_multiple_4096 "$name") }
  if ((Test-Path -LiteralPath $sums) -and (Test-Path -LiteralPath $sig) -and (Test-Path -LiteralPath $cert)) {
    if (-not (Verify-Sig $sums $sig $cert)) { Die (M sha256sums_not_signed_lumen_os_2 "$nm") }
    Ok (M sha256sums_signature_lumen_os_key "$nm")
  } elseif ($UnsignedImage) {
    Warn (M unsigned_image_image_not)
    if ((State-Get unsigned_ok) -ne "${name}:$(File-Key $img)") {
      if ((Read-Host (M type_unsigned_install_anyway "$name")) -cne "UNSIGNED") { Die (M cancelled_nothing_written) }
      State-Set unsigned_ok "${name}:$(File-Key $img)"
    }
    if (-not (Test-Path -LiteralPath $sums)) {   # a list that names the image, or the image's own .sha256
      $sums = $null
      $cand = @(Get-ChildItem -Path (Join-Path $dir "SHA256SUMS*") -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*.sig" }) + @(Get-Item -LiteralPath "$img.sha256" -ErrorAction SilentlyContinue)
      foreach ($f in ($cand | Where-Object { $_ })) {
        if (Get-Content -LiteralPath $f.FullName | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))$" }) { $sums = $f.FullName; break }
      }
    }
  } else {
    if (-not (Test-Path -LiteralPath $sums)) { Die (M no_sha256sums_next_image_cannot) }
    if (-not (Test-Path -LiteralPath $sig)) { Die (M no_sha256sums_sig_next_image) }
    Die (M installer_certificate_missing_re "$($script:Prof['CERT'])" "$nm")
  }
  if ($sums -and (Test-Path -LiteralPath $sums)) {
    $want = (Get-Content -LiteralPath $sums | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))$" } | ForEach-Object { ($_ -split "\s+")[0] }) | Select-Object -First 1
    if (-not $want) { Die (M not_listed "$name" "$(Split-Path -Leaf $sums)") }
    if ($Force -or (State-Get image_ok) -ne "${want}:$(File-Key $img)") {
      Say (M checking_image_checksum_about)
      if ((Sha256 $img) -ne $want.ToLower()) { State-Set image_ok none; Die (M checksum_mismatch_image_damaged) }
      State-Set image_ok "${want}:$(File-Key $img)"
    }
    Ok "$name sha256 $($want.Substring(0,16))..."
  } else { Warn (M unsigned_image_without_any) }
  # what the image says about itself, when debugfs (e2fsprogs) is installed. Without it the name (its version, checked
  # above against release.conf) and the signed SHA256SUMS (which lists the file under that name) bind the image to
  # this release; this also catches a release built wrong. The files in the order init reads them: the last one wins.
  $bound = if ($nv) { M signed_sha256sums_lists_file_lumen "$nv" } else { M own_build_unsigned_image }
  if (Get-Command debugfs -ErrorAction SilentlyContinue) {
    $bp = @(foreach ($pf in @("/system/etc/prop.default", "/system/build.prop", "/system/system_ext/etc/build.prop", "/system/product/etc/build.prop")) { & debugfs -R "cat $pf" $img 2>$null })
    if ($bp.Count -gt 0) {
      $pv = { param($k) $l = $bp | Where-Object { $_ -like "$k=*" } | Select-Object -Last 1; if ($l) { $l.Substring($k.Length + 1).Trim() } else { "" } }
      $v = & $pv (P6-LProp)
      if ($v -cne (P6-LVal)) { Die (M not_lumen_os_nothing_written "$name" "$nm" "$(P6-LProp)" "$v") }
      $v = & $pv $script:Prof["VERSION_PROP"]; $sec = & $pv "ro.adb.secure"
      if (-not $UnsignedImage) {
        if ($v -cne $conf["VERSION"]) { Die (M holds_lumen_os_release_nothing "$name" "$v" "$($conf['VERSION'])") }
        if ($sec -ne "1") { Die (M test_build_ro_adb_secure "$name" "$sec") }
      }
      Ok (M image_lumen_os "$v" "$nm")
    } else { Warn (M debugfs_could_not_read_image "$bound") }
  } else { Say (M image_content_not_read_debugfs "$bound") }
  State-Set image_gms 0
  Ok (M edition_lumen_os_without_google)
}
function Play6-Check {
  Check-Tools
  if (-not (Play6-Adb 60)) { Die (P6-NoConn) }
  Play6-Device
  P6-Prechecks
  $free = (Get-PSDrive -Name (Get-Item -LiteralPath $HOME).PSDrive.Name -ErrorAction SilentlyContinue).Free
  if ($free -and $free -lt 4GB) { Warn (M less_4_gb_free_computer) }
  Play6-Check-Image
  Ok (M check_passed_nothing_written)
  State-Set step_check done
}
function P6-In-Fastboot {
  if (-not $script:Serial) { return $false }
  return (@(Pt-Fastboot devices 2>$null | ForEach-Object { ("$_".Trim() -split "\s+")[0] }) -ccontains $script:Serial)
}
function P6-Fb-Wait($secs) {  # the projector ($script:Serial) in fastboot mode on the cable
  for ($i = 0; $i -lt $secs; $i += 2) {
    if (P6-In-Fastboot) { return $true }
    if ($i -gt 0 -and ($i % 60) -eq 0) { Say (M still_waiting_fastboot_mode) }
    Start-Sleep 2
  }
  return $false
}
function P6-Fb-Stop($m) {  # leave fastbootd for the old system: nothing was written
  Fb reboot | Out-Null
  Die ($m + " " + (M nothing_written_unplug_cable))
}
function Gv-Val($gv, $k) { $l = $gv | Where-Object { "$_" -like "(bootloader) ${k}:*" } | Select-Object -First 1; if ($l) { return ("$l".Substring(("(bootloader) ${k}:").Length)).Trim() } else { return "" } }
function Play6-Fbd-Check {  # fastbootd: this Play 6 (serial), its firmware and the known layout of super; returns 'getvar all'
  $gv = @(Fb getvar all); $nm = P6-Name; $why = ""
  if ((Gv-Val $gv "is-userspace") -ne "yes") { $why = M not_fastbootd }
  elseif ((Gv-Val $gv "product") -cne $script:Prof["FB_PRODUCT"]) { $why = (M fastbootd_not_one "$nm") + " ($(Gv-Val $gv 'product'))" }
  elseif ((Gv-Val $gv "unlocked") -ne "yes") { $why = M bootloader_locked }
  elseif ((Gv-Val $gv "serialno") -cne $script:Serial) { $why = (M another_projector_fastboot_mode) + " (serialno $(Gv-Val $gv 'serialno'), $(M expected) $($script:Serial))" }
  elseif (@("", "0") -notcontains (Gv-Val $gv "slot-count")) { $why = "slot-count $(Gv-Val $gv 'slot-count')" }
  elseif (Gv-Val $gv "current-slot") { $why = "current-slot $(Gv-Val $gv 'current-slot')" }
  elseif ((Gv-Val $gv "super-partition-name") -cne "super") { $why = "super-partition-name '$(Gv-Val $gv 'super-partition-name')'" }
  else {
    $l = (@($gv | ForEach-Object { if ("$_" -match '^\(bootloader\) is-logical:([^:]+):yes') { $Matches[1] } } | Sort-Object -Unique -CaseSensitive) -join " ")
    $want = (@($script:Prof["LOGICAL"] -split " " | Sort-Object -Unique -CaseSensitive) -join " ")
    # the Google services add-on ('gapps') adds one partition of its own
    $wantg = (@(@($script:Prof["LOGICAL"] -split " ") + @("lumen_gapps") | Sort-Object -Unique -CaseSensitive) -join " ")
    if ($l -cne $want -and $l -cne $wantg) { $why = (M partitions_super_not_known_ones) + " ($l)" }
    else {
      $fp = Gv-Val $gv "vendor-fingerprint"; $fw = $false
      foreach ($v in ($script:Prof["VENDOR_OK"] -split " ")) { if ($fp -like "*/$($v.Substring(1)):*") { $fw = $true } }
      if (-not $fw) { $why = (M xgimi_firmware_not_supported) + " ($fp)" }
    }
  }
  if ($why) { P6-Fb-Stop ((M fastboot_mode_check_failed) + ": $why.") }
  Ok (M fastboot_mode "$nm" "$($script:Serial)")
  return $gv
}
function P6-Room($gv, $gapps) {  # the image must fit into super next to the other logical partitions (each rounded up to 1 MiB);
  # with -GappsZip the image and lumen_gapps at $gapps bytes together (an existing lumen_gapps is resized to it)
  $sup = Gv-Size $gv "super"
  if ($null -eq $sup) { P6-Fb-Stop (M fastbootd_did_not_report_size) }
  $used = [int64]0
  foreach ($p in ($script:Prof["LOGICAL"] -split " ")) {
    if ($p -eq "system") { continue }
    $sz = Gv-Size $gv $p
    if ($null -eq $sz) { P6-Fb-Stop (M fastbootd_did_not_report_size_2 "$p") }
    $used += [int64]([math]::Ceiling($sz / 1MB) * 1MB)
  }
  $sz = Gv-Size $gv "lumen_gapps"   # the Google services add-on ('gapps') keeps its space
  if ($null -ne $sz -and -not $gapps) { $used += [int64]([math]::Ceiling($sz / 1MB) * 1MB) }
  $room = [int64]($sup - $used - [int64]$script:Prof["SUPER_RESERVE"])
  $len = (Get-Item -LiteralPath $script:Image).Length
  if ($gapps) {
    $need = [int64]([math]::Ceiling($len / 1MB) * 1MB) + [int64]([math]::Ceiling([int64]$gapps / 1MB) * 1MB)
    if ($need -gt $room) { P6-Fb-Stop (M image_google_services_not_fit "$([math]::Truncate($room / 1MB))" "$([math]::Truncate($need / 1MB))") }
    return
  }
  if ($len -gt $room) { P6-Fb-Stop (M image_not_fit_super_mb "$([math]::Truncate($room / 1MB))" "$([math]::Ceiling($len / 1MB))") }
}
function P6-Cable-Out {  # after 'fastboot reboot': the projector does not start while the computer's cable is in
  $nm = P6-Name
  Write-Host ""
  Write-Host (M written_unplug_cable_projector_now "$nm")
  $null = Read-Host (M press_enter_when_cable_out)
}
function Play6-Write {  # check, confirmation, cable, fastbootd, write system, restart (resumes in fastbootd)
  $nm = P6-Name; $infb = $false
  Check-Tools
  if ((State-Get step_fastboot) -and (State-Get step_flash) -ne "done" -and (P6-In-Fastboot)) {
    $infb = $true
    if ((State-Get step_flash_system) -eq "done") {   # fastboot said OKAY, the restart was not sent
      if ($GappsZip) { State-Set gapps_plan $script:P6gSha }
      if ((State-Get gapps_plan) -and (State-Get step_gapps_part) -ne "done") { $null = Play6-Fbd-Check; P6-Gapps-Add }
      if (-not $KeepData -and (State-Get wipe_plan) -eq "bcb" -and -not (State-Get step_bcb)) { $null = Play6-Fbd-Check; $null = P6-Bcb-Write }
      Ok (M system_already_written_restarting)
      Fb reboot | Out-Null
      State-Set step_flash done
      P6-Cable-Out
      return
    }
    Say (M projector_waits_fastboot_mode)
  } else {
    if (-not (Play6-Adb 60)) {
      # an earlier run sent the projector to fastboot mode: it is neither there (on the cable) nor back in its system
      if (State-Get step_fastboot) { Die (M projector_neither_fastboot_mode "$($script:AdbT)" "$nm") }
      Die (P6-NoConn)
    }
    Play6-Device
    P6-Prechecks
  }
  Play6-Check-Image -Force
  Write-Host ""
  if ($KeepData) {
    Write-Host (M writing_lumen_os_system_partition "$nm")
    if ((Read-Host (M type_write_continue)) -cne "WRITE") { Die (M cancelled_nothing_written) }
    State-Set wipe_plan keep
  } else {
    Write-Host (M writing_lumen_os_system_partition_2 "$nm")
    if ($GappsZip) {
      Write-Host (M gapps_zip_same_fastboot_session "$P6Gapps" "$([math]::Floor($script:P6gPart / 1MB))" "$($script:P6gName)")
    }
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_written) }
    $script:EraseNow = $true
    State-Set erase_ok (Get-Date -Format "yyyyMMdd-HHmmss")
    State-Set wipe_plan bcb
  }
  # -GappsZip: the Google services of this install (P6-Gapps-Fill-Step adds them after the first start). A new write
  # takes this run's choice; a write resumed in fastboot mode keeps the saved one unless -GappsZip is given.
  if (-not $infb -or $GappsZip -or $KeepData) { State-Set gapps_plan $script:P6gSha }
  if (-not $infb) {
    foreach ($k in @("step_fastboot", "step_flash_system", "step_flash", "step_boot1", "step_wipe", "step_verify", "wipe_marker", "step_gapps_part", "step_gapps_fill", "step_bcb")) { State-Set $k "" }
    # the proof for 'verify' and for the light first start: /data/local/tmp does not survive the erase
    if (-not $KeepData -and -not (P6-Marker-Write)) { Die (M could_not_write_check_file "$P6Marker") }
    Write-Host ""
    Write-Host (M take_usb_stick_whatever_out)
    $null = Read-Host (M press_enter_when_cable)
    Say (M restarting_fastboot_mode_about_30)
    # saved first: a restart that happens although adb reports an error (the Wi-Fi connection drops with it) is still
    # continued in fastboot mode by the next run
    State-Set step_fastboot (Get-Date -Format "yyyyMMdd-HHmmss")
    Adb reboot fastboot | Out-Null
    if ($LASTEXITCODE -ne 0 -and (P6-Alive)) {
      State-Set step_fastboot ""
      Die (M adb_reboot_fastboot_failed)
    }
    Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
  }
  if (-not (P6-Fb-Wait 180)) { Die ((M projector_did_not_appear_cable_5) + (Fb-Driver-Hint)) }
  $gv = Play6-Fbd-Check
  P6-Room $gv (P6-Plan-Part)
  Say (M writing_system_about_1_minute)
  if (-not (Fb-Do flash system $script:Image)) { Die (M system_not_written_completely) }
  State-Set step_flash_system done
  if (State-Get gapps_plan) { P6-Gapps-Add }
  if (-not $KeepData -and (State-Get wipe_plan) -eq "bcb") { $null = P6-Bcb-Write }
  Fb reboot | Out-Null
  State-Set step_flash done
  P6-Cable-Out
}
function P6-Write-Again {  # the write did not take effect: the next run (or 'flash') writes system again from the start
  foreach ($k in @("step_fastboot", "step_flash_system", "step_flash", "step_bcb")) { State-Set $k "" }
}
function Play6-Boot1 {  # the first start of Lumen OS on the old data: Wi-Fi and debugging carry over from stock
  $nm = P6-Name
  if (-not $script:AdbT) { $script:AdbT = State-Get ip }
  if (-not $script:AdbT) { Die "internal: no saved address of the projector" }
  Say (M waiting_lumen_os_over_wi)
  if (-not (Wifi-Connect 600)) {
    if ($script:P6St -match "unauthorized") { Die (M lumen_os_runs_but_did) }
    P6-Noboot-Help
    Die (M lumen_os_did_not_answer "$nm")
  }
  P6-Identity
  $sw = [Diagnostics.Stopwatch]::StartNew()
  for ($i = 0; (Prop sys.boot_completed) -ne "1"; $i += 5) {
    if ($i -ge 300 -or $sw.Elapsed.TotalSeconds -ge 300) { Die (M lumen_os_did_not_finish) }
    Start-Sleep 5
  }
  $lum = Prop (P6-LProp)
  if (-not $lum) {
    P6-Write-Again
    Die (M projector_still_runs_xgimis_system)
  }
  if ($lum -cne (P6-LVal)) { Die (M projector_runs_lumen_os_another_2 "$lum") }
  $v = Prop $script:Prof["VERSION_PROP"]
  if (-not $UnsignedImage -and $conf["VERSION"] -and $v -cne $conf["VERSION"]) {
    if ((State-Get dev_lumen) -eq "1" -and $v -ceq (State-Get dev_version)) {
      P6-Write-Again
      Die (M projector_still_runs_lumen_os "$v")
    }
    # the image was checked against this release before the write (name, signature, checksum): only its own version
    # name differs, which does not make the wipe of this projector any less right
    Warn (M lumen_os_runs_installer_download "$v" "$($conf['VERSION'])")
  }
  Ok ((M lumen_os_started "$v") + " ($($script:Serial), $($script:AdbT))")
  if (((Adb shell pidof $P6App) | Out-String).Trim()) { Ok (M projector_app_running) } else { Warn (M projector_app_not_running_yet "$P6App") }
  State-Set ip $script:AdbT
  State-Set step_boot1 done
}
function P6-Root {  # adb root restarts adbd, so connect again. 0 = root; 1 = this system gives no root (not userdebug);
  # 2 = no answer after 'adb root' (the allow prompt was not accepted, or the network): try again later
  # 3 = Developer options > Rooted debugging is off (LineageOS adbd: "ADB Root access is disabled by system setting")
  if ((Prop ro.debuggable) -ne "1") { return 1 }
  if (((Adb shell id "-u") | Out-String).Trim() -eq "0") { return 0 }
  $t = if ($script:AdbT) { $script:AdbT } else { $script:Serial }
  $out = (Pt-Adb -s $t root 2>&1) | Out-String   # both streams: the refusal text decides return 3
  if ($out -match "disabled by system setting") { return 3 }
  Start-Sleep 3
  if (-not (Wifi-Connect 60)) { return 2 }
  if (((Adb shell id "-u") | Out-String).Trim() -eq "0") { return 0 } else { return 1 }
}
function Play6-Wipe {  # Android's own factory reset on the first Lumen OS start; XGIMI's recovery carries it out
  $nm = P6-Name
  if ((State-Get step_boot1) -ne "done") { Die "internal: no factory reset before Lumen OS has started" }
  if ($KeepData) { Die "internal: no factory reset with -KeepData" }
  if (-not $script:AdbT) { $script:AdbT = State-Get ip }
  if (-not (Wifi-Connect 120)) {
    # a factory reset asked for by an earlier run (cut off before it saved that) erases Wi-Fi and turns debugging off
    if ((State-Get wipe_marker) -and $script:P6St -notmatch "unauthorized") { Die (M projector_not_answer_over_wi "$($script:AdbT)" "$nm") }
    Die (P6-NoConn 120)
  }
  P6-Identity
  if ((Prop (P6-LProp)) -cne (P6-LVal)) { Die (M projector_not_run_lumen_os_2 "$nm") }
  # an earlier run asked for the reset and was cut off before it saved that: the check file tells what happened
  if ((State-Get wipe_marker) -and (P6-Marker) -eq "gone") {
    Ok (M data_already_erased_check_file)
    State-Set step_wipe done
    return
  }
  if (-not $script:EraseNow) {
    Write-Host ""
    if ((State-Get step_bcb) -eq "done" -and (State-Get step_gapps_fill) -eq "done") {
      Write-Host (M google_apps_copied_next_androids)
    } else {
      Write-Host (M lumen_os_runs_old_data)
    }
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_erased) }
    $script:EraseNow = $true
  }
  # the proof for 'verify': /data/local/tmp does not survive a real wipe
  if (-not (P6-Marker-Write)) { Die (M could_not_write_check_file_2 "$P6Marker") }
  $rc = P6-Root
  if ($rc -eq 2) { Die (M after_adb_root_projector_did) }
  if ($rc -ne 0) {
    if ($rc -eq 3) { Say (M root_debugging_off_settings_device) }
    Warn (M adb_root_not_possible_system_2)
    Say (M projector_settings_device)
    State-Set step_wipe manual
    return
  }
  Say (M asking_android_factory_reset)
  # --user 0: MasterClearReceiver wipes only the sending user when that is not user 0
  $out = ((Adb shell am broadcast "--user" "0" "-a" "android.intent.action.FACTORY_RESET" "-p" "android" "--receiver-foreground" "--es" "android.intent.extra.REASON" "lumen-install") | Out-String).Trim()
  if ($out -notmatch "Broadcast completed") { Die (M factory_reset_not_accepted_data "$out") }
  # the projector restarts into XGIMI's recovery: it stops answering. Asked on a new connection each time, since the
  # computer's old one can still say 'device' for a while after the projector went away.
  $sw = [Diagnostics.Stopwatch]::StartNew()
  for ($i = 0; (P6-Alive); $i += 5) {
    if ($i -ge 60 -or $sw.Elapsed.TotalSeconds -ge 60) { Die (M projector_still_answers_minute) }
    Start-Sleep 5
  }
  Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
  State-Set step_wipe requested
  $script:WipedNow = $true
  Write-Host ""
  Write-Host (M projector_restarts_xgimis_recovery)
  if (Ask (M wait_lumen_home_projector_up)) { Ok (M lumen_os_started_clean) }
  else { Warn (M press_power_button_once_if "$nm") ; P6-Noboot-Help }
}
function Play6-Verify($secs) {  # over Wi-Fi, after the wipe (the user joined Wi-Fi and turned debugging on again)
  $nm = P6-Name
  if (-not $script:AdbT) { $script:AdbT = State-Get ip }
  if (-not $script:AdbT -and $script:Serial) { $script:AdbT = P6-Discover $script:Serial }   # only this projector, by its serial
  $script:P6St = ""
  if (-not $script:AdbT -or -not (Wifi-Connect $secs)) {
    if ($script:P6St -match "unauthorized") { Warn (M projector_did_not_allow_computer_2 "$($script:AdbT)") }
    else { Warn (M no_adb_connection_projector_over "$nm") }
    return
  }
  P6-Identity
  if ((Prop sys.boot_completed) -eq "1") { Ok (M started) } else { Warn "sys.boot_completed=$(Prop sys.boot_completed)" }
  if ((Prop (P6-LProp)) -cne (P6-LVal)) { Die (M projector_not_run_lumen_os_3 "$nm") }
  $v = Prop $script:Prof["VERSION_PROP"]; $sec = Prop ro.adb.secure
  if (-not $UnsignedImage -and $conf["VERSION"] -and $v -cne $conf["VERSION"]) { Warn "Lumen OS $v ($(M expected_2) $($conf['VERSION']))" } else { Ok "Lumen OS $v ($($script:Serial), $($script:AdbT))" }
  if ($sec -eq "1") { Ok (M debugging_asks_before_new_computer) } else { Warn "ro.adb.secure=$sec ($(M test_build))" }
  $st = State-Get step_wipe
  if (@("requested", "manual", "done") -contains $st) {
    $mk = P6-Marker
    if ($mk -eq "there") {
      Warn (M data_not_erased_check_file)
      if (Ask (M erase_all_data_now_androids)) { $script:EraseNow = $false; Play6-Wipe; return }
    } elseif ($mk -eq "gone") {
      Ok (M data_erased_check_file_before)
      State-Set step_wipe done
    } else { Warn (M check_file_factory_reset_could) }
    if (-not ((Adb shell pm path $P6Tuner) | Out-String).Trim()) { Ok (M systemui_tuner_stock_gone) } else { Warn (M systemui_tuner_stock_still) }
    $n = @(Adb shell pm list packages "-3" | Where-Object { "$_" -like "package:*" }).Count
    if ($n -eq 0) { Ok (M no_apps_before) } else { Say (M user_apps "$n") }
  } elseif ($st -eq "keep") { Ok (M data_kept_keep_data) }
  $flags = ""
  foreach ($s in @(@("global", "device_provisioned"), @("secure", "user_setup_complete"), @("secure", "tv_user_setup_complete"))) { $flags += ((Adb shell settings get $s[0] $s[1]) | Out-String).Trim() }
  if ($flags -eq "111") { Ok (M projector_marked_set_up_home) }
  else { Warn (M projector_not_marked_set_up "$flags") }
  if (((Adb shell pidof $P6App $P6AppOld) | Out-String).Trim()) { Ok (M projector_app_running) } else { Warn (M projector_app_not_running "$P6App") }
  if ((Adb shell dumpsys media.audio_policy) -match "Output") { Ok (M audio_outputs_present) } else { Warn (M no_audio_outputs_listed) }
  State-Set step_verify done
}
function Play6-Install {  # write (with the erase request), first start, wipe: each step once, continued where it stopped
  if ((State-Get step_flash) -ne "done") { Play6-Write }
  if ((State-Get step_boot1) -ne "done") {
    # the erase request in misc: XGIMI's recovery erases before the first start. Without it (-KeepData, refused by
    # fastboot mode, or an install begun by an older installer): the first start on the old data, the wipe after it
    if (-not $KeepData -and (State-Get step_bcb) -eq "done") { Play6-Boot-Fresh } else { Play6-Boot1 }
  }
  if ((State-Get gapps_plan) -and (State-Get step_gapps_fill) -ne "done") {
    $w = State-Get step_wipe
    if (-not $w) { P6-Gapps-Fill-Step }                                                                 # on the old data, as before
    elseif (($w -eq "requested" -or $w -eq "done") -and (State-Get step_bcb) -eq "done") { P6-Gapps-Fresh-Fill }   # on clean data
  }
  if (-not (State-Get step_wipe)) { if ($KeepData) { State-Set step_wipe keep } else { Play6-Wipe } }
}
function Play6-Done {
  $nm = P6-Name; $w = State-Get step_wipe
  Write-Host ""
  if ($w -eq "keep") {
    if ((State-Get step_verify) -ne "done") { Play6-Verify 30 }
    Write-Host (M done_lumen_os_runs_data "$nm")
  } elseif ($w -eq "manual") {
    Write-Host (M lumen_os_written_finish_factory)
  } else {
    # a later run: the projector may answer again (Wi-Fi joined, debugging on): then check it
    if (-not $script:WipedNow -and (State-Get step_verify) -ne "done") {
      if (-not $script:AdbT) { $script:AdbT = State-Get ip }
      if ($script:AdbT -and (Wifi-Connect 0)) { Play6-Verify 0; Write-Host "" }
    }
    Write-Host (M done_projector_settings_network "$nm")
    if ((State-Get gapps_plan) -and (State-Get step_gapps_fill) -eq "done") {
      Ok (M google_services_after_joining_wi "$nm")
    }
  }
  Write-Host "  $(M guide): $(P6-Guide)"
}
function Play6-Flash {  # the 'flash' command: a new write, unless a write waits in fastbootd (then it continues there)
  if (-not ((State-Get step_fastboot) -and (State-Get step_flash) -ne "done" -and (P6-In-Fastboot))) {
    # a finished earlier write leaves nothing to continue: its step_fastboot must not count for this new write
    if ((State-Get step_flash) -eq "done") { P6-Write-Again }
  }
  Play6-Install
  Play6-Done
}
function Play6-All {
  if ($GappsZip) { P6-Gapps-Plan }
  if ((State-Get step_check) -ne "done") { Play6-Check }
  if (-not (State-Get step_backup) -and -not (P6-In-Fastboot)) {
    if (Ask (M make_backup_apps_files_first)) { Cmd-Backup } else { State-Set step_backup skipped }
  }
  Play6-Install
  Play6-Done
}
function Stick-Check {  # the stock-restore USB stick of this projector, read only (it never formats anything)
  $nm = P6-Name; $bin = $script:Prof["RESTORE_BIN"]; $bad = $false
  if (-not $bin) { Die (M stick_checks_restore_stick_bash "$(Dev-Name (Flow-Id play6-wifi))") }
  $m = if ($Path) { $Path } elseif ($script:Image) { $script:Image } else { "" }   # 'stick E:\' binds E:\ to -Image
  if (-not $m) { Die (M which_stick_lumen_install_cmd) }
  if (-not (Test-Path -LiteralPath $m -PathType Container)) { Die (M not_folder "$m") }
  Say (M checking_stock_restore_stick_read "$m")
  # the partition table and the file system (MBOOT reads only MBR + one FAT32 partition)
  $pt = ""
  try {
    $drv = (Get-Item -LiteralPath $m).PSDrive.Name
    $part = Get-Partition -DriveLetter $drv -ErrorAction Stop
    $pt = "$((Get-Disk -Number $part.DiskNumber -ErrorAction Stop).PartitionStyle)"
    $parts = @(Get-Partition -DiskNumber $part.DiskNumber -ErrorAction Stop).Count
    $fs = "$((Get-Volume -DriveLetter $drv -ErrorAction Stop).FileSystem)"
  } catch { $pt = "" }
  if (-not $pt) { Warn (M partition_table_could_not_read) }
  else {
    if ($pt -eq "MBR") { Ok (M partition_table_mbr) } else { Warn (M partition_table_must_mbr_master "$pt"); $bad = $true }
    if ($parts -eq 1) { Ok (M one_partition) } else { Warn (M partitions_there_must_exactly_one "$parts"); $bad = $true }
    if ($fs -eq "FAT32") { Ok (M file_system_fat32) } else { Warn (M file_system_must_fat32 "$fs"); $bad = $true }
  }
  # exactly the firmware file and an empty factory.txt (folders the computer makes itself do no harm)
  foreach ($e in @(Get-ChildItem -LiteralPath $m -Force -ErrorAction SilentlyContinue)) {
    $n = $e.Name
    if (@($bin, "factory.txt", ".fseventsd", ".Spotlight-V100", ".Trashes", ".TemporaryItems", "System Volume Information") -ccontains $n) { continue }
    if ($n.StartsWith("._")) { Warn (M macos_helper_file_delete "$n"); $bad = $true }
    else { Warn (M nothing_else_may_stick "$n"); $bad = $true }
  }
  $ft = Join-Path $m "factory.txt"
  if (Test-Path -LiteralPath $ft) {
    if ((Get-Item -LiteralPath $ft).Length -gt 0) { Warn (M factory_txt_must_empty); $bad = $true } else { Ok (M factory_txt_empty) }
  } else { Warn (M no_factory_txt_create_empty); $bad = $true }
  $bf = Join-Path $m $bin
  if (Test-Path -LiteralPath $bf) {
    Say (M checking_md5_about_minute "$bin")
    $sum = (Get-FileHash -LiteralPath $bf -Algorithm MD5).Hash.ToLower()
    $ver = ""
    foreach ($e in ($script:Prof["RESTORE_MD5"] -split " ")) { $kv = $e -split ":", 2; if ($kv[0] -eq $sum) { $ver = $kv[1] } }
    if ($ver) { Ok "${bin}: $(M xgimi_firmware) $ver (md5 $sum)" }
    else { Warn (M not_firmware_file_installer_knows "$bin" "$sum"); $bad = $true }
  } else { Warn (M no_stick_exactly_name "$bin"); $bad = $true }
  if ($bad) { Die (M stick_not_ready_fix_lines "$nm") }
  Ok (M stick_ready_restore_power_cable)
}
# ------------------------------------------------------------------ XGIMI Play 6: gapps and gsf (see lumen-install.sh, same section)
# -Zip FILE (only a MindTheGapps zip whose sha256 is in lib\play6\gapps_allow.txt) goes into the logical partition
# lumen_gapps in super, which the image's lumen_gapps.sh mounts read-only at every start (sys.lumen.gapps). Checks,
# then lib\play6\gapps_fill.sh 'off' (the next starts leave the partition unmounted), fastboot mode over the cable:
# create or resize lumen_gapps (room in super checked first), back in Lumen OS: adb root, the zip pushed,
# gapps_fill.sh formats and fills it on the projector (no e2fsprogs on this computer), then Android's factory reset
# (not with -KeepData). -Remove deletes the partition in fastboot mode, with the same wipe. 'gapps': the state.
$P6Gapps = "lumen_gapps"
function P6-Gapps-Lib($f) { return (Join-Path (Join-Path (Join-Path $Here "lib") "play6") $f) }
function P6-Gapps-Lf($f) {  # a copy of lib\play6\<f> with LF line ends (whatever a Windows checkout made of it); its path
  New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
  $tmp = Join-Path $StateDir "play6-$f"
  [IO.File]::WriteAllText($tmp, ([IO.File]::ReadAllText((P6-Gapps-Lib $f)) -replace "`r", ""))
  return $tmp
}
function P6-Gapps-Line($sha) { Get-Content (P6-Gapps-Lf "gapps_allow.txt") | Where-Object { $_ -like "zip=$sha *" } | Select-Object -First 1 }
function P6-Gapps-Push {  # fill.sh, the allow-list and the sysconfig layer into $GappsDir on the projector
  Adb shell rm -rf $GappsDir | Out-Null
  Adb shell mkdir -p $GappsDir | Out-Null
  foreach ($f in @(@("gapps_fill.sh", "fill.sh"), @("gapps_allow.txt", "allow.txt"), @("lumen-gapps.xml", "lumen-gapps.xml"))) {
    Adb push (P6-Gapps-Lf $f[0]) "$GappsDir/$($f[1])" | Out-Null
    if ($LASTEXITCODE -ne 0) { Die "adb push lib\play6\$($f[0])" }
  }
}
function P6-Lumen-Only {  # connected: Lumen OS for this projector runs (Play6-Device said so), else STOP
  $nm = P6-Name
  if ((State-Get dev_lumen) -ne "1") { Die (M projector_runs_xgimis_system "$nm") }
}
function P6-Root-Or-Die {  # adb root over Wi-Fi, else STOP
  $rc = P6-Root
  if ($rc -eq 2) { Die (M after_adb_root_projector_did_2) }
  if ($rc -eq 3) { Die (M root_debugging_off_projector_turn) }
  if ($rc -ne 0) { Die (M adb_root_not_possible_system) }
}
function P6-Wait-Lumen($why) {  # after a restart: Lumen OS of this projector back over Wi-Fi and started (else STOP)
  $nm = P6-Name
  Say (M waiting_lumen_os_over_wi_2)
  if (-not (Wifi-Connect 600)) { Die ((M lumen_os_did_not_answer_2) + " $why") }
  P6-Identity
  $sw = [Diagnostics.Stopwatch]::StartNew()
  for ($i = 0; (Prop sys.boot_completed) -ne "1"; $i += 5) {
    if ($i -ge 300 -or $sw.Elapsed.TotalSeconds -ge 300) { Die ((M lumen_os_did_not_finish_2) + " $why") }
    Start-Sleep 5
  }
  if ((Prop (P6-LProp)) -cne (P6-LVal)) { Die (M projector_not_run_lumen_os_3 "$nm") }
}
function P6-To-Fastboot {  # Lumen OS runs: cable in, restart into fastboot mode, check it there; returns 'getvar all'
  Write-Host ""
  Write-Host (M take_usb_stick_out_projectors)
  $null = Read-Host (M press_enter_when_cable)
  Say (M restarting_fastboot_mode_about_30)
  Adb reboot fastboot | Out-Null
  if ($LASTEXITCODE -ne 0 -and (P6-Alive)) { Die (M adb_reboot_fastboot_failed_2) }
  Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
  if (-not (P6-Fb-Wait 180)) { Die ((M projector_did_not_appear_cable_2) + (Fb-Driver-Hint)) }
  return (Play6-Fbd-Check)
}
function P6-Super-Free($gv) {  # bytes of super no logical partition uses (each rounded up to 1 MiB), minus SUPER_RESERVE
  $sup = Gv-Size $gv "super"
  if ($null -eq $sup) { return [int64]0 }
  $used = [int64]0
  $names = @($gv | ForEach-Object { if ("$_" -match "^\(bootloader\) is-logical:([^:]+):yes") { $Matches[1] } } | Sort-Object -Unique)
  foreach ($n in $names) { $sz = Gv-Size $gv $n; if ($null -ne $sz) { $used += [int64][math]::Ceiling($sz / 1MB) * 1MB } }
  return [int64]($sup - $used - [int64]$script:Prof["SUPER_RESERVE"])
}
function P6-Gapps-Wipe {  # the data wipe of the install (Android's factory reset), right after a 'gapps' change
  # Play6-Wipe works on Lumen OS that has started (step_boot1) and asks again unless ERASE was typed; a check file
  # of an earlier wipe must not count for this one
  State-Set step_boot1 done; State-Set wipe_marker ""; State-Set step_wipe ""; State-Set step_verify ""
  $script:EraseNow = $true
  Play6-Wipe
}
function Play6-Gapps-Install {
  $nm = P6-Name
  if (-not (Test-Path -LiteralPath $Zip)) { Die (M no_file "$Zip") }
  $zip = (Resolve-Path -LiteralPath $Zip).Path
  foreach ($f in @("gapps_allow.txt", "gapps_fill.sh", "lumen-gapps.xml")) { if (-not (Test-Path (P6-Gapps-Lib $f))) { Die "internal: lib\play6\$f is missing: re-download the installer of the $nm release" } }
  Check-Tools
  $zname = Split-Path -Leaf $zip
  Say (M checking "$zname")
  $zsha = Sha256 $zip
  $zl = P6-Gapps-Line $zsha
  if (-not $zl) { Die ((M not_mindthegapps_file_installer_2 "$zname" "$nm" "$($zsha.Substring(0,16))") + ". " + (M nothing_written)) }
  $name = ("$zl" -split " ")[1]; $part = [int64](Gapps-Field $zl "part"); $mb = [int64][math]::Floor($part / 1MB)
  if ((Sha256 (P6-Gapps-Lf "lumen-gapps.xml")) -ne (Gapps-Field $zl "sysconfig")) { Die "internal: lib\play6\lumen-gapps.xml is not the allow-listed one: re-download the installer" }
  Ok "$name, sha256 $($zsha.Substring(0,16))..."
  if (-not (Play6-Adb 60)) {
    # an earlier run may have stopped while the projector waited in fastboot mode on the cable
    if (P6-In-Fastboot) { Fb reboot | Out-Null; Die (M projector_waiting_fastboot_mode) }
    Die (P6-NoConn)
  }
  Play6-Device
  P6-Lumen-Only
  $have = Prop sys.lumen.gapps
  if (-not $have) { Die (M lumen_os_projector_has_no "$nm") }
  if ($have -like "mounted:*") { Ok (M google_services_already_added); return }
  if ($have -eq "none" -and (Prop ro.com.google.gmsversion)) { Die (M projector_runs_lumen_os_google_2) }
  P6-Root-Or-Die
  Write-Host ""
  if ($KeepData) {
    Write-Host (M adding_google_services_installer "$P6Gapps" "$mb" "$name")
    Say (M keep_data_sign_google_later_2)
    if ((Read-Host (M type_write_continue)) -cne "WRITE") { Die (M cancelled_nothing_written) }
  } else {
    Write-Host (M adding_google_services_installer_2 "$P6Gapps" "$mb" "$name")
    if ((State-Get step_backup) -ne "done" -and (Ask (M make_backup_apps_files_first))) { Cmd-Backup }
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_written) }
  }
  # 1. the fill script's own checks on the projector, then the 'off' file (the next starts leave the partition alone)
  P6-Gapps-Push
  $o = Gapps-Sh pre $zsha (Get-Item -LiteralPath $zip).Length
  if (-not $o.StartsWith("OK pre ")) { Adb shell rm -rf $GappsDir | Out-Null; Die ((M projector_not_ready) + ": " + ($o -replace "^FAIL ", "") + ". " + (M nothing_written)) }
  $o = Gapps-Sh off $zsha
  Adb shell rm -rf $GappsDir | Out-Null
  if (-not $o.StartsWith("OK off")) { Die ((M projector_not_ready) + ": " + ($o -replace "^FAIL ", "") + ". " + (M nothing_written)) }
  # 2. fastboot mode: the partition
  $gv = P6-To-Fastboot
  $free = P6-Super-Free $gv; $cur = Gv-Size $gv $P6Gapps; if ($null -eq $cur) { $cur = [int64]0 }
  if (($free + [int64][math]::Ceiling($cur / 1MB) * 1MB) -lt $part) { P6-Fb-Stop (M not_enough_free_space_super_3 "$([math]::Truncate(($free + $cur) / 1MB))" "$mb") }
  if ($cur -eq 0) {
    Say (M creating_mb "$P6Gapps" "$mb")
    if (-not (Fb-Do create $P6Gapps $part)) { P6-Fb-Stop (M could_not_created_2 "$P6Gapps") }
  } elseif ($cur -ne $part) {
    Say (M resizing_mb "$P6Gapps" "$mb")
    if (-not (Fb-Do resize $P6Gapps $part)) { P6-Fb-Stop (M could_not_resized "$P6Gapps") }
  } else { Ok (M there_mb "$P6Gapps" "$mb") }
  Fb reboot | Out-Null
  P6-Cable-Out
  # 3. filled over Wi-Fi as root
  P6-Wait-Lumen (M still_empty_lumen_os_starts "$P6Gapps")
  P6-Root-Or-Die
  if ((Prop sys.lumen.gapps) -ne "off") { Die (M projector_did_not_leave_unmounted "$P6Gapps" "$(Prop sys.lumen.gapps)") }
  P6-Gapps-Push
  Say (M copying_projector_over_wi_fi "$name")
  Adb push $zip "$GappsDir/gapps.zip" | Out-Null
  if ($LASTEXITCODE -ne 0) { Adb shell rm -rf $GappsDir | Out-Null; Die (M adb_push_zip_failed_lumen) }
  Say (M formatting_copying_google_apps "$P6Gapps")
  $o = Gapps-Sh fill $zsha
  Adb shell rm -rf $GappsDir | Out-Null
  if (-not $o.StartsWith("OK fill ")) { Die ((M google_services_were_not_added) + ": " + ($o -replace "^FAIL ", "") + ". " + (M lumen_os_starts_without_them_2)) }
  Ok "${P6Gapps}: $($o.Substring(8)) $(M of) $name"
  State-Set gapps $name
  # 4. the wipe, so that the first start sets up Google services
  if ($KeepData) {
    Say (M restarting)
    Adb reboot | Out-Null
    for ($i = 0; (P6-Alive) -and $i -lt 60; $i += 5) { Start-Sleep 5 }   # until it goes away
    Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
    P6-Wait-Lumen ""
    $v = Prop sys.lumen.gapps
    if ($v -like "mounted:*") { Ok (M google_services_sign_settings) }
    else { Warn (M projector_not_use_google_services "$v") }
  } else {
    P6-Gapps-Wipe
    Ok (M done_join_wi_fi_projector "$nm")
  }
  State-Set step_gapps done
}
function Play6-Gapps-Remove {
  Check-Tools
  if (-not (Play6-Adb 60)) { Die (P6-NoConn) }
  Play6-Device
  P6-Lumen-Only
  $v = Prop sys.lumen.gapps
  if (-not $v -or $v -eq "none") { Ok (M projector_has_no_nothing_remove "$P6Gapps"); State-Set gapps ""; return }
  Write-Host ""
  if ($KeepData) {
    Write-Host (M removing_google_services_added_3 "$P6Gapps")
    if ((Read-Host (M type_write_continue)) -cne "WRITE") { Die (M cancelled_nothing_written) }
  } else {
    Write-Host (M removing_google_services_added_4 "$P6Gapps")
    if ((Read-Host (M type_erase_continue)) -cne "ERASE") { Die (M cancelled_nothing_written) }
  }
  $gv = P6-To-Fastboot
  $removed = $false
  if ($null -ne (Gv-Size $gv $P6Gapps)) {
    if (-not (Fb-Do delete $P6Gapps)) { P6-Fb-Stop (M could_not_deleted "$P6Gapps") }
    $removed = $true; Ok (M deleted "$P6Gapps")
  } else { Ok (M no_super_nothing_remove "$P6Gapps") }
  Fb reboot | Out-Null
  P6-Cable-Out
  State-Set gapps ""
  if ($removed -and -not $KeepData) { P6-Wait-Lumen ""; P6-Gapps-Wipe }
  Ok (M projector_starts_lumen_os_without)
}
function Play6-Gapps-Status {
  $nm = P6-Name
  if (-not (Play6-Adb 60)) { Die (P6-NoConn) }
  Play6-Device
  P6-Lumen-Only
  $v = Prop sys.lumen.gapps
  if ($v -like "mounted:*") { Ok ((M google_services_added) + " ($(Prop ro.com.google.gmsversion))") }
  elseif ($v -eq "none") {
    if (Prop ro.com.google.gmsversion) { Ok (M lumen_os_has_google_services) }
    else { Say (M no_google_services_added_add_2 "$nm") }
  }
  elseif ($v -eq "off") { Warn (M google_services_off_gapps_zip) }
  elseif (-not $v) { Say (M lumen_os_has_no_google_2) }
  else { Warn (M google_services_added_cannot_used "$v") }
}
function Play6-Gapps {
  if ($Zip -and $Remove) { Die "gapps: -Zip or -Remove, not both" }
  if ($Remove) { Play6-Gapps-Remove } elseif ($Zip) { Play6-Gapps-Install } else { Play6-Gapps-Status }
}
function Play6-Gsf {
  $nm = P6-Name
  Check-Tools
  if (-not (Play6-Adb 60)) { Die (P6-NoConn) }
  Play6-Device
  P6-Lumen-Only
  if (-not ((Adb shell pm path com.google.android.gsf) | Out-String).Trim()) {
    Ok (M lumen_os_has_no_google)
    Say (M add_them_bash_lumen_install_2 "$nm")
    return
  }
  P6-Root-Or-Die
  $id = ((Adb shell "sqlite3 /data/data/com.google.android.gsf/databases/gservices.db `"select value from main where name='android_id';`"") | Out-String).Trim()
  if (-not $id) { Die (M no_gsf_id_yet_connect) }
  Write-Host "GSF ID: $id"
  Write-Host (M register_https_www_google_com)
}
# ------------------------------------------------------------------ XGIMI Play 6: Lumen OS and Google services in one pass (-GappsZip FILE)
# See lumen-install.sh, same section: the zip checked first (P6-Gapps-Plan); the write's fastboot session counts the
# image and lumen_gapps together (P6-Room), writes system, then creates or resizes lumen_gapps (P6-Gapps-Add; a
# failure leaves Google out); the first start on the old data, then P6-Gapps-Fill-Step (adb root with the Rooted
# debugging pause, a lumen_gapps still in use turned off with one restart, gapps_fill.sh pre / zip / fill), then
# Play6-Wipe. State: gapps_plan, step_gapps_part, step_gapps_fill.
$script:P6gSha = ""; $script:P6gName = ""; $script:P6gPart = [int64]0; $script:P6gZip = ""
function P6-Plan-Part {  # lumen_gapps bytes of the saved plan ("" without one)
  $sha = State-Get gapps_plan
  if (-not $sha) { return "" }
  return (Gapps-Field (P6-Gapps-Line $sha) "part")
}
function P6-Gapps-Plan {  # -GappsZip, before anything else: the zip, and that the install saved here can take it
  $nm = P6-Name
  if (-not (Test-Path -LiteralPath $GappsZip)) { Die (M no_file "$GappsZip") }
  $script:P6gZip = (Resolve-Path -LiteralPath $GappsZip).Path
  foreach ($f in @("gapps_allow.txt", "gapps_fill.sh", "lumen-gapps.xml")) { if (-not (Test-Path (P6-Gapps-Lib $f))) { Die "internal: lib\play6\$f is missing: re-download the installer of the $nm release" } }
  $zname = Split-Path -Leaf $script:P6gZip
  Say (M checking "$zname")
  $script:P6gSha = Sha256 $script:P6gZip
  $zl = P6-Gapps-Line $script:P6gSha
  if (-not $zl) { Die ((M not_mindthegapps_file_installer_2 "$zname" "$nm" "$($script:P6gSha.Substring(0,16))") + ". " + (M nothing_written)) }
  $script:P6gName = ("$zl" -split " ")[1]; $script:P6gPart = [int64](Gapps-Field $zl "part")
  if ((Sha256 (P6-Gapps-Lf "lumen-gapps.xml")) -ne (Gapps-Field $zl "sysconfig")) { Die "internal: lib\play6\lumen-gapps.xml is not the allow-listed one: re-download the installer" }
  Ok "$($script:P6gName), sha256 $($script:P6gSha.Substring(0,16))..."
  if ((State-Get step_flash) -ne "done") { return }
  $plan = State-Get gapps_plan
  if (State-Get step_wipe) {
    if ((State-Get step_gapps_fill) -ne "done") { Die (M lumen_os_installed_here_without_2) }
  } elseif (-not $plan) {
    Die (M install_started_without_google)
  } elseif ($plan -ne $script:P6gSha) {
    Die (M install_started_another)
  }
}
function P6-Gapps-Add {  # fastboot mode, system written: lumen_gapps for the saved plan; a failure leaves Google out (warned)
  $nm = P6-Name
  $pt = P6-Plan-Part
  $gv = Fb getvar all   # system has its new size now
  $why = "internal: lib\play6\gapps_allow.txt part="
  if ($pt) {
    $part = [int64]$pt; $mb = [int64][math]::Floor($part / 1MB)
    $free = P6-Super-Free $gv; $cur = Gv-Size $gv $P6Gapps; if ($null -eq $cur) { $cur = [int64]0 }
    if (($free + [int64][math]::Ceiling($cur / 1MB) * 1MB) -lt $part) {
      $why = M not_enough_free_space_super_3 "$([math]::Truncate(($free + $cur) / 1MB))" "$mb"
    } elseif ($cur -eq 0) {
      Say (M creating_mb "$P6Gapps" "$mb")
      if (Fb-Do create $P6Gapps $part) { State-Set step_gapps_part done; return }
      $why = M could_not_created_2 "$P6Gapps"
    } elseif ($cur -ne $part) {
      Say (M resizing_mb "$P6Gapps" "$mb")
      if (Fb-Do resize $P6Gapps $part) { State-Set step_gapps_part done; return }
      $why = M could_not_resized "$P6Gapps"
    } else { Ok (M there_mb "$P6Gapps" "$mb"); State-Set step_gapps_part done; return }
  }
  Warn ($why + " " + (M lumen_os_installed_without_google "$nm"))
  State-Set gapps_plan ""
}
function P6-Root-Wait {  # adb root over Wi-Fi; Rooted debugging off: waits for the user; else STOP (the next run continues here)
  while ($true) {
    $rc = P6-Root
    if ($rc -eq 0) { return }
    if ($rc -eq 3) { if (-not (Root-Setting-Wait)) { Die (M turn_rooted_debugging_settings_2 "$(P6-Ne)") }; continue }
    if ($rc -eq 2) { Die (M after_adb_root_projector_did_3 "$(P6-Ne)") }
    Die (M adb_root_not_possible_system_3 "$(P6-Ne)")
  }
}
function P6-Gapps-Fill-Step {  # on the first start (old data): lumen_gapps filled as root; Google left out: plan cleared
  $nm = P6-Name
  $again = M run_same_command_again_continues_2 "$(P6-Ne)"
  if (-not $GappsZip) {
    Say (M install_started_google_services)
    if (-not (Ask (M finish_install_without_google_2))) { Die (M run_same_command_again_gapps_2 "$(P6-Ne)") }
    State-Set gapps_plan ""; return
  }
  if (-not $script:AdbT) { $script:AdbT = State-Get ip }
  if (-not (Wifi-Connect 120)) { Die ((P6-NoConn 120) + " " + (P6-Ne)) }
  P6-Identity
  if ((Prop (P6-LProp)) -cne (P6-LVal)) { Die (M projector_not_run_lumen_os_4 "$nm" "$(P6-Ne)") }
  $v = Prop sys.lumen.gapps
  if (-not $v -or $v -eq "none") {
    Warn (M lumen_os_shows_no_sys "$P6Gapps" "$v")
    State-Set gapps_plan ""; return
  }
  P6-Root-Wait
  if ((Prop sys.lumen.gapps) -like "mounted:*") {
    # Google from an earlier install is in use: the 'off' file, one restart, then the partition is free to format
    Say (M google_services_earlier_install)
    P6-Gapps-Push
    $o = Gapps-Sh off $script:P6gSha
    Adb shell rm -rf $GappsDir | Out-Null
    if (-not $o.StartsWith("OK off")) { Die ((M projector_not_ready) + ": " + ($o -replace "^FAIL ", "") + ". " + (P6-Ne)) }
    Adb reboot | Out-Null
    for ($i = 0; (P6-Alive) -and $i -lt 60; $i += 5) { Start-Sleep 5 }   # until it goes away
    Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
    P6-Wait-Lumen (P6-Ne)
    P6-Root-Wait
    if ((Prop sys.lumen.gapps) -ne "off") { Die (M projector_did_not_leave_unmounted_2 "$P6Gapps" "$(Prop sys.lumen.gapps)" "$(P6-Ne)") }
  }
  P6-Gapps-Push
  $o = Gapps-Sh pre $script:P6gSha (Get-Item -LiteralPath $script:P6gZip).Length
  if (-not $o.StartsWith("OK pre ")) { Adb shell rm -rf $GappsDir | Out-Null; Die ((M projector_not_ready) + ": " + ($o -replace "^FAIL ", "") + ". " + (P6-Ne)) }
  Say (M copying_projector_over_wi_fi "$($script:P6gName)")
  Adb push $script:P6gZip "$GappsDir/gapps.zip" | Out-Null
  if ($LASTEXITCODE -ne 0) { Adb shell rm -rf $GappsDir | Out-Null; Die ((M adb_push_zip_failed) + $again) }
  Say (M formatting_copying_google_apps "$P6Gapps")
  $o = Gapps-Sh fill $script:P6gSha
  Adb shell rm -rf $GappsDir | Out-Null
  if (-not $o.StartsWith("OK fill ")) { Die ((M google_services_were_not_added) + ": " + ($o -replace "^FAIL ", "") + ". " + $again) }
  Ok "${P6Gapps}: $($o.Substring(8)) $(M of) $($script:P6gName)"
  State-Set gapps $script:P6gName
  State-Set step_gapps_fill done
}


# ------------------------------------------------------------------ projectors with FLOW=ab-fastbootd (see lumen-install.sh, same section)
# A virtual A/B projector whose stock Android has adb over the network only (port 5555; Developer options > USB
# debugging opens it, stock asks for no allow prompt) and whose fastbootd turns the one USB port into a device port:
# the A-to-A cable goes in when the installer asks, right before 'adb reboot fastboot' (Play6-Adb's way of
# connecting: $script:AdbT = ADDR:PORT, $script:Serial = ro.serialno, the fastboot serial). What the projector IS
# comes from its profile ($script:Prof); what may be written is fixed in Fb-Allowed: z9x_blobs<slot> (create,
# resize, flash) and system<slot> of the running slot when SLOTS has it, then 'fastboot format:<WIPE_FS>' of
# WIPE_PARTS (fastbootd reports them as raw, so the type is always given: ext4 made by mke2fs of the Platform-Tools,
# or f2fs by its make_f2fs, with casefold and projid for userdata as on the Z9X), and with
# GAPPS_ALLOW z9x_gapps<slot> (create, resize, flash, delete: the Google services add-on). Never vbmeta (the user
# types that step: 'vbmeta'), never -w, erase, another delete, --slot, set_active, a KEEP partition or the
# other slot. The slot is the one the system runs from and fastbootd reports (a or b: after a Lumen OS update it is
# the other one). fastbootd has no idle timeout, so one visit writes and wipes. A staged or merging update (stock or
# Lumen OS) shows in fastbootd (snapshot-update-status, *-cow): 'flash' stops there, nothing written, so userdata and
# metadata are never formatted while one exists. Steps: check -> backup (asked) -> vbmeta (asked) -> blobs -> flash;
# verify later, after the setup with debugging on again. Progress: $StateDir\<id>\state (step_system: started |
# done, so a write cut off in the middle is never followed by a restart that would start half a system; step_zblobs:
# z9x_blobs written in this visit, so a resumed run needs no blobs file). The image content is read with a
# dump.erofs when there is one (tools\erofs-utils, -ErofsUtils, or PATH); Windows has none of its own yet, and then
# the name and the signed SHA256SUMS bind the image.
$script:AbfS = ""         # the slot Abf-Fbd-Check found running in fastbootd (_a, _b); Fb-Allowed writes only there
$script:AbfGv = @()       # its 'getvar all'
$script:AbfUp = $false    # adb reached the projector in this run (Abf-Adb)
$script:AbfDump = ""      # dump.erofs that reads the image ("" = none on this computer)
$script:AbfResume = $false   # Abf-Write continues a visit in fastboot mode: an image STOP goes through Abf-Fb-Stop
$script:AbfGapps = $false    # this run is 'gapps' (its own fastbootd visit): a STOP there goes by AbfGw
$script:AbfGw = $false       # 'gapps' changed z9x_gapps in this visit (a STOP then keeps it in fastboot mode)
$script:AbfGneed = [int64]0  # bytes of super the add-on of this write still needs (-GappsZip; Abf-Room counts them)

function Abf-Other($sl) { if ($sl -ceq "a") { "b" } else { "a" } }   # slot -> the other one (slot-count is 2); the slot checks of SLOT_CHECK=yes
function Abf-On { return ($script:Prof["FLOW"] -ceq "ab-fastbootd") }           # the projector of this run is one
function Abf-List($k) { return @("$($script:Prof[$k])" -split " " | Where-Object { $_ }) }   # a list key of the profile
function Abf-Path($rel) { return (Join-Path $Here ($rel -replace '/', [string][IO.Path]::DirectorySeparatorChar)) }   # a profile path in the installer folder
function Abf-MiB($n) { return [int64]([math]::Floor(([int64]$n + 1048575) / 1048576)) * 1048576 }   # rounded up to 1 MiB
function Abf-Refuse-Opts {  # options of the Z9X and Play 6 ways that this flow does not have
  if ($KeepData) { Not-Yet "-KeepData" }
  if ($Wipe -ne "format") { Not-Yet "-Wipe $Wipe" }
  if ($GappsZip -and -not (Abf-Gapps-On)) { Not-Yet "-GappsZip" }
}
function Abf-Gapps-On { return [bool]$script:Prof["GAPPS_ALLOW"] }   # the profile has the Google services add-on (GAPPS_ALLOW, GAPPS_IMAGES)
function Abf-Hint { return (M if_hangs_cable_pull_cable) }
function Abf-Dot($m) { if ($m -match '[.!?]$') { $m } else { $m + "." } }   # a message that another sentence follows ends in a full stop
function Abf-Fb-Stop($m) {  # message: leave fastbootd for the old system (nothing was written), or stay once system was begun
  $m = Abf-Dot $m
  if ($script:AbfGapps) {   # 'gapps': its own visit decides, the install's steps are not this visit's
    if ($script:AbfGw) { Die ($m + " " + (M z9x_gapps_changed_visit_projector)) }
    State-Set gapps_fastboot ""
    Fb reboot | Out-Null
    Die ($m + " " + (M nothing_written_projector_starts) + " " + (Abf-Hint))
  }
  $s = State-Get step_system   # a restart now would start half a system, or Lumen OS on the old data
  if ($s -eq "started" -or $s -eq "done") { Die ($m + " " + (M write_install_not_finished)) }
  # this visit is over: a later fastboot mode (entered by hand) is never taken for it, so no run skips the checks
  State-Set step_fastboot ""
  Fb reboot | Out-Null
  Die ($m + " " + (M nothing_written_projector_starts_2) + " " + (Abf-Hint))
}
function Abf-Img-Stop($m, [switch]$Nw) {  # message [-Nw = it ends in 'Nothing was written.']: an image STOP, in a fastboot visit by Abf-Fb-Stop
  if ($script:AbfResume) { Abf-Fb-Stop $m }
  if ($Nw) { Die ((Abf-Dot $m) + " " + (M nothing_written)) }
  Die $m
}

function Abf-Other-Hint($model, $brd) {  # another projector this installer knows, and its way in
  $id = Profile-Of $model $brd
  if (-not $id) { return "" }
  $fl = Profile-Flow $id
  if ($fl -ceq "z9x-usb") { return " " + (M xgimi_z9x_installs_over_usb) }
  if ($fl -ceq "play6-wifi") { return " " + (M installs_installer_own_release "$(Dev-Name $id)") }
  if (Devconf-Todo $id) { return " " + (M lumen_os_not_support_yet "$(Dev-Name $id)") }   # its profile is not finished (TODO): no way in yet
  return " " + (M installs_installer_own_release_2 "$(Dev-Name $id)" "$id")
}
function Abf-Identity {  # the device at AdbT: this projector model, and the saved one (DHCP may give its address away)
  $nm = P6-Name; $t = $script:AdbT
  $code = Prop ro.boot.xgimi.modelname; $brd = Prop ro.boot.hardware; $ser = Prop ro.serialno
  if (-not (Abf-Is-Model $code) -or $brd -cne $script:Prof["BOARD"]) {
    Pt-Adb disconnect $t 2>&1 | Out-Null
    # a model code of this projector that is not supported yet (MODELS_NOT_YET): named as such, never 'not an HB'
    if ($brd -ceq $script:Prof["BOARD"] -and $code -and @("$($script:Prof['MODELS_NOT_YET'])" -split ' ') -ccontains $code) {
      Die (M model_not_supported_yet_lumen "$nm" "$code" "$(Abf-Matrix)")
    }
    Die ((M not_model_board_nothing_done "$nm" "$code" "$brd") + (Abf-Other-Hint $code $brd))
  }
  if (-not $ser) { Die (M projector_reports_no_serial_number "$t") }
  if ($script:Serial -and $script:Serial -cne $ser) {
    Pt-Adb disconnect $t 2>&1 | Out-Null
    $saved = $script:Serial; $id = $script:Projector
    if ($script:SerialFromState) { Die (M projector_has_serial_but_install_2 "$t" "$ser" "$saved" "$id") }
    Die (M projector_has_serial_not_serial "$t" "$ser" "$saved")
  }
  $script:Serial = $ser; $script:AbfModel = $code
  if ($script:Prof["MODELS_OK"]) { Abf-Revision $code }
}
function Abf-Revision($model) {  # its stock really is that model's (MODELS_OK profiles; read only): the stock
  # ro.product.device (not asked on a running Lumen OS of this projector, whose system names the device itself), the
  # stock product name of that model (another one = the firmware of another model: a cross-flashed unit) and the
  # stock config folder of that model (MODEL_CONFIG/<model>/system.ini). The shell may list that folder but not stat
  # its files (the stock SELinux policy: shell has dir access to mnt_xgimiconfig_file only; 'ls FILE' is denied), so
  # the folder is listed and system.ini must be one of its names
  $nm = P6-Name; $t = $script:AdbT
  $dev = Prop ro.product.device; $want = Pair-Get $script:Prof["PRODUCT_NAMES"] $model
  if ((Prop (P6-LProp)) -cne (P6-LVal) -and $dev -cne $script:Prof["DEVICE"]) {
    Pt-Adb disconnect $t 2>&1 | Out-Null
    Die (M not_stock_reports_device_not "$nm" "$dev" "$($script:Prof['DEVICE'])")
  }
  $prod = Prop ro.vendor.xgimi.product.name
  if (-not $prod) {
    Warn (M projector_reports_no_stock_product "$want")
  } elseif ($prod -cne $want) {
    Pt-Adb disconnect $t 2>&1 | Out-Null
    Die (M model_reports_stock_product_not "$nm" "$model" "$prod" "$want")
  }
  $cfg = "$($script:Prof['MODEL_CONFIG'])/$model/system.ini"
  $got = @(Adb shell ls "$($script:Prof['MODEL_CONFIG'])/$model" 2>$null | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ceq "system.ini" }).Count
  if ($got -ne 1) {
    Pt-Adb disconnect $t 2>&1 | Out-Null
    Die (M xgimi_firmware_has_no_settings "$nm" "$model" "$cfg")
  }
}
function Abf-Adb($secs) {  # adb over the network (-Ip, the saved address, or mDNS) and who it is; $false = no answer
  if (-not $script:AdbT) {
    $a = State-Get ip
    if (-not $a) {
      $a = P6-Discover ""
      if (-not $a -or -not (Ask (M projector_answers_yours_one "$a"))) {
        $nm = P6-Name
        Die (M give_projectors_network_address "$nm")
      }
    }
    $script:AdbT = $a
  }
  Say (M connecting_over_network "$($script:AdbT)")
  if (-not (Wifi-Connect $secs)) { return $false }
  Abf-Identity
  $script:AbfUp = $true
  return $true
}
function Abf-Device {  # connected: firmware, slot, virtual A/B, stock or Lumen OS; saves serial, address, firmware, slot
  $nm = P6-Name
  $vend = Prop ro.vendor.build.version.incremental; $slot = Prop ro.boot.slot_suffix; $vab = Prop ro.virtual_ab.enabled
  $lum = Prop (P6-LProp); $sl = $slot.TrimStart('_'); $slots = Abf-List SLOTS
  # a projector that shares its XGIMI product image with another (FAMILY) could run that one's Lumen OS: it names
  # itself by the family's version property, never by ours
  if (-not $lum -and $script:Prof["FAMILY"]) {
    $fv = Devconf-Get $script:Prof["FAMILY"] "VERSION_PROP"
    if ($fv -and $fv -cne $script:Prof["VERSION_PROP"] -and (Prop $fv)) { $lum = $script:Prof["FAMILY"] }
  }
  if ($script:Prof["MODELS_OK"]) {
    # the model x firmware table: this model's own list; a firmware only other models of this projector run is the
    # firmware of another model on it (a cross-flash), and the way out is its own stock, never Lumen OS
    $am = $script:AbfModel; $fv = @(Abf-Model-Fw $am)
    if ($fv -cnotcontains $vend) {
      if ((Abf-List VENDOR_OK) -ccontains $vend) {
        Die (M xgimi_firmware_not_firmware_model "$vend" "$nm" "$am" "$(Abf-Fw-Names $fv)" "$(Abf-Matrix)" "$(Abf-Fw-Names @($fv[0]))")
      }
      Die (M xgimi_firmware_not_supported_lumen_2 "$vend" "$nm" "$am" "$(Abf-Fw-Names $fv)")
    }
  } elseif (-not (Is-Todo $script:Prof["VENDOR_OK"]) -and (Abf-List VENDOR_OK) -cnotcontains $vend) {   # TODO: not known yet, Abf-Check stops after its reads
    Die (M xgimi_firmware_not_supported_lumen_3 "$vend" "$nm" "$(P6-Fw-List)")
  }
  if ($slot -cnotmatch '^_.$') { Die (M projector_reports_no_system_slot "$slot" "$nm") }
  if ($slots -cnotcontains $sl) { Die (M runs_slot_lumen_os_installs "$nm" "$sl" "$($script:Prof['SLOTS'])") }
  if ($vab -cne "true") { Die (M projector_has_no_virtual_b "$vab" "$nm") }
  if (-not $lum) { State-Set dev_lumen 0; State-Set dev_version ""; $ver = M xgimi_stock }
  elseif ($lum -ceq (P6-LVal)) { $v = Prop $script:Prof['VERSION_PROP']; State-Set dev_lumen 1; State-Set dev_version $v; $ver = "Lumen OS $v" }
  else { Die (M projector_runs_lumen_os_another "$lum") }
  State-Set serial $script:Serial; State-Set ip $script:AdbT; State-Set vendor $vend; State-Set slot $slot
  Ok "$nm $($script:Serial) ($($script:AdbT)), $(M firmware) $vend, $(M slot) $sl, $(M system): $ver"
}

# The vbmeta step, read only: with verification off first-stage init sets up no dm-verity, so /dev/block/mapper
# has no '*-verity' device (stock with its own vbmeta shows system-verity, vendor-verity...). 'check' reports it;
# 'flash' stops without it (a system written over a verified slot fails dm-verity at every start).
function Abf-Vbmeta-State {  # 0 = verification off on the running slot, 1 = on, 2 = could not read
  $m = @(Adb shell ls /dev/block/mapper/ | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
  if ($m.Count -eq 0) { return 2 }
  if (@($m | Where-Object { $_ -cmatch '-verity$' }).Count -gt 0) { return 1 }
  return 0
}
function Abf-Vbmeta-Check($mode) {  # check|flash
  $s = (State-Get slot).TrimStart('_'); $st = Abf-Vbmeta-State
  if ($st -eq 0) { Ok (M vbmeta_verification_off_slot_no_2 "$s"); State-Set step_vbmeta done }
  elseif ($st -eq 1) {
    State-Set step_vbmeta ""
    if ($mode -eq "flash") { Die (M running_slot_still_verifies_system_2 "$s") }
    Warn (M vbmeta_slot_still_verifies_system "$s")
  } else {
    if ($mode -eq "flash") { Die (M could_not_read_dev_block_2) }
    Warn (M could_not_read_dev_block)
  }
}
function Abf-Vbmeta {  # the one manual step; the installer never runs any vbmeta command
  $nm = P6-Name; $a = if ($script:AdbT) { $script:AdbT } else { State-Get ip }
  if (-not $a) { $a = "ADDRESS:5555" }
  $h = Abf-Hint
  Write-Host ""
  Write-Host (M one_manual_step_vbmeta_type "$nm" "$a" "$h")
  Write-Host "  Google: $GoogleGsi"
  Write-Host "  $(M guide): $(P6-Guide)#vbmeta"
  Write-Host ""
  if (Ask (M have_done_vbmeta_step_did_2)) { State-Set step_vbmeta done }
  else { Die (M vbmeta_step_first_run_installer_2) }
}

function Abf-Image-Props {  # the image's properties in the order init reads them (a later file wins: product is read last)
  foreach ($p in @("/system/etc/prop.default", "/system/build.prop", "/system/system_ext/etc/build.prop", "/system/product/etc/build.prop")) {
    & $script:AbfDump --cat "--path=$p" $script:Image 2>$null
  }
}
function Abf-Image-Has($p) {  # $true = the image holds that file with content
  $l = @(& $script:AbfDump "--path=$p" $script:Image 2>$null | Where-Object { "$_" -match '^Size: ([0-9]+)' } | Select-Object -First 1)
  return ($l.Count -gt 0 -and "$($l[0])" -match '^Size: ([0-9]+)' -and [int64]$Matches[1] -gt 0)
}
function Abf-Dump-Find {  # dump.erofs: the installer's own (tools\erofs-utils, checked, or -ErofsUtils), else one on PATH
  if ($script:AbfDump) { return }
  if (-not $script:OnWindows) {   # pwsh on macOS or Linux: our erofs-utils are Windows builds; a plain dump.erofs in -ErofsUtils DIR
    $f = if ($ErofsUtils) { Join-Path $ErofsUtils "dump.erofs" } else { "" }
    if ($f -and (Test-Path -LiteralPath $f -PathType Leaf)) { $script:AbfDump = (Resolve-Path -LiteralPath $f).Path; return }
  } else {
    $plat = Eu-Plat
    if ($ErofsUtils -or ($plat -and (Test-Path -LiteralPath (Join-Path (Join-Path (Join-Path $Here "tools") "erofs-utils") $plat)))) { Eu-Setup; $script:AbfDump = Join-Path $script:EuDir "dump.erofs.exe"; return }
  }
  $c = Get-Command dump.erofs -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1; if ($c) { $script:AbfDump = $c.Path }
}
function Abf-Check-Image([switch]$Force) {  # the release image of this projector: name, erofs, size, signature, checksum, content
  Find-Image
  $img = $script:Image; $name = Split-Path -Leaf $img; $dir = Split-Path -Parent $img; $nm = P6-Name; $tok = $script:Prof["IMAGE_TOKEN"]
  $sums = Join-Path $dir "SHA256SUMS"; $sig = "$sums.sig"; $cert = Abf-Path $script:Prof["CERT"]
  $sysmax = -not (Is-Todo $script:Prof["SYSTEM_MAX"]); $max = if ($sysmax) { [int64]$script:Prof["SYSTEM_MAX"] } else { [int64]0 }
  if (-not $name.Contains($tok)) { Abf-Img-Stop (M not_image_name_must_contain_2 "$name" "$nm" "$tok") -Nw }
  if ($name -cmatch "PRIVATE") { Abf-Img-Stop (M private_image_never_installed_2 "$name" "$nm") -Nw }
  $nv = ""
  if (-not $UnsignedImage) {
    if ($name -notlike "*-nogms-*") { Abf-Img-Stop (M not_release_file_has_lumen_2 "$name" "$nm") -Nw }
    # the release version is in the name, and the signed SHA256SUMS lists the image under that name
    if (-not $conf["VERSION"]) { Die "internal: release.conf has no VERSION: re-download the installer of the $nm release" }
    if ($name -cmatch "^lumen-os-$([regex]::Escape($tok))-(.+)-nogms-system\.img$") { $nv = $Matches[1] }
    if (-not $nv) { Abf-Img-Stop (M not_named_like_release_file_2 "$name" "$tok") -Nw }
    if ($nv -cne $conf["VERSION"]) { Abf-Img-Stop (M lumen_os_but_installer_download_2 "$name" "$nv" "$($conf['VERSION'])" "$nm") -Nw }
  }
  # the file system, read without any tool: erofs has e2 e1 f5 e0 at byte 1024 (the fstab mounts nothing else)
  if ((Read-Hex $img 1024 4) -ne "e2e1f5e0") {
    if ((Read-Hex $img 1080 2) -eq "53ef") { Abf-Img-Stop (M ext4_image_another_projectors_not "$name" "$nm") -Nw }
    if ((Read-Hex $img 0 4) -eq "3aff26ed") { Abf-Img-Stop (M sparse_image_not_release_file_2 "$name") -Nw }
    Abf-Img-Stop (M not_erofs_system_image "$name") -Nw
  }
  $len = (Get-Item -LiteralPath $img).Length
  if ($len % 4096 -ne 0) { Abf-Img-Stop (M has_size_not_multiple_4096_2 "$name") -Nw }
  # with blobs the new z9x_blobs and the reserve of super come off the budget too (as Abf-Room counts them later);
  # SYSTEM_MAX TODO: the budget is not known yet (the profile is not finished, Abf-Check stops after its reads)
  if ($sysmax) {
    if ($Blobs -eq "partition") { $max = $max - [int64]$BlobSize - [int64]$script:Prof["SUPER_RESERVE"] }
    if ($len -gt $max) { Abf-Img-Stop (M too_big_mb_most_mb "$name" "$nm" "$([math]::Ceiling($len / 1MB))" "$([math]::Truncate($max / 1MB))") -Nw }
    # with -GappsZip the add-on image (rounded up to 1 MiB) comes off it as well, as Abf-Room counts it in super
    $g = Abf-Gapps-Need
    if ($g -gt 0 -and ((Abf-MiB $len) + $g) -gt $max) { Abf-Img-Stop (M google_services_not_fit_together "$name" "$nm" "$([math]::Ceiling($len / 1MB))" "$([math]::Truncate($g / 1MB))" "$([math]::Truncate($max / 1MB))") -Nw }
  }
  if ((Test-Path -LiteralPath $sums) -and (Test-Path -LiteralPath $sig) -and (Test-Path -LiteralPath $cert)) {
    if (-not (Verify-Sig $sums $sig $cert)) { Abf-Img-Stop (M sha256sums_not_signed_lumen_os_2 "$nm") }
    Ok (M sha256sums_signature_lumen_os_key "$nm")
  } elseif ($UnsignedImage) {
    Warn (M unsigned_image_image_not)
    if ((State-Get unsigned_ok) -ne "${name}:$(File-Key $img)") {
      if ((Read-Host (M type_unsigned_install_anyway "$name")) -cne "UNSIGNED") {
        if ($script:AbfResume) { Abf-Fb-Stop (M cancelled) }
        Die (M cancelled_nothing_written)
      }
      State-Set unsigned_ok "${name}:$(File-Key $img)"
    }
    if (-not (Test-Path -LiteralPath $sums)) {   # a list that names the image, or the image's own .sha256
      $sums = $null
      $cand = @(Get-ChildItem -Path (Join-Path $dir "SHA256SUMS*") -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*.sig" }) + @(Get-Item -LiteralPath "$img.sha256" -ErrorAction SilentlyContinue)
      foreach ($f in ($cand | Where-Object { $_ })) {
        if (Get-Content -LiteralPath $f.FullName | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))$" }) { $sums = $f.FullName; break }
      }
    }
  } else {
    if (-not (Test-Path -LiteralPath $sums)) { Abf-Img-Stop (M no_sha256sums_next_image_cannot) }
    if (-not (Test-Path -LiteralPath $sig)) { Abf-Img-Stop (M no_sha256sums_sig_next_image) }
    Abf-Img-Stop (M installer_certificate_missing_re "$($script:Prof['CERT'])" "$nm")
  }
  if ($sums -and (Test-Path -LiteralPath $sums)) {
    $want = (Get-Content -LiteralPath $sums | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))$" } | ForEach-Object { ($_ -split "\s+")[0] }) | Select-Object -First 1
    if (-not $want) { Abf-Img-Stop (M not_listed "$name" "$(Split-Path -Leaf $sums)") }
    if ($Force -or (State-Get image_ok) -ne "${want}:$(File-Key $img)") {
      Say (M checking_image_checksum_about)
      if ((Sha256 $img) -ne $want.ToLower()) { State-Set image_ok none; Abf-Img-Stop (M checksum_mismatch_image_damaged) }
      State-Set image_ok "${want}:$(File-Key $img)"
    }
    Ok "$name sha256 $($want.Substring(0,16))..."
  } else { Warn (M unsigned_image_without_any) }
  # what the image says about itself, when a dump.erofs is there; without one the name (its version, checked
  # above) and the signed SHA256SUMS (which lists the file under that name) bind the image to this release
  $bound = if ($nv) { M signed_sha256sums_lists_file_lumen "$nv" } else { M own_build_unsigned_image }
  Abf-Dump-Find
  if ($script:AbfDump) {
    $bp = @(Abf-Image-Props)
    if ($bp.Count -gt 0) {
      $pv = { param($k) $l = $bp | Where-Object { "$_" -like "$k=*" } | Select-Object -Last 1; if ($l) { "$l".Substring($k.Length + 1).Trim() } else { "" } }
      $v = & $pv (P6-LProp)
      if ($v -cne (P6-LVal)) { Abf-Img-Stop (M not_lumen_os "$name" "$nm" "$(P6-LProp)" "$v") -Nw }
      # ABI: a system of the other userspace never starts on this projector's stock vendor (an arm64 image on a
      # 32-bit-only vendor finds none of its 64-bit libraries); the image says it in ro.system.product.cpu.abilist
      if ($script:Prof["ABI"]) {
        $v = (& $pv "ro.system.product.cpu.abilist") -replace '\s', ''
        $a = if (",$v," -like "*,arm64-v8a,*") { "arm64" } elseif (",$v," -like "*,armeabi-v7a,*") { "arm32" } else { "" }
        if ($a -cne $script:Prof["ABI"]) { Abf-Img-Stop (M not_built_needs_system_image "$name" "$nm") -Nw }
      }
      $v = & $pv $script:Prof["VERSION_PROP"]; $sec = & $pv "ro.adb.secure"
      if (-not $UnsignedImage) {
        if ($v -cne $conf["VERSION"]) { Abf-Img-Stop (M holds_lumen_os_release "$name" "$v" "$($conf['VERSION'])") -Nw }
        if ($sec -ne "1") { Abf-Img-Stop (M test_build_ro_adb_secure_2 "$name" "$sec") -Nw }
      }
      # a public image holds none of the allow-list's files at /system/<path in the tar>: they come from 'blobs'
      # (BLOBS_ALLOW TODO: no list yet, the profile is not finished)
      $own = $false
      $allow = if (Is-Todo $script:Prof["BLOBS_ALLOW"]) { @() } else { @(Get-Content -LiteralPath (Abf-Path $script:Prof["BLOBS_ALLOW"])) }
      foreach ($line in $allow) {
        $f = @("$line".Trim() -split "\s+")
        if (-not $f[0] -or $f[0].StartsWith("#") -or $f[0].StartsWith("set=") -or $f.Count -lt 2) { continue }
        if (Abf-Image-Has "/system/$($f[1])") { $own = $true }
      }
      if ($own -and $Blobs -eq "partition") { Abf-Img-Stop (M carries_mediatek_xgimi_files "$name") }
      if (-not $own -and $Blobs -eq "embedded") { Abf-Img-Stop (M public_image_mediatek_xgimi_files "$name") }
      Ok (M image_lumen_os "$v" "$nm")
    } else { Warn (M dump_erofs_could_not_read "$bound") }
  } else { Say (M image_content_not_read_dump "$bound") }
  if ($Blobs -eq "none") { Warn (M no_blobs_no_sound_no_2) }
  State-Set image_gms 0
  Ok (M edition_lumen_os_without_google)
}
function Abf-Check {
  Abf-Refuse-Opts
  Check-Tools
  if (-not (Abf-Adb 60)) { Die (P6-NoConn) }
  Abf-Device
  Abf-Vbmeta-Check check
  $free = (Get-PSDrive -Name (Get-Item -LiteralPath $HOME).PSDrive.Name -ErrorAction SilentlyContinue).Free
  if ($free -and $free -lt 4GB) { Warn (M less_4_gb_free_computer) }
  Abf-Check-Image
  # MODELS_CHECK_ONLY: the check passes, but the model is not written yet (Abf-Live-Gate), so the check is never kept
  # as a step done ('all' checks again and stops there)
  if (Abf-Check-Only) { Warn (M model_supported_after_live_test "$(P6-Name)" "$($script:AbfModel)") }
  # not finished (TODO values): what could be read was read and nothing was written; the check never passes
  if ($script:Todo) { Abf-Todo-Stop }
  Ok (M check_passed_nothing_written)
  if (-not (Abf-Check-Only)) { State-Set step_check done }
}

# blobs: the files of the profile's allow-list (BLOBS_ALLOW, the image's /system/etc/z9x/blobs_allow.txt) pulled
# from this projector's own system, each checked by sha256 (or derived by its from= line deletes, Remove-Lines),
# into the ustar tar (Write-Ustar) of $BlobSize (4 MiB, or the profile's BLOBS_SIZE) that the image's blobs reader
# reads from z9x_blobs<slot>. need=eye lines (the eye-protection drawer of the image) are files like the others, but
# such a profile is never installed without them: no -NoBlobs, no -Blobs embedded (Abf-Main), and a file that does
# not match stops for good (lumen-install.sh abf_eye).
function Abf-Eye {  # $true = the profile's allow-list has need=eye lines
  $allow = Abf-Path $script:Prof["BLOBS_ALLOW"]
  if (-not (Test-Path -LiteralPath $allow)) { return $false }
  return [bool](@(Get-Content -LiteralPath $allow | Where-Object { "$_" -cmatch '^[0-9a-f]{64} \S+ \S+ (.* )?need=eye( |$)' }).Count)
}
function Abf-Blobs-File { if ($Blobs -eq "partition") { return (Join-Path $BackupRoot "$($script:Projector)-blobs-$($script:Serial).img") }; return "" }
function Abf-Blobs-Ok {  # $true = the blobs tar of this projector is ready (or none is needed)
  $f = Abf-Blobs-File
  if (-not $f) { return $true }
  return ((State-Get step_blobs) -eq "done" -and (State-Get blobs_img) -eq $f -and (Test-Path -LiteralPath $f) -and (Get-Item -LiteralPath $f).Length -eq $BlobSize)
}
function Abf-Blobs {
  if ($Blobs -eq "none") {
    Warn (M no_blobs_mediatek_xgimi_files)
    State-Set step_blobs skip; return
  }
  if ($Blobs -ne "partition") {
    Ok (M image_carries_mediatek_xgimi_files)
    State-Set step_blobs skip; return
  }
  if (-not $script:AbfUp -and -not (Abf-Adb 60)) { Die (P6-NoConn) }
  $allow = Abf-Path $script:Prof["BLOBS_ALLOW"]
  if (-not (Test-Path -LiteralPath $allow)) { Die "internal: $($script:Prof['BLOBS_ALLOW']) is missing: re-download the installer" }
  $w = Join-Path $script:SD "blobs"; Remove-Item -Recurse -Force -LiteralPath $w -ErrorAction SilentlyContinue
  $x = Join-Path $w "x"; New-Item -ItemType Directory -Force -Path $x | Out-Null
  $set = ((Get-Content -LiteralPath $allow | Where-Object { $_ -like "set=*" } | Select-Object -First 1) -replace "^set=", "")
  $manifest = @("set=$set"); $n = 0
  $fw = Prop ro.vendor.build.version.incremental
  foreach ($line in (Get-Content -LiteralPath $allow)) {
    $f = @("$line".Trim() -split "\s+")
    if (-not $f[0] -or $f[0].StartsWith("#") -or $f[0].StartsWith("set=")) { continue }
    $h = $f[0]; $rel = $f[1]; $src = $f[2]
    # from= may be given several times: one stock original per XGIMI firmware (each with its own line deletes);
    # the one whose sha256 the pulled file has is used, and the result must still be <sha256>
    $froms = @(); $alts = @()
    foreach ($o in ($f | Select-Object -Skip 3)) {
      # alt=<firmware>:<sha256>: that stock firmware's own copy, taken as it is only on that firmware (this
      # projector's ro.vendor.build.version.incremental); the image's reader checks it the same way
      if ($o -like "alt=*") { $alts += $o.Substring(4); continue }
      if ($o -notlike "from=*") { continue }
      $from = $o.Substring(5)
      if (-not $from.Contains(":")) { Die "internal: ${rel}: from= needs <sha256>:<deletes>" }
      if (($from -split ":", 2)[1] -notmatch '^[0-9,d;]*$') { Die "internal: ${rel}: from= may only delete lines" }
      $froms += $from
    }
    $dst = Join-Path $x ($rel -replace '/', [string][IO.Path]::DirectorySeparatorChar)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dst) | Out-Null
    $got = ""
    foreach ($p in @($src, "/system/$rel")) {
      Adb pull $p $dst | Out-Null
      if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dst)) { continue }
      $got = Sha256 $dst
      if ($got -eq $h) { break }
      foreach ($a in $alts) { if ($fw -and $a -ceq "${fw}:$got") { $h = $got } }
      if ($got -eq $h) { break }
      foreach ($from in $froms) {
        if ($got -ne ($from -split ":", 2)[0]) { continue }
        Remove-Lines $dst ($from -split ":", 2)[1]
        $got = Sha256 $dst
        break
      }
      if ($got -eq $h) { break }
      $got = "bad"
    }
    if ($got -ne $h -and (Abf-Eye)) { Die (M projector_not_match_allow_list_2 "$rel" "$got" "$(P6-Name)") }
    if ($got -ne $h) { Die (M projector_not_match_allow_list_3 "$rel" "$got") }
    $manifest += "$rel $h $((Get-Item -LiteralPath $dst).Length)"; $n++
  }
  [IO.File]::WriteAllText((Join-Path $x "MANIFEST"), (($manifest -join "`n") + "`n"))
  $img = Join-Path $w "z9x_blobs.img"
  Write-Ustar $x $img
  New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null
  $out = Abf-Blobs-File
  Copy-Item -LiteralPath $img -Destination $out -Force
  State-Set blobs_img $out; State-Set step_blobs done
  Ok (M mediatek_xgimi_files_projector_set "$n" "$set" "$out")
}

function Abf-Fbd-Check {  # fastbootd, one getvar all: this projector, its running slot and the known layout; else STOP
  $nm = P6-Name; $why = ""; $script:AbfS = ""
  $gv = @(Fb getvar all)
  # the HB fastbootd answers 'getvar slot-successful:<slot>' but may leave it out of 'getvar all' (2026-11-05, a
  # user's projector stopped with "slot-successful:a ''"): when 'all' names no slot's mark, ask each slot, read only
  if (-not @($gv | Where-Object { "$_" -like "(bootloader) slot-successful:*" }).Count) {
    foreach ($s in (Abf-List SLOTS)) {
      $l = @(Fb getvar "slot-successful:$s") | Where-Object { "$_" -like "slot-successful:${s}:*" } | Select-Object -First 1
      $v = if ($l) { ("$l".Substring(("slot-successful:${s}:").Length)).Trim() } else { "" }
      if ($v -ceq "yes" -or $v -ceq "no") { $gv = @($gv) + "(bootloader) slot-successful:${s}:$v" }
    }
  }
  $script:AbfGv = $gv
  $cs = Gv-Val $gv "current-slot"; $lum = ((State-Get dev_lumen) -eq "1")
  if ((Gv-Val $gv "is-userspace") -ne "yes") { $why = M not_fastbootd }
  elseif ((Gv-Val $gv "product") -cne $script:Prof["FB_PRODUCT"]) { $why = (M fastbootd_not_one "$nm") + " ($(Gv-Val $gv 'product'))" }
  elseif ((Gv-Val $gv "serialno") -cne $script:Serial) { $why = (M another_projector_fastboot_mode) + " (serialno $(Gv-Val $gv 'serialno'), $(M expected) $($script:Serial))" }
  elseif ((Gv-Val $gv "unlocked") -ne "yes") { $why = M bootloader_locked }
  elseif ((Abf-List SLOTS) -cnotcontains $cs) { $why = "current-slot '$cs'" }
  elseif ("_$cs" -cne (State-Get slot)) { $why = "current-slot $cs, $(M system_ran) $(State-Get slot)" }
  elseif ((Gv-Val $gv "slot-count") -ne "2") { $why = "slot-count '$(Gv-Val $gv 'slot-count')'" }
  elseif ((Gv-Val $gv "slot-successful:$cs") -ne "yes") { $why = "slot-successful:$cs '$(Gv-Val $gv "slot-successful:$cs")'" }
  # a Lumen OS update: it finishes only while Lumen OS runs
  elseif ($lum -and (Gv-Val $gv "snapshot-update-status") -ne "none") { $why = (M update_finishing) + " (snapshot-update-status $(Gv-Val $gv 'snapshot-update-status')): " + (M start_lumen_os_wait_10) }
  elseif ($lum -and @($gv | Where-Object { "$_" -cmatch ':[a-z0-9_]+-cow(:|$)' }).Count -gt 0) { $why = M cow_partitions_update_finishing }
  elseif ((Gv-Val $gv "snapshot-update-status") -ne "none") { $why = (M xgimi_update_staged_not_finished) + " (snapshot-update-status $(Gv-Val $gv 'snapshot-update-status')): " + (M let_projector_start_turn_automatic) }
  elseif (@($gv | Where-Object { "$_" -cmatch ':[a-z0-9_]+-cow(:|$)' }).Count -gt 0) { $why = M cow_partitions_xgimi_update_not }
  elseif ((Gv-Val $gv "super-partition-name") -cne "super") { $why = "super-partition-name '$(Gv-Val $gv 'super-partition-name')'" }
  elseif ((Gv-Val $gv "is-logical:system_$cs") -ne "yes") { $why = "is-logical:system_$cs '$(Gv-Val $gv "is-logical:system_$cs")'" }
  else {
    foreach ($k in (Abf-List KEEP)) {
      $sz = Gv-Size $gv "$($k)_$cs"
      if ($null -eq $sz -or $sz -le 0) { $why = (M partition_missing "$($k)" "$cs") + " (partition-size '$(if ($null -ne $sz) { "0x{0:X}" -f $sz })')"; break }
    }
  }
  if ($why) { Abf-Fb-Stop ((M fastboot_mode_check_failed) + ": $why.") }
  $script:AbfS = "_$cs"
  Ok (M fastboot_mode_slot "$nm" "$($script:Serial)" "$cs")
}
function Abf-Room($bf) {  # [blobs file]: the new z9x_blobs and the image (and the add-on of AbfGneed) fit into super; else STOP
  $gv = $script:AbfGv; $sx = $script:AbfS; $res = [int64]$script:Prof["SUPER_RESERVE"]; $blob = [int64]0
  if ($null -eq (Gv-Size $gv "super")) { Abf-Fb-Stop (M fastbootd_did_not_report_size) }
  $free = [int64](Super-Free $gv); $sys = Gv-Size $gv "system$sx"; $old = Gv-Size $gv "z9x_blobs$sx"
  if ($null -eq $sys) { $sys = 0 }
  if ($null -eq $old) { $old = 0 }
  if ($bf) { $blob = [int64]$BlobSize - [int64]$old; if ($blob -lt 0) { $blob = [int64]0 } }
  if ($free -lt ($blob + $res)) { Abf-Fb-Stop (M no_room_super_z9x_blobs "$sx" "$([math]::Truncate($free / 1MB))" "$([math]::Truncate(($blob + $res) / 1MB))") }
  # the write resizes system to the image: the space it has now counts for it
  $room = $free + (Abf-MiB $sys) - (Abf-MiB $blob) - $res
  $need = Abf-MiB (Get-Item -LiteralPath $script:Image).Length
  if ($need -gt $room) { Abf-Fb-Stop (M image_not_fit_super_mb "$([math]::Truncate($room / 1MB))" "$([math]::Truncate($need / 1MB))") }
  # -GappsZip: z9x_gapps is made or resized to the add-on image after system; what it adds to its space counts too
  if ($script:AbfGneed -le 0) { return }
  $gold = Gv-Size $gv "z9x_gapps$sx"; if ($null -eq $gold) { $gold = 0 }
  $g = [int64]$script:AbfGneed - (Abf-MiB $gold); if ($g -lt 0) { $g = [int64]0 }
  if (($need + $g) -gt $room) { Abf-Fb-Stop (M image_google_services_not_fit_2 "$([math]::Truncate($room / 1MB))" "$([math]::Truncate(($need + $g) / 1MB))") }
}
# SLOT_CHECK=yes (the X50): the other slot of XGIMI's stock holds no system while its boot loader keeps that slot
# bootable (see lumen-install.sh, abf_slot_check, the same place). Never a write of the other slot, never a slot
# switch. 'before': the written slot not unbootable, the other slot's system size saved (other_sys), told and asked
# when it holds no system. 'after': one more getvar all; still the written slot, not unbootable, the other slot's
# system as before, else STOP before the wipe; a lost successful mark is told. Reads only.
function Abf-Slot-Check($when) {  # before|after
  $nm = P6-Name; $rb = $script:Prof["RESTORE_BIN"]
  $cs = $script:AbfS.TrimStart('_'); $o = Abf-Other $cs
  if ($when -ceq "before") {
    $gv = $script:AbfGv
    if ((Gv-Val $gv "slot-unbootable:$cs") -eq "yes") { Abf-Fb-Stop (M boot_loader_marks_slot_one "$cs") }
    $osz = Gv-Size $gv "system_$o"; if ($null -eq $osz) { $osz = [int64]0 }
    State-Set other_sys "$osz"
    if ($osz -gt 0) {
      Ok (M slot_system_holds_system_mb "$o" "$([math]::Truncate($osz / 1MB))")
      return
    }
    $u = Gv-Val $gv "slot-unbootable:$o"; $r = Gv-Val $gv "slot-retry-count:$o"
    Write-Host ""
    Write-Host (M slot_holds_no_system_system "$o" "$nm" "$cs" "$u" "$r" "$rb")
    if (-not (Ask (M write_slot_anyway "$cs"))) { Abf-Fb-Stop (M cancelled) }
    return
  }
  $gv = @(Fb getvar all)
  # as in Abf-Fbd-Check: a fastbootd that leaves the mark out of 'getvar all' is asked for this slot alone
  if (-not @($gv | Where-Object { "$_" -like "(bootloader) slot-successful:${cs}:*" }).Count) {
    $l = @(Fb getvar "slot-successful:$cs") | Where-Object { "$_" -like "slot-successful:${cs}:*" } | Select-Object -First 1
    $v = if ($l) { ("$l".Substring(("slot-successful:${cs}:").Length)).Trim() } else { "" }
    if ($v -ceq "yes" -or $v -ceq "no") { $gv = @($gv) + "(bootloader) slot-successful:${cs}:$v" }
  }
  $script:AbfGv = $gv
  $cur = Gv-Val $gv "current-slot"; $suc = Gv-Val $gv "slot-successful:$cs"; $u = Gv-Val $gv "slot-unbootable:$cs"
  $r = Gv-Val $gv "slot-retry-count:$cs"
  $o2 = Gv-Size $gv "system_$o"; if ($null -eq $o2) { $o2 = [int64]0 }
  $osz = State-Get other_sys; $op = if ("$osz") { "$osz" } else { "?" }
  if ($cur -cne $cs -or $u -eq "yes" -or ("$osz" -and "$o2" -cne "$osz")) {
    Die (M after_write_fastboot_mode_reports "$cur" "$cs" "$u" "$o" "$o2" "$op")
  }
  Ok (M slot_stays_one_starts_current "$cs" "$cur" "$o")
  if ($suc -eq "yes") { return }
  if ($o2 -gt 0) {
    Warn (M slot_no_longer_marked_successful "$cs" "$suc" "$r" "$o")
  } else {
    Warn (M slot_no_longer_marked_successful_2 "$cs" "$suc" "$r" "$o" "$rb")
  }
}
function Abf-Slot-Note {  # SLOT_CHECK, the end of a write: the other slot that holds no system, told once more
  if ($script:Prof["SLOT_CHECK"] -cne "yes" -or (State-Get other_sys) -cne "0") { return }
  $rb = $script:Prof["RESTORE_BIN"]; $cs = $script:AbfS.TrimStart('_'); $o = Abf-Other $cs
  Write-Host (M slot_starts_lumen_os_slot "$cs" "$o" "$rb")
}
function Abf-Infb { return ((State-Get step_fastboot) -and (State-Get step_flash) -ne "done" -and (P6-In-Fastboot)) }
function Abf-Write {  # stock or Lumen OS running: checks, ERASE, cable, fastbootd: z9x_blobs, system, the wipe, restart
  $nm = P6-Name; $infb = $false
  Abf-Refuse-Opts
  Check-Tools
  if (Abf-Infb) {
    $infb = $true; $script:AbfResume = $true
    Say (M projector_waits_fastboot_mode)
  } else {
    if (-not $script:AbfUp -and -not (Abf-Adb 60)) {
      if ((State-Get step_fastboot) -and (State-Get step_flash) -ne "done") { Die (M projector_neither_fastboot_mode_2 "$($script:AdbT)") }
      Die (P6-NoConn)
    }
    Abf-Device
    Abf-Live-Gate
    Abf-Vbmeta-Check flash
  }
  Abf-Check-Image -Force
  $bf = Abf-Blobs-File
  # z9x_blobs written in this visit already (step_zblobs): only system and the wipe are left, the file is not needed
  if ($infb -and (State-Get step_zblobs) -eq "done") { $bf = "" }
  if ($bf -and (-not $infb -or (State-Get step_system) -ne "done") -and -not (Abf-Blobs-Ok)) {
    if ($infb -and (State-Get step_system) -eq "started") { Abf-Fb-Stop (M mediatek_xgimi_files_projector "$bf") }
    if ($infb) { Abf-Fb-Stop (M mediatek_xgimi_files_projector_2 "$bf") }
    Abf-Blobs
  }
  # -GappsZip (one visit, one wipe, as on the Z9X): the add-on image built and checked here, before the ERASE
  # question. A visit resumed in fastboot mode keeps its saved add-on only with -GappsZip given again
  if ($GappsZip) { Z9x-Gapps-Prepare }
  elseif ($infb -and (State-Get gapps_plan) -and (State-Get step_gapps_flash) -ne "done") {
    Say (M install_started_google_services_2)
    if (-not (Ask (M finish_install_without_google))) { Abf-Fb-Stop (M run_same_command_again_gapps_3) }
    State-Set gapps_plan ""
  }
  $sl = (State-Get slot).TrimStart('_')
  Write-Host ""
  if ($bf) {
    Write-Host (M writing_lumen_os_slot_erasing_2 "$sl" "$nm")
  } else {
    Write-Host (M writing_lumen_os_slot_erasing_3 "$sl" "$nm")
  }
  if ($GappsZip) {
    Write-Host (M gapps_zip_same_visit_also "$($script:GzName)" "$sl" "$([math]::Ceiling($script:GiSize / 1MB))")
  }
  if ((Read-Host (M type_erase_continue)) -cne "ERASE") {
    if ($infb) { Abf-Fb-Stop (M cancelled) }
    Die (M cancelled_nothing_written)
  }
  $script:EraseNow = $true
  # the add-on of this write (Abf-Gapps-Write); a new write without -GappsZip forgets what an earlier one saved
  if (-not $infb -and ($GappsZip -or (State-Get gapps_plan) -or (State-Get step_gapps_flash))) {
    State-Set gapps_plan $script:GzSha; State-Set gapps_img $script:GiSha; State-Set step_gapps_flash ""
  } elseif ($GappsZip -and (State-Get gapps_plan) -ne $script:GzSha) {
    State-Set gapps_plan $script:GzSha; State-Set gapps_img $script:GiSha; State-Set step_gapps_flash ""
  }
  $script:AbfGneed = [int64]0
  if ((State-Get gapps_plan) -and (State-Get step_gapps_flash) -ne "done") { $script:AbfGneed = Abf-MiB $script:GiSize }
  if (-not $infb) {
    foreach ($k in @("step_fastboot", "step_zblobs", "step_system", "step_wipe", "step_flash", "step_verify")) { State-Set $k "" }
    Write-Host ""
    Write-Host (M connect_projectors_usb_port)
    $null = Read-Host (M press_enter_when_cable)
    Say (M restarting_fastboot_mode_about_30)
    # saved first: a restart that happens although adb reports an error is still continued by the next run
    State-Set step_fastboot (Get-Date -Format "yyyyMMdd-HHmmss")
    Adb reboot fastboot | Out-Null
    if ($LASTEXITCODE -ne 0 -and (P6-Alive)) {
      State-Set step_fastboot ""
      Die (M adb_reboot_fastboot_failed_3)
    }
    Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
  }
  if (-not (P6-Fb-Wait 180)) { Die ((M projector_did_not_appear_cable_3) + (Fb-Driver-Hint)) }
  Abf-Fbd-Check
  $sx = $script:AbfS; $len = (Get-Item -LiteralPath $script:Image).Length
  if (-not (State-Get gapps_plan) -and $null -ne (Gv-Size $script:AbfGv "z9x_gapps$sx")) {
    Say (M z9x_gapps_google_services_added "$sx")
  }
  if ((State-Get step_system) -ne "done") {
    if ($script:Prof["SLOT_CHECK"] -ceq "yes") { Abf-Slot-Check before }
    Abf-Room $bf
    if ($bf) {
      if ($null -ne (Gv-Size $script:AbfGv "z9x_blobs$sx")) { $made = Fb-Do resize "z9x_blobs$sx" $BlobSize }
      else { $made = Fb-Do create "z9x_blobs$sx" $BlobSize }
      if (-not $made) { Abf-Fb-Stop (M partition_z9x_blobs_could_not "$sx") }
      if (-not (Fb-Do flash "z9x_blobs$sx" $bf)) { Abf-Fb-Stop (M z9x_blobs_not_written_system "$sx") }
      State-Set step_zblobs done
      Ok "z9x_blobs$sx"
    }
    Say (M writing_system_about_1_minute)
    State-Set step_system started
    if (-not (Fb-Do flash "system$sx" $script:Image)) { Die (M system_not_written_completely) }
  }
  $got = ""
  foreach ($l in @(Fb getvar "partition-size:system$sx")) { if ("$l" -match "^partition-size:system${sx}:\s*(0x[0-9a-fA-F]+)") { $got = [Convert]::ToInt64($Matches[1], 16); break } }
  if ("$got" -ne "$len") {
    State-Set step_system started
    $g = if ("$got") { "$got" } else { "?" }
    Die (M after_write_system_has_bytes "$sx" "$g" "$len")
  }
  State-Set step_system done
  Ok "system$sx ($([math]::Truncate($len / 1MB)) MB)"
  if ($script:Prof["SLOT_CHECK"] -ceq "yes") { Abf-Slot-Check after }
  if ((State-Get gapps_plan) -and (State-Get step_gapps_flash) -ne "done") { if (-not (Abf-Gapps-Write)) { State-Set gapps_plan "" } }
  $wp = $script:Prof["WIPE_PARTS"]
  Say (M wiping_data_format "$($script:Prof['WIPE_FS'])" "$wp")
  foreach ($p in (Abf-List WIPE_PARTS)) {
    if (-not (Fb-Do format $p)) { Die (M wipe_failed_projector_stays "$p") }
  }
  State-Set step_wipe done
  Fb reboot | Out-Null
  State-Set step_flash done
  $h = Abf-Hint
  Write-Host ""
  if ((State-Get step_gapps_flash) -eq "done") {
    Write-Host (M written_projector_starts_lumen_os "$h")
    Abf-Slot-Note
    return
  }
  Write-Host (M written_projector_starts_lumen_os_2 "$h")
  Abf-Slot-Note
}
# The Google services add-on of a profile with GAPPS_ALLOW (see lumen-install.sh, the same place): the user's own
# MindTheGapps zip becomes, on this computer, the erofs image of the profile's GAPPS_IMAGES (Gi-Build), written into
# z9x_gapps_<slot> in fastbootd. -GappsZip: part of the install's one visit (Abf-Gapps-Write: after system, before the
# wipe; no room or a failed write leaves Google out, warned). 'gapps -Zip FILE' on Lumen OS of this projector: one
# fastbootd visit of its own (z9x_gapps, then the format of WIPE_PARTS); no fill over adb, no root. 'gapps -Remove':
# z9x_gapps deleted in such a visit, the same wipe. 'gapps': the state. 'gsf': the GSF ID (adb root). A 'gapps' visit
# cut off after z9x_gapps was changed stays in fastboot mode (gapps_fastboot in the state); the same command continues.
function Abf-Gapps-Write {  # fastbootd, system written: z9x_gapps_<slot> sized to the add-on image and flashed; $false = left out
  # (warned; the partition is deleted again, so the system starts plainly without Google)
  $p = "z9x_gapps$($script:AbfS)"
  $need = Abf-MiB $script:GiSize
  $why = M install_goes_without_google
  $gv = @(Fb getvar all); $script:AbfGv = $gv   # system and z9x_blobs have their new sizes now
  $free = [int64](Super-Free $gv); $cur = Gv-Size $gv $p; if ($null -eq $cur) { $cur = [int64]0 }
  if (($free + $cur) -lt $need) {
    if ($cur -gt 0) { $null = Fb-Do delete $p }
    Warn ((M not_enough_free_space_super_2 "$([math]::Truncate(($free + $cur) / 1MB))" "$([math]::Truncate($need / 1MB))" "$why"))
    State-Set step_gapps_flash "skipped:room"; return $false
  }
  Say (M writing_google_services_mb_about "$p" "$([math]::Truncate($need / 1MB))")
  $made = if ($cur -gt 0) { Fb-Do resize $p $need } else { Fb-Do create $p $need }
  if (-not $made) { $null = Fb-Do delete $p; Warn (M could_not_created "$p" "$why"); State-Set step_gapps_flash "skipped:create"; return $false }
  if (-not (Fb-Do flash $p $script:GiImg)) {
    if (-not (P6-In-Fastboot)) { Die (M fastboot_mode_ended_while_being "$p") }
    $null = Fb-Do delete $p
    Warn (M could_not_written_removed_again "$p" "$why")
    State-Set step_gapps_flash "skipped:flash"; return $false
  }
  State-Set step_gapps_flash done; State-Set gapps $script:GzName; State-Set gapps_img $script:GiSha
  Ok "${p}: $($script:GzName)"
  return $true
}
function Abf-Gapps-Need {  # bytes of super the -GappsZip add-on takes (its checked image rounded up to 1 MiB), 0 without one
  if (-not $GappsZip -or -not $script:GzSha -or -not (Test-Path -LiteralPath $script:GappsImagesF)) { return [int64]0 }
  $ref = Get-Content -LiteralPath $script:GappsImagesF | Where-Object { $w = @("$_".Trim() -split "\s+"); $w.Count -eq 5 -and $w[0] -eq $script:GzSha -and $w[1] -eq $EuVer -and $w[2] -eq "plain" } | Select-Object -First 1
  if (-not $ref) { return [int64]0 }
  $sz = @("$ref".Trim() -split "\s+")[4]
  if ($sz -notmatch '^[0-9]+$') { return [int64]0 }
  return (Abf-MiB $sz)
}
function Abf-Gapps-Cable {  # Lumen OS runs: cable in, restart into fastboot mode for the 'gapps' visit, wait for it there
  Write-Host ""
  Write-Host (M connect_projectors_usb_port)
  $null = Read-Host (M press_enter_when_cable)
  Say (M restarting_fastboot_mode_about_30)
  State-Set gapps_fastboot (Get-Date -Format "yyyyMMdd-HHmmss")
  Adb reboot fastboot | Out-Null
  if ($LASTEXITCODE -ne 0 -and (P6-Alive)) {
    State-Set gapps_fastboot ""
    Die (M adb_reboot_fastboot_failed_3)
  }
  Pt-Adb disconnect $script:AdbT 2>&1 | Out-Null
  if (-not (P6-Fb-Wait 180)) { Die ((M projector_did_not_appear_cable_4) + (Fb-Driver-Hint)) }
}
function Abf-Gapps-Wipe {  # the 'gapps' visit, z9x_gapps changed: the format of WIPE_PARTS, then the restart
  $wp = $script:Prof["WIPE_PARTS"]
  Say (M wiping_data_format "$($script:Prof['WIPE_FS'])" "$wp")
  foreach ($p in (Abf-List WIPE_PARTS)) {
    if (-not (Fb-Do format $p)) { Die (M wipe_failed_projector_stays_2 "$p") }
  }
  Fb reboot | Out-Null
  State-Set gapps_fastboot ""
}
function Abf-Gapps-Lumen {  # connected: Lumen OS of this projector without Google built in, else STOP
  P6-Lumen-Only
  if ((Prop ro.z9x.gms) -ne "0") { Die (M projector_runs_lumen_os_google_2) }
}
function Abf-Gapps-Install {
  $infb = $false; $h = Abf-Hint
  Abf-Refuse-Opts
  if (-not (Test-Path -LiteralPath $Zip)) { Die (M no_file "$Zip") }
  Check-Tools
  Gapps-Zipcheck $Zip
  if ((State-Get gapps_fastboot) -and (P6-In-Fastboot)) {
    $infb = $true
    Say (M projector_waits_fastboot_mode)
  } else {
    if (-not (Abf-Adb 60)) { Die (P6-NoConn) }
    Abf-Device
    Abf-Live-Gate
    Abf-Gapps-Lumen
    # the same add-on, on: writing it again would only cost the Google apps their data (one start without them)
    if ((Prop sys.z9x.gapps) -eq "ok" -and (Prop sys.z9x.gapps.zip) -eq $script:GzSha) {
      Ok (M these_google_services_already "$($script:GzName)")
      return
    }
    # the Lumen OS on the projector takes this zip as an erofs add-on (its own files, read without root)
    $al = @((Adb shell cat /system/etc/z9x/gapps_allow.txt) | ForEach-Object { "$_".TrimEnd("`r") })
    $gs = @((Adb shell cat /system/etc/z9x/z9x_gapps.sh) | ForEach-Object { "$_".TrimEnd("`r") })
    if (-not ($al | Where-Object { $_ -like "zip=$($script:GzSha) *" }) -or -not ($gs | Where-Object { $_ -match '^# lumen-gapps-fs:.* erofs' })) {
      Die (M lumen_os_projector_not_accept)
    }
  }
  Eu-Setup
  Gi-Build
  $sl = (State-Get slot).TrimStart('_'); $mb = [math]::Ceiling($script:GiSize / 1MB)
  Write-Host ""
  Write-Host (M adding_google_services_slot_one "$sl" "$mb" "$($script:GzName)")
  if (-not $infb -and (State-Get step_backup) -ne "done" -and (Ask (M make_backup_apps_files_first))) { Cmd-Backup }
  $a = Read-Host (M type_erase_continue)
  $script:AbfGapps = $true; $script:AbfGw = $false
  if ($a -cne "ERASE") {
    if ($infb) { Abf-Fb-Stop (M cancelled) }
    Die (M cancelled_nothing_written)
  }
  $script:EraseNow = $true
  if (-not $infb) { Abf-Gapps-Cable }
  Abf-Fbd-Check
  $p = "z9x_gapps$($script:AbfS)"
  # room: super's free space and what the partition has now
  $need = Abf-MiB $script:GiSize; $res = [int64]$script:Prof["SUPER_RESERVE"]
  $free = [int64](Super-Free $script:AbfGv); $cur = Gv-Size $script:AbfGv $p; if ($null -eq $cur) { $cur = [int64]0 }
  if (($free + $cur) -lt ($need + $res)) { Abf-Fb-Stop (M not_enough_free_space_super_3 "$([math]::Truncate(($free + $cur) / 1MB))" "$([math]::Truncate(($need + $res) / 1MB))") }
  Say (M writing_google_services_mb_about "$p" "$([math]::Truncate($need / 1MB))")
  $made = if ($cur -gt 0) { Fb-Do resize $p $need } else { Fb-Do create $p $need }
  if (-not $made) { Abf-Fb-Stop (M could_not_made "$p") }
  $script:AbfGw = $true
  if (-not (Fb-Do flash $p $script:GiImg)) {
    if (-not (P6-In-Fastboot)) { Die (M fastboot_mode_ended_while_being_2 "$p") }
    if (-not (Fb-Do delete $p)) { Die (M could_not_written_projector_stays "$p") }
    State-Set gapps_fastboot ""
    Fb reboot | Out-Null
    Die ((M could_not_written_removed_again_2 "$p") + " " + $h)
  }
  Ok "${p}: $($script:GzName)"
  State-Set gapps $script:GzName; State-Set gapps_img $script:GiSha
  Abf-Gapps-Wipe
  State-Set step_gapps done
  Write-Host ""
  Write-Host (M done_projector_starts_lumen_os "$h")
}
function Abf-Gapps-Remove {
  $infb = $false; $h = Abf-Hint
  Abf-Refuse-Opts
  Check-Tools
  if ((State-Get gapps_fastboot) -and (P6-In-Fastboot)) {
    $infb = $true
    Say (M projector_waits_fastboot_mode)
  } else {
    if (-not (Abf-Adb 60)) { Die (P6-NoConn) }
    Abf-Device
    Abf-Live-Gate
    Abf-Gapps-Lumen
    if ((Prop sys.z9x.gapps) -eq "none") {
      $sl = (State-Get slot).TrimStart('_')
      Ok (M slot_has_no_z9x_gapps "$sl")
      return
    }
  }
  Write-Host ""
  Write-Host (M removing_google_services_added_one)
  $a = Read-Host (M type_erase_continue)
  $script:AbfGapps = $true; $script:AbfGw = $false
  if ($a -cne "ERASE") {
    if ($infb) { Abf-Fb-Stop (M cancelled) }
    Die (M cancelled_nothing_written)
  }
  $script:EraseNow = $true
  if (-not $infb) { Abf-Gapps-Cable }
  Abf-Fbd-Check
  $p = "z9x_gapps$($script:AbfS)"
  if ($null -ne (Gv-Size $script:AbfGv $p)) {
    $script:AbfGw = $true
    $del = Fb-Do delete $p
    if (-not $del) { Die (M could_not_deleted_projector_stays "$p") }
    Ok (M deleted "$p")
  } elseif (-not $infb) {
    $sl = $script:AbfS.TrimStart('_')
    Abf-Fb-Stop (M slot_has_no_nothing_remove_2 "$sl" "$p")
  }
  Abf-Gapps-Wipe
  State-Set gapps ""; State-Set gapps_img ""
  Ok ((M projector_starts_lumen_os_without_2) + " " + $h)
}
function Abf-Gapps-Status {
  Check-Tools
  if (-not (Abf-Adb 60)) { Die (P6-NoConn) }
  Abf-Device
  P6-Lumen-Only
  $v = Prop sys.z9x.gapps; $z = Prop sys.z9x.gapps.zip
  if ($v -eq "ok") { Ok ((M google_services_added) + " ($(("$(Gapps-Line $z)" -split ' ')[1]))") }
  elseif ($v -eq "none" -or -not $v) { Say (M no_google_services_added_add) }
  elseif ($v -eq "bad:slow") { Warn (M last_start_check_google_services) }
  elseif ($v -eq "off") {
    Warn (M google_services_were_turned_off)
    if (Ask (M turn_them_again_projector_restarts)) {
      P6-Root-Or-Die
      Adb shell rm -rf /metadata/z9x_gapps | Out-Null
      Adb reboot | Out-Null
      Ok (M turned_projector_restarts)
    }
  }
  else { Warn (M google_services_added_cannot_used "$v") }
}
function Abf-Gapps {
  if ($Zip -and $Remove) { Die "gapps: -Zip or -Remove, not both" }
  if ($Remove) { Abf-Gapps-Remove } elseif ($Zip) { Abf-Gapps-Install } else { Abf-Gapps-Status }
}
function Abf-Gsf {  # the Google Services Framework ID of the projector's Lumen OS, for google.com/android/uncertified
  Check-Tools
  if (-not (Abf-Adb 60)) { Die (P6-NoConn) }
  Abf-Device
  P6-Lumen-Only
  if (-not ((Adb shell pm path com.google.android.gsf) | Out-String).Trim()) {
    Ok (M lumen_os_has_no_google)
    Say (M add_them_bash_lumen_install)
    return
  }
  P6-Root-Or-Die
  $id = ((Adb shell "sqlite3 /data/data/com.google.android.gsf/databases/gservices.db `"select value from main where name='android_id';`"") | Out-String).Trim()
  Adb unroot | Out-Null
  if (-not $id) { Die (M no_gsf_id_yet_connect) }
  Write-Host "GSF ID: $id"
  Write-Host (M register_https_www_google_com)
}
function Abf-Verify {  # after the setup, with debugging on again: Lumen OS of this projector started, with its files
  $nm = P6-Name; $good = $true
  Check-Tools
  if (-not (Abf-Adb 60)) { Die (P6-NoConn) }
  if ((Prop sys.boot_completed) -ne "1") { Die (M system_has_not_finished_starting) }
  $v = Prop (P6-LProp)
  if ($v -cne (P6-LVal)) { Die (M projector_not_run_lumen_os_5 "$nm" "$(P6-LProp)" "$v") }
  $vp = $script:Prof["VERSION_PROP"]; $v = Prop $vp
  if ($v) { Ok "Lumen OS $v $(M on_the) $nm $($script:Serial)" } else { Warn (M missing "$vp"); $good = $false }
  if ($Blobs -eq "partition") {
    $bl = Prop sys.z9x.blobs
    if ($bl -eq "ok") { Ok (M mediatek_xgimi_files_z9x_blobs "$(State-Get slot)") }
    else { Warn "sys.z9x.blobs=$bl ($(M no_sound_no_mediatek_video))"; $good = $false }
  }
  if (Abf-Gapps-On) {
    $gs = Prop sys.z9x.gapps
    if ($gs -eq "ok") { Ok (M google_services_added_gapps) }
    elseif ($gs -eq "off" -or $gs -like "bad:*") { Warn (M google_services_add_not_use "$gs") }
  }
  if ($good) { State-Set step_verify done; Ok (M verify_passed) }
}
function Abf-Done {
  $nm = P6-Name
  Write-Host ""
  Write-Host (M done_projector_lumen_os_setup "$nm")
  if ((State-Get step_gapps_flash) -eq "done") {
    Write-Host (M google_sign_if_google_refuses)
  }
  Write-Host "  $(M guide): $(P6-Guide)"
}
function Abf-Flash {  # the 'flash' command: a new write, unless one waits in fastboot mode (then it continues there)
  Abf-Write
  Abf-Done
}
function Abf-All {  # check, backup (asked), vbmeta (asked), blobs, flash: each step once, continued where it stopped
  Abf-Refuse-Opts
  if ($GappsZip) { Z9x-Gapps-Plan }   # -GappsZip: the zip, and that the install saved here can take it
  if (-not (Abf-Infb)) {
    if ((State-Get step_check) -ne "done") { Abf-Check }
    Abf-Live-Gate
    # -GappsZip: the add-on image built before the backup question, so that a refusal comes first
    if ($GappsZip -and (State-Get step_flash) -ne "done") { Z9x-Gapps-Prepare }
    if (-not (State-Get step_backup)) {
      if (Ask (M make_backup_apps_files_first)) { Cmd-Backup } else { State-Set step_backup skipped }
    }
    if ((State-Get step_flash) -ne "done" -and (State-Get step_vbmeta) -ne "done") { Abf-Vbmeta }
    if ((State-Get step_flash) -ne "done" -and -not (Abf-Blobs-Ok)) { Abf-Blobs }
  }
  if ((State-Get step_flash) -ne "done") { Abf-Write }
  Abf-Done
}
function Abf-Todo-Stop {  # a profile with TODO values (lib\devices.conf header): nothing is ever written to that projector
  Die (M lumen_os_cannot_installed_computer "$(P6-Name)")
}
function Abf-Main {  # what every command of a FLOW=ab-fastbootd projector checks first (lib\flows.conf START)
  # a profile that is not finished (TODO values): only 'check' runs (it reads, then stops); every other command
  # stops here, before adb or fastboot
  $c = if ($Command) { "$Command".ToLower() } else { "all" }
  if ($script:Todo -and $c -cne "check") { Abf-Todo-Stop }
  if ((Abf-Eye) -and $Blobs -ne "partition") {   # its eye-protection drawer comes from its own files (Abf-Blobs)
    Die (M draws_eye_protection_mask_files "$(P6-Name)")
  }
}

function Z9x-New-Install-If-Stock {  # progress says Lumen OS was written, but the projector runs XGIMI's stock system again
  # (restored from a USB stick since): that progress is the earlier install's, start over (vbmeta stays: Cmd-Check proves it)
  if ((State-Get step_flash) -ne "done") { return }
  $was = $script:Serial
  if (-not $script:Serial) {
    $list = @(Pt-Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] })
    if ($list.Count -ne 1) { return }
    $script:Serial = $list[0]
  }
  if ((Prop ro.boot.slot_suffix) -and -not (Prop ro.z9x.version)) {
    Say (M projector_runs_stock_system_again)
    foreach ($k in @("step_check", "step_backup", "step_blobs", "step_flash", "step_wipe", "step_verify", "step_gapps_part", "step_gapps_fill", "step_gapps_flash", "gapps_plan", "gapps_img", "image_ok")) { State-Set $k "" }
  }
  $script:Serial = $was
}

function Flow-Step($flow, $key) {  # flow KEY -> @(function, arguments...) of this script (lib\flows.conf), @() without one
  $w = @("$(Flow-Get $flow $key)" -split ' ' | Where-Object { $_ })
  if (-not $w) { return @() }
  $fn = (@($w[0] -split '_' | ForEach-Object { $_.Substring(0, 1).ToUpper() + $_.Substring(1) }) -join '-')
  if (-not (Get-Command $fn -CommandType Function -ErrorAction SilentlyContinue)) { Die "internal: lib\flows.conf: $flow.$key" }
  return @($fn) + @($w | Select-Object -Skip 1 | ForEach-Object { [int]$_ })
}
function Flow-Run {  # the command of this projector's flow, as lib\flows.conf says
  $f = $script:Prof["FLOW"]; $cmd = if ($Command) { "$Command".ToLower() } else { "all" }
  $s = @(Flow-Step $f "START"); if ($s) { $a = @($s | Select-Object -Skip 1); & $s[0] @a }
  if (@("$(Flow-Get $f 'NOT_NEEDED')" -split ' ') -contains $cmd) { $nm = P6-Name; Die (M not_needed_nothing_done "$Command" "$nm") }
  if (@("$(Flow-Get $f 'NOT_YET')" -split ' ') -contains $cmd) { Not-Yet $Command }
  $need = Pair-Get (Flow-Get $f "NEEDS") $cmd
  if ($need -and -not $script:Prof[$need]) { Not-Yet $Command }
  $s = @(if ($cmd -cmatch '^[a-z]+$') { Flow-Step $f "CMD_$($cmd.ToUpper())" })
  if (-not $s) { Get-Content $PSCommandPath -TotalCount 20 | Select-Object -Skip 1; exit 2 }   # the usage lines above
  if (@("$(Flow-Get $f 'TOOLS')" -split ' ') -contains $cmd) { Pt-Setup }
  $a = @($s | Select-Object -Skip 1); & $s[0] @a
}

function Cmd-All {
  Z9x-New-Install-If-Stock
  if ($GappsZip) { Z9x-Gapps-Plan }
  if ((State-Get step_check) -ne "done") { Cmd-Check }
  if ($GappsZip -and (State-Get image_gms) -ne "0") { Die (M image_has_google_services_built) }
  if ($GappsZip -and (State-Get step_flash) -ne "done") { Z9x-Gapps-Prepare }   # before the backup question: a refusal comes first
  if ((State-Get step_backup) -ne "done") { if (Ask (M make_backup_apps_files_first)) { Cmd-Backup } else { State-Set step_backup skipped } }
  if ($Blobs -eq "partition" -and (State-Get step_blobs) -ne "done") { Cmd-Blobs }
  if ((State-Get step_vbmeta) -ne "done") { Cmd-Vbmeta }
  if ((State-Get step_flash) -ne "done") { Cmd-Flash }
  if ((State-Get step_gapps_part) -eq "done" -and -not (State-Get step_wipe)) { Z9x-Gapps-Finish }
  # after a wipe USB debugging is off on the fresh system: waiting for adb only hangs; 'verify' stays a command
  if ((State-Get step_wipe) -eq "done") { State-Set step_verify "skipped:wiped" }
  elseif ((State-Get step_verify) -ne "done") { Cmd-Verify }
  if ((State-Get image_gms) -eq "0" -and (State-Get step_gapps_fill) -ne "done" -and (State-Get step_gapps_flash) -ne "done") {
    Write-Host (M done_finish_setup_projector)
  } else {
    Write-Host (M done_finish_setup_projector_google)
  }
}

$script:Image = $Image; $script:Serial = $Serial; $script:SerialFromState = $false
if ($Ip) {
  if ($Ip -notmatch '^[A-Za-z0-9._-]+(:[0-9]+)?$') { Die (M ip_not_address_example_ip "$Ip") }
  $script:AdbT = if ($Ip -match ":") { $Ip } else { "${Ip}:5555" }
}
if ($Command -eq "status") {   # the saved progress only: no projector, no profile needed
  $shown = $false
  foreach ($id in @(Profile-Ids)) {   # the projector whose progress is the root folder as it is, the others by name
    if (@("", $id) -notcontains $Device) { continue }
    $dir = State-Dir-Of $id; $s = Join-Path $dir "state"
    if (-not (Test-Path $s)) { continue }
    if ($dir -ceq $StateDir) { Get-Content $s } else { Write-Host "$(Dev-Name $id):"; Get-Content $s | ForEach-Object { "  $_" } }
    $shown = $true
  }
  if (-not $shown) { Write-Host (M no_saved_progress) }
  exit 0
}
if ($Command -eq "reset") {
  if (-not $Device) { Remove-Item -Recurse -Force $StateDir -ErrorAction SilentlyContinue }
  elseif ((State-Dir-Of $Device) -ceq $StateDir) {   # every other projector's folder stays
    $keep = @(Profile-Ids | Where-Object { $_ -ne $Device })
    Get-ChildItem -LiteralPath $StateDir -Force -ErrorAction SilentlyContinue | Where-Object { $keep -notcontains $_.Name } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
  }
  else { Remove-Item -Recurse -Force (Join-Path $StateDir ("$Device".ToLower())) -ErrorAction SilentlyContinue }   # a projector of lib\devices.conf (checked above)
  Write-Host (M progress_forgotten)
  exit 0
}
if ($GappsZip) {   # one pass, one wipe: the full install only
  if ($Command) { Die (M gapps_zip_belongs_full_install) }
  if ($KeepData) { Die (M gapps_zip_installs_one_data) }
  if ($Wipe -ne "format") { Die (M gapps_zip_wipes_data_itself) }
}
Resolve-Device
if (-not $script:Serial) { $script:Serial = State-Get serial; if ($script:Serial) { $script:SerialFromState = $true } }
if (@("", "check", "flash") -contains $Command) { Release-Guard }
Flow-Run
Pt-Cleanup
