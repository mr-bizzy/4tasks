# 4Tasks — journal of decisions

Short entries, newest last. The reasoning before the first build is in PHASE0-PLAN.md.

## 2026-10-02 — phase 1

- **Owner's rulings** on the nine points of the phase 0 plan: name 4Tasks (`uk.mr_biz.fourtasks`);
  4Tasks GPL-3.0 and 4link Apache-2.0 and public, the Play data note moved to the 4Dictate repo
  first; fork of tag 15.12, generic flavor, package stays `org.tasks`; CalDAV disabled, not
  deleted (phase 2, the owner's own server), no network permission in phase 1; keep widgets and
  file backup; Android auto-backup off; the four catalogue details; the date line in the skill
  prompt; SCHEDULE_EXACT_ALARM with an inexact fallback.
- **Surprise at the tag.** The survey had read Tasks.org's development head. Tag 15.12 has the same
  `org.tasks.api` layer, but under `kmp/src/jvmCommonMain` rather than `commonMain`.
- **minSdk is 33,** not Tasks.org's 26, because the 4Link library needs API 33 (the same floor as
  4Dictate). Raising it was a consequence of the library, not a free choice.
- **Removed, not just hidden:** Firebase, Crashlytics, PostHog, Play Billing and Services, the
  oss-licences plugin, the wear/desktop/iOS modules, fastlane, CI, Tasks.org's OAuth client files,
  the exported content providers and AppFunctions service (and the content-provider reference
  with its test), Tasker, DashClock, voice command, location permissions and receivers.
- **Switched off, code kept:** all sync providers (CalDAV, Tasks.org account, Etebase, Google
  Tasks, Microsoft, OpenTasks), geofences and calendar events, through `PlatformConfiguration` in
  `app/src/generic/.../FlavorModule.kt`. Google Tasks and Microsoft code is dormant, with no
  network permission to run on; delete it or keep it in phase 2.
- **A launch crash I caused:** removing the location receiver from the manifest while the Android
  location service still ran. Replaced by `NoLocationService`.
- **Version.** Name `0.1.0-beta`, code `151204`: Tasks.org's upgrade steps compare the code, so
  the numbering scheme is kept and only the visible name changed.
- **Two upstream bugs found by running it.** (1) Tasks.org's `matches` text filter never matched
  on a phone: its watchdog wrapper has no `toString` and Android's regex reads input with
  `toString`. The door uses a plain substring match instead (proved on a phone). A one-line fix is also in `Deadline.Watched`, but that fix has not been exercised on a phone.
  (2) Without the exact alarm permission upstream sets no alarm at all, so a reminder never
  fires. 4Tasks now falls back to `setAndAllowWhileIdle`.
- **Cold start race.** A 4Link call can reach the provider before the Application has finished
  starting (WorkManager not configured). The provider waits for the main thread's next turn.
- **Proof on an emulator** (Android 16, `fourtasks_test`): reminder fires 53 s late without the
  permission, within 1 s with it; add, list and complete through the door (debug and release
  builds); a stranger is refused until the user allows it, is limited to what was ticked, and is
  refused again after Remove; a release-signed caller is family automatically, a debug-signed
  one is not.
- **Tests:** 1,924 pass (app 611, kmp 1,240, data 73) under a Temurin 21 JDK.
- **The S25 is fragile** (Android 17 beta in DeX; SystemUI died during my test installs). No
  further adb use on it without the owner's go-ahead.

## 2026-10-02 (later) — owner's second set of rulings

- 4link and 4tasks to be made public as they are (no history rewrite), before any APK goes to
  a tester, for the GPL source obligation.
- **Microphone button removed** (4Dictate is the voice input): menu item, handler, result
  handler and the speech-recogniser query in the manifest. The privacy and Data safety drafts no
  longer mention it. (Voice *reminders*, which read a due task aloud with the phone's own
  text-to-speech, are a different feature and stay.)
- Android 13 minimum accepted. Sync code stays switched off; CalDAV to the owner's own server is
  phase 2.
- **Found on the A9, which has the real Tasks.org installed:** 4Tasks would not install
  (INSTALL_FAILED_CONFLICTING_PROVIDER, `org.tasks.opentasks`). The OpenTasks provider and its
  receiver, the AppAuth redirect activity and cert4android's trust activity are added to the
  merged manifest by libraries, so my earlier manifest edits had not removed them. The OpenTasks
  provider was an exported data door. All are now removed with `tools:node="remove"`; the merged
  manifest has only 4Tasks' own authorities. (An emulator without Tasks.org could not show this.)

## 2026-10-03 — found on the A9 during the voice test

- **4Tasks crashed at every start beside the real Tasks.org.** The OpenTasks authority was still
  the hard-coded `org.tasks.opentasks`, which on that phone is Tasks.org's own provider, so the
  startup observer's `registerContentObserver` was denied (SecurityException, seven crashes, then
  Samsung stopped the app). The authority is now `uk.mr_biz.fourtasks.opentasks` and the observer is
  only registered when OpenTasks sync is switched on (it is not in phase 1).
- **Reproduced before fixing:** the real Tasks.org 15.12, built from the tag, installed on the
  emulator beside the unfixed 4Tasks gave the same exception. After the fix, three cold-start
  calls (add with reminder, list, complete) and 20 s of background work gave no crash.
- **Lesson:** test beside the real Tasks.org, not only on a clean emulator.

## 2026-10-03 — tasks.complete finds a misheard title by sound (spec 11a)

- **Why:** the recogniser heard "Mark the sounder task done" and the model sent `{"title":"sounder"}`.
  "sounder" and "Sandra" both encode as SNTR in Double Metaphone (commons-codec, already in the tree).
- **Behaviour (owner chose option B):** a text match works as before. If no title contains the words,
  open tasks are compared by sound, word by word (query words of 3+ letters; numbers must match as
  text). One sound match completes NOTHING; it answers `bad_arguments` with a plain message and a
  `suggestion` (question, function, arguments `{"id": N}`) for the caller to put to the user. Several
  sound matches are named, no suggestion. Nothing at all: up to five open titles are named so the
  mishearing is visible. "Center" is also SNTR, which is why a sound match is never acted on alone.
- **No title leak:** titles appear in errors and suggestions only for family, or a paired app granted
  `tasks.list`. A paired app granted only `tasks.complete` gets "No open task matches “x”." and no
  suggestion (proved on the emulator). It can still complete a task it guesses by text, which is what
  that grant means.
- **Library:** `Suggestion`, `Outcome.BadArguments(message, suggestion)`, `InvokeResult.Error.suggestion`,
  Bundle key `suggestion`, spec §11a (draft 4), 4link 0c12b19. 4Dictate 0.3.151 follows it.
- **Tests:** 86 door tests (18 new), 81 library tests.
