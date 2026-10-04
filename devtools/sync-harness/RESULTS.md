# Sync harness results

Generated 2026-10-04 by `make_results.py` from `results/*.csv` (seconds; the harness is described in README.md, the reasoning in `docs/SYNC-DESIGN.md`).
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

### Push: seconds from the change on the origin to the server

| origin | op | state | API 33 before | API 33 after | API 34 before | API 34 after | API 36 before | API 36 after | API 37 before | API 37 after |
|---|---|---|---|---|---|---|---|---|---|---|
| door | add | bg | 14.2 | 2.2 | 14.3 | 4.0 | 13.5 | 4.3 | 14.4 | 2.4 |
| door | add | cached | 15.2 | 4.3 | 14.7 | 5.0 | 14.5 | 3.7 | 13.7 | 5.2 |
| door | add | doze | 14.7 | 4.5 | 15.0 | 3.0 | 15.5 | 5.0 | 15.2 | 2.6 |
| door | add | fg | 14.2 | 3.8 | 13.6 | 3.9 | 14.3 | 4.6 | 13.9 | 2.7 |
| door | add | killed | 14.8 | 4.3 | 14.0 | 3.9 | 14.0 | 5.1 | 13.6 | 1.9 |
| door | tick | bg | 14.0 | 4.2 | 12.7 | 3.5 | 15.0 | 4.2 | 13.9 | 3.4 |
| door | tick | cached |  | 4.7 | 14.0 | 3.5 |  | 4.5 | 14.6 | 3.9 |
| door | tick | doze | never (>120s) | 4.2 | never (>120s) | 2.9 | never (>120s) | 4.5 | never (>120s) | 4.4 |
| inapp | add | bg | 14.8 | 4.0 | 15.1 | 4.3 | 13.8 | 4.0 | 13.8 | 3.6 |
| inapp | add | doze |  | 2.1 |  | 3.4 |  | 3.9 |  | 4.4 |
| inapp | delete | bg |  | 3.0 |  | 3.8 |  | 4.1 |  | 3.1 |
| inapp | edit_due | bg |  | 4.0 |  | 4.1 |  | 3.6 |  | 3.8 |
| inapp | edit_title | fg | 13.5 | 4.0 | 12.3 | 3.8 | 13.8 | 3.5 | 14.0 | 4.0 |
| inapp | move | bg |  | 4.3 |  | 5.1 |  | 4.3 |  | 4.6 |
| inapp | tick | bg | 14.1 | 4.1 | 13.8 | 3.8 | 14.8 | 3.6 | 14.6 | 3.3 |
| inapp | tick | doze | never (>120s) | 9.9 | never (>120s) | 2.4 | never (>120s) | 3.6 | never (>120s) | 4.1 |
| inapp | tick | fg | 14.0 | 4.3 |  | 3.8 | 13.5 | 4.5 | 13.8 | 4.5 |
| inapp | tick | killed | 14.4 | 4.1 | 13.9 | 4.1 | 21.7 | 3.4 | 13.3 | 4.4 |
| inapp | untick | bg |  | 4.1 |  | 3.8 |  | 3.8 |  | 2.6 |
| notification | tick | bg | 14.1 | 3.8 | 14.3 | 4.5 | 14.3 | 4.3 | 14.3 | 4.0 |
| notification | tick | doze | 14.3 | 3.8 | 14.1 | 4.0 | 14.1 | 3.5 | 14.3 | 3.3 |
| notification | tick | killed | 14.6 | 5.3 | 16.6 | 5.3 | 14.6 | 5.1 | 15.3 | 4.8 |
| share | add | bg | 14.3 | 3.8 | 14.8 | 3.3 | 14.6 | 4.3 | 14.3 | 3.6 |
| share | add | doze |  | 9.6 |  | 2.9 |  | 3.6 |  | 3.9 |
| tile | add | bg | 14.1 | 3.8 | 14.6 | 4.5 | 14.6 | 3.8 | 14.1 | 3.6 |
| widget | add | bg |  | 4.0 |  | 3.5 |  | 4.0 |  | 4.0 |
| widget | tick | bg |  | 3.8 | 14.3 | 3.8 |  | 4.3 | 14.3 | 4.3 |
| widget | tick | doze |  |  |  |  |  | 4.2 |  |  |
| widget | tick | killed |  |  |  |  |  | 3.6 |  |  |

### Receive: seconds from the server change to the receiver showing it

| discovery | | state | API 33 before | API 33 after | API 34 before | API 34 after | API 36 before | API 36 after | API 37 before | API 37 after |
|---|---|---|---|---|---|---|---|---|---|---|
| open |  | bg | 5.8 | 5.1 | 4.9 | 6.9 | 5.4 | 5.5 | 2.4 | 6.1 |
| open |  | killed | 7.7 | 7.6 | 8.1 | 8.6 | 7.0 | 8.0 | 6.2 | 7.9 |
| periodic |  | bg |  |  |  |  |  | 855.2 | 648.1 | 1037.2 |
| periodic |  | doze |  |  |  |  |  | never (>1200s) |  |  |
| periodic |  | killed |  |  |  |  |  | 810.6 |  | 937.5 |
| pull |  | fg | 6.8 | 6.0 | 7.1 | 5.9 | 5.8 | 6.5 | 5.5 | 5.8 |
| widget |  | bg | 7.9 | 8.6 | 7.7 | 8.5 | 7.7 | 7.6 | 5.9 | 8.5 |



## What Android does with jobs for a cached app (`probe_jobs.py`)

A probe job of each kind is enqueued by the debug app, the app is put in the state, and each job reports when it ran and whether a socket worked inside it.

**API 36, app bg**

```
api 36 state bg: probes enqueued; waiting 1140s
delayed-connected    ran at 24.6 s (network=no, http=SocketTimeoutException)
delayed-free         ran at 24.6 s (network=no, http=SocketTimeoutException)
expedited-connected  ran at 1.1 s (network=yes, http=401)
expedited-free       ran at 0.3 s (network=yes, http=401)
periodic-connected   ran at 1.1 s (network=yes, http=401); ran at 968.2 s (network=yes, http=401)
periodic-free        ran at 1.6 s (network=yes, http=401); ran at 967.9 s (network=yes, http=401)
periodic-kick        ran at 2.2 s (network=yes, http=401); ran at 968.1 s (network=yes, http=401)
kicked-expedited     ran at 3.0 s (network=yes, http=401); ran at 969.2 s (network=yes, http=401)
```

**API 36, app killed**

```
api 36 state killed: probes enqueued; waiting 1140s
delayed-connected    ran at 23.9 s (network=no, http=SocketTimeoutException)
delayed-free         ran at 23.9 s (network=no, http=SocketTimeoutException)
expedited-connected  ran at 0.2 s (network=yes, http=401)
expedited-free       ran at 0.1 s (network=yes, http=401)
periodic-connected   ran at 0.2 s (network=yes, http=401); ran at 938.7 s (network=yes, http=401)
periodic-free        ran at 1.6 s (network=yes, http=401); ran at 939.4 s (network=yes, http=401)
periodic-kick        ran at 1.7 s (network=yes, http=401); ran at 939.1 s (network=yes, http=401)
kicked-expedited     ran at 3.6 s (network=yes, http=401); ran at 940.1 s (network=yes, http=401)
```

**API 37, app bg**

```
api 37 state bg: probes enqueued; waiting 1140s
delayed-connected    ran at 25.2 s (network=no, http=SocketTimeoutException)
delayed-free         ran at 25.2 s (network=no, http=SocketTimeoutException)
expedited-connected  ran at 1.6 s (network=yes, http=401)
expedited-free       ran at 1.5 s (network=yes, http=401)
periodic-connected   ran at 1.7 s (network=yes, http=401); ran at 944.4 s (network=yes, http=401)
periodic-free        ran at 2.3 s (network=yes, http=401); ran at 944.6 s (network=yes, http=401)
periodic-kick        ran at 2.9 s (network=yes, http=401); ran at 944.2 s (network=yes, http=401)
kicked-expedited     ran at 3.7 s (network=yes, http=401); ran at 945.2 s (network=yes, http=401)
```

**API 37, app killed**

```
api 37 state killed: probes enqueued; waiting 1140s
delayed-connected    ran at 24.1 s (network=no, http=SocketTimeoutException)
delayed-free         ran at 24.1 s (network=no, http=SocketTimeoutException)
expedited-connected  ran at 0.7 s (network=yes, http=401)
expedited-free       ran at 0.4 s (network=yes, http=401)
periodic-connected   ran at 1.1 s (network=yes, http=401); ran at 967.9 s (network=yes, http=401)
periodic-free        ran at 1.9 s (network=yes, http=401); ran at 967.6 s (network=yes, http=401)
periodic-kick        ran at 2.1 s (network=yes, http=401); ran at 967.5 s (network=yes, http=401)
kicked-expedited     ran at 3.8 s (network=yes, http=401); ran at 968.9 s (network=yes, http=401)
```

