# SPDX-License-Identifier: Apache-2.0
# The *-cow rule of installer/lumen-install.ps1, executed on every case of cow_cases.tsv for both slots, as
# installer_cow_rules.sh does for lumen-install.sh: Cow-Odd / Cow-Leftover / Fb-Allowed with the real
# lib\cow_rules.conf, the Z9X block of Cmd-Flash (the real lines, from its Cow-Load to its delete loop), the
# FLOW=ab-fastbootd STOP (Cow-Odd) and Abf-Cow-Clear, Fb-Allowed one partition at a time in both flows, and the rule
# data missing (Cow-Load stops, Cow-Odd names the file). pwsh 7 (Windows PowerShell 5.1 too):
#   pwsh -NoProfile -File tools/devices/test/installer_cow_rules.ps1 [-Src path/to/lumen-install.ps1]
param([string]$Src = "")
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Src) { $Src = Join-Path $here "../../../installer/lumen-install.ps1" }
$Src = (Resolve-Path -LiteralPath $Src).Path
$inst = Split-Path -Parent $Src
$text = (Get-Content -Raw -LiteralPath $Src) -replace "`r`n", "`n"
$script:pass = 0; $script:fail = 0
function Ok($m) { $script:pass++; Write-Output "ok   $m" }
function Bad($m) { $script:fail++; Write-Output "FAIL $m" }

function Fn($name) {  # the source of function $name: its one line, or up to its closing brace at the line start
  $m = [regex]::Match($text, "(?m)^function $([regex]::Escape($name))\b[^\n]*\n")
  if (-not $m.Success) { Write-Output "FAIL function $name not found"; exit 1 }
  $first = $m.Value.TrimEnd("`n")
  if (($first.ToCharArray() | Where-Object { $_ -eq '{' }).Count -eq ($first.ToCharArray() | Where-Object { $_ -eq '}' }).Count) { return $first }
  $end = $text.IndexOf("`n}`n", $m.Index)
  return $text.Substring($m.Index, $end - $m.Index + 2)
}
$funcs = (@("Gv-Val", "Gv-Size", "Cow-Load", "Cow-Parts", "Cow-Leftover", "Cow-Odd", "Fb-Allowed", "Abf-Cow-Clear", "Cow-Dm-Busy", "Cow-Ota-Busy", "Cow-Adb-Seen") | ForEach-Object { Fn $_ }) -join "`n"
# the Z9X block of Cmd-Flash: its Cow-Load to the end of its delete loop
$z = [regex]::Match($text, '(?s)\n(  Cow-Load\n  \$script:FlashGv = \$gv\n.*?\n  foreach \(\$p in \(Cow-Parts \$gv\)\) \{[^\n]*\n)')
if (-not $z.Success) { Write-Output "FAIL the Z9X *-cow block of Cmd-Flash not found"; exit 1 }
$z9x = $z.Groups[1].Value
# the STOP of Abf-Fbd-Check when adb showed an update in use (CowAdb) and the table has a *-cow
$a = [regex]::Match($text, '(?s)\n(  if \(\$script:CowAdb -and @\(Cow-Parts \$gv\)\.Count\) \{\n.*?\n  \}\n)')
if (-not $a.Success) { Write-Output "FAIL the CowAdb STOP of Abf-Fbd-Check not found"; exit 1 }
$abfAdb = $a.Groups[1].Value

function Gv-Of($st, $sl, $table) {  # a fastbootd 'getvar all', lines with a CR as on a Windows pipe they may be
  $o = if ($sl -ceq "_a") { "_b" } else { "_a" }
  $l = @("(bootloader) snapshot-update-status:$st`r", "(bootloader) current-slot:$($sl.TrimStart('_'))`r")
  foreach ($p in ($table -split " ")) {
    if (-not $p) { continue }
    $p = $p.Replace("{s}", $sl).Replace("{o}", $o); $sz = "0x100000"
    if ($p.EndsWith("=0")) { $p = $p.Substring(0, $p.Length - 2); $sz = "0x0" }
    $l += "(bootloader) is-logical:${p}:yes`r"; $l += "(bootloader) partition-size:${p}:$sz`r"
  }
  return , ($l | ForEach-Object { $_.TrimEnd("`r") })
}

# one run: the installer's functions and fakes in a fresh scope; Die / Abf-Fb-Stop recorded and thrown
function Run($slot, $gv, $flow, $body, $here) {
  $script:out = New-Object System.Collections.Generic.List[string]
  $script:Prof = @{ FLOW = $flow; SLOTS = "a b" }
  $script:AbfS = $slot; $script:AbfGv = $gv; $script:FlashGv = $null
  $script:CowSfx = ""; $script:CowBase = ""; $script:CowStatus = ""; $script:CowAdb = ""
  $script:SlotState = $slot; $script:GvNow = $gv
  $S = $slot; $Here = $here
  function State-Get($k) { if ($k -ceq "slot") { return $script:SlotState } else { return "" } }
  function M { param([Parameter(ValueFromRemainingArguments = $true)]$a) return ($a -join " ") }
  function Say($t) { }
  function Fb { param([Parameter(ValueFromRemainingArguments = $true)]$a) if (($a -join " ") -ceq "getvar all") { return $script:GvNow } }
  function Die($t) { $script:out.Add("DIE $t"); throw "die" }
  function Abf-Fb-Stop($t) { $script:out.Add("STOP $t"); throw "stop" }
  Invoke-Expression $funcs
  function Fb-Do($act, $part) { if (-not (Fb-Allowed $act $part)) { $script:out.Add("NOTALLOWED $act $part"); return $false }; $script:out.Add("DO $act $part"); return $true }
  try { Invoke-Expression $body } catch { if ("$_" -cnotin @("die", "stop")) { $script:out.Add("ERROR $_") } }
  return , @($script:out)
}

$rows = Get-Content -LiteralPath (Join-Path $here "cow_cases.tsv") | Where-Object { $_ -and -not $_.StartsWith("#") }
foreach ($row in $rows) {
  $c = $row -split "`t"
  $name = $c[0]; $st = $c[1]; $table = $c[2]; $want = $c[3]
  foreach ($S in @("_a", "_b")) {
    $O = if ($S -ceq "_a") { "_b" } else { "_a" }
    $gv = Gv-Of $st $S $table
    $w = $want.Replace("{s}", $S).Replace("{o}", $O)
    $wl = @(); if ($w.StartsWith("clear:")) { $wl = @($w.Substring(6) -split " " | Where-Object { $_ } | Sort-Object -CaseSensitive) }
    # 1. the decision
    $r = Run $S $gv "" 'Cow-Load; $c = Cow-Odd $gv $S; if ($c) { $script:out.Add("stop:$c") } else { $l = @(Cow-Parts $gv); if ($l.Count) { $script:out.Add("clear:" + (($l | Sort-Object -CaseSensitive) -join " ")) } else { $script:out.Add("none") } }' $inst
    $wn = if ($w.StartsWith("clear:")) { "clear:" + ($wl -join " ") } else { $w }
    if (($r -join "|") -ceq $wn) { Ok "${name}${S}: decision $w" } else { Bad "${name}${S}: decision: got [$($r -join '|')] want [$wn]" }
    # 2. the Z9X block of Cmd-Flash
    $zw = if ($w -ceq "none") { "" } elseif ($w.StartsWith("clear:")) { ($wl | ForEach-Object { "DO delete $_" }) -join "|" } else { "DIE cow_partition_not_slot $($w.Substring(5)) $($S.TrimStart('_'))" }
    $r = Run $S $gv "" $z9x $inst
    $got = (@($r | Where-Object { $_ -match '^(DO|DIE|NOTALLOWED|ERROR)' }) | Sort-Object -CaseSensitive) -join "|"
    if ($got -ceq $zw) { Ok "${name}${S}: Z9X block" } else { Bad "${name}${S}: Z9X block: got [$got] want [$zw]" }
    # 3. FLOW=ab-fastbootd: the STOP of Abf-Fbd-Check, else Abf-Cow-Clear
    $aw = if ($w -ceq "none") { "" } elseif ($w.StartsWith("clear:")) { ($wl | ForEach-Object { "DO delete $_" }) -join "|" } else { "STOP $($w.Substring(5))" }
    $r = Run $S $gv "ab-fastbootd" 'Cow-Load; $c = Cow-Odd $gv $S; if ($c) { Abf-Fb-Stop $c }; Abf-Cow-Clear' $inst
    $got = (@($r | Where-Object { $_ -match '^(DO|STOP|NOTALLOWED|ERROR)' }) | Sort-Object -CaseSensitive) -join "|"
    if ($got -ceq $aw) { Ok "${name}${S}: ab-fastbootd" } else { Bad "${name}${S}: ab-fastbootd: got [$got] want [$aw]" }
    # 4. Fb-Allowed one partition at a time, in both flows
    $cows = @($gv | ForEach-Object { if ($_ -cmatch '^\(bootloader\) partition-size:([^:]*-cow):') { $Matches[1] } })
    foreach ($p in $cows) {
      if ($w.StartsWith("clear:")) { $exp = ($wl -ccontains $p) } else { $exp = -not ($p -ceq $w.Substring(5) -or $st -cne "none") }
      foreach ($flow in @("", "ab-fastbootd")) {
        $r = Run $S $gv $flow ('Cow-Load; $script:FlashGv = $gv; $script:out.Add([string](Fb-Allowed "delete" "' + $p + '"))') $inst
        $fl = if ($flow) { $flow } else { "z9x" }
        if (($r -join "|") -ceq [string]$exp) { Ok "${name}${S}: Fb-Allowed $fl delete $p -> $exp" } else { Bad "${name}${S}: Fb-Allowed $fl delete ${p}: $($r -join '|'), want $exp" }
      }
    }
  }
}

# the rule data: a missing file or key stops, and without it the rule deletes nothing
$e = Join-Path ([System.IO.Path]::GetTempPath()) ("cowrules" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path (Join-Path $e "lib") | Out-Null
foreach ($conf in @("", "suffix=-cow`nstatus=none", "suffix=-cow`nbase=`nstatus=none")) {
  $f = Join-Path (Join-Path $e "lib") "cow_rules.conf"
  if ($conf) { Set-Content -LiteralPath $f -Value $conf } elseif (Test-Path -LiteralPath $f) { Remove-Item -LiteralPath $f }
  $r = Run "_b" @() "" 'Cow-Load; $script:out.Add("loaded")' $e
  if (($r -join "|") -clike "DIE lib\cow_rules.conf has no *") { Ok "Cow-Load stops on [$($conf -replace "`n", ' ')]" } else { Bad "Cow-Load with [$conf]: $($r -join '|')" }
}
Remove-Item -Recurse -Force -LiteralPath $e
$gv = Gv-Of "none" "_b" "system{s} system{s}-cow"
$r = Run "_b" $gv "" '$script:FlashGv = $gv; $script:out.Add("odd=" + (Cow-Odd $gv "_b")); $script:out.Add([string](Fb-Allowed "delete" "system_b-cow"))' $inst
if (($r -join "|") -ceq "odd=lib\cow_rules.conf|False") { Ok "without Cow-Load: Cow-Odd names the file, Fb-Allowed refuses" } else { Bad "without Cow-Load: $($r -join '|')" }

# what adb showed before fastbootd (Cow-Adb-Seen): a snapshot device in /dev/block/mapper, or with a root shell the
# boot indicator or a state other than none / merge-completed in /metadata/ota
$fakeAdb = 'function Adb { $k = $args -join " "; switch -CaseSensitive ($k) { "shell ls /dev/block/mapper/" { $script:FMapper -split "`n" } "shell id -u" { $script:FUid } "shell ls /metadata/ota/ 2>/dev/null" { $script:FLs -split "`n" } "shell od -An -tx1 /metadata/ota/state 2>/dev/null" { $script:FSt } default { $script:out.Add("ADB $k") } } }'
function Seen($mapper, $uid, $ls, $st) {
  $script:FMapper = $mapper; $script:FUid = $uid; $script:FLs = $ls; $script:FSt = $st
  $r = Run "_a" @() "" ($fakeAdb + "`n" + '$script:out.Add("=" + (Cow-Adb-Seen))') $inst
  return ($r -join "|")
}
$l0 = "control`nsystem_a`nvendor_a`nsystem_a-cow`nsystem-verity"
foreach ($x in @("-base", "-src", "-cow-img", "-user-cow", "-user-cow-init")) {
  $got = Seen "$l0`nvendor_a$x" "2000" "" ""
  if ($got -ceq "=/dev/block/mapper/vendor_a$x") { Ok "Cow-Adb-Seen: mapper vendor_a$x" } else { Bad "Cow-Adb-Seen: mapper vendor_a${x}: [$got]" }
}
$got = Seen "/dev/block/mapper/system_b-base" "2000" "" ""
if ($got -ceq "=/dev/block/mapper/system_b-base") { Ok "Cow-Adb-Seen: a listing with paths" } else { Bad "Cow-Adb-Seen: a listing with paths: [$got]" }
$l1 = "control`nsystem_a`nsystem_a-cow"
$seenRows = @(
  @("2000", "snapshot-boot`nstate", " 08 03", ""), @("0", "", "", ""), @("0", "state", "", ""), @("0", "state", " 08 00", ""),
  @("0", "state snapshots", " 08 05", ""), @("0", "snapshot-boot`nstate", " 08 05", "/metadata/ota/snapshot-boot"),
  @("0", "snapshot-boot-without-slot-switch`nstate", "", ""), @("0", "state", " 08 01", "/metadata/ota/state 08 01"),
  @("0", "state", " 08 02", "/metadata/ota/state 08 02"), @("0", "state", " 08 03", "/metadata/ota/state 08 03"),
  @("0", "state", " 08 04", "/metadata/ota/state 08 04"), @("0", "state", " 08 06", "/metadata/ota/state 08 06"),
  @("0", "state", " 75 6e 76", "/metadata/ota/state 75 6e 76"))
foreach ($w in $seenRows) {
  $got = Seen $l1 $w[0] $w[1] $w[2]
  if ($got -ceq ("=" + $w[3])) { Ok "Cow-Adb-Seen: uid $($w[0]), ls [$($w[1] -replace "`n", ' ')], state [$($w[2])] -> [$($w[3])]" } else { Bad "Cow-Adb-Seen: uid $($w[0]), ls [$($w[1] -replace "`n", ' ')], state [$($w[2])]: [$got] want [=$($w[3])]" }
}
# with CowAdb set: every *-cow kept by the rule, and the ab-fastbootd check stops before the first write when the
# table has one (stock or Lumen OS words), never without one
$gv = Gv-Of "none" "_a" "system{s} system{s}-cow"
foreach ($lum in @($false, $true)) {
  $r = Run "_a" $gv "ab-fastbootd" ('Cow-Load; $lum = $' + "$lum".ToLower() + '; $script:CowAdb = "/dev/block/mapper/system_a-base"' + "`n" + $abfAdb + '$script:out.Add("passed")') $inst
  $w = if ($lum) { "STOP fastboot_mode_check_failed: update_finishing (/dev/block/mapper/system_a-base): start_lumen_os_wait_10." } else { "STOP fastboot_mode_check_failed: xgimi_update_staged_not_finished (/dev/block/mapper/system_a-base): let_projector_start_turn_automatic." }
  if (($r -join "|") -ceq $w) { Ok "ab-fastbootd: adb showed an update, a *-cow there (Lumen OS $lum): STOP" } else { Bad "ab-fastbootd: adb showed an update (Lumen OS $lum): [$($r -join '|')] want [$w]" }
}
$r = Run "_a" (Gv-Of "none" "_a" "system{s}") "ab-fastbootd" ('Cow-Load; $lum = $false; $script:CowAdb = "/dev/block/mapper/system_a-base"' + "`n" + $abfAdb + '$script:out.Add("passed")') $inst
if (($r -join "|") -ceq "passed") { Ok "ab-fastbootd: adb showed an update, no *-cow: no STOP" } else { Bad "ab-fastbootd: adb showed an update, no *-cow: [$($r -join '|')]" }
foreach ($flow in @("", "ab-fastbootd")) {
  $r = Run "_a" $gv $flow 'Cow-Load; $script:CowAdb = "/metadata/ota/snapshot-boot"; $script:FlashGv = $gv; $script:out.Add("odd=" + (Cow-Odd $gv "_a")); $script:out.Add([string](Fb-Allowed "delete" "system_a-cow"))' $inst
  $fl = if ($flow) { $flow } else { "z9x" }
  if (($r -join "|") -ceq "odd=system_a-cow|False") { Ok "CowAdb set: Cow-Odd names the *-cow, Fb-Allowed $fl refuses its delete" } else { Bad "CowAdb set ($fl): $($r -join '|')" }
}

Write-Output "installer cow rules (ps1): $($script:pass) passed, $($script:fail) failed"
if ($script:fail) { exit 1 }
