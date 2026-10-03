"""0.1.5 -> current: does the periodic job move from SyncWork to PeriodicSyncWork? Usage: upgrade_check.py SERIAL APK_AFTER"""
import os, sqlite3, subprocess, sys, time
import lib
from lib import Dev, Server, PKG

serial, after = sys.argv[1], sys.argv[2]
d = Dev(serial)
srv = Server(port=6999); srv.start()

def specs(tag):
    d.sh(f"run-as {PKG} sh -c 'cp -r no_backup/androidx.work.workdb* cache/ 2>/dev/null; true'")
    tmp = f"/tmp/wm-{serial}.db"
    for suffix in ("", "-wal", "-shm"):
        subprocess.run(f"adb -s {serial} exec-out run-as {PKG} cat cache/androidx.work.workdb{suffix} > {tmp}{suffix}", shell=True)
    c = sqlite3.connect(tmp)
    rows = c.execute("select w.worker_class_name, w.interval_duration, w.state from WorkSpec w join WorkName n on n.work_spec_id=w.id where n.name=?", (tag,)).fetchall()
    c.close()
    return rows

d.install(lib.WORK + "/before.apk")
d.reset_app(srv)
time.sleep(5)
print("0.1.5 tag_background_sync:", specs("tag_background_sync"))
d.adb("install", "-r", "-d", "-g", after, timeout=300)
d.start_app(); time.sleep(10)
print("0.1.6 tag_background_sync:", specs("tag_background_sync"))
print("0.1.6 tag_periodic_sync:", specs("tag_periodic_sync"))
srv.stop()
