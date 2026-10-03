#!/usr/bin/env python3
"""probe_jobs.py <api> <bg|killed|doze> <wait seconds>: which kinds of background job does Android run for this app in that state?

Enqueues, from the app (debug build), a probe job of each kind (delayed / expedited, with and without a network constraint, one-time and
periodic) and then puts the app in the state; reports when each job ran and whether a socket worked inside it.
"""
import sys, time, re, lib, rig as rigmod

api, state, wait = int(sys.argv[1]), sys.argv[2], int(sys.argv[3])
base = rigmod.BASE_PORT[api]
side = int(sys.argv[4]) if len(sys.argv) > 4 else 0
d = lib.Dev(f"emulator-{base + side}")
url = f"http://10.0.2.2:{6000 + api}/test/"
d.install(lib.WORK + "/after2.apk")
d.start_app(); time.sleep(5)
d.adb("logcat", "-c")
d.sh(f"am broadcast -n {lib.PKG}/org.tasks.harness.HarnessReceiver -a org.tasks.harness.PROBEJOBS --es url {url} --ei delay 20 --ei period 15 >/dev/null")
t0 = time.time()
time.sleep(1)
if state in ("bg", "killed", "doze"):
    d.home()
if state == "killed":
    time.sleep(2); d.kill()
if state == "doze":
    time.sleep(1); d.doze(True)
print(f"api {api} state {state}: probes enqueued; waiting {wait}s", flush=True)
seen = {}
runs = {}
end = t0 + wait
while time.time() < end:
    for l in d.logcat_lines(r"HarnessProbe: JOB\|"):
        m = re.search(r"JOB\|([a-z-]+)\|ran\|network=(\w+)\|http=(\S+)\|attempt=(\d+)", l)
        if m:
            key = m.group(1)
            runs.setdefault(key, [])
            rec = (round(lib.epoch_of(l) - t0, 1), m.group(2), m.group(3))
            if rec not in runs[key]:
                runs[key].append(rec)
            seen[key] = runs[key]
    time.sleep(5)
d.doze(False)
for mode in ("delayed-connected", "delayed-free", "expedited-connected", "expedited-free", "periodic-connected", "periodic-free", "periodic-kick", "kicked-expedited"):
    rs = runs.get(mode)
    print(f"{mode:20s}", "; ".join(f"ran at {r[0]} s (network={r[1]}, http={r[2]})" for r in rs) if rs else "did not run")
