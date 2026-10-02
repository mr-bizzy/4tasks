# 4Tasks — phase 0 plan (survey, proposal, decisions needed)

Status: DRAFT for the owner, 2026-10-02. Nothing in phase 1 has been started.
No fork, no GitHub repository, no licence change, no build, no phone use.
Survey source: Tasks.org tag 15.12 (latest tag; `main` is already 15.13 development),
read in a scratch clone. This folder holds only this plan; the repository
`mr-bizzy/4tasks` is created from the Tasks.org tag in phase 1 and this file moves in.

## 0. What the survey found that changes the plan

1. Tasks.org already has a transport-independent write layer, `org.tasks.api`
   (`ApiWriter`, `ApiQueryEngine`, in `kmp/`). Its content provider and its AndroidX
   AppFunctions service both call it. The 4Link door should call the same layer. That
   gives us Tasks.org's own create, complete and reminder code, including alarm
   scheduling, with almost no new data code.
2. The `generic` build flavor already has no Firebase, no Play Billing and no Play
   Services. Its analytics class only writes to the local log. Phase 1 can be that
   flavor alone.
3. 4Dictate does NOT tell the model today's date, time or time zone. See section 7.
4. The 4Link schema subset has no arrays, so list results must be text. See section 5.
5. If the public 4Tasks repository includes the 4link library as a submodule, the
   library repository must be public, or nobody can build the published source. See
   section 1.

## 1. Licence plan (nothing is done until the owner agrees)

- Tasks.org is GPL-3.0. Its tree has a single LICENSE file and no other licence file.
  4Tasks is therefore GPL-3.0, with its full source public at `mr-bizzy/4tasks`,
  Tasks.org's copyright notices kept, and attribution in About and the README.
- The 4link library compiled into 4Tasks must be GPL-compatible. Proposal: release
  4link under Apache-2.0. Apache-2.0 code may be included in a GPL-3.0 program, and
  4Dictate stays closed because it only includes the Apache library.
- 4Dictate and 4Tasks are separate programs that talk over Android IPC, so 4Dictate
  is not a derivative of the GPL app.
- Consequence to decide: the library repository (`mr-bizzy/4link`, private) must
  become PUBLIC, because the GPL requires the complete corresponding source to be
  obtainable. The alternative is to vendor a copy of the library source inside the
  4tasks repository. Before anything is made public: scan the 4link history and
  docs for secrets and hostnames, and decide whether `docs/PLAY-DATA-NOTE.md`
  (Play-compliance notes for 4Dictate) stays in the public repository.
- The fork must drop the Tasks.org name and icon. Only the code is licensed, not the
  brand. The Firebase project files in the Tasks.org tree
  (`app/src/debug|release/google-services.json`, project `tasks-98543`) belong to
  Tasks.org and must not be carried into 4Tasks.

## 2. Tasks.org inventory

Build: Gradle 9.7.1, Android Gradle Plugin 9.4.0, Kotlin 2.4.10, compileSdk 37,
targetSdk 36, minSdk 26, Kotlin JVM target 17 with a toolchain of 21. This
workstation has Android platforms 35, 36 and 37 and JDKs 17 and 21 (the default
`java` is 25, so JAVA_HOME must be set to 21). Not yet built here; the first
phase-1 step is a debug build to confirm.

| Module | What it is | Phase 1 |
|---|---|---|
| `app` | The Android app: UI, Hilt, WorkManager, notifications | keep |
| `data` | Room database, entities, DAOs (tasks, lists, alarms) | keep |
| `kmp` | Shared logic: TaskSaver, TaskCompleter, AlarmService, the `org.tasks.api` layer | keep |
| `cert4android` | TLS certificate trust for self-hosted CalDAV servers | drop with sync if nothing else needs it |
| `composeApp` | Desktop and iOS UI, not used by the Android app | leave out of settings |
| `wear`, `wear-datalayer` | Wear OS app and phone link | leave out |
| `pebble`, `iosApp`, `fastlane` | other platforms and store metadata | leave out |

Data layer, as it stands:
- Lists are `CaldavCalendar` rows under a `CaldavAccount`; with no sync there is one
  local account. Tasks are `Task` rows; due date and time are one encoded value with
  an all-day flag; reminders are `Alarm` rows.
- New tasks go through `TaskCreator.basicQuickAddTask` with title parsing and user
  defaults switched OFF, then `TaskSaver`. So a task created through the API layer
  gets no automatic default reminder; only a reminder we add explicitly exists.
- Reminders are scheduled by `AlarmService` and `WorkManagerImpl`, which uses
  `setExactAndAllowWhileIdle` when exact alarms are allowed. When they are NOT
  allowed (Android 12 and later) it sets no alarm at all, so the reminder does not
  fire until something else wakes the app. Notifications come from
  `NotificationManager`.
- `TaskCompleter` handles repeating tasks (completing one moves it to its next date),
  subtasks and clearing the notification.

Proprietary, account-bound or branded pieces:

| Piece | Where | Phase 1 |
|---|---|---|
| Firebase Crashlytics, Remote Config, FCM; Tasks.org's Firebase project files | `googleplay` flavor, google-services plugin | remove |
| PostHog analytics | `googleplay` flavor | remove |
| Analytics calls (132 files call the `Analytics` interface) | everywhere | keep the interface, make it a no-op that logs nothing; deleting 132 call sites buys nothing |
| Play Billing, "Pro" subscription, donation banners, `PurchaseActivity` | `Inventory`, `Banner`, strings | remove the UI; features are already unlocked in the generic flavor |
| Play Services: maps, location, review, code scanner, OSS licences, Wear | `googleplay` flavor | remove |
| Tasks.org account (caldav.tasks.org), CalDAV, Etebase, OpenTasks | `kmp`, `app`, `cert4android` | remove (no sync in phase 1) |
| Google Tasks, Microsoft To Do, Google Drive backup | OAuth clients, AppAuth, Google API libraries | remove. Google Tasks sync is a later option; it needs our own OAuth client and Google verification |
| Calendar integration (READ/WRITE_CALENDAR) | manifest | remove; sensitive Play permission |
| Location reminders and places (ACCESS_BACKGROUND_LOCATION, geofences, Mapbox, osmdroid) | manifest, `location/` | remove; background location needs a Play declaration and is outside the minimum |
| Tasker and Locale plugin, DashClock, Pebble | receivers, `locale/`, `dashclock/` | remove |
| Exported data doors: `Astrid2TaskProvider`, `TasksContentProvider`, `TasksApiHiltProvider`, OpenTasks provider, AppFunctions service | manifest | remove. Each lets any app the user grants a permission read or write tasks, which bypasses 4Link's pairing and confirmation rules |
| Branding: id `org.tasks`, name, icon, 20-odd tasks.org URLs, help and privacy links | resources, strings | replace with ours |
| INTERNET and ACCESS_NETWORK_STATE | manifest | goal: remove, so 4Tasks ships with no network permission. Confirm in the merged manifest |

No accessibility service exists anywhere in the Tasks.org tree.

## 3. What phase 1 keeps and removes

KEEP (all local, offline): lists, tasks, notes, due date and time, reminders and
notifications, completion, subtasks, priority, tags, repeating tasks, search, filters,
sorting, themes and colours, home-screen widgets (in-process, no network), and
backup and restore to a file the user picks.

REMOVE or DISABLE: everything in the "remove" rows above.

Approach: neutralise before deleting. Where call sites are many (analytics, billing
state), keep the small interface and replace the implementation. Delete whole
features only where they are self-contained (sync accounts, Wear, maps, location).
Keep the Kotlin package and Gradle namespace `org.tasks`, and change only the
application id, name and icon, so later upstream merges stay cheap. Distinct
application ids mean 4Tasks installs beside the real Tasks.org.

Permissions after the change: POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM,
RECEIVE_BOOT_COMPLETED, WAKE_LOCK, VIBRATE, plus FOREGROUND_SERVICE only if something
still needs it (it is declared today; nothing in the main source starts a foreground
service, so it likely goes).

## 4. Catalogue mapped onto Tasks.org's real calls

All strings below are within the spec limits (titles under 60 characters,
descriptions under 300). Provider authority: `uk.mr_biz.fourtasks.4link`.
No delete function in phase 1.

| Function | Effect | Title | Tasks.org call |
|---|---|---|---|
| `tasks.add` | change | Add a task or reminder | `ApiQueryEngine.createTasks`, then `setTaskReminders` with one `date_time` reminder |
| `tasks.list` | read | List tasks | `findTasks` with status, list ids, due bounds, sort by due, limit 50 |
| `tasks.complete` | change | Mark a task done | `findTasks` to resolve the task, then `completeTasks` |
| `lists.list` | read | List the task lists | `findLists` |

Descriptions (what the model sees):
- `tasks.add`: "Creates a task. title is what to do, in the user's words. due is when it is due and reminder is when to be notified, both local time. For "remind me at 3" send both. Omit list for the default list."
- `tasks.list`: "Lists open tasks, soonest due first, up to 50. Narrow by list name and by due_from and due_to (inclusive local dates or date-times). Set include_completed to also see finished tasks."
- `tasks.complete`: "Completes one task. Send the task's id, or part of its title, which must match exactly one open task. A repeating task moves to its next date instead of staying done."
- `lists.list`: "Returns the names of the user's task lists, so a list can be named when adding or finding tasks."

Inputs (the schema subset: strings, numbers, booleans, one object level):

| Function | Inputs | Output |
|---|---|---|
| `tasks.add` | `title` (required, 200), `notes` (2000), `due` (25), `reminder` (25), `list` (60), `allow_past` (boolean) | `id` number, `summary` text, e.g. "Get back to Sandra Elaine, tomorrow 15:00, reminder set" |
| `tasks.list` | `list`, `due_from`, `due_to`, `include_completed` | `count` number, `tasks` text, one line per task with id, title, due and list |
| `tasks.complete` | `id` number, `title` text | `summary` text |
| `lists.list` | none | `count` number, `lists` text, comma separated |

Behaviour, each point a decision for the owner if it surprises:
- Dates are ISO-8601 local time with no zone. A date alone makes an all-day task; a
  date and time makes a timed task. A zone or offset is refused as `bad_arguments`.
- Impossible dates (30 February, month 13) and years outside 2000 to 2100 are refused
  with a plain sentence. A due date in the past is accepted (the task is just
  overdue). A past reminder is refused unless `allow_past` is true; the model may set
  that only when the user said so. This is how "unless the user said so" is carried
  across, since 4Link has no other channel for it.
- No implicit reminder. The model sends `reminder` for "remind me"; a plain due time
  does not alarm by itself. The summary says "reminder set" only when one was written.
- Validate everything before writing. `tasks.add` is two writes (create, then add the
  reminder). Validating first makes a half result very unlikely. If the reminder
  write still fails, the answer is `failed` and the sentence says the task WAS
  created without its reminder, so the user is never told a false story.
- `list` is matched by name, ignoring case. An unknown name is refused with the
  names that exist. 4Tasks never creates a list on a model's word.
- `tasks.complete` takes `id` or `title`; at least one is needed (the schema subset
  cannot say "one of", so the provider checks it). A title is a case-insensitive
  substring over OPEN tasks. Exactly one match completes it. Zero or several
  completes nothing and answers with up to five candidate titles. The confirmation
  dialog in 4Dictate shows the typed words ("title: Sandra"), not the resolved task,
  so the result sentence always names the task that was completed.
- `tasks.list` caps at 50 lines and reports the true count. `due_to` as a date alone
  means the end of that day.
- Errors map from Tasks.org's own classes: bad request to `bad_arguments`, read-only
  list to `refused`, everything else to `failed`.
- If exact alarms are not allowed, the `tasks.add` summary says so ("reminder set,
  but this phone is not allowing exact alarms, so it may come late").
- Calls arrive on a binder thread. The engine functions are suspending, so the
  provider runs them on an IO dispatcher with a timeout. No main-thread hop is
  needed, as the AppFunctions service in Tasks.org also shows.
- Pairing screen and "Paired apps" list with the audit log come from the library
  and get one entry in 4Tasks' settings.

## 5. Why list results are text

The spec's schema subset forbids arrays in both input and output, and the reader
drops any function whose schema leaves the subset. 4Dictate shows a read's output as
"key: value; key: value". So `tasks.list` and `lists.list` return a count and one
text field. This also reads well when spoken back.

## 6. Google Play and the exact-alarm risk

- Own listing, privacy policy and Data safety answers; drafted in phase 1 under
  `play/`, plus a page source for mr-biz.uk/4tasks/privacy/. NOTHING is written to
  `~/Documents/mr-biz-uk` without the owner's explicit "publish".
- Likely Data safety answer: no data collected, none shared, no network permission.
  The one wrinkle is 4Link: tasks a user approves for a paired third-party app on the
  same phone, declared in the same way as the P4 note for 4Dictate.
- Exact alarms are the real Play risk. Play limits `SCHEDULE_EXACT_ALARM` and
  `USE_EXACT_ALARM` to apps whose core function is alarm clocks, timers or calendar
  style notifications, and flags manifests that declare them otherwise
  ([Esper summary](https://www.esper.io/blog/android-13-exact-alarm-api-restrictions),
  [Android docs](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)).
  A task-reminder app is a defensible fit and Tasks.org ships with the permission,
  but the declaration must be justified in the Play Console and I could not verify
  Play's current wording here. Proposal: SCHEDULE_EXACT_ALARM (user-granted, the
  broader use case), a first-run explanation, and a clear warning in the app when it
  is off. Because upstream sets no alarm at all without the permission, phase 1 also
  adds a small inexact fallback (`setAndAllowWhileIdle`) in `WorkManagerImpl`, so a
  refused or revoked permission means a late reminder, not a silent one. If Play
  refuses the declaration, that fallback is all we would have, which weakens
  "reminders that fire on time".
- On Android 14 and later a new install gets SCHEDULE_EXACT_ALARM denied by default,
  so proving the reminder on the S25 means granting "Alarms and reminders" first.
- Play App Signing must be enrolled with OUR release key, as the 4Link spec says,
  or Play installs are not family to 4Dictate.

## 7. Dates: the caller side (found, needs the owner's agreement)

`SkillPrompt.instructions` (library) gives the model the rules and the catalogues
only. `SkillRunner` (4Dictate) passes that and the spoken words. Neither includes
the current date, time or time zone, so "tomorrow at 3" has nothing to resolve
against and the model would guess.

Proposed change, with a test:
- Library: `SkillPrompt.instructions(sources, now: ZonedDateTime)` adds one line,
  for example "Now: Friday 2026-10-02 14:05, time zone Europe/London. Resolve words
  like tomorrow or next Friday from this. Write dates as ISO-8601 local time."
- 4Dictate: `SkillRunner` passes the current time. Edited in Bizzy-Ring, which is the
  owner's closed app.
- `SkillPromptTest` gains a case checking the line appears and the weekday is right;
  spec section 12 gains one sentence (draft 3).

Not made until the owner says yes.

## 8. Phase 1 order of work (after the yes)

1. Create `mr-bizzy/4tasks` from Tasks.org tag 15.12 with history kept and an
   `upstream` remote. Debug-build it unmodified on this workstation. Record the JDK.
2. New application id, name and icon; attribution in About and README; remove the
   agreed pieces; build; install on the S25 (announced first); prove lists, tasks,
   due times and a reminder that fires.
3. Signing: release build signed with the family release key through a local,
   uncommitted properties file; debug on the debug key.
4. Add the 4link submodule, the provider subclass, the four functions, the pairing
   screen and "Paired apps". Pure-Kotlin tests for argument checking, dates and the
   result sentences; Tasks.org's own tests that still apply kept green.
5. Prove on the S25 the six "done when" scenarios, one by one, with 4Dictate
   command mode.
6. Draft Play listing, privacy policy and Data safety under `play/` for approval.
7. A short journal of decisions in the repo; commit after each verified step; push
   after each verified install, after a scan for secrets and hostnames.

Hard rules carried over: no 4Zones repository touched in any way, no clipboard, no
analytics, no account, no accessibility code, no force-stop of the owner's apps, and
`adb -s <device>` on every phone command.

## 9. Decisions needed from the owner

1. Name: "4Tasks" (the brief) or "4Task" (the session title). It fixes the
   repository, the application id `uk.mr_biz.fourtasks` and the authority.
2. Licence: 4Tasks under GPL-3.0; 4link under Apache-2.0 and made public (or
   vendored). Also whether `PLAY-DATA-NOTE.md` stays in the public repository.
3. Base: fork Tasks.org tag 15.12, generic flavor only, package left as `org.tasks`.
4. The remove list in section 2, including no network permission, no calendar, no
   location and no exported data doors other than the 4Link door.
5. The keep list in section 3, in particular widgets and file backup.
6. Android auto-backup: Tasks.org enables it with its own backup agent. Keep it, as
   4Dictate's choice, and declare it in the privacy policy, or switch it off.
7. The catalogue details in section 4: text results, `allow_past`, substring title
   match for completing, unknown list refused, no implicit reminder.
8. The date line in section 7, which changes the library and 4Dictate.
9. SCHEDULE_EXACT_ALARM with the Play declaration risk accepted, and the small
   inexact-alarm fallback added to Tasks.org's scheduler.
