# 4Tasks — Play Console "Data safety" answers

**DRAFT 2026-10-03 (Data safety answer decided by the owner the same day), from the 0.1.0-beta code with CalDAV sync; amended the same day for Microsoft To Do and Google Tasks sync (Phases B and C), which are in the first Play release. Nothing has been entered in Play Console.**

The form asks about data the app *transmits off the device*. With no sync account connected,
4Tasks transmits nothing. If the user connects a sync account, 4Tasks sends the user's tasks and lists, and the account's
sign-in details, **to the service the user connected, and nowhere else**: the CalDAV server they typed
in, Microsoft (Microsoft Graph) or Google (the Google Tasks API). We run no server and receive nothing.
For Microsoft and Google the user signs in on the provider's own screen, so 4Tasks never sees a
password; it holds a token (Microsoft: encrypted on the phone; Google: none of its own, the phone's
account manager hands out a short-lived one each time). (Release APK permissions: seven, including INTERNET and ACCESS_NETWORK_STATE.)

**DECIDED by the owner, 2026-10-03: declare the data.** Tasks (user-generated content) and the sync
sign-in (user IDs) are declared as **collected**, for **app functionality**, **not shared**,
**optional** (the user chooses to connect an account), **encrypted in transit**, and **deletable**.
I could not check Play's current form wording from here, so confirm the exact option names in Play
Console when entering it.

| Question | Answer (decided) | Why |
|---|---|---|
| Collect or share required user data types? | **Yes, collected, not shared** | tasks and sign-in details are sent to the user's own sync server |
| Encrypted in transit? | **Yes** | https only; a certificate must match the server's name; self-signed only by an explicit user switch, off by default |
| Way to request deletion? | **Yes** | remove the account in the app; the data on the user's server is deleted there |
| Account creation | none with us | the sync account is the user's, on their own server |
| Independent security review | no | |

**Microsoft To Do and Google Tasks (the same answers, the same basis):** the data types are the same (tasks as
user-generated content; the account identifier: Microsoft name and email address, Google account name), collected
for app functionality, not shared (it goes only to the service the user connected, on their instruction), optional,
encrypted in transit (HTTPS to graph.microsoft.com, login.microsoftonline.com, tasks.googleapis.com,
www.googleapis.com), and deletable (remove the account; delete the tasks at the provider).
Google Tasks needs the **Limited Use** wording in the privacy policy (it is in the draft, section 2 "Google Tasks").
Work and school accounts add that an organisation's administrator controls whether the app may connect.
Re-check the Play form's wording for "shared" with the owner, as with 4Dictate.

## Points a reviewer may raise

1. **Android's own backup is off** (`allowBackup="false"`), so no data goes to Google Drive through
   Auto Backup. 4Tasks' own backup file leaves out sync passwords.
2. **4Link** passes task data to *other apps on the same phone* only: our own family apps
   automatically, anyone else only after the user allows that app, by name, on a screen listing
   what it may do. No data leaves the device through 4Link itself, so it is not "collection" or
   "sharing" under the form. The privacy policy describes it (section 5) because a user may allow
   a third-party app. **Revisit this if the Play review asks.**
3. No ads, no advertising ID, no analytics SDK, no crash-reporting SDK, no third-party SDK that
   transmits anything (the only network code is the CalDAV client, which talks to the user's server, and the Microsoft Graph and Google Tasks clients, which talk to the provider the user chose) (the Tasks.org Firebase, PostHog and Play Billing code is removed). The Google flow uses Android's account manager and no Play Services library (an AuthorizationClient route would add one and need this answer revisiting).
