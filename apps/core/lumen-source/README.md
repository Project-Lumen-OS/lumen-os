# lumen-source

The Source key of every Lumen projector, built once (owner 2026-11-18: "just show that the source switched,
automatically"). Stock behaviour per model with evidence: `gsi-research/SOURCE_KEY_STOCK.md`.

- `SourceKey.onKey(context, event)`: the key receivers call it for `KEYCODE_TV_INPUT`; it acts on the first DOWN
  (stock: `pressBehaver 0`, no long press action). `SourceKey.press(context, why)`: the same step for the Source
  request receivers and the quick menu's Input tile on models without an input popup.
- `SourceCycle` (pure): the stock `XrmShortcutEvent.jumpToHdmi` rules. `SourcePress` (pure): a press while the card is
  up goes on from the last step. `OsdText` (pure): "HDMI 1 · PlayStation 5".
- `SourceInputs`: our HDMI inputs from the TV input framework (org.lumen.tvinput), their state, the CEC name, and
  which port is on screen (org.lumen.tvinput's `hdmi_state` provider).
- `SourceOsd`: the card, one overlay window, Lumen 2.0 palette (follows the theme), Inter. A new press while it is up
  changes the text in place and restarts the timer: no new slide, no blink.
- `SourceKey.openPort` / `SourceKey.goHome`: the one way every Lumen app part opens an input (boot source, the
  quick menu's input popup).

Data `devices/<c>/source.tsv`: `after_last` (`home` or `first`), `osd_ms`. Tests: `bash apps/core/lumen-source/test/run.sh`.
