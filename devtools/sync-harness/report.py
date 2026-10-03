#!/usr/bin/env python3
"""report.py: results/*.csv -> a markdown table. Cells in both a 'before' and an 'after' file are shown side by side (seconds)."""
import csv, glob, collections, sys

rows = collections.defaultdict(dict)    # (kind, a, op, state) -> {(api, apk): value}
notes = {}
for f in sorted(glob.glob("results/*.csv")):
    for r in csv.DictReader(open(f)):
        key = (r["kind"], r["origin_or_discovery"], r["op"], r["state"])
        val = r["seconds"] if r["seconds"] else ("never (" + r["note"] + ")" if r["note"] else "")
        rows[key][(r["api"], r["apk"])] = val
cols = sorted({c for v in rows.values() for c in v}, key=lambda c: (int(c[0]), c[1] != "before"))
out = []
for kind, title in (("push", "Push: seconds from the change on the origin to the server"), ("recv", "Receive: seconds from the server change to the receiver showing it")):
    out.append(f"### {title}\n")
    out.append("| " + ("origin | op | state" if kind == "push" else "discovery | | state") + " | " + " | ".join(f"API {a} {b}" for a, b in cols) + " |")
    out.append("|---|---|---|" + "---|" * len(cols))
    for key in sorted(k for k in rows if k[0] == kind):
        out.append(f"| {key[1]} | {key[2] if kind == 'push' else ''} | {key[3]} | " + " | ".join(rows[key].get(c, "") for c in cols) + " |")
    out.append("")
print("\n".join(out))
