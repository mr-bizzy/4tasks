#!/usr/bin/env python3
"""make_results.py: RESULTS.md from results/*.csv and results/probe-*.log."""
import glob, re, subprocess, datetime

tables = subprocess.run(["python3", "report.py"], capture_output=True, text=True).stdout
probe = []
for f in sorted(glob.glob("results/probe-*.log")):
    api, state = re.match(r"results/probe-(\d+)-(\w+)\.log", f).groups()
    probe.append(f"**API {api}, app {state}**\n\n```\n" + open(f).read().strip() + "\n```\n")
text = f"""# Sync harness results

Generated {datetime.date.today()} by `make_results.py` from `results/*.csv` (seconds; the harness is described in README.md, the reasoning in `docs/SYNC-DESIGN.md`).
**before** = 4Tasks 0.1.5 (a changed task goes to a 10 s delayed background job; the periodic job syncs by itself); **after** = 0.1.6 (the push is expedited right after a 1 s
debounce, and flushed when the app is left; the periodic job only hands an expedited sync over). Emulators: Android 13 (33), 14 (34), 16 (36) and 17 (37), Google APIs images,
software GPU; one change per cell, the app reset to a known state before each. An empty cell was not run; `never` means the change did not reach its destination in the window
(120 s for push and receive cells, 20 minutes for periodic).

**Not measured:** the 0.1.5 widget tick on API 33 and 36 (the harness's tap started the widget's COMPLETE_TASK activity but the task stayed open on the device, so there was nothing to
sync; on 34 and 37 the same cell worked, 14.3 s). **Periodic** cells are the time from a server change to the first background sync; the periodic job runs about every 15 minutes
from whenever it was last scheduled, so the figure depends on where the change falls in that cycle (0.1.5: 648 s on API 37; 0.1.6: 811-1037 s) and is not a before/after comparison;
what it shows is that the job runs, with the app in the background or killed, and that the sync it starts has network. In forced Doze the periodic job did not run in 20 minutes (0.1.6 on API 36; not run for 0.1.5): Android defers ordinary jobs in Doze, which is why pushes are expedited (every push-in-Doze cell above is 2-5 s on 0.1.6 apart from two 9-10 s on API 33).
A before cell that is `never` is a change that did not reach the server within 120 s (the 0.1.5 delayed job did not run while the app was cached or in Doze).

How to read the states: **fg** the app stays open; **bg** Home pressed at once; **killed** the process killed (door: force-stopped before the call; notification: killed as the
system does, so the notification stays); **doze** `deviceidle force-idle` after the change (door: before the call); **cached** (door) the app sat in the background for 75 s
before the call, long enough for Android to cut its UID off the network.

{tables}

## What Android does with jobs for a cached app (`probe_jobs.py`)

A probe job of each kind is enqueued by the debug app, the app is put in the state, and each job reports when it ran and whether a socket worked inside it.

{chr(10).join(probe)}
"""
open("RESULTS.md", "w").write(text)
print("RESULTS.md written,", len(text), "chars")
