#!/usr/bin/env python3
"""The two-device sync matrix.

  push cells : an operation on the ORIGIN (origin x operation x state of the origin) -> seconds from the change to the SERVER.
  recv cells : a change on the server -> seconds until the RECEIVER shows it, by how it finds out (open, pull, widget, periodic).

python3 run.py --api 37 --apk after --cells smoke --out results/after-37.csv
"""
import argparse, csv, itertools, os, sys, time, lib, ops, rig as rigmod

SMOKE_PUSH = [("door", "add", "fg"), ("door", "add", "killed"), ("door", "tick", "bg"), ("inapp", "add", "bg"), ("inapp", "tick", "bg"),
              ("inapp", "tick", "killed"), ("inapp", "edit_title", "fg"), ("inapp", "delete", "bg"), ("share", "add", "bg"), ("tile", "add", "bg"),
              ("widget", "tick", "bg"), ("notification", "tick", "bg")]
SMOKE_RECV = [("open", "bg"), ("pull", "fg"), ("widget", "bg")]


def all_push():
    for origin, oplist in ops.ORIGINS.items():
        for op in oplist:
            for state in ops.STATES:
                yield (origin, op, state)


def all_recv():
    for disc, states in (("open", ["bg", "killed"]), ("pull", ["fg"]), ("widget", ["bg"]), ("periodic", ["bg", "killed", "doze"])):
        for st in states:
            yield (disc, st)


# ------------------------------------------------------------------------------------------------------------ state of a device
UI_ORIGINS = {"inapp", "widget", "share", "tile"}


def before(dev, origin, state):
    """For origins that do not need the screen (door, notification) the state is set BEFORE the change."""
    if origin in UI_ORIGINS:
        return
    if state == "fg":
        dev.start_app(); time.sleep(1.5)
    elif state == "bg":
        dev.start_app(); time.sleep(1.5); dev.home(); time.sleep(1)
    elif state == "killed":
        dev.force_stop(); time.sleep(1)
    elif state == "doze":
        dev.home(); time.sleep(1); dev.doze(True); time.sleep(1.5)


def after(dev, origin, state):
    """For origins that use the screen the state is entered right AFTER the change is committed."""
    if origin not in UI_ORIGINS:
        return
    if state == "bg":
        dev.home()
    elif state == "killed":
        dev.home(); time.sleep(3); dev.kill()
    elif state == "doze":
        dev.home(); time.sleep(0.5); dev.doze(True)


# ------------------------------------------------------------------------------------------------------------ push cell
def push_cell(rig, origin, op, state, timeout=120):
    d, s = rig.origin, rig.server
    d.doze(False)
    stamp = str(int(time.time()))[-6:]
    o = ops.make_op(s, op, stamp)
    s.wipe()
    alarm = 45 if origin == "notification" else None
    t_put = time.time()
    if o.needs or origin == "notification":
        s.put(o.title, completed=o.needs_completed, alarm_in=alarm)
        if not ops.ensure_on_device(d, o.title, only=True):
            return None, "precondition: task did not reach the origin"
    else:
        ops.open_list(d); ops.pull_refresh(d); time.sleep(5)
    if origin == "notification":
        d.home()
        time.sleep(max(0, t_put + 52 - time.time()))   # the reminder fires 45 s after the put
    if origin == "widget":
        d.home(); time.sleep(1)
    elif origin in UI_ORIGINS:
        d.start_app(); time.sleep(1.5)
    before(d, origin, state)
    func = ops.ORIGIN_FUNCS[origin]
    t0 = func(d, op, o)
    if t0 is None:
        d.doze(False)
        return None, "op failed"
    after(d, origin, state)
    t = s.wait_for(lambda: o.expect(s), timeout)
    d.doze(False)
    return (None if t is None else round(t - t0, 1)), ("" if t else f">{timeout}s")


# ------------------------------------------------------------------------------------------------------------ receive cell
def recv_cell(rig, disc, state, timeout=120):
    r, s = rig.receiver, rig.server
    r.doze(False)
    s.wipe()
    r.force_stop() if state == "killed" else None
    ops.open_list(r); ops.pull_refresh(r); time.sleep(6)       # receiver empty and freshly synced
    time.sleep(35)                                             # past the 30 s on-open guard
    title = "R" + str(int(time.time()))[-6:]
    if disc == "periodic":
        timeout = 20 * 60
    if disc == "pull":
        ops.open_list(r); time.sleep(1.5)
    elif state == "bg" or disc == "widget":
        r.start_app(); time.sleep(1.5); r.home(); time.sleep(1)
    elif state == "killed":
        r.home(); time.sleep(1); r.force_stop()
    elif state == "doze":
        r.start_app(); time.sleep(1.5); r.home(); time.sleep(1); r.doze(True)
    r.adb("logcat", "-c")
    _, _, t_s = s.put(title)
    if disc == "open":
        r.start_app()
    elif disc == "pull":
        ops.pull_refresh(r)
    elif disc == "widget":
        n = r.find(desc="Sync now", exact=True)
        if n:
            r.tap_node(n)
    if disc == "periodic":
        end = time.time() + timeout
        while time.time() < end:
            lines = r.logcat_lines(r"SyncWork: Sync started, source=BACKGROUND|CaldavSync: pushing|updating CaldavTask|MULTI")
            hit = [l for l in lines if "BACKGROUND" in l]
            if hit:
                r.doze(False)
                return round(lib.epoch_of(hit[0]) - t_s, 1), "first background SyncWork"
            time.sleep(10)
        r.doze(False)
        return None, f">{timeout}s"
    end = time.time() + timeout
    while time.time() < end:
        got = r.probe(timeout=6) or {}
        if title in got:
            r.doze(False)
            return round(time.time() - t_s, 1), ""
        time.sleep(0.5)
    r.doze(False)
    return None, f">{timeout}s"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", type=int, required=True)
    ap.add_argument("--apk", default="after")
    ap.add_argument("--cells", default="smoke", help="smoke | full | push | recv | origin=door,op=add ...")
    ap.add_argument("--out", required=True)
    ap.add_argument("--keep", action="store_true", help="leave the emulators running")
    ap.add_argument("--no-periodic", action="store_true")
    a = ap.parse_args()
    rig = rigmod.Rig(a.api, a.apk)
    rig.up()
    push = list(SMOKE_PUSH) if a.cells == "smoke" else list(all_push()) if a.cells in ("full", "push") else []
    recv = list(SMOKE_RECV) if a.cells == "smoke" else list(all_recv()) if a.cells in ("full", "recv") else []
    if a.cells.startswith("list:"):
        # list:push=door/add/doze;widget/tick/bg:recv=periodic/bg
        parts = dict(x.split("=", 1) for x in a.cells[5:].split(":") if x)
        push = [tuple(c.split("/")) for c in parts.get("push", "").split(";") if c]
        recv = [tuple(c.split("/")) for c in parts.get("recv", "").split(";") if c]
    if a.cells == "periodic":
        recv = [c for c in all_recv() if c[0] == "periodic"]
    if a.cells.startswith("origin="):
        want = dict(kv.split("=") for kv in a.cells.split(","))
        push = [c for c in all_push() if all(c[i] == want[k] for i, k in enumerate(("origin", "op", "state")) if k in want)]
    if a.no_periodic:
        recv = [c for c in recv if c[0] != "periodic"]
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    new = not os.path.exists(a.out)
    with open(a.out, "a", newline="") as f:
        w = csv.writer(f)
        if new:
            w.writerow(["api", "apk", "kind", "origin_or_discovery", "op", "state", "seconds", "note"])
        for origin, op, state in push:
            try:
                secs, note = push_cell(rig, origin, op, state)
            except Exception as e:
                secs, note = None, f"error: {e!r}"
            w.writerow([a.api, a.apk, "push", origin, op, state, secs, note]); f.flush()
            print("push", origin, op, state, secs, note, flush=True)
        for disc, state in recv:
            try:
                secs, note = recv_cell(rig, disc, state)
            except Exception as e:
                secs, note = None, f"error: {e!r}"
            w.writerow([a.api, a.apk, "recv", disc, "add", state, secs, note]); f.flush()
            print("recv", disc, state, secs, note, flush=True)
    if not a.keep:
        rig.down()


if __name__ == "__main__":
    main()
