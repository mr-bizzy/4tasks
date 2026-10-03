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
- **Site:** play/site/4tasks/index.html is a draft home page in the site's own style. Nothing is written to the site folder
  until the owner says "publish".
- **Play:** icon-512, feature graphic and three screenshots redone (generators were throwaway scripts; the SVG source of
  the icon is graphics/4tasks-icon.svg).

## 2026-10-03 — Family look, second round (owner's side-by-side on the A9)

- **Bar:** the title is the app name ("4Tasks") in the ordinary text colour at titleLarge, with the open list's name under
  it; the settings button is the family's ⚙ glyph (the same text glyph 4Dictate and 4Zones draw), one tap to Settings.
- **Groups:** each group's label is a small-capital label (labelSmall) above one rounded card (surfaceContainer, 16 dp
  corners) holding the group's rows. The cards are painted by an item decoration (GroupCardDecoration); rows draw no
  background of their own. Bottom bar and + button stay (owner's decision).
- **Colour, a bug found on the way:** the Toolbar/popup overlay themes were Material3.Light/Dark, which reset the colours
  inside the bar to the baseline palette, so the bar and the bottom bar did not follow dynamic colour. They are now the
  colour-neutral ThemeOverlay.Material3; status bar, bar, page and bottom bar are the same surface family in both modes.
- **Splash and window ground under dynamic colour:** the Android 12+ splash is drawn before the dynamic overlay is applied,
  so it used the theme's fallback navy. It now takes the system's own neutral surface (system_neutral1_10 light,
  system_neutral1_900 dark), which is what the dynamic scheme uses as its surface. The window background itself is the
  theme's colorSurface attribute, so it is dynamic once the overlay is applied. With wallpaper colour OFF the splash is
  still the neutral surface (a resource cannot know the setting), so a flash is possible only in that case.
- **Group style** is one switch (tasklist/FamilyLayout.kt): PLAIN, LABEL_ABOVE_CARD (shipped for now), TITLE_IN_CARD
  (4Zones' way, built, screenshots made). The owner chooses the family standard.

## 2026-10-03 — Settings in the family's structure (owner's order after the side-by-side of the three Settings pages)

- **Tabs:** Settings is the "← Settings" bar, then a scrollable tab row (Accounts, Tasks, Look, Backup, About) over a pager.
  Each page is a column of cards; a card is a title and a one-line explanation and opens the detail screen it names (4Dictate's
  SettingsLink). Accounts: local lists, each account, add account, apps allowed to use 4Tasks. Tasks: task defaults, task list
  options, edit screen options, date and time, notifications. Look: look and feel, navigation drawer, widgets. Backup:
  backups, advanced. About. The tab you were on is kept when you come back from a detail screen.
- **Numbers (4Dictate's SettingsActivity Section / SettingsLink, MainPager):** Card() with its stock colours and shape;
  14 dp inside; title titleSmall; explanation bodySmall in onSurfaceVariant; cards 8 dp apart; tabs titleSmall with the
  stock primary indicator; no leading row icons. Measured on the emulator (density 420): card 64.4 dp high (14 + 14 + the two
  text lines), 8.0 dp between cards; card colour = the dynamic surfaceContainerHighest; page colour = the scheme's surface.
  Every detail screen uses the same shared card and row, so they follow it too. Slider titles are titleSmall.
- **Not done and why:** 4Dictate's Settings could not be opened on the emulator without accepting its accessibility
  disclosure, which is not mine to accept; the comparison is by its code and the measured numbers.

## 2026-10-03 — Leftovers removed (PMs' consistency decisions, owner's delegation)

- Removed: the Google Drive backup and Android Backup Service sections and the "Documentation" row (a Tasks.org link) from
  Backups; the Places row in the drawer and the Places section in the drawer's settings (the drawer config now says places
  are never enabled: no location); the leading icon on the danger cards (reset, delete). Kept: the open list's name under
  the title, the bottom bar and the + button, collapse chevrons, list chips, the edit-screen field icons and switches, and
  "Astrid manual sorting", which is a working legacy sort mode for My Tasks, Today and tags (its own summary says it will be
  replaced by "My order"), so removing it would remove a function.

## 2026-10-03 — Settings bar, card corners and tabs matched to 4Dictate's source

- Bar: 48 dp high with a 48 dp back button and the title at its edge, no shadow, the tab row directly under it (4Dictate
  MainPager.kt:45-51 padding, :53-70 the row with a default 48 dp IconButton, :84-87 the tab row). Measured on the emulator:
  bar top = status bar bottom, 48.0 dp high, title starts at 48.4 dp, tab row starts at the bar's bottom.
- Group cards on the list: 12 dp corners, the stock Card shape (4Dictate HomeScreen.kt:420-424 passes no shape).
- Tabs: four (Accounts, Tasks, Look, More), edge padding 16 dp as MainPager.kt:84-87. Backups, Advanced and About are on More.

## 2026-10-03 — Window shape bug in freeform windows, and the widgets in the family look

- **Bug, reproduced on the emulator in freeform:** returning from Settings opened a NEW task at new bounds. Measured: before
  Settings, task 171 at Rect(276,694-804,1774); Settings opened in the same task; after Back, a new task 172 at
  Rect(343,844-871,1924). Cause: TaskListFragment's settings result handler always called MainActivity.restartActivity(), which
  did finish() and startActivity(MainActivity), a new task, and freeform windows open a new task at default bounds. Fix:
  restartActivity() is recreate(), in the same task and window. After the fix: task 173 at Rect(276,694-804,1774) before,
  during and after Settings. The "logo" was the white mark in the empty detail pane of the wide two-pane layout; removed (the
  pane is the page surface). The subtitle clipped to half height because the bar height came from actionBarSize, 48 dp in a
  short window; it is now at least 56 dp.
- **Widgets:** the default look (system-default theme, no colour chosen) is the family look: the system's own dynamic surface
  (system_neutral1_10 / 900, API 34: system_surface_light / dark), on-surface text and on-surface-variant icons and group
  labels (system_neutral1 / 2 tokens), the wallpaper's accent instead of Tasks.org's blue, the header on the page surface with
  the title at the bar's size (14 sp when the widget is narrow). A widget the user gave a colour keeps its filled header. The old
  default blue that was stored the first time a widget was drawn counts as "not chosen". The widget picker has a preview in the
  family look.

## 2026-10-03 — Privacy: the Tasks.org blog check is switched off

- **Found while checking the manual's privacy claim:** at start-up the app scheduled the upstream "blog feed" job (default mode
  "announcements"), which fetches https://tasks.org/blog/rss.xml. With INTERNET added in Phase A it could run on any build since
  then, so the statement "your tasks and settings go only to the server you chose" was not true of those builds (the request
  carries no task data, but it is a connection to Tasks.org). Now scheduleBlogFeedCheck() only cancels the job, and the worker
  itself does nothing. Firebase remote config and billing are empty stubs in this flavour. The only other HTTP clients are
  CalDAV (the server the user types), and code for Tasks.org accounts, Microsoft sign-in and place search that cannot be reached.

## 2026-10-03 — User manual and site pages; a guard against calling home

- **Manual:** play/site/4tasks/manual/index.html, in the family's manual style (4Dictate's stylesheet), 14 numbered sections,
  checked claim by claim against the app. The home page is play/site/4tasks/index.html and the privacy page
  play/site/4tasks/privacy/index.html. Nothing is written to the site folder; the owner's "publish" copies them.
- **Guard:** app/src/test/java/org/tasks/NoCallHomeTest.kt fails when a file that can make network calls is not on its list, when
  one names a fixed host that is not on its list, or when the Tasks.org blog check is scheduled again. Proven to fail: a probe file
  using OkHttp and a fixed host failed two of its three tests.

## 2026-10-03 — The three 4Link screens in the family look

- **Apps allowed to use 4Tasks, the pairing screen and the licences screen** were plain Android Views in the system's grey. They
  are now Compose in the Settings theme: a ← and a titleLarge title, cards in the same colour and corner as Settings (titleSmall
  title, bodySmall line, 14 dp inside, 8 dp apart), one shared FamilyScreen/FamilyCard (fourlink/FamilyScreen.kt). Their words
  are unchanged, except that the GNU licence title now spells "Licence" as the About page does. Each pairing function is a card
  that ticks as a whole; Remove still asks first; the licence text is selectable monospace.
- **Checked on the emulator** in light and dark: the pairing screen reached from the dev caller (which has a new "pair" action for
  this), allow, the list refreshing, Remove with its dialog, the licence text. Found and fixed in the process: the ← and the
  headings were black on the dark page until the screen sat on a Surface.
- **A9 network watch (12:20–12:23):** the passive watcher saw no 4Tasks socket at all in 200 s.
