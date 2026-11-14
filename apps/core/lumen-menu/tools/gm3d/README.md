# Game menu controller, real-time model (assets/gm3d)

The status card of the game menu shows the console's controller turning, then swaying. Since gm3d it is drawn on the
GPU (Gm3dView, OpenGL ES 3.0) from a baked model per theme; the 54-frame strip (assets/gamemenu/<theme>/) stays as
the fallback (Gm3dGate). This folder bakes the models from the same scenes that rendered the frames.

## Sources and licences

The models are the ones of the frame strip (qp_design/models/README.md): DualSense by Taohid Animation, Xbox One
controller by paxillop (also the generic pad, recoloured), Joy-Cons by Wille Evensson, all CC BY 4.0. They ship as
reduced meshes now, so the credit lines in GmLicences say "reduced and converted to a real-time model". Same logos and
symbols removed as in the frames (scenes.json: hide / drop / cap / paint).

## Pipeline

1. `export.py` loads each scene with `export.js` (three.js, the frame renderer's own scene code: hide, drop, paint,
   cap, recolour, normalise, framing) in an offscreen WKWebView (`gm3dsnap.swift`), and writes the parts, materials,
   textures, studio lights, the framing table (camera distance per yaw) and the room environment (PMREM layers).
2. `bake_mesh.mjs` merges parts per material, welds, simplifies with meshoptimizer (error 0.0005 of the model
   width, borders locked) and reorders for the vertex cache.
3. `bake.py model` packs one theme into `<theme>.lgm`: int16 positions, 10:10:10:2 normals and tangents, half
   float UVs, 16-bit indices where they fit, ETC2 / EAC textures with mipmaps (constant maps folded into the material,
   used UV area only, at most 512 px). `bake.py env` packs the environment into `env.lge` (RGB9_E5 equirect layers by
   roughness).
4. `preview.py` / `preview.js` render the baked files with the device shaders in WebGL2 next to the shipped frames.

Format: Gm3dModel.java (the only reader on the device; every offset, size and index checked before GL sees it).
Budget: under 1.5 MB per model (ps 1.31 MB, switch 0.75, xbox 0.37, pad 0.37; env 0.36), read + parse under 20 ms.

## Device

- Gm3dView: TextureView over the card's picture slot (GmStage), own EGL context (ES 3.0, RGBA8888) on one shared GL
  thread, 4x MSAA at twice the slot size, resolved and scaled down, one frame per display refresh while visible,
  nothing while hidden, all GL objects and the context released when the menu closes.
- Motion (Gm3dMotion): the strip's turn (360 degrees, same easing) then a 7 degree sway with the idle frames' period,
  plus a small float, continuous at 60 fps.
- Fallback (Gm3dGate): the strip when `setprop debug.lumen.gm3d 0`, when the window draws in software, when a model
  file is bad (that menu), when GL fails (the whole process) or when no first frame arrives in 2.5 s. The strip's
  hero frame holds the card while the model loads.
- Logs (Z9xGameMenu): `gm3d <theme>: first frame N ms after the ask (read, parse, gl)` and on close
  `gm3d <theme>: F frames in S s (fps), L late, M ms per frame on the GL thread`.

## Tests

`sh test/gm3d/run.sh` (reader, framing, motion, gate, wiring; `GM_APK=... ` also checks the APK's assets) and
`python3 test/gm3d/mutate.py` (fallback and reader mutants).
