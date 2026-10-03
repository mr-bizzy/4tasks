# sync-harness: the two-device sync matrix

What it answers: **how many seconds from a change on one phone until the server has it, and until the other phone shows it**, for every way of making a change and every
state the app can be in. It runs real emulators against a real (local) CalDAV server; nothing is mocked. See `docs/SYNC-DESIGN.md` for why these cells.

## What it needs

* the Android emulator and system images (`sdkmanager "system-images;android-<API>;google_apis;x86_64"`), AVDs named `sync<API>_a` (origin) and `sync<API>_b` (receiver)
  (`avdmanager create avd -n sync37_a -k "system-images;android-37.0;google_apis;x86_64" -d pixel_8`, and `_b`). Ports: API 33 5560/5562, 34 5564/5566, 36 5568/5570, 37 5572/5574.
* Python 3 (Radicale is installed into `.work/venv` on first run), `adb`.
* A DEBUG build of 4Tasks, copied to `.work/after.apk` (`.work/before.apk` for a comparison build): `./gradlew :app:assembleGenericDebug`. The debug build carries
  `app/src/debug/.../harness/HarnessReceiver.kt` (never in release): SETUP (add the CalDAV account), SETDEFAULT (default list), ADDWIDGET (ask the launcher to pin the widget),
  DUMP (task state on logcat tag `HarnessProbe`).
* The 4Link dev caller (`devtools/fourlink-caller`, built to `app-debug.apk`), for the 4Link door origin.

## Running

```
cd devtools/sync-harness
python3 run.py --api 37 --apk after --cells smoke --no-periodic --out results/after-37.csv      # the per-build smoke subset
python3 run.py --api 37 --apk after --cells full --out results/after-37-full.csv                # every valid cell (hours)
python3 run.py --api 37 --apk after --cells "list:push=door/add/doze;widget/tick/bg:recv=periodic/bg" --out results/x.csv
python3 run.py --api 37 --apk before --cells smoke --out results/before-37.csv                  # a comparison build
```

Add `--keep` to leave the emulators running between runs. Each API version has its own Radicale (port 6000 + API) so versions can run side by side. The CSV columns are
`api, apk, kind, origin_or_discovery, op, state, seconds, note`; an empty `seconds` with a note means it never arrived in the window.

## The cells

* **push cells** (`kind=push`): origin (`door`, `inapp`, `widget`, `notification`, `share`, `tile`) x operation x state of the origin after the change.
  Operations an origin can make: door add/tick; in-app add/edit_title/edit_due/tick/untick/delete/move; widget add/tick; notification tick; share add; tile add.
  States: `fg` (the app stays open), `bg` (Home pressed at once), `killed` (Home, then the process killed 3 s later; for the door, force-stopped before the call), `doze`
  (`dumpsys deviceidle force-idle` right after the change; for the door, before the call). Seconds = from the moment the change is committed (the Save tap, the tick, the door's
  answer) to the moment the server's storage shows it.
* **recv cells** (`kind=recv`): a task is put on the server (as if another phone made it) and the receiver finds out by `open` (app opened), `pull` (pull-to-refresh), `widget`
  (the widget's refresh button) or `periodic` (nothing is done; the first background `SyncWork` is timed; 20-minute window). Seconds = from the server change to the receiver
  showing it (periodic: to the first background sync).

The tile cannot be clicked from adb, so the `tile` origin starts what the tile starts (`TileService.onClick`'s intent). The widget is pinned through the launcher's own prompt.

## Known limits

Emulators do not have a vendor's battery manager (Samsung's "sleeping apps" etc.), and a cached app's network block is Android's, not the vendor's, so the harness reproduces
the AOSP behaviour, not every phone's. API 33/34 images are Android 13/14; 36 and 37 are Android 16 and 17.
