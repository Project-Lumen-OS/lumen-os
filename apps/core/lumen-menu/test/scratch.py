#!/usr/bin/env python3
"""Mutation runner for the lumen-menu suites that assemble a device app (test/<suite>/run.sh): every mutant runs in a
scratch gsi/ whose files are symlinks to the repo's, only the mutated file is a copy, so the repo is never written.
A mutant is (name, path relative to gsi/, old text, new text); old must occur exactly once."""
import os, shutil, subprocess, sys, tempfile

G = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', '..'))
WALK = ('apps', 'devices', 'tools')
SKIP = ('build', '.gradle', '__pycache__')


def tree(tmp, rel=None):
    root = os.path.join(tmp, 'gsi')
    os.makedirs(root)
    for e in os.listdir(G):
        src = os.path.join(G, e)
        if e == '.git':
            continue
        if e not in WALK or not os.path.isdir(src):
            os.symlink(src, os.path.join(root, e))
            continue
        for d, dirs, files in os.walk(src):
            dirs[:] = [x for x in dirs if x not in SKIP]
            out = os.path.join(root, os.path.relpath(d, G))
            os.makedirs(out, exist_ok=True)
            for f in files:
                os.symlink(os.path.join(d, f), os.path.join(out, f))
    if rel:
        p = os.path.join(root, rel)
        os.unlink(p)
        shutil.copy(os.path.join(G, rel), p)
    return root


def run(root, suite, env=None):
    r = subprocess.run(['sh', os.path.join(root, suite)], capture_output=True, text=True,
                       env=dict(os.environ, **(env or {})))
    return r.returncode, r.stdout + r.stderr


def anchors(mutants):
    """The mutants whose old text is not in the repo exactly once: (count, name)."""
    bad = []
    for name, rel, old, _ in mutants:
        with open(os.path.join(G, rel), encoding='utf-8') as f:
            n = f.read().count(old)
        if n != 1:
            bad.append((n, name))
    return bad


def main(suite, mutants, env=None):
    only = sys.argv[sys.argv.index('-k') + 1] if '-k' in sys.argv else None
    broken = anchors(mutants)
    for n, name in broken:
        print('BROKEN mutant (pattern %d times): %s' % (n, name))
    if broken:
        sys.exit(2)
    tmp = tempfile.mkdtemp(prefix='menu_mut_')
    try:
        rc, out = run(tree(tmp), suite, env)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc != 0:
        print('BASELINE RED: ' + out[-800:])
        sys.exit(2)
    killed, tried = 0, 0
    for name, rel, old, new in mutants:
        if only and only not in name:
            continue
        tried += 1
        tmp = tempfile.mkdtemp(prefix='menu_mut_')
        try:
            root = tree(tmp, rel)
            target = os.path.join(root, rel)
            with open(target, encoding='utf-8') as f:
                s = f.read()
            with open(target, 'w', encoding='utf-8') as f:
                f.write(s.replace(old, new))
            rc, out = run(root, suite, env)
            if rc != 0:
                killed += 1
                why = next((l for l in out.splitlines() if 'FAIL' in l), 'red')
                print('killed   %-45s %s' % (name, why.strip()[:100]))
            else:
                print('SURVIVED ' + name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print('mutants: %d/%d killed' % (killed, tried))
    sys.exit(0 if killed == tried else 1)
