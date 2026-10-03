# 4Tasks — Privacy policy

**Published 2026-10-03; changes to the page are a publish (the 4Dictate PM copies it on the owner's word).** The page is `play/site/4tasks/privacy/index.html` (proposed address https://mr-biz.uk/4tasks/privacy/). This file is the readable working copy. Written from the code of 0.1.0-beta with CalDAV sync; the Microsoft and Google sections are added as those phases are built. 

**Effective date:** 3 October 2026
**Developer:** John Paul Bizeray, trading as Mr-Bizzy, United Kingdom
**Contact:** support@mr-biz.uk

## The short version

- 4Tasks is a to-do and reminder app. **Your tasks stay on your phone** unless you connect a sync account.
- If you connect a **CalDAV account**, your tasks sync **directly between your phone and the server you chose**. We run no server and receive none of your data.
- There is **no account with us, no analytics, no advertising, no crash reporting and no tracking**.
- Android's cloud backup and phone-to-phone transfer are **switched off** for 4Tasks, and 4Tasks' own backup file **never contains your sync passwords**.
- Other apps can use 4Tasks only on this phone, and only as described in section 5.

## 1. What 4Tasks stores, and where

Your lists, tasks, notes, comments, due dates, reminders, tags, saved filters and settings, and any files or photos you attach, are stored in 4Tasks' own folders on your phone. If you connect a sync account, 4Tasks also stores that account's address and user name, its password or token, which is **encrypted with a key held in the Android Keystore** before it is saved, the session cookies the server gives it, and any certificate you chose to trust. 4Tasks also keeps automatic backup files (section 3), a short diagnostic log on the phone that is never sent anywhere, and its 4Link records (section 5). It stores nothing else. Android's *Settings, Apps, 4Tasks, Storage, Clear storage* deletes it all. When you uninstall 4Tasks, Android asks whether to keep its data; if you say no, it is deleted. A backup you saved to a folder of your own choosing stays where you put it.

## 2. Sync with a CalDAV server

CalDAV is an open standard for tasks and calendars, offered by many services and by servers people run themselves. When you add a CalDAV account in *Settings, Accounts, Add account*, 4Tasks connects to the address you typed.

- **What is sent:** your tasks and lists (titles, notes, due dates, reminders, repeat rules, priority, tags, list names), and your user name and password, to that server only.
- **Where it goes:** only to the server you entered. It is **not** sent to us or to anyone else. That server's own privacy policy and security apply to what it keeps.
- **How:** over an encrypted (HTTPS) connection. Plain `http://` addresses are refused. By default only certificates your phone trusts are accepted. A server you run yourself with a self-signed certificate works only if you turn on *Settings, More, Advanced, Allow self-signed certificates*; you are then asked to trust that one certificate, and its name must still match the address. Turning the switch off forgets the certificates you trusted.
- **When:** while the app is in use, after changes, and from time to time in the background. You can remove the account at any time in *Settings*; syncing stops. Tasks already on the server stay there until you delete them there.

## 3. Backups

Once a day 4Tasks automatically saves a backup file and keeps the newest seven, in the backup folder shown in *Settings, More, Backups*. Until you choose another folder this is 4Tasks' own folder on the phone, which Android deletes if you uninstall. You can turn this off, make a backup at any time, and read one back. The file holds your tasks, comments, filters and settings, and each sync account's address and user name, but **never its password or token**: after a restore, the account comes back and asks you to sign in again. 4Tasks does not upload backups anywhere. If you choose a folder that a cloud service syncs, that service's policy applies.

## 4. Permissions, and why

- **Notifications:** to show a reminder when it is due.
- **Alarms and reminders (exact alarms):** so a reminder comes at the minute you set, not minutes late. If you do not allow it, 4Tasks still sets the reminder and Android may deliver it a little late.
- **Run at start-up:** to set your reminders again after the phone restarts.
- **Keep the device awake, vibrate:** to show a reminder, and to vibrate if you chose that.
- **Internet and network state:** only to sync with the servers you connect, and to know whether the phone is online.

4Tasks does not ask for your location, contacts, calendar, camera, microphone or storage. It has no microphone button; voice input is 4Dictate's job. If you turn on spoken reminders, the reminder text is passed to your phone's own text-to-speech engine.

## 5. Other apps (4Link)

4Tasks has one door for other apps on the same phone, called 4Link. It never shows anything on its own and works only on this phone.

- **Our own apps** (for example 4Dictate) are recognised by their signing certificate. They can add a task, list tasks, mark a task done, and list your lists. 4Dictate asks you to confirm, on its own screen, before it adds or completes anything.
- **Any other app** can use 4Tasks only after you allow it on a screen that names the app, shows its certificate fingerprint and lists each thing it asks to do, with a short description of it. You choose what to allow. You can remove an app at any time in *Settings, Accounts, Apps allowed to use 4Tasks*, which also shows what each app did and when (the newest 100 calls).
- There is **no delete function**: no app can delete a task or a list through 4Link.
- You can also share text or files to 4Tasks from other apps' Share menus; that opens a new-task screen on your phone and sends nothing out.
- 4Tasks keeps a log of the last 500 calls (time, app, function, result). It never records the details of a task. The log stays on your phone.

When you allow an app, that app receives what the allowed functions return (for example, the titles of your tasks). What it does with them is governed by that app's own policy. Tasks you add or complete through 4Link sync like any other change.

## 6. Links

Some screens open a web page in your browser: this policy, the source code on GitHub, and a few help pages on tasks.org (the original Tasks.org project's site). Your browser, not 4Tasks, connects to those sites.

## 7. Open source, and where it comes from

4Tasks is free software under the GNU General Public License v3, based on **Tasks.org** (https://github.com/tasks/tasks). The source is at <https://github.com/mr-bizzy/4tasks>. It has **switched off**, and cannot reach, Tasks.org's own account service, Google and Microsoft services, maps, location and calendar features, and it has no Firebase, analytics, crash reporting or payments. Sync with Microsoft To Do and Google Tasks is added in a later release; this policy will say so before it ships.

## 8. Children

4Tasks is not directed at children and collects no personal information from anyone.

## 9. Deleting your data

On your phone: delete a task in the app, remove a sync account, clear 4Tasks' storage, or uninstall it. Data that has been synced lives on the server you chose; delete it there. We hold nothing, so there is nothing to ask us to delete.

## 10. Changes

If this policy changes, the new version will be published on this page with a new effective date.

## 11. Contact

John Paul Bizeray, trading as Mr-Bizzy, United Kingdom, support@mr-biz.uk
