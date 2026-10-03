# 4Tasks: how sync works, and why

Written 2026-10-03 after four sync bugs in one day (a push that never left the phone, an app-open that never synced, a delayed job Android held for ever, a worker that
"succeeded" without a network). The owner asked for one coherent design instead of more patches. This is it, with the measurements that back it.
`file:line` references are to this repository at the commit that carries this document; upstream references are to Tasks.org 15.12 (`1211fc4694`).

## 1. What sync is

The database on the phone is the truth the user sees; a sync account (CalDAV, Microsoft To Do, Google Tasks) is a replica. Two things move data:

* **Push:** every local change, from any origin, marks the task *dirty* at the data layer: `TaskDao.kt:84,197,231`, `DeletionDao.kt:68`, `CaldavDao.kt:274,298`
  all call `DirtyDao.setDirty` (`DirtyDao.kt:72-83`: a row in `task_dirty`, `dirty_version > synced_version`). There is exactly ONE signal that something needs pushing,
  whether the change came from the list, the edit screen, the widget, a notification action, the share sheet, the quick-settings tile or the 4Link door.
* **Pull:** the synchroniser (`CaldavSynchronizer` and its siblings) fetches the server's changes. There is no server push (see 4).

Both happen inside one worker, `SyncWork` (`app/.../jobs/SyncWork.kt`), which runs each account's synchroniser: push the dirty tasks, then pull. So "when does it sync" is
only the question "when does `SyncWork` run".

## 2. The triggers: what starts `SyncWork`, where it runs, what we expect

Everything goes through `SyncAdapters` (`kmp/.../sync/SyncAdapters.kt`), which decides *whether* and hands *how* to `WorkManagerImpl.sync` (`WorkManagerImpl.kt:101-122`).
How is decided by the `SyncSource` (`kmp/.../sync/SyncSource.kt`): `waitsInWorkManager`, `expedited`, `showIndicator`.

| Trigger | Path | Runs as | Latency we aim for |
|---|---|---|---|
| Local change (any origin) | `hasDirtyTasks()` flow (`SyncAdapters.kt:81`) → 1 s debounce (`:176`) → `runSync(TASK_CHANGE)` (`:122`) | **expedited**, no delay (`SyncSource.TASK_CHANGE`) | push in seconds |
| Leaving the app | `onPause` → `flushPending()` (`TasksApplication.kt:114`, `SyncAdapters.kt:156`) | in-process, then expedited (`APP_BACKGROUND`) | at once |
| App opened | `onResume`, unless it synced < 30 s ago (`TasksApplication.kt:107-108`) → `APP_RESUME` | immediate | pull in seconds |
| Pull-to-refresh | `TaskListFragment.onRefresh` (`:294`) → `USER_INITIATED` | **expedited** | seconds |
| Widget refresh button | `WidgetSyncReceiver` → `USER_INITIATED` | **expedited** | seconds |
| Periodic | `WorkManagerImpl.updateBackgroundSync` (`:124-145`), 15 / 30 / 60 min (Settings, Accounts) | WorkManager periodic job | the interval (a safety net, not a latency promise) |
| After boot | `SystemEventReceiver` starts the process; `TasksApplication.backgroundWork()` builds `SyncAdapters` (`:188-193`), whose flow pushes anything dirty; WorkManager restores the periodic job itself | in-process, expedited | push at once; pull at the next trigger |
| Network regained | the `CONNECTED` constraint on the job; and `SyncWork` returns `Result.retry()` when there is none (`SyncWork.kt:103`) | WorkManager | when Android grants it |
| Account added | `SyncAdapters` watches the account count (`:95-105`) → `ACCOUNT_ADDED` | immediate | seconds |

The rule behind the table: **nothing a person is waiting on depends on a delayed background job.** Pushes are expedited and start from the process that is still on screen;
the periodic job is only the net underneath.

## 3. What Android does to a background app (and what we saw)

* **Standby buckets, Doze, Data Saver** (the documented set): a job may be deferred in a low bucket or in Doze; Data Saver blocks background data on metered networks.
* **The per-UID background network block** (not documented as a feature, but in every `dumpsys netpolicy`): when the app's process is cached, Android puts the UID in
  `blocked=APP_BACKGROUND` (`allowed=NONE`). On the owner's Galaxy S25 (Android 17) a `SyncWork` job with a 10 s initial delay sat in JobScheduler for **9 minutes** with
  `Unsatisfied constraints: CONNECTIVITY` although Wi-Fi was validated, the bucket was ACTIVE and Data Saver off. A job whose network request belongs to a blocked UID is never
  satisfied until the app comes to the foreground. The Android 16 emulator shows the same `blocked=APP_BACKGROUND` state for a cached process, but its delayed jobs did run, late
  (30 s of delay became 50-60 s).
* **Expedited jobs** run when scheduled, at the app's priority, within a quota (`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST` falls back to ordinary work when the quota is
  used up).

## 4. Inherited Tasks.org assumptions that no longer hold

1. **"Opening the app syncs."** Upstream's `SyncSource.upgrade` (`SyncSource.kt:18-22`, 15.12) keeps `NONE` against any source that shows no indicator and is not delayed, which includes
   `APP_RESUME`; `runSync(NONE)` returns at once (`SyncAdapters.kt:122`, 15.12). So `TasksApplication.onResume` (`TasksApplication.kt:104-105`, 15.12) never started a sync.
   **This is upstream's bug, not ours:** it arrived in upstream commit `677dec5170` (2026-02-08, "Improved sync indicator logic"), which made `upgrade` the one-line form
   above (before it, the debouncer took a plain `immediate` flag). It was invisible upstream because Tasks.org's own server pushes changes to the phone (FCM and an SSE
   client: `PUSH_NOTIFICATION`, `kmp/.../sse/SseClient.kt:153`); 4Tasks has no server, so every pull depends on the triggers in 2. Fixed in `SyncSource.upgrade`.
2. **"A delayed job is good enough for a push."** Upstream waits 30 s in WorkManager to batch edits. That assumes JobScheduler will run the job soon after; section 3 says it
   will not on a phone where the app is cached. Replaced by: debounce in-process (1 s), then run expedited.
3. **"`SyncAdapters` exists."** It is a lazy singleton: it was built the first time the list screen asked for it. A task made by 4Dictate through the 4Link door, in a process nobody
   had opened, was never noticed. Now built in `Application.backgroundWork()` (`TasksApplication.kt:188-193`) at every process start.
4. **"A worker that finds no network has done its job."** `SyncWork` returned success without syncing (`doSync` skipped everything when `hasNetworkConnectivity()` was false), and
   the dirty task counted as handed off. Now it looks for the network for 5 s and otherwise returns `retry`.
5. **"The sync indicator and the scheduling are one thing."** `SyncSource.showIndicator` (what to show in the UI) and `immediate` (how urgent) are folded into one `upgrade`
   rule, which is how 1 happened. The scheduling policy now has its own fields (`waitsInWorkManager`, `expedited`) and its own tests (`SyncSourceTest`).
6. **"The hourly job is the background sync."** It was hourly, fixed. It is now the user's choice (15 / 30 / 60 minutes) and explicitly a safety net.

## 5. What we cannot make Android do

If Android blocks the network for a cached app, nothing in the app can force a background pull while the phone sits idle: not WorkManager, not an alarm. What we can do: make
every foreground moment count (open, pull, widget refresh all sync), push before the app is cut off (flush on leaving), and keep the periodic job as unconstrained as it can
safely be (see 7). The manual says so, and suggests setting the app's battery use to Unrestricted for phones that hold background network back.

## 6. The harness

`devtools/sync-harness/`: two emulators per Android version (an ORIGIN where changes are made, a RECEIVER) and a local Radicale CalDAV server per version. A debug-only
receiver in the app (`app/src/debug/.../HarnessReceiver.kt`) adds the account and dumps task state, so the harness reads the app in under a second and never has to scrape a screen
for state. Changes are made through the real paths (4Link door through the dev caller app, the app's own screens by uiautomator, the widget, a reminder's Complete button, the
share sheet, the tile's launch intent), the server's storage is polled every 0.25 s, and the table is seconds from the change to the server (push cells) and from a change on the
server to the receiver showing it (receive cells, by open / pull / widget refresh / periodic).

Valid operations per origin (a door cannot edit; a notification can only complete): door add, tick; in-app add, edit title, edit due, tick, untick, delete, move; widget add, tick;
notification tick; share add; tile add. Origin states: foreground, backgrounded, killed, Doze. `python3 run.py --api 37 --apk after --cells smoke --out results/after-37.csv`
runs the per-build smoke subset; `--cells full` the whole matrix. It runs on every build (`devtools/sync-harness/README.md`).

## 7. Results

See `devtools/sync-harness/RESULTS.md` (before = 0.1.5, after = the 0.1.6 build).
