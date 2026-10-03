# 4Tasks — sync plan (CalDAV, then Microsoft To Do, then Google Tasks)

**DRAFT 2026-10-03 (revised the same day for work accounts and the owner's four rulings) for the owner. Nothing in it is built, and sync is not started until the owner gives the go after reviewing this plan.** It replaces the phase 0 plan's "no sync in
phase 1" for the first Play release. Sources are linked where a fact comes from a vendor's page; where I
could not confirm something, it says so.

## 0. What the owner decided

- The first Play release syncs with generic CalDAV (any server; Mailcow/SOGo is the test server, not a special
  case), Microsoft To Do and Google Tasks. No sync-less Play release. Order of work: CalDAV, Microsoft, Google.
- Google's OAuth verification is applied for as early as the rules allow. Timing: after the app is complete and
  has been through Play **internal testing**, before **open testing**. During internal testing Google Tasks
  runs in the consent screen's **Testing** mode with the testers listed by hand.
- Keep the Google and Microsoft code (the earlier "keep or delete" question is settled).
- **Also decided:** the **help page for organisation admins** (sections 3a and 3b) is part of the **first Play release**.
- **Rulings of 2026-10-03:** (1) the release is signed with **our own release key uploaded to Play App Signing
  through PEPK**, so the SHA-1 of that key (see section 5, step 7) is the production fingerprint for the Google Android client;
  **amended 2026-10-03 (owner): 4Tasks has its OWN release key (`~/keys/4tasks-release.p12`, alias `fourtasks-release`), one set of
  credentials per app as for 4Dictate and 4Zones, not 4Dictate's; the earlier SHA-1 `E6:2C:…` (4Dictate's key) is void and was never
  registered anywhere;
  (2) the **file backup does not include sync credentials**, and a restore leaves accounts needing sign-in again;
  (3) cert4android's "trust this self-signed certificate" stays, **behind "Advanced: allow self-signed certificates",
  off by default, and even when on a trusted certificate under the WRONG hostname is refused**; (4) the **Tasks.org help links stay for now and move to mr-biz.uk before open testing**.
- **Work accounts are in:** Microsoft work or school accounts as well as personal ones (a multitenant
  registration with publisher verification), and Google Workspace accounts as well as consumer ones.

## 1. What the code does today (read from the source, 2026-10-03)

| Service | How it signs in | What identifies 4Tasks to the provider |
|---|---|---|
| CalDAV | Username and password, or an app password, to the server's URL, over HTTPS (library dav4jvm); self-signed certificates are trusted by asking the user (cert4android) | Nothing: no registration with anyone |
| Microsoft To Do | OAuth in the browser with PKCE, via AppAuth, against `login.microsoftonline.com/consumers` (**personal Microsoft accounts only today; this changes to `common` for work accounts**), scope `user.read Tasks.ReadWrite openid offline_access email`, then Microsoft Graph `me/todo/lists` | An Entra **client ID**. The code still carries **Tasks.org's own client ID** (`9d4babd5-…`). It must be replaced by ours; using theirs would be impersonation and stops working with our redirect |
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
- **Backup without credentials:** the file backup (Settings, Backups) writes accounts without their password or token
  column. A restore brings the accounts back and marks them "needs sign-in". Add a test that a backup file contains no
  credential column and that a restore of an old backup that still has one ignores it. (Today it writes the ciphertext.)
- **Self-signed certificates:** the trust prompt appears only when the new Advanced switch is on; with it off, a server
  whose certificate the phone does not trust fails with a plain "certificate not trusted" message that names the
  switch. Turning it off again also forgets the certificates the user trusted. A test per case.
- **Hostname must match, always.** cert4android's `CustomCertManager.HostnameVerifier` today falls back to "is this
  exact certificate trusted by the user?" when the normal hostname check fails, so a certificate the user trusted is
  accepted under a **wrong hostname**. Change it (in our copy of the module, `cert4android/src/jvmCommonMain/.../CustomCertManager.kt`):
  `verify` returns false whenever the platform's hostname check fails, whatever the user has trusted. Trust then
  means "this certificate may stand in for the system's CA check", never "any name". The same verifier is used by the debug
  sign-in connection builder, so both follow. Tests: (i) a certificate the user trusted and whose name matches the
  host is accepted; (ii) the **same trusted certificate** on a different hostname is refused; (iii) an untrusted
  certificate with a matching name is refused with the switch off and prompts only with it on; (iv) a changed
  certificate for the same host asks again. A user who runs a server whose certificate lacks the name they connect by must
  fix the certificate (add the name) or connect by a name it carries; the "certificate not trusted" message says so.
  This is a deliberate departure from the upstream library's behaviour; note it in the journal when made.
- **Organisation admin help page** (part of the first release): a page on `mr-biz.uk` (and linked from the sign-in
  failure screen) for IT admins, saying what 4Tasks is, who publishes it, the one permission or scope it asks for and
  why, that it has no delete-everything access beyond the user's own tasks, and how to allow it: Microsoft (grant
  consent for the organisation in Enterprise applications, or enable the admin consent workflow) and Google Workspace
  (trust the app by Android package name or client ID in the Admin console; Limited or Specific Google data is enough).
  It is written with sections 3a and 3b and gets the real links and client IDs once the registrations exist. Publishing
  it is the owner's "publish", like the privacy page.
- Check that tasks made over 4Link sync, that a 4Link `tasks.complete` syncs, and that the reminder survives
  a sync.
- Test against Mailcow/SOGo and a second server type (Nextcloud or Radicale) so "any server" is true.
- Rewrite what says "no network": the About screen, README, store listing, privacy page, Data safety
  (section 4 below).

### Phase B — Microsoft To Do (personal and work or school accounts)
- Replace Tasks.org's client ID with ours (the Entra registration in section 5); redirect
  `msauth://uk.mr_biz.fourtasks/<signature hash>` (of the NEW 4Tasks release key, not 4Dictate's) as Entra's Android platform setup gives it; restore the AppAuth
  redirect activity in the manifest with our scheme.
- Change the sign-in authority from `consumers` to **`common`**, so personal accounts and any organisation's
  accounts can sign in. The scopes stay `user.read Tasks.ReadWrite openid offline_access email`. Microsoft's Graph
  permissions reference says `Tasks.ReadWrite` (delegated) has **AdminConsentRequired: No** and "is available for
  consent in personal Microsoft accounts" ([reference](https://learn.microsoft.com/en-us/graph/permissions-reference)).
- Show a clear screen when an organisation refuses the sign-in (section 3a), not a bare error.
- Test with: a personal account; a work tenant that allows user consent; a tenant limited to verified publishers
  and low-impact permissions; a tenant with the admin-consent workflow on; a tenant where the admin has granted
  consent for everyone. To Do for work or school accounts may also need an Exchange Online mailbox; the pages I read
  do not say, so test it.
- Test: sign in, two-way sync, completion, due dates, the sign-out and "account removed" paths.

### Phase C — Google Tasks
- Prove the account-manager token flow with our own Android client (risk 1) in Testing mode.
- Then sync as above. Restore `GtasksLoginActivity` and the list settings entries in the manifest.
- Drive backup stays off (no Drive scope is requested; only Tasks).
- Work accounts: test with a Google Workspace account as well as a consumer one, including an admin who has blocked
  third-party apps (section 3b).

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

## 3a. Microsoft work and school accounts: consent, admins and what the user sees

What decides it is the organisation's **user consent setting** and whether 4Tasks has a **verified publisher**
([consent overview](https://learn.microsoft.com/en-us/entra/identity/enterprise-apps/user-admin-consent-overview),
[configure user consent](https://learn.microsoft.com/en-us/entra/identity/enterprise-apps/configure-user-consent)):

| Organisation's setting | What a user of 4Tasks sees |
|---|---|
| Users may consent to any app for permissions that need no admin (Microsoft says this is the default) | The consent screen, with the blue "verified" badge if we are publisher verified; the user accepts and syncs. `Tasks.ReadWrite` needs no admin |
| **Verified publishers, low-impact permissions only** (the setting Microsoft recommends) | Even a verified app only gets permissions the admin has **classified as low impact**. `Tasks.ReadWrite` is unlikely to be on that list, so the user gets an "approval required" or "need admin approval" screen |
| User consent switched off | The same: the user cannot consent. If the admin has enabled the **admin consent workflow**, the screen offers "Request approval" with a justification box; the admin is emailed, and the user is emailed when it is approved or refused. If not, the user must ask the admin outside the app |
| Admin has already granted consent for the whole organisation | No prompt at all; sign-in just works |
| We are **not** publisher verified, in a tenant with risk-based step-up consent on | Users cannot consent to most newly registered multitenant apps, and a warning says the publisher is unverified and risky. This is why verification is not optional for work accounts |
| Conditional access, "user assignment required", or tenant restrictions | Sign-in can be refused outright; only the admin can change it |

What 4Tasks must do about it: show the provider's refusal text, then a short "what to tell your IT admin" screen
with the app name, the publisher, the one permission it needs (`Tasks.ReadWrite`, "create, read, update, and
delete the signed-in user's tasks and task lists") and why, and the admin's route (grant consent for the
organisation in the Entra admin center under Enterprise applications). The exact admin-consent link goes into the
help page once the registration exists. I will not word this from memory of error codes; I will read the real
responses in the tests above.

## 3b. Google Workspace accounts: what an admin can block, and what verification covers

From Google's page on [controlling which apps access Workspace data](https://knowledge.workspace.google.com/admin/apps/control-which-apps-access-google-workspace-data):
- An admin can set an app to **Trusted** (all services, including restricted ones), **Limited** (only unrestricted
  services), **Specific Google data** (only scopes the admin lists) or **Blocked** (no Google data). Apps the admin
  has not configured fall under a setting that can allow all, allow only basic sign-in, or **block all**.
- A blocked user sees the admin's custom message or a default one.
- Tasks is not named as a restrictable service on that page, and the scopes Google calls restricted are Gmail, Drive and
  the others in section 3. So a **Limited** setting should still let 4Tasks reach Tasks; **Blocked**, or "block all
  unconfigured", will stop it. I could not confirm from the page what an admin can do about Tasks specifically.

Does verification cover Workspace users? Verification is about the consent screen and the scope, and it removes the
unverified-app warning and the 100-user cap for **everyone**, Workspace users included. It does **not** override an
admin's controls: Workspace admins decide on top of it. So the answer for Workspace is the same as for Microsoft: a
verified app works wherever the admin allows third-party apps, and elsewhere the admin has to trust 4Tasks first
(by its Android package name or client ID in the Admin console). Plan: a help page for admins, and a clear message in
the app when Google says access is blocked.

Test: a Google Workspace test domain (a Workspace trial will do) with one user and an admin, in three settings: allowed,
Limited and Blocked. The owner's own mail is on Mailcow, not Workspace, so he has none today.

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
  including the Limited Use requirements." The page source is `play/site/4tasks/privacy/index.html`; it changes only
  on the owner's "publish".
- **Data safety:** the answer flips from "no data collected" to data that leaves the device to services the
  user picks: tasks (user-generated content) and the account identifier, encrypted in transit, not sold, not
  used for ads. Declared as collected, with the 4Dictate precedent for "shared" (user-initiated, to a service
  the user chose) reviewed again with the owner.
- **Work accounts add to the policy:** that an organisation's admin controls whether 4Tasks may connect and can
  see the consent; that tasks synced to a work account belong to that organisation's service and policies.
- **Help links:** About and the settings screens still open Tasks.org's help pages (backups, filters, notification
  troubleshooting). They stay for now and move to pages on `mr-biz.uk` before **open testing**, so a Play reviewer or user
  is not sent to another project's site.
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
   signs what Play installs. **Decided:** the Play app uses *our own* release key, uploaded to Play App Signing through PEPK (the 4Link spec
   requires it), so the production SHA-1 is known once the key exists and is **the SHA-1 of the NEW 4Tasks release key**
   (`5D:70:F9:67:FC:56:E7:00:F2:61:44:D7:2A:65:5B:72:29:8C:0E:6A`, SHA-256 `92:C5:1B:99:…:AD:06`, created 2026-10-03; the earlier `E6:2C:…` was 4Dictate's key and is void, never registered). The *upload* key does not matter to Google. (Had Play generated
   its own signing key, the SHA-1 would only be in Play Console after enrolment; that route is not taken.) A second client with the workstation debug SHA-1
   `16:3C:86:72:29:4C:69:FB:AB:52:1A:6A:03:DA:AD:BB:D3:9E:8C:4E` lets debug builds sign in.
8. **After internal testing:** put the final privacy text (section 4) live; write the scope justification (I draft
   it); record the **demo video** on a real phone with the Play build: the app, adding the Google account,
   Google's consent screen naming 4Tasks and the scope, tasks syncing, removing the account. Upload it unlisted
   to YouTube.
9. **Publish to production and submit for verification.** Then wait; answer the reviewer within days.

Microsoft (work and school accounts included; the requirements are from
[publisher verification](https://learn.microsoft.com/en-us/entra/identity-platform/publisher-verification-overview),
all free; the owner does 1 to 6 in this order, because each needs the one before):
1. **Microsoft AI Cloud Partner Program account** at Partner Center, verified, and it must be the *partner global account*
   (not a location account). Use a mailbox on `mr-biz.uk` for it. This is the step most likely to be slow: Microsoft
   verifies the organisation behind it, and I have not read what it asks of a sole trader trading as Mr-Bizzy.
   Start it first.
2. **A Microsoft Entra tenant** for the developer organisation (free), with `mr-biz.uk` added and verified there as a
   custom domain (a DNS record). Microsoft requires the CPP account's email domain to match the app's publisher
   domain or a DNS-verified custom domain in that tenant. The publisher domain cannot be `*.onmicrosoft.com`.
3. **An admin sign-in in that tenant** with multi-factor authentication, holding Application Administrator (or Cloud
   Application Administrator) in Entra **and** CPP Partner Admin or Account Admin in Partner Center. If the tenant
   is not the one tied to the partner account, associate the two in Partner Center.
4. **Register the app** "4Tasks" in that tenant (it must be registered with this work account, not with a personal
   Microsoft account, or it cannot be verified): supported account types **"Accounts in any organizational
   directory and personal Microsoft accounts"**; public client, no secret; platform Android with package
   `uk.mr_biz.fourtasks` and the signature hash of 4Tasks' own release certificate (section 5, Google step 7); delegated Graph
   permissions `User.Read`, `Tasks.ReadWrite`, `openid`, `offline_access`, `email`; and set the **publisher domain** to
   `mr-biz.uk`. Microsoft's own page on setting a publisher domain may ask for a small file on mr-biz.uk; I have not
   read it.
5. **Mark the app as publisher verified** by entering the Partner ID (Microsoft says this takes minutes once the
   requirements are met).
6. Give me the **Application (client) ID**.
Without step 5, work-account users in tenants with step-up consent cannot sign in (section 3a); personal accounts
are unaffected.

CalDAV: nothing to register. The owner supplies the Mailcow/SOGo URL, a test account and an app password.

## 6. Order and what blocks what

1. **Now, in parallel with Phase A:** Google steps 1 to 6 and Microsoft steps 1 to 3 (the Partner Center
   verification is the slow one and blocks Microsoft step 5). They cost the owner nothing. Publishing the home and
   privacy pages is the only step that needs the "publish".
2. Phase A (CalDAV), then Phase B (Microsoft, needs the client ID), then Phase C (Google, needs the Android
   client). Play internal testing starts when the app builds as an AAB with all three.
3. Google verification submission after internal testing and before open testing, as the owner set it.

## 7. Security: what the code does today (read from the source, 2026-10-03)

**How sync credentials and tokens are stored.** Every credential is encrypted before it is written, with
`KeyStoreEncryption` (AES-256 in GCM mode with a fresh random 12-byte IV for each value), using a key held in the **Android
Keystore** (alias `passwords`). The ciphertext goes into the account's password column in the app's own database.
That covers the CalDAV password or app password, the Microsoft sign-in state (it includes the refresh token) and
the Etebase session. Google Tasks keeps **no token of its own**: the phone's account manager hands one out per use.
Points to know:
- The key does not require the user to authenticate and is not asked to be StrongBox-backed (it is hardware-backed
  where the phone's secure hardware supports it). `setRandomizedEncryptionRequired(false)` is set, so the Keystore does not
  enforce unique IVs; the code generates them itself with `SecureRandom`, which is correct but is a convention, not an enforcement.
- The rest of the database is **not** encrypted: tasks, notes and list names are readable to anyone with the
  app's private storage (root, or a forensic image). Android's cloud backup is off.
- **The file backup** (Settings, Backups) writes the account rows too (`caldavAccounts`), so today it carries the password
  ciphertext, useless on another phone because the key never leaves this one. **Ruling: it must not include sync
  credentials.** Phase A changes it so the backup leaves the credential column out and a restore leaves the accounts
  needing sign-in again.
- A decryption failure returns an empty value without telling the user; sync then fails as "wrong password".
- I did not audit logging for tokens. That is a line item before release.

**Network.** The debug build has a network security config that permits plain HTTP; **release has none**, so cleartext is
blocked by Android's default (target 36). The HTTP client is OkHttp 5.5.0 with its default connection specs, which offer
modern TLS (1.2 and 1.3) and, in principle, cleartext, which the platform then refuses. minSdk is 33. The only custom
trust code is cert4android's, below.

## 8. Questions the owner has answered (2026-10-03)

**(a) cert4android's self-signed certificate trust: kept, behind "Advanced: allow self-signed certificates", off by
default.** What it does when on: the user trusts one exact certificate, kept in a private file in the
app's storage; **it is no longer accepted under a wrong hostname (ruling below)**; a changed certificate asks again; a background sync that
meets an unknown certificate posts a notification instead of trusting silently. With the switch off, ordinary users
never see the prompt, and a server with a certificate the phone does not trust simply fails with a clear message.

**(b) How credentials are stored:** section 7. **Ruling:** the file backup must not carry them.

**(b2) Hostname (ruling):** even with the switch on, a trusted certificate under the WRONG hostname is refused. See
Phase A for the change and its four tests.

**(c) Signing:** 4Tasks' own release key (`~/keys/4tasks-release.p12`) through PEPK (so the Google Android client uses that key's SHA-1, step 7).

**(d) Tasks.org help links:** stay for now, move to `mr-biz.uk` before open testing.

## 9. Not yet decided

- Nothing in this plan is open now. The go-ahead to start building Phase A has **not** been given.
