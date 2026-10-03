"""Helpers for the two-device sync harness: adb devices driven through uiautomator, and a local Radicale CalDAV server read from disk.

Everything here talks to real emulators and a real CalDAV server: no mocks. Times are host-clock seconds (the emulators run on the host's clock).
"""
import os, re, subprocess, time, glob, shutil, xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.environ.get("HARNESS_WORK", os.path.join(HERE, ".work"))   # radicale venv, storage, logs, results (ignored by git)
ADB = os.environ.get("ADB", "adb")
PKG = "uk.mr_biz.fourtasks"


def run(cmd, timeout=120, check=False):
    r = subprocess.run(cmd, shell=isinstance(cmd, str), capture_output=True, text=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(f"{cmd}: {r.stderr or r.stdout}")
    return r.stdout


class Node:
    def __init__(self, el):
        self.text = el.get("text") or ""
        self.desc = el.get("content-desc") or ""
        self.res = el.get("resource-id") or ""
        self.clickable = el.get("clickable") == "true"
        b = list(map(int, re.findall(r"\d+", el.get("bounds") or "0 0 0 0")))
        self.l, self.t, self.r, self.b = b[:4]
        self.cx, self.cy = (self.l + self.r) // 2, (self.t + self.b) // 2

    def __repr__(self):
        return f"Node({self.text!r},{self.desc!r},{self.l},{self.t},{self.r},{self.b})"


class Dev:
    """One emulator."""

    def __init__(self, serial, label=None):
        self.serial = serial
        self.label = label or serial

    # -- adb plumbing
    def adb(self, *args, timeout=120):
        return run([ADB, "-s", self.serial, *args], timeout=timeout)

    def sh(self, cmd, timeout=120):
        return self.adb("shell", cmd, timeout=timeout)

    def wait_boot(self, timeout=420):
        end = time.time() + timeout
        while time.time() < end:
            if self.sh("getprop sys.boot_completed").strip() == "1":
                return True
            time.sleep(3)
        raise TimeoutError(f"{self.serial} did not boot")

    @property
    def api(self):
        return int(self.sh("getprop ro.build.version.sdk").strip() or 0)

    # -- ui
    def dump(self):
        self.sh("uiautomator dump /sdcard/h.xml >/dev/null 2>&1")
        x = self.sh("cat /sdcard/h.xml")
        if "<?xml" not in x:
            return []
        root = ET.fromstring(x[x.index("<?xml"):])
        return [Node(n) for n in root.iter("node")]

    def find(self, text=None, desc=None, exact=False, nodes=None):
        for n in nodes if nodes is not None else self.dump():
            if text is not None and (n.text == text if exact else n.text.startswith(text)):
                return n
            if desc is not None and (n.desc == desc if exact else n.desc.startswith(desc)):
                return n
        return None

    def tap(self, x, y):
        self.sh(f"input tap {x} {y}")

    def tap_node(self, n):
        self.tap(n.cx, n.cy)

    def tap_text(self, text=None, desc=None, tries=1, wait=1.0, exact=False):
        for _ in range(tries):
            n = self.find(text=text, desc=desc, exact=exact)
            if n:
                self.tap_node(n)
                return n
            time.sleep(wait)
        return None

    def swipe(self, x1, y1, x2, y2, ms=250):
        self.sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")

    def key(self, k):
        self.sh(f"input keyevent {k}")

    def text(self, s):
        self.sh("input text " + s.replace(" ", "%s"))

    def screenshot(self, path):
        with open(path, "wb") as f:
            f.write(subprocess.run([ADB, "-s", self.serial, "exec-out", "screencap", "-p"], capture_output=True).stdout)

    # -- app state
    def home(self):
        self.key("KEYCODE_HOME")

    def start_app(self):
        self.sh(f"am start -n {PKG}/com.todoroo.astrid.activity.MainActivity >/dev/null")

    def force_stop(self):
        self.sh(f"am force-stop {PKG}")

    def kill(self):
        self.sh(f"am kill {PKG}")

    def doze(self, on):
        if on:
            self.sh("dumpsys battery unplug; cmd deviceidle enable all; cmd deviceidle force-idle")
        else:
            self.sh("cmd deviceidle unforce; dumpsys battery reset")

    def process_alive(self):
        return bool(self.sh(f"pidof {PKG}").strip())

    def install(self, apk):
        if "caller" not in apk:
            self.adb("uninstall", PKG)
        out = self.adb("install", "-r", "-d", "-g", apk, timeout=300)
        if "Success" not in out:
            raise RuntimeError(f"{self.serial}: install of {apk} failed: {out}")

    def reset_app(self, server, settle=60):
        """A clean app: data cleared, permissions as a user would give them, the harness account added and synced once."""
        self.doze(False)
        self.force_stop()
        self.sh(f"pm clear {PKG}")
        self.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
        self.sh(f"cmd appops set {PKG} SCHEDULE_EXACT_ALARM allow")
        self.sh(f"am set-standby-bucket {PKG} active")
        self.sh("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0")
        self.start_app()
        time.sleep(4)
        self.setup_account(server.port)
        # the first sync adds the server's lists; wait for it by putting a marker task on the server and seeing it arrive
        server.put("__marker__"); server.put("__marker2__", coll="tasks2")
        end = time.time() + settle
        while time.time() < end:
            t = self.probe(timeout=6)
            if t and "__marker__" in t and "__marker2__" in t:
                break
            time.sleep(2)
        else:
            raise TimeoutError(f"{self.serial}: account did not sync")
        self.sh(f'am broadcast -n {PKG}/org.tasks.harness.HarnessReceiver -a org.tasks.harness.SETDEFAULT --es list tasks >/dev/null')
        time.sleep(1.5)
        for coll in ("tasks", "tasks2"):
            for f in glob.glob(os.path.join(server.root, coll, "*.ics")):
                if "__marker" in open(f).read():
                    os.remove(f)

    # -- the app, through the debug receiver
    def setup_account(self, port):
        self.sh(f'am broadcast -n {PKG}/org.tasks.harness.HarnessReceiver -a org.tasks.harness.SETUP '
                f'--es url http://10.0.2.2:{port}/test/ --es user test --es password test')

    def probe(self, timeout=20):
        """Task state as the app sees it: {title: (completed_ms, due_ms, list)}; None when no answer."""
        self.adb("logcat", "-c")
        self.sh(f"am broadcast -n {PKG}/org.tasks.harness.HarnessReceiver -a org.tasks.harness.DUMP >/dev/null")
        end = time.time() + timeout
        while time.time() < end:
            out = self.adb("logcat", "-d", "-s", "HarnessProbe:I")
            if "END|" in out:
                tasks = {}
                for line in out.splitlines():
                    m = re.search(r"HarnessProbe: T\|(\d+)\|(.*)\|(\d+)\|(\d+)\|(.*)$", line)
                    if m:
                        tasks[m.group(2)] = (int(m.group(3)), int(m.group(4)), m.group(5))
                return tasks
            time.sleep(0.4)
        return None

    def logcat_lines(self, pattern):
        out = self.adb("logcat", "-d", "-v", "epoch")
        return [l for l in out.splitlines() if re.search(pattern, l)]


def epoch_of(line):
    m = re.match(r"\s*(\d+\.\d+)", line)
    return float(m.group(1)) if m else None


class Server:
    """The local Radicale: started by start(), read straight from its storage folder."""

    def __init__(self, port=5987):
        self.port = port
        self.dir = os.path.join(WORK, f"radicale-{port}")
        self.root = os.path.join(self.dir, "radcol", "collection-root", "test")
        self.proc = None

    def start(self):
        os.makedirs(WORK, exist_ok=True)
        venv = os.path.join(WORK, "venv")
        if not os.path.exists(os.path.join(venv, "bin", "radicale")):
            run(f"python3 -m venv {venv} && {venv}/bin/pip install -q radicale", timeout=300, check=True)
        os.makedirs(self.dir, exist_ok=True)
        users = os.path.join(self.dir, "users")
        open(users, "w").write("test:test\n")
        conf = os.path.join(self.dir, "radicale.conf")
        open(conf, "w").write(f"[server]\nhosts = 0.0.0.0:{self.port}\n[auth]\ntype = htpasswd\nhtpasswd_filename = {users}\n"
                              f"htpasswd_encryption = plain\n[storage]\nfilesystem_folder = {os.path.join(self.dir, 'radcol')}\n[logging]\nlevel = info\n")
        self.log = os.path.join(self.dir, "radicale.log")
        if run(f"curl -s -o /dev/null -w '%{{http_code}}' -u test:test http://127.0.0.1:{self.port}/test/").strip() in ("207", "403", "404"):
            for coll in ("tasks", "tasks2"):
                self.mkcol(coll)
            return   # already running (started by an earlier run, or by hand)
        self.proc = subprocess.Popen([f"{venv}/bin/python", "-m", "radicale", "--config", conf], stdout=open(self.log, "a"), stderr=subprocess.STDOUT, start_new_session=True)
        for _ in range(40):
            if run(f"curl -s -o /dev/null -w '%{{http_code}}' -u test:test http://127.0.0.1:{self.port}/test/").strip() in ("207", "403", "404"):
                break
            time.sleep(0.25)
        for coll in ("tasks", "tasks2"):
            self.mkcol(coll)

    def stop(self):
        if self.proc:
            self.proc.terminate()

    def mkcol(self, coll):
        body = ('<?xml version="1.0" encoding="UTF-8" ?><create xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"><set><prop><resourcetype><collection/>'
                '<C:calendar/></resourcetype><C:supported-calendar-component-set><C:comp name="VTODO"/></C:supported-calendar-component-set>'
                f'<displayname>{coll}</displayname></prop></set></create>')
        run(["curl", "-s", "-o", "/dev/null", "-u", "test:test", "-X", "MKCOL", f"http://127.0.0.1:{self.port}/test/{coll}/",
             "-H", "Content-Type: application/xml", "-d", body])

    # -- state
    def tasks(self, coll="tasks"):
        out = {}
        for f in glob.glob(os.path.join(self.root, coll, "*.ics")):
            try:
                text = open(f).read()
            except OSError:
                continue
            m = re.search(r"SUMMARY:(.*)", text)
            if not m:
                continue
            title = m.group(1).strip()
            out[title] = {
                "file": f,
                "uid": (re.search(r"UID:(.*)", text) or [None, ""])[1].strip(),
                "completed": "STATUS:COMPLETED" in text or "COMPLETED:" in text,
                "due": (re.search(r"DUE[^:]*:(.*)", text) or [None, None])[1],
                "text": text,
            }
        return out

    def wipe(self):
        for coll in ("tasks", "tasks2"):
            for f in glob.glob(os.path.join(self.root, coll, "*.ics")):
                os.remove(f)

    def put(self, title, due=None, completed=False, coll="tasks", alarm_in=None):
        import uuid
        uid = str(uuid.uuid4())
        lines = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//harness//EN", "BEGIN:VTODO", f"UID:{uid}",
                 "DTSTAMP:" + time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()), "CREATED:" + time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()),
                 f"SUMMARY:{title}"]
        if due:
            lines.append(f"DUE:{due}")
        if completed:
            lines += ["STATUS:COMPLETED", "COMPLETED:" + time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())]
        if alarm_in is not None:
            at = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime(time.time() + alarm_in))
            lines += ["BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:harness", f"TRIGGER;VALUE=DATE-TIME:{at}", "END:VALARM"]
        lines += ["END:VTODO", "END:VCALENDAR", ""]
        code = run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "-u", "test:test", "-X", "PUT",
                    f"http://127.0.0.1:{self.port}/test/{coll}/{uid}.ics", "-H", "Content-Type: text/calendar",
                    "--data-binary", "\r\n".join(lines)])
        return uid, code.strip(), time.time()

    def wait_for(self, predicate, timeout=120, poll=0.25):
        end = time.time() + timeout
        while time.time() < end:
            if predicate():
                return time.time()
            time.sleep(poll)
        return None
