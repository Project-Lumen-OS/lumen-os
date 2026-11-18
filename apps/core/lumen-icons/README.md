# lumen-icons

The one icon set of every Lumen app: Material Symbols Rounded (weight 400, grade 0, optical size 24; filled only where
the stock pattern is filled) as Android vector drawables, generated from Google's official source at a pinned commit
by `tools/icons/gen_icons.py` (table `tools/icons/icons.tsv`). Apache License 2.0: the Licences screen of the updater
(`lic_icons`) and the About screen of Lumen Installer (`about_lic_icons`) name it.

A text glyph is never an icon: a tick, an arrow or a bullet character drawn by the font looks cheap next to the stock
Android TV rows and changes with every font.
`tools/icons/check_glyphs.py` refuses one in any string, layout or code literal of `apps/` (run by
`tools/build/build.py check` before every image and by `tools/arch/suites.sh`; test `tools/icons/test/check_glyphs_test.py`).

## Use

An app names the module in its recipe (`module lumen-icons`; stand-alone apps in `apps/<App>/app.layers`) and
`org.lumen.icons` in its `R_EXTRA_PACKAGES` (the module's code reads its drawables through `org.lumen.icons.R`).

| need | call |
|---|---|
| a mark at a list row's end, stock Android TV pattern | `Icons.radio(view, on)` single choice, `Icons.toggle(view, on)` on/off setting, `Icons.check(view, on)` several chosen, `Icons.chevron(c)` opens a page |
| any icon in a row | `Icons.view(c, R.drawable.lumen_ic_x, dp)`: never focusable, draws in the row's state |
| a step or check state | `Icons.status(view, IconStatus.DONE, color)` (a running step's arc turns) |
| a remote key in a hint | the string keeps the arrow (`IconText.LEFT`, `RIGHT`, `UP`, `DOWN`; \u2190 \u2192 too) and `IconText.keys(c, text)` draws it, or the layout uses `org.lumen.icons.KeyHintView`; list the string in `tools/icons/key_hints.tsv` |
| a key cap | `IconText.key(c, R.drawable.lumen_ic_key_left)` |
| a list item, a check line | `IconText.bullet(text, color, radius, gap)`, `IconText.mark(c, status, color, text)` |
| a canvas row | `IconText.draw` / `IconText.measure` (Row.drawTextAt does it) |

Colours: `@color/lumen_icon_tint` (white on the focused accent row, the TITLE text colour otherwise, OFF_TEXT when
disabled) and `@color/lumen_icon_tint_on` (the accent for a chosen mark, white when focused); a row with its own text
colours passes `Icons.tint(normal, focused, disabled)`. Arrows and chevrons along the reading direction mirror in a
right to left UI; the remote key arrows never do.

## Change the set

Add a row to `tools/icons/icons.tsv` (our name by meaning, the Material Symbols name, fill, `-`, meaning), run
`python3 tools/icons/gen_icons.py write`, commit the drawable and the recorded sha256. A new pin: change `PIN`, set every
sha256 to `-`, write, review the diff of every drawable.
