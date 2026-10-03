# 4Tasks — sync plan (CalDAV, then Microsoft To Do, then Google Tasks)

**DRAFT 2026-10-03 for the owner. Nothing in it is built.** It replaces the phase 0 plan's "no sync in
phase 1" for the first Play release. Sources are linked where a fact comes from a vendor's page; where I
could not confirm something, it says so.

## 0. What the owner decided

- The first Play release syncs with generic CalDAV (any server; Mailcow/SOGo is the test server, not a special
  case), Microsoft To Do and Google Tasks. No sync-less Play release. Order of work: CalDAV, Microsoft, Google.
- Google's OAuth verification is applied for as early as the rules allow. Timing: after the app is complete and
  has been through Play **internal testing**, before **open testing**. During internal testing Google Tasks
  runs in the consent screen's **Testing** mode with the testers listed by hand.
- Keep the Google and Microsoft code (the earlier "keep or delete" question is settled).

## 1. What the code does today (read from the source, 2026-10-03)

| Service | How it signs in | What identifies 4Tasks to the provider |
|---|---|---|
| CalDAV | Username and password, or an app password, to the server's URL, over HTTPS (library dav4jvm); self-signed certificates are trusted by asking the user (cert4android) | Nothing: no registration with anyone |
| Microsoft To Do | OAuth in the browser with PKCE, via AppAuth, against `login.microsoftonline.com/consumers` (**personal Microsoft accounts only**), scope `user.read Tasks.ReadWrite openid offline_access email`, then Microsoft Graph `me/todo/lists` | An Entra **client ID**. The code still carries **Tasks.org's own client ID** (`9d4babd5-…`). It must be replaced by ours; using theirs would be impersonation and stops working with our redirect |
| Google Tasks | Android's account manager: `getAuthToken("oauth2:https://www.googleapis.com/auth/tasks")` for a Google account on the phone, then REST calls to `tasks.googleapis.com`. No browser, no redirect, no client secret, no Play Services SDK | An **Android OAuth client**: our package name plus the SHA-1 of the signing certificate. Tasks.org's clients are for `org.tasks` and do not apply to us |

Two risks I found by reading, to be tested before anyone spends effort on verification:
1. The Google path is the older account-manager token flow. I have not proved it still returns a token for a
   2026 phone with a project of our own. First job of the Google phase, before the consent screen is submitted.
2. Releases refuse plain HTTP (only debug builds allow it), so a CalDAV server must be HTTPS. That is right
   for Play; say so in the app's "add account" help.

## 2. Work by phase

### Phase A — CalDAV (any server)
- Give the app the network permission back (INTERNET, ACCESS_NETWORK_STATE; WorkManager's network
  constraint needs the second).
- Turn on `supportsCaldav` in `FlavorModule.kt`; leave Tasks.org account, Etebase, OpenTasks off. Restore the
  manifest entries I removed for the add-account and sign-in screens, and cert4android's trust screen
  (`TrustCertificateActivity`); remove the "Add account" hiding in the settings and welcome screens
  (`showAddAccount`, `showSignIn`).
- Check that tasks made over 4Link sync, that a 4Link `tasks.complete` syncs, and that the reminder survives
  a sync.
- Test against Mailcow/SOGo and a second server type (Nextcloud or Radicale) so "any server" is true.
- Rewrite what says "no network": the About screen, README, store listing, privacy page, Data safety
  (section 4 below).

### Phase B — Microsoft To Do
- Replace Tasks.org's client ID with ours (Entra registration, section 3); redirect `msauth://uk.mr_biz.fourtasks/<signature hash>`
  as Entra's Android platform setup gives it; restore the AppAuth redirect activity in the manifest with
  our scheme.
- Personal accounts only, as the code already does (the `consumers` endpoint). Work or school accounts are an
  option, not part of this plan (see "Publisher verification").
- Test: sign in, two-way sync, completion, due dates, the sign-out and "account removed" paths.

### Phase C — Google Tasks
- Prove the account-manager token flow with our own Android client (risk 1) in Testing mode.
- Then sync as above. Restore `GtasksLoginActivity` and the list settings entries in the manifest.
- Drive backup stays off (no Drive scope is requested; only Tasks).

### Everywhere
- Reminders and the 4Link door are unchanged. No analytics, ads or crash reporting are added.
- The 4Link `tasks.*` functions stay offline-first: a call returns when the local database is written; sync
  happens afterwards.

## 3. Google, in detail (the long pole)

### Is `https://www.googleapis.com/auth/tasks` sensitive or restricted?
- **Not restricted.** Google's restricted-scope help page lists the restricted scopes by API: Gmail, Drive,
  Fit, Chat, Data Portability, Photos Ambient and Google Health. Tasks is not among them
  ([support.google.com/cloud/answer/13464325](https://support.google.com/cloud/answer/13464325)). So **no paid
  security assessment** (that is the requirement for restricted scopes
  ([restricted-scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification))).
- **Sensitive or not-sensitive: not stated** on any Google page I could read (the Tasks scope page, the scopes
  list and the verification pages do not label it). Google says sensitive scopes carry a "sensitive" marker
  in the Cloud Console. The scope is "Create, edit, organize, and delete all your tasks"
  ([Tasks scopes](https://developers.google.com/workspace/tasks/auth)), which is personal user data, so
  **plan for the sensitive route** (review, no assessment). The owner confirms it when adding the scope: the
  Console shows the marker. Send me a screenshot and I update this line.
- The only narrower scope is `tasks.readonly`, which cannot do two-way sync, so the justification for the
  full scope is: the app creates, edits and completes tasks the user changes on their phone.

### What the sensitive route needs
From Google's [sensitive scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification):
verified ownership of the authorised domain in Search Console; a privacy policy on the same domain as the
home page, linked from the consent screen, saying how the app accesses, uses, stores and shares Google user data;
a public home page with the app's description and links to the policy; branding (name, logo, contact);
a written justification for each sensitive scope and why a narrower one is not enough; a YouTube (unlisted)
demonstration video; and compliance with the Google API Services User Data Policy. Google says the review
"typically takes 3-5 business days" and branding review "usually completes in a few minutes". I would plan for
weeks, because a reviewer's question restarts the clock.

### What Testing mode allows before approval
From [Google's audience page](https://support.google.com/cloud/answer/15549945):
- Up to **100 test users** per project, **listed by hand** in the consent screen.
- Their authorisation, and any refresh token, **expires after 7 days**: a tester must grant access again each
  week and 4Tasks will show "needs sign-in" until they do. Expect that and tell the testers.
- Only listed users can grant the scope; everyone else is blocked.
- Testers see Google's "unverified app" warning and must click through it.
- Once published to production but still unverified, there is a separate cap: **100 new users in total** after
  the unverified screen is shown. Do not publish before verification.
- Play internal testing also allows 100 testers; each tester must be added in both places.

## 4. Play, privacy and Data safety changes once there is a network

- **Permissions:** add INTERNET and ACCESS_NETWORK_STATE; the declared list in `play/PERMISSIONS.md` grows from
  five to seven. The Android account-manager flow needs no extra permission (the account is chosen in the
  system dialog).
- **Privacy policy:** drop "no internet permission" and "your tasks never leave this phone". Add: sync to the
  servers the user connects (their CalDAV server, Microsoft, Google), what is sent (tasks, lists, notes,
  due dates and reminders, the account name and a token or password stored encrypted on the phone), that
  nothing goes to us, and how to disconnect (remove the account in the app, and delete the tasks on the
  service). Add the **Limited Use** statement Google requires: "4Tasks's use and transfer to any other app of
  information received from Google APIs will adhere to the
  [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
  including the Limited Use requirements." The page source is `play/site/privacy/index.html`; it changes only
  on the owner's "publish".
- **Data safety:** the answer flips from "no data collected" to data that leaves the device to services the
  user picks: tasks (user-generated content) and the account identifier, encrypted in transit, not sold, not
  used for ads. Declared as collected, with the 4Dictate precedent for "shared" (user-initiated, to a service
  the user chose) reviewed again with the owner.
- **Listing and About:** remove the "no internet" claims; the short description changes.

## 5. What the owner must do himself

Google (in this order; steps 1 to 6 can start now, 7 needs the app on Play, 8 and 9 come after internal testing):
1. **Google Cloud project** for 4Tasks under the account that will own it (a long-lived one, not a throwaway).
2. Enable the **Google Tasks API** in the project.
3. **Branding** (OAuth consent screen): app name 4Tasks, support email, logo (I have `play/graphics/icon-512.png`),
   home page `https://mr-biz.uk/4tasks/`, privacy policy `https://mr-biz.uk/4tasks/privacy/`, authorised
   domain `mr-biz.uk`. The home page and privacy page must really be live: publishing them is his "publish".
4. **Search Console:** verify `mr-biz.uk` (a DNS record) with the same Google account that owns the project.
5. **Data access:** add the scope `https://www.googleapis.com/auth/tasks` and note whether the Console marks it
   "sensitive" (screenshot to me).
6. **Audience:** External, **Testing**, and list the test users (up to 100).
7. **Android OAuth clients** (type Android): package `uk.mr_biz.fourtasks` with the SHA-1 of the certificate that
   signs what Play installs. **Order matters.** If the Play app uses *our own* release key through PEPK upload
   (the 4Link spec requires it), that SHA-1 is known now and is
   `E6:2C:D7:5A:DD:84:03:B4:EB:0B:31:0A:AA:07:08:24:67:FB:EA:EE` (from the release APK). If Play signs with a key
   Google generates, the SHA-1 is only in Play Console, App signing, after the app is created and enrolled; use
   that one instead. The *upload* key does not matter to Google. A second client with the workstation debug SHA-1
   `16:3C:86:72:29:4C:69:FB:AB:52:1A:6A:03:DA:AD:BB:D3:9E:8C:4E` lets debug builds sign in.
8. **After internal testing:** put the final privacy text (section 4) live; write the scope justification (I draft
   it); record the **demo video** on a real phone with the Play build: the app, adding the Google account,
   Google's consent screen naming 4Tasks and the scope, tasks syncing, removing the account. Upload it unlisted
   to YouTube.
9. **Publish to production and submit for verification.** Then wait; answer the reviewer within days.

Microsoft:
1. A **Microsoft Entra tenant** (registering an app needs one: a free Azure account will do) and a sign-in
   with at least the Application Developer role.
2. **App registration** "4Tasks": supported account types **Personal accounts only** (matches the code), public
   client, no secret. Add the Android redirect (package `uk.mr_biz.fourtasks`, signature hash from the same
   certificate as above). Add delegated Microsoft Graph permissions `User.Read`, `Tasks.ReadWrite`, `openid`,
   `offline_access`, `email`. Give me the **Application (client) ID**.
3. **Publisher verification: not needed for personal accounts only.** Microsoft describes it as primarily for
   multitenant apps, and the consent warning it removes is for users in other organisations' tenants
   ([overview](https://learn.microsoft.com/en-us/entra/identity-platform/publisher-verification-overview)).
   If work or school accounts are wanted later: a verified **Microsoft AI Cloud Partner Program** account (its
   partner global account), the app registered in a work or school tenant (an app registered with a personal
   Microsoft account cannot be verified), publisher domain `mr-biz.uk` matching the partner account's email
   domain, MFA, and the Application Administrator and Partner Center admin roles. Microsoft says there is no charge.
   I could not confirm from the pages I could read that `Tasks.ReadWrite` is open to personal accounts, but
   Tasks.org ships exactly that against the personal-accounts endpoint, so it evidently is.

CalDAV: nothing to register. The owner supplies the Mailcow/SOGo URL, a test account and an app password.

## 6. Order and what blocks what

1. **Now, in parallel with Phase A:** Google steps 1 to 6 (they cost the owner nothing and start the clock on
   branding and domain verification). Publishing the home and privacy pages is the only step that needs the "publish".
2. Phase A (CalDAV), then Phase B (Microsoft, needs the client ID), then Phase C (Google, needs the Android
   client). Play internal testing starts when the app builds as an AAB with all three.
3. Google verification submission after internal testing and before open testing, as the owner set it.
