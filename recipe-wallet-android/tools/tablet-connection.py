#!/usr/bin/env python3
"""Check local preview connectivity; optionally restore USB forwarding on reconnect."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request


def find_adb():
    candidates = [os.environ.get("ADB"), shutil.which("adb")]
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            candidates.append(str(Path(os.environ[name]) / "platform-tools/adb"))
    candidates.append(str(Path.home() / "Library/Android/sdk/platform-tools/adb"))
    for candidate in candidates:
        if candidate and os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    raise SystemExit("Set ADB to your platform-tools/adb executable, or add adb to PATH.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Authorized tablet serial from adb devices")
    parser.add_argument("--port", type=int, default=4242, help="Local backend port (default 4242)")
    parser.add_argument("--watch", action="store_true", help="Restore the tunnel after USB reconnects until Ctrl-C")
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    adb = find_adb()
    previous = None

    def run(*command):
        result = subprocess.run([adb, "-s", args.serial, *command], capture_output=True, text=True, timeout=10)
        if result.returncode:
            raise RuntimeError("Tablet unavailable or not authorized; check USB and adb devices.")
        return result.stdout

    while True:
        ok = False
        try:
            if run("get-state").strip() != "device":
                raise RuntimeError("Tablet unavailable; reconnect USB and unlock it.")
            target = f"tcp:{args.port}"
            tunnels = [line.split()[-2:] for line in run("reverse", "--list").splitlines()]
            if ["tcp:4242", target] not in tunnels:
                run("reverse", "tcp:4242", target)
                print("Restored tablet backend connection.", flush=True)
            # Ignore HTTP proxy settings for this loopback-only health check.
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open(f"http://127.0.0.1:{args.port}/health", timeout=5) as response:
                if response.status != 200 or json.load(response).get("ok") is not True:
                    raise RuntimeError("Backend health check failed.")
            status = "Tablet USB tunnel and local backend are ready."
            ok = True
        except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
            status = "Backend unavailable; start it locally." if isinstance(error, OSError) else str(error)
        if status != previous:
            print(status, flush=True)
            previous = status
        if not args.watch:
            return 0 if ok else 1
        time.sleep(5)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nStopped watching; the existing USB tunnel is unchanged.")
