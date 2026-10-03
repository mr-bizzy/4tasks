"""One rig = one Android version: a local Radicale, an ORIGIN emulator (where changes are made) and a RECEIVER emulator."""
import os, subprocess, time, lib, ops

EMULATOR = os.path.expanduser("~/Android/Sdk/emulator/emulator")
BASE_PORT = {33: 5560, 34: 5564, 36: 5568, 37: 5572}
CALLER_APK = os.path.join(os.path.dirname(lib.HERE), "fourlink-caller", "app", "build", "outputs", "apk", "debug", "app-debug.apk")


class Rig:
    def __init__(self, api, apk="after"):
        self.api = api
        self.apk = os.path.join(lib.WORK, f"{apk}.apk")
        base = BASE_PORT[api]
        self.server = lib.Server(6000 + api)
        self.origin = lib.Dev(f"emulator-{base}", f"{api}-origin")
        self.receiver = lib.Dev(f"emulator-{base + 2}", f"{api}-receiver")
        self._procs = []

    def _boot(self, dev, avd, port):
        if dev.adb("get-state").strip() == "device":
            return
        log = open(os.path.join(lib.WORK, f"emu-{avd}.log"), "w")
        self._procs.append(subprocess.Popen([EMULATOR, "-avd", avd, "-port", str(port), "-no-window", "-no-audio", "-no-snapshot",
                                              "-no-boot-anim", "-gpu", "swiftshader_indirect"], stdout=log, stderr=subprocess.STDOUT, start_new_session=True))

    def up(self):
        self.server.start()
        base = BASE_PORT[self.api]
        self._boot(self.origin, f"sync{self.api}_a", base)
        self._boot(self.receiver, f"sync{self.api}_b", base + 2)
        for d in (self.origin, self.receiver):
            d.wait_boot()
            d.install(self.apk)
            d.install(CALLER_APK)
        for d in (self.origin, self.receiver):
            d.reset_app(self.server)
        for d in (self.origin, self.receiver):
            if not ops.ensure_widget(d):
                raise RuntimeError(f"{d.label}: could not place the widget")

    def down(self):
        for d in (self.origin, self.receiver):
            d.adb("emu", "kill")
        self.server.stop()
