# 4Tasks

A private to-do and reminder app for Android that works with
[4Dictate](https://mr-biz.uk/): say "remind me tomorrow at 3 to get back to Sandra about her
reservation" and it is a task, with its due time and reminder.

- **No account and no internet permission.** Your tasks stay on your phone.
- No analytics, advertising, crash reporting or payments.
- Reminders at the minute you set (allow *Alarms & reminders*); if you do not, 4Tasks still sets
  an inexact alarm, so a reminder always comes.
- Other apps can use 4Tasks over **4Link** ([spec](4link/docs/4LINK-SPEC.md)): our own apps are
  recognised by their signing certificate, any other app only after you allow it by name. There is
  no delete function.

## This is a fork of Tasks.org

4Tasks is a modified version of [Tasks.org](https://github.com/tasks/tasks) (tag 15.12), free
software from Todoroo (Astrid) and the Tasks.org contributors. It is **not** made or supported by
the Tasks.org project. Tasks.org's own README is kept in
[docs/TASKS_ORG_README.md](docs/TASKS_ORG_README.md); its copyright notices are kept in the source.

What changed: its own name, icon and application id (`uk.mr_biz.fourtasks`); no Google, Firebase,
Play Billing, analytics or crash reporting; no network, location or calendar permission; accounts
and sync switched off (the CalDAV code is kept, dormant, for a later version); Tasks.org's
exported content providers and AppFunctions service removed; Android auto-backup off; a 4Link
door; and a different About screen. The Kotlin package stays `org.tasks`, so upstream changes
merge cleanly. The decisions are in [docs/PHASE0-PLAN.md](docs/PHASE0-PLAN.md) and
[docs/JOURNAL.md](docs/JOURNAL.md).

## Licence

GNU General Public License v3 (see [LICENSE](LICENSE)). The 4Link library in `4link/` is
Apache-2.0.

## Building

You need JDK 21 with `javac` (Android Studio's bundled runtime is Java 25, which Mockito cannot
instrument, so use a Temurin or other JDK 21 for Gradle), and the Android SDK with platform 37.

```
git clone --recurse-submodules https://github.com/mr-bizzy/4tasks.git
cd 4tasks
./gradlew :app:assembleGenericDebug        # debug build
./gradlew :app:testGenericDebugUnitTest :kmp:jvmTest :kmp:testDebugUnitTest :data:jvmTest
```

A release build is signed with the family release key, which is not in this repository. Point
`FOURTASKS_SIGNING_PROPERTIES` at a properties file with `storeFile`, `storePassword`, `keyAlias`
and `keyPassword`; without it `assembleGenericRelease` produces an unsigned APK.

`devtools/fourlink-caller` is a tiny 4Link caller for trying the door from `adb` (see its source).

## Play Store drafts

`play/` holds the store listing, privacy policy (and its web page source), Data safety answers
and permission declarations. They are drafts for the owner and are not published.
