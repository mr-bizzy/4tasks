# 4Tasks — Play Console "Data safety" answers

**DRAFT 2026-10-03 for owner review, from the 0.1.0-beta code with CalDAV sync. Nothing has been entered in Play Console.**

The form asks about data the app *transmits off the device*. With no sync account connected,
4Tasks transmits nothing. If the user connects a CalDAV account, 4Tasks sends the user's tasks, lists
and sign-in details **to the server the user typed in, and nowhere else**. We run no server and
receive nothing. (Release APK permissions: seven, including INTERNET and ACCESS_NETWORK_STATE.)

**The honest answer is a judgement call for the owner, and I could not check Play's current form
wording from here.** Two readings:

1. *Declare it.* Answer "Yes, collects" for *App activity / other user-generated content* (the tasks)
   and *Personal info / user IDs* (the sync sign-in), purpose *App functionality*, **not shared**,
   **optional** (the user chooses to connect), encrypted in transit **yes** (https only; plain
   http is refused), and "users can request deletion": yes, remove the account in the app and delete
   the data on their own server. This is the conservative answer and is the one I recommend.
2. *Declare nothing*, on the ground that the data goes only to a server the user chose and the
   developer never receives it. Play's own help treats some user-initiated transfers to a third
   party as not "sharing", but I cannot confirm it covers a self-chosen server. Not recommended
   without checking.

| Question | Draft answer | Why |
|---|---|---|
| Collect or share required user data types? | **Yes, collected, not shared** (reading 1) | tasks and sign-in details are sent to the user's own sync server |
| Encrypted in transit? | **Yes** | https only; a certificate must match the server's name; self-signed only by an explicit user switch, off by default |
| Way to request deletion? | **Yes** | remove the account in the app; the data on the user's server is deleted there |
| Account creation | none with us | the sync account is the user's, on their own server |
| Independent security review | no | |

Microsoft To Do and Google Tasks sync (later phases) will add rows here. Google Tasks needs the
Limited Use wording in the privacy policy.

## Points a reviewer may raise

1. **Android's own backup is off** (`allowBackup="false"`), so no data goes to Google Drive through
   Auto Backup. 4Tasks' own backup file leaves out sync passwords.
2. **4Link** passes task data to *other apps on the same phone* only: our own family apps
   automatically, anyone else only after the user allows that app, by name, on a screen listing
   what it may do. No data leaves the device through 4Link itself, so it is not "collection" or
   "sharing" under the form. The privacy policy describes it (section 5) because a user may allow
   a third-party app. **Revisit this if the Play review asks.**
3. No ads, no advertising ID, no analytics SDK, no crash-reporting SDK, no third-party SDK that
   transmits anything (the only network code is the CalDAV client, which talks to the user's server) (the Tasks.org Firebase, PostHog and Play Billing code is removed).
