"""play6_tree.py: the default APP_DIR of the Play 6 projector checks (check_play6_hal.py, check_handshake.py).

apps/Play6Projector is only the Play 6 layer since the design notes; the full source tree is
assembled from devices/dragon_healthy/app/Play6Projector.layers by tools/devices/assemble_app.py. default_app()
writes that tree into a temporary folder, removed when the process exits, and returns its path. A recipe error
is printed and ends the process with exit 1.
"""
import atexit
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.dirname(os.path.dirname(HERE))


def default_app(prog):
    sys.path.insert(0, os.path.join(G, "tools", "devices"))
    import assemble_app  # noqa: E402
    recipe = assemble_app.find_recipe(G, "dragon_healthy", "Play6Projector")
    if not recipe:
        print("%s: ERROR no recipe for Play6Projector in devices/dragon_healthy/app/" % prog)
        sys.exit(1)
    files, _, _, errors = assemble_app.assemble(G, recipe)
    if errors:
        for e in errors:
            print("%s: ERROR %s: %s" % (prog, os.path.relpath(recipe, G), e))
        sys.exit(1)
    d = tempfile.mkdtemp(prefix="play6_tree.")
    atexit.register(shutil.rmtree, d, True)
    out = os.path.join(d, "Play6Projector")
    assemble_app.write(files, out)
    return out
