# X50 t13: the eye-protection trap (live 2026-11-08) and the t14 fix

Logs on xgimi-builder `~/devices/g0111_mt9681/t13_live/` (`logcat_2158.txt`, `logcat_eyeoff_2204.txt`,
`props_eyeoff_2204.txt`, `act_eyeoff_2204.txt`, run 2: `logcat_run2_2222.txt`, `win_run2_2222.txt`, `screen_2222.png`).
The main buffer keeps only ~1 min (vendor `FaceCutoutBin` floods it), so the X50Proj lines of the menu minute are gone;
the timeline below is from the events / system buffers (activity and window records, key-click sounds) and the props.

## What happened (run 1)

| time | evidence | what it was |
|---|---|---|
| 21:58:00.8 | `wm_create_activity MenuActivity` right after `LauncherActivity` | menu opened from Lumen Home's Projector tab |
| 21:59:25.1 to 27.5 | new MenuActivity window `d476d2` (a dialog), focus-move sound 26.53, click 27.48 | a confirm dialog answered with the second button. Not the eye-off dialog: the guard polls the switch every second and saw nothing for 22 s. Which one (ceiling?) is unproven |
| 21:59:47.53 | new MenuActivity window `59c55da` | the eye switch row flipped while on: the menu sent 61 `setHumanDetectOnOff(false)` + `persist.sys.humandetect.enable=false` AT ONCE (stock order) and opened "Turn off eye protection?" with the focus on Cancel |
| 21:59:47.94 | `START CoverActivity` (uid 10050), 48.11 `NoticeActivity` | X50EyeGuard read the switch off within one poll: cover in a new task on top, SWITCH_OFF notice |
| 22:00:24 | notice closed (OK) | the cover stays; Back is swallowed |
| 22:00:37 / 56, 22:01:17 / 21 | Home resumed < 1 s, cover back each time | HOME pressed; `Cover.ensure` relaunch |
| 22:04:25 | `X50Proj: gear: eye protection in front (org.lumen.x50.eyeguard), no menu` | gear refused over the cover |
| 22:04 props | `persist.sys.humandetect.enable=false`, `init.svc.hd_server2=stopped`, `sys.lumen.x50.eye=0`; 127 reads 0 | switch off for good; mode 0 (eye follow) was the tester's own earlier choice on the mode row (126) |

Calls: one 61(false) at ~21:59:47.5 (no 61(true) after); 126 = 0 once, earlier (dumps had 1). Stock newsettings
`MoreRepository.setHumanDetectOnOff`: off = `setHumandetect(false)` (61 + property) before the dialog; "close" =
`setHumandetect(false)` again after 300 ms; "cancel" = `setHumandetect(true)`; Back = on; and an OnDismissListener =
`setHumandetect(true)` on EVERY dismiss (the 300 ms only orders "close" after it). On path (camera solution): dialog
first; "open" = on after 300 ms, "cancel" / Back / dismiss = off.

Why it ended OFF: the stock order (off before the answer) meets a Lumen-only fail-safe. X50EyeGuard covers the picture
as soon as the switch reads off, in a task in front of the menu, so the dialog whose Cancel / Back turns protection back
on was behind the cover. The menu was only stopped, the dialog never dismissed, and the t13 dialog had no stock
dismiss -> on rule either. The cover then said "Lumen never changes this setting: turn it on in the XGIMI settings and
restart" (false: the menu had just changed it; impossible in a DSU trial), swallowed Back, and the gear was refused
over it: a dead end until a reboot into stock.

## Run 2: the stale property (owner, live)

Switched on again in stock (stock prop true, 62 on, `hd_server2` running), then the same DSU trial booted. Its own
`/data` still held `persist.sys.humandetect.enable=false` from the menu write, and t13 `Armed.withSwitch` made any set
property other than `true` NOT_ARMED: covered for good while the vendor was armed. The property lives in each system's
own `/data` (absent in every DSU dump before); only 62 (Env `HumanDetect_OnOff`, xgimicri) is shared.

Also run 2: a light (cyan) panel with invisible text at the bottom while the menu was used. X50Projector draws no
overlay; its only windows besides the menu are AlertDialogs (DeviceDefault) and text toasts, which Android 14 renders
in SystemUI (`VRI[Toast]` in the SystemUI pid at 22:20:46, pkg org.lumen.x50.projector, LENGTH_SHORT). The same image
drew the EyeGuard notice's DeviceDefault button as a light fill with a white label. Both now use explicit colours.

## t14 fix (branch x50-eyefix)

- X50EyeGuard `Armed.withSwitch`: 62 is the only switch input; the property never blanks (it only goes into the log
  when it disagrees with an armed 62). Test `liveStaleProp`: handshake armed + `hd_server2` steady + 62 on + property
  `false` = ARMED, 5 min of Guard ticks never blank. A property change pokes the 62 reader (2 s period for 30 s), so a
  switch flipped by the menu is still seen at once.
- X50EyeGuard cover: one centred card (title + text once, no dialog over the cover); SWITCH_OFF text = the menu's
  "Off. The picture stays dark while it is off." (18 locales reused) and the focused TvFocus button "Turn on eye
  protection" -> explicit broadcast to X50Projector `EyeOnReceiver` (signature permission `EYE_PROTECTION_ON`) -> the
  stock switch-on `Sequences.eyeSet(true)` (61 true + property). EyeGuard still sends no 61 / 126. Re-arm: the vendor
  starts `hd_server2`, the cover goes after 12 s of steady running (2 ARMED ticks); if detection is still not armed
  30 s after 62 reads on, the card says BLANKED (no button for a switch that is on).
- X50Projector: gear over the cover opens the menu on the eye row (translucent menu, cover stays under it); the eye
  switch row sends nothing before the dialog answer (`Menu.eyeAfter`: off only on "turn off", same end states as
  stock); dialogs answer once incl. dismiss; boot sync `App.followEyeSwitch` makes the property follow 62; dialogs,
  buttons and notices in TvFocus colours (own toast view instead of the system text toast).

## Safety reasoning: off = dark picture

Stock with the switch off simply projects with no person detection (the user's explicit choice). Lumen keeps the blank
(owner decision for t5 on: X50EyeGuard blanks whenever detection is not armed; the menu row already said "the picture
stays dark while it is off"): Lumen cannot certify unprotected laser projection on an unofficial system, and a dark
picture is the fail-safe direction. What made it a trap was the missing way back, now fixed twice (cover button, gear).
Projecting with protection off as stock does is an owner decision for later, not taken here.

## Next trial: capture

`adb logcat -G 16M` before the test, or `logcat -b main -s X50Proj:V X50Eye:V` in a second stream: the menu logs the
eye dialog answer (`eye switch dialog (was on): CONFIRM -> 61(false)`), EyeOnReceiver and the boot sync log their
calls, EyeGuard logs every 62 poke.
