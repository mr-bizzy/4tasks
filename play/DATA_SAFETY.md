# 4Tasks — Play Console "Data safety" answers

**DRAFT 2026-10-02 for owner review, from the 0.1.0-beta code. Nothing has been entered in Play Console.**

The form asks about data the app *transmits off the device*. 4Tasks has no internet permission,
so it transmits nothing. (Verified in the built release APK: five permissions, none of them
INTERNET or ACCESS_NETWORK_STATE.)

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Tasks, notes and settings stay in the app's private storage; no network permission |
| Is all of the user data collected by your app encrypted in transit? | not asked once "No" | nothing is transmitted |
| Do you provide a way for users to request that their data is deleted? | not asked once "No" | everything is on the phone: delete in the app, clear storage, or uninstall |
| Account creation | none | no accounts |
| Independent security review | no | |

## Points a reviewer may raise

1. **Android's own backup is off** (`allowBackup="false"`), so no data goes to Google Drive through
   Auto Backup.
2. **The microphone button** hands over to Android's speech recogniser. 4Tasks has no
   RECORD_AUDIO permission and receives no audio, so the recogniser's data handling is not
   4Tasks' to declare. The privacy policy says so (section 5).
3. **4Link** passes task data to *other apps on the same phone* only: our own family apps
   automatically, anyone else only after the user allows that app, by name, on a screen listing
   what it may do. No data leaves the device through 4Link, so it is not "collection" or
   "sharing" under the form. The privacy policy describes it (section 4) because a user may allow
   a third-party app. **Revisit this if the Play review asks.**
4. No ads, no advertising ID, no analytics SDK, no crash-reporting SDK, no third-party SDK that
   transmits anything (the Tasks.org Firebase, PostHog and Play Billing code is removed).
