#!/usr/bin/env python3
"""server.py start|stop: the local Radicale the harness uses, kept running between runs."""
import sys, os, signal, lib
if sys.argv[1] == "start":
    s = lib.Server(int(sys.argv[2]) if len(sys.argv) > 2 else 5987); s.start(); print("radicale on", s.port, s.root)
else:
    out = lib.run("ss -ltnp | grep ':5987'")
    import re
    for pid in re.findall(r"pid=(\d+)", out):
        os.kill(int(pid), signal.SIGTERM)
