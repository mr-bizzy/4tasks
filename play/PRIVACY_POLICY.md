# 4Tasks — Privacy policy

**DRAFT 2026-10-03 for owner review. Not published.** The publishable page is `play/site/privacy/index.html` (proposed address https://mr-biz.uk/4tasks/privacy/). This file is the readable working copy. Written from the code of 0.1.0-beta with CalDAV sync; the Microsoft and Google sections are added as those phases are built. Square brackets are the owner's to fill.

**Effective date:** [DATE]
**Developer:** John Paul Bizeray, trading as Mr-Bizzy, United Kingdom
**Contact:** support@mr-biz.uk

## The short version

- 4Tasks is a to-do and reminder app. **Your tasks stay on your phone** unless you connect a sync account.
- If you connect a **CalDAV account**, your tasks sync **directly between your phone and the server you chose**. We run no server and receive none of your data.
- There is **no account with us, no analytics, no advertising, no crash reporting and no tracking**.
- Android's cloud backup and phone-to-phone transfer are **switched off** for 4Tasks, and 4Tasks' own backup file **never contains your sync passwords**.
- Other apps can use 4Tasks only on this phone, and only as described in section 5.

## 1. What 4Tasks stores, and where

Your lists, tasks, notes, due dates, reminders, tags and settings are stored in 4Tasks' private storage on your phone. If you connect a sync account, 4Tasks also stores that account's address and user name, and its password or token, which is **encrypted with a key held in the Android Keystore** before it is saved. Nothing else is stored. Uninstalling 4Tasks, or Android's *Settings, Apps, 4Tasks, Storage, Clear storage*, deletes it all.

## 2. Sync with a CalDAV server

CalDAV is an open standard for tasks and calendars, offered by many services and by servers people run themselves. When you add a CalDAV account in *Settings, Add account*, 4Tasks connects to the address you typed.

- **What is sent:** your tasks and lists (titles, notes, due dates, reminders, repeat rules, priority, tags, list names), and your user name and password, to that server only.
- **Where it goes:** only to the server you entered. It is **not** sent to us or to anyone else. That server's own privacy policy and security apply to what it keeps.
- **How:** over an encrypted (HTTPS) connection. Plain `http://` addresses are refused. By default only certificates your phone trusts are accepted. A server you run yourself with a self-signed certificate works only if you turn on *Settings, Advanced, Allow self-signed certificates*; you are then asked to trust that one certificate, and its name must still match the address. Turning the switch off forgets the certificates you trusted.
- **When:** while the app is in use, after changes, and from time to time in the background. You can remove the account at any time in *Settings*; syncing stops. Tasks already on the server stay there until you delete them there.

## 3. Backups you make yourself

*Settings, Backups* can write a backup file, to a folder you choose, and read one back. 4Tasks does not upload it anywhere. The file **does not contain sync passwords or tokens**: after a restore, a sync account comes back and asks you to sign in again. Where the file goes afterwards (a cloud drive you sync the folder to, for example) is your choice, and that service's policy applies.

## 4. Permissions, and why

- **Notifications:** to show a reminder when it is due.
- **Alarms and reminders (exact alarms):** so a reminder comes at the minute you set, not minutes late. If you do not allow it, 4Tasks still sets the reminder and Android may deliver it a little late.
- **Run at start-up:** to set your reminders again after the phone restarts.
- **Keep the device awake, vibrate:** to show a reminder, and to vibrate if you chose that.
- **Internet and network state:** only to sync with the servers you connect, and to know whether the phone is online.

4Tasks does not ask for your location, contacts, calendar, camera, microphone or storage. It has no microphone button; voice input is 4Dictate's job.

## 5. Other apps (4Link)

4Tasks has one door for other apps on the same phone, called 4Link. It never shows anything on its own and works only on this phone.

- **Our own apps** (for example 4Dictate) are recognised by their signing certificate. They can add a task, list tasks, mark a task done, and list your lists. Anything that changes something is confirmed by you in that app before it is sent.
- **Any other app** can use 4Tasks only after you allow it on a screen that names the app, shows its certificate fingerprint and lists, in plain words, each thing it asks to do and the details it would receive. You choose what to allow. You can remove an app at any time in *Settings, Apps allowed to use 4Tasks*, which also shows what each app did and when.
- There is **no delete function**: no app can delete a task or a list through 4Link.
- 4Tasks keeps a log of the last 500 calls (time, app, function, result). It never records the details of a task. The log stays on your phone.

When you allow an app, that app receives what the allowed functions return (for example, the titles of your tasks). What it does with them is governed by that app's own policy. Tasks you add or complete through 4Link sync like any other change.

## 6. Links

Some screens open a web page in your browser (this policy, the source code, help pages). Your browser, not 4Tasks, connects to the site.

## 7. Open source, and where it comes from

4Tasks is free software under the GNU General Public License v3, based on **Tasks.org** (https://github.com/tasks/tasks). The source is at https://github.com/mr-bizzy/4tasks. It has **removed** Tasks.org's own account service, Google and Firebase services, analytics, crash reporting, payments, maps, location and calendar features. Sync with Microsoft To Do and Google Tasks is added in a later release; this policy will say so before it ships.

## 8. Children

4Tasks is not directed at children and collects no personal information from anyone.

## 9. Deleting your data

On your phone: delete a task in the app, remove a sync account, clear 4Tasks' storage, or uninstall it. Data that has been synced lives on the server you chose; delete it there. We hold nothing, so there is nothing to ask us to delete.

## 10. Changes

If this policy changes, the new version will be published on this page with a new effective date.

## 11. Contact

John Paul Bizeray, trading as Mr-Bizzy, United Kingdom, support@mr-biz.uk
