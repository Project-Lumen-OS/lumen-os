# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""src_cmp.py DEXDUMP OURS.dex SHIPPED.dex OUTER...: our javac+d8 compile of a LineageOS 21 source against the class
in the shipped (R8-optimised) services.jar. Byte equality cannot hold (R8 inlines, rewrites switches and member owners),
so per class family (OUTER and its nested classes) it compares: the methods (name + signature), and the multiset of
referenced member names + signatures, strings and int literals of each method (owners, registers, offsets, branch
shapes ignored). Prints SAME or the differences of each class family."""
import collections
import re
import subprocess
import sys

dexdump, ours, shipped, outers = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]


def parse(dex):
    out = subprocess.run([dexdump, "-d", dex], capture_output=True, text=True).stdout
    m, cur, meth = {}, None, None
    for l in out.splitlines():
        r = re.match(r"\s*Class descriptor\s*:\s*'(\S+)'", l)
        if r:
            cur = r.group(1); m[cur] = collections.defaultdict(collections.Counter); meth = None
            continue
        if cur is None:
            continue
        r = re.match(r"^[0-9a-f]{6}:\s+\|\[[0-9a-f]{6}\] \S+?\.([^.]+:\(.*)$", l)
        if r:
            meth = r.group(1); m[cur][meth]["<m>"] += 1
            continue
        r = re.match(r"^[0-9a-f]{6}:(?: [0-9a-f]{4})*\s*\|[0-9a-f]{4}: (.*)$", l)
        if r and meth:
            ins = r.group(1)
            for owner, ref in re.findall(r"(L[\w/$]+;)\.([\w$<>]+:\S+)", ins):
                # compiler glue, not source: R8 moves a lambda body into its synthetic class (d8 calls it there),
                # javac 17 reads the outer instance field where it needs it, and d8 folds new StringBuilder() +
                # append(String) into new StringBuilder(String)
                if ref.startswith("lambda$") or ref.startswith("this$"):
                    continue
                if owner == "Ljava/lang/StringBuilder;" and ref == "<init>:(Ljava/lang/String;)V":
                    m[cur][meth]["ref <init>:()V"] += 1
                    m[cur][meth]["ref append:(Ljava/lang/String;)Ljava/lang/StringBuilder;"] += 1
                    continue
                m[cur][meth]["ref " + re.sub(r"\$\$ExternalSyntheticLambda\d+|\$\d+", "$", ref)] += 1
            for s in re.findall(r'string@[0-9a-f]+', ins):
                pass
            q = re.search(r'const-string(?:/jumbo)? v\d+, (".*") //', ins)
            if q: m[cur][meth]["str " + q.group(1)] += 1
    return m


def fam(m, o):
    pre = "Lcom/android/server/hdmi/%s" % o
    meths = collections.defaultdict(collections.Counter)
    for k, v in m.items():
        if k == pre + ";" or k.startswith(pre + "$"):
            for name, c in v.items():
                if "lambda$" in name or name.startswith("$r8"):
                    name = "<lambda>"
                meths[re.sub(r"\$\$ExternalSyntheticLambda\d+|\$\d+", "$", name)] += c
    return meths


a, b = parse(ours), parse(shipped)
bad = 0
for o in outers:
    fa, fb = fam(a, o), fam(b, o)
    diffs = []
    for name in sorted(set(fa) | set(fb)):
        if name == "<lambda>":
            continue
        if fa.get(name) != fb.get(name):
            x, y = fa.get(name, collections.Counter()), fb.get(name, collections.Counter())
            only_o, only_s = x - y, y - x
            diffs.append("%s: ours+%s shipped+%s" % (name, dict(only_o), dict(only_s)))
    if not diffs:
        print("SAME %s" % o)
    else:
        bad += 1
        print("DIFF %s (%d methods differ)" % (o, len(diffs)))
        for d in diffs[:25]:
            print("   " + d[:400])
sys.exit(1 if bad else 0)
