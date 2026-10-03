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

## 2026-10-03 — Phase A (CalDAV), first part (owner's go-ahead, with his rulings)

- **Network is back:** INTERNET and ACCESS_NETWORK_STATE (seven permissions in all); `supportsCaldav` on; Add account
  and the welcome screen offer it again; the add-account and certificate-trust screens are back in the manifest.
  Tasks.org's account, Etebase, OpenTasks, geofences and calendar stay off. Release still has no
  network-security-config, so plain HTTP is refused; only the debug build allows it.
- **Backup without credentials:** the exporter writes accounts with the password column blank; the importer
  ignores a password even in an older file and marks the restored account "needs sign-in" (HTTP 401 state).
  Instrumented tests on an emulator: the file contains no password; an injected password is dropped.
- **Self-signed certificates:** Settings, Advanced, "Allow self-signed certificates", off by default. Off: the
  system's trust store decides, no prompt, anything trusted earlier is ignored; turning it off also forgets the
  certificates the user trusted. On: the user may trust one exact certificate, as before. Errors say which case it is.
- **Hostname always matches:** `cert4android`'s `HostnameVerifier` no longer consults user trust. Upstream accepted
  a trusted certificate under a wrong name and even asked about system-trusted ones. This is a deliberate change
  to a third-party module (MPL-2.0, file-level: the changed files keep their MPL notices and stay available).
  Tests use real certificates made with the JDK's keytool.
- **Test pitfall avoided:** connected (instrumented) Gradle tasks install on EVERY attached device. They are only
  run with ANDROID_SERIAL set to the emulator.
- 1,959 unit tests and the 10 instrumented importer tests pass.

## 2026-10-03 — Phase A (CalDAV), finished

- A plain `http://` address now says "Plain http:// connections are not allowed. Use an https:// address."
  (release build on the emulator; the server saw no request). Debug builds still allow cleartext for testing.
- Advanced no longer shows the calendar-event rows (the feature is off). The self-signed switch stays.
- Privacy policy (Markdown and web page, both generated from one source), Data safety, permissions (seven),
  store listing, README and the About line now describe optional CalDAV sync. Data safety: the owner decided (2026-10-03) to
  declare tasks and the sync sign-in as collected, for app functionality, not shared, optional, encrypted in transit, deletable.
- Release APK 0.1.0-beta (code 151204), family key 7ffc5b0d, seven permissions, installed over the existing
  copy on the emulator and on the A9 (in-place update, 4Dictate's accessibility service still bound).
- Not yet done for Phase A: the owner's own test against his Mailcow (he types the credentials himself).
  Phases B (Microsoft) and C (Google) are not started.

## 2026-10-03 — Family look (owner's order: 4Tasks should look and read like 4Dictate and 4Zones)

- **Colour:** wallpaper (dynamic) colour is on by default and never Pro-gated. The default theme colour is the family
  sky blue: seed #0284C7, which resolves to #38BDF8 in dark mode. With wallpaper colour off the family palette applies
  (navy #0F172A, surfaces #1E293B, muted #94A3B8), also in the View-based screens (night colours, bottom bar). The picker
  keeps every colour, the family one first.
- **Icon:** option A (box and tick), chosen by the owner on the A9 from three. He asked for a bolder tick and heavier
  outline; checked at 24 dp and as the white notification icon. No digit in the launcher icon. Adaptive, with a monochrome
  layer. The 20 alternate launcher colours, their setting and the picker are removed; shortcuts use the one icon. The
  welcome logo is the mark on navy. The "4" (the old blocky digit) is kept as a family mark in sky blue on navy, on the
  Play feature graphic and the site page, next to icon A.
- **Words:** British spelling in the English UI; the app is "4Tasks" in strings that said "Tasks". Strings about
  Tasks.org, Pro, subscriptions, sponsorship, cloud, desktop and QR linking are removed from every translation (the screens
  are unreachable here; the English text stays for the dead code). A crawl of settings, drawer, menus, add-account and
  new-task screens found Tasks.org only in About, which stays (GPL attribution). Release Settings has no Debug row.
- **Site:** play/site/index.html is a draft home page in the site's own style. Nothing is written to the site folder
  until the owner says "publish".
- **Play:** icon-512, feature graphic and three screenshots redone (generators were throwaway scripts; the SVG source of
  the icon is graphics/4tasks-icon.svg).
