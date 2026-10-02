# 4Tasks — permissions and Play declarations

**DRAFT 2026-10-02 for owner review.** From the merged manifest of the 0.1.0-beta release.

| Permission | Level | Needed for | Play declaration |
|---|---|---|---|
| POST_NOTIFICATIONS | runtime | showing a reminder when it is due | none (Android 13+ prompt) |
| SCHEDULE_EXACT_ALARM | special access, user-granted ("Alarms & reminders") | a reminder at the exact minute | **Yes: exact alarm declaration, below** |
| RECEIVE_BOOT_COMPLETED | normal | re-setting reminders after a restart | none |
| WAKE_LOCK | normal | keeping the device awake while a notification is posted | none |
| VIBRATE | normal | vibrating with a reminder if the user chose that | none |

There is no INTERNET, ACCESS_NETWORK_STATE, location, calendar, contacts, camera, microphone,
storage or foreground-service permission. A build that adds any of them breaks the privacy policy
and the Data safety answers.

## Exact alarm declaration (Play Console, "App content")

Play limits exact alarms to apps whose core function needs them (alarm clocks, timers, calendar
or reminder notifications). The core function of 4Tasks is reminding the user of a task at the
time they set. Suggested wording:

> 4Tasks is a task and reminder app. Its core feature is a notification at the exact time the
> user sets for a task, for example "remind me at 3 pm". A reminder that arrives minutes late
> is a failed reminder. We use SCHEDULE_EXACT_ALARM (not USE_EXACT_ALARM), so the user decides,
> and the app explains why on a banner on the list screen with a button to the system setting.
> If the user does not grant it, the app still sets the reminder with an inexact alarm.

Source: Android's guidance on exact alarms,
https://developer.android.com/about/versions/14/changes/schedule-exact-alarms. I could not verify
Play's current declaration form wording from here; check it in Play Console before submitting.

## Behaviour to demonstrate to a reviewer

- Denied: the reminder is still set (inexact), and a banner asks for the permission.
- Granted: the reminder fires within a second of its minute (proved on an emulator, 2026-10-02).
