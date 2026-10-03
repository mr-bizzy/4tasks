package org.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The privacy policy says 4Tasks sends your tasks only to the server you chose, and the app makes no other connection.
 * Upstream (Tasks.org) code has more: a blog feed, its own cloud, location search. An upstream merge must not quietly bring a
 * call home back, so this test fails when:
 *  - a source file that can make network calls is not on the list below (a new file, or a renamed one);
 *  - one of those files names a fixed host that is not on the list below;
 *  - the Tasks.org blog check is scheduled again.
 * If it fails: a file that talks to a server the USER types in is fine, add it with a reason. A fixed host is not fine unless the
 * policy and the manual are changed first.
 */
class NoCallHomeTest {
    private val roots = listOf(
        "src/main", "src/generic",
        "../kmp/src/commonMain", "../kmp/src/androidMain", "../kmp/src/jvmCommonMain", "../kmp/src/jvmMain",
        "../data/src",
    )

    private val networkCapable = Regex(
        "okhttp3|HttpURLConnection|\\.openConnection\\(|OkHttpClientFactory|HttpClientFactory|java\\.net\\.URL\\b|\\bURL\\(|ktor|\\bHttpClient\\b"
    )
    private val fixedHost = Regex("https?://([A-Za-z0-9.-]+\\.[A-Za-z]{2,})")

    /** Files that may make network calls, and why that is acceptable. Paths are relative to the repository root. */
    private val knownNetworkFiles = mapOf(
        // To the CalDAV server the user typed in (or shared by it).
        "kmp/src/jvmCommonMain/kotlin/org/tasks/caldav/CaldavClient.kt" to "CalDAV, the user's server",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/caldav/CaldavClientProvider.kt" to "CalDAV, the user's server",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/caldav/CaldavSynchronizer.kt" to "CalDAV, the user's server",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/caldav/CapabilityProbe.kt" to "CalDAV, the user's server",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/caldav/metadata/TagMetadataSync.kt" to "CalDAV, the user's server",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/http/DefaultOkHttpClientFactory.kt" to "HTTP client plumbing",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/http/HttpErrorHandler.kt" to "HTTP client plumbing",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/http/OkHttpClientFactory.kt" to "HTTP client plumbing",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/http/UserAgentInterceptor.kt" to "HTTP client plumbing",
        "kmp/src/jvmMain/kotlin/org/tasks/http/DesktopCookieStorage.kt" to "desktop target, not in the Android app",
        "kmp/src/jvmMain/kotlin/org/tasks/http/DesktopOkHttpClientFactory.kt" to "desktop target, not in the Android app",
        "kmp/src/jvmMain/kotlin/org/tasks/http/EncryptedCookieStore.kt" to "desktop target, not in the Android app",
        "kmp/src/androidMain/kotlin/org/tasks/http/AndroidOkHttpClientFactory.kt" to "HTTP client plumbing (certificate trust)",
        "kmp/src/androidMain/kotlin/org/tasks/caldav/TasksCookieJar.kt" to "cookies for the user's CalDAV server",
        "app/src/main/java/org/tasks/http/HttpClientFactory.kt" to "HTTP client plumbing (the self-signed switch)",
        "app/src/main/java/org/tasks/http/AndroidCookieStorage.kt" to "cookies for the user's CalDAV server",
        "app/src/main/java/org/tasks/injection/ApplicationModule.kt" to "wires the clients",
        "app/src/main/java/org/tasks/auth/DebugConnectionBuilder.kt" to "OAuth debug helper, not reachable",
        // Not reachable in this build: the features are switched off (PlatformConfiguration) and their screens are not shown.
        "kmp/src/jvmCommonMain/kotlin/org/tasks/caldav/TasksClient.kt" to "Tasks.org account: off",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/caldav/TasksBasicAuth.kt" to "Tasks.org account: off",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/auth/TasksOAuthClient.kt" to "Tasks.org account: off",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/billing/DesktopLinkServiceImpl.kt" to "Tasks.org desktop link: off",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/sse/SseClient.kt" to "Tasks.org account: off",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/etebase/EtebaseClientProvider.kt" to "Etebase: off",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/etebase/OkHttpBridge.kt" to "Etebase: off",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/googleapis/ProxyAuthProvider.kt" to "Google Tasks: off until its phase",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/http/MicrosoftGraphClient.kt" to "Microsoft To Do sync: Graph, after the user signs in",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/sync/microsoft/MicrosoftService.kt" to "Microsoft To Do sync: Graph, after the user signs in",
        "kmp/src/jvmCommonMain/kotlin/org/tasks/sync/microsoft/MicrosoftSynchronizer.kt" to "Microsoft To Do sync, after the user signs in",
        "kmp/src/jvmMain/kotlin/org/tasks/sync/microsoft/DesktopMicrosoftClientProvider.kt" to "Microsoft, desktop target",
        "app/src/generic/java/org/tasks/auth/MicrosoftAuthenticationActivity.kt" to "Microsoft sign-in: the userinfo call, after the user signs in",
        "app/src/main/java/org/tasks/location/GeocoderMapbox.kt" to "location: off",
        "app/src/main/java/org/tasks/location/GeocoderNominatim.kt" to "location: off",
        "app/src/main/java/org/tasks/location/PlaceSearchGoogle.kt" to "location: off",
        "app/src/main/java/org/tasks/location/PlaceSearchMapbox.kt" to "location: off",
        // Switched off in code (see the last test): scheduleBlogFeedCheck() only cancels, the worker does nothing.
        "kmp/src/jvmCommonMain/kotlin/org/tasks/feed/BlogFeedChecker.kt" to "Tasks.org blog: DISABLED, see the last test",
    )

    /** Fixed hosts those files may name. */
    private val knownHosts = mapOf(
        "tasks.org" to "BlogFeedChecker: disabled",
        "api.mapbox.com" to "location: off",
        // Microsoft To Do sync is ON (docs/SYNC-PLAN.md phase B): these two hosts are what the privacy page names for it.
        // login.microsoftonline.com is where the user signs in (browser, AppAuth token calls); graph.microsoft.com holds the tasks.
        "login.microsoftonline.com" to "Microsoft sign-in, only after the user chooses to add a Microsoft account",
        "graph.microsoft.com" to "Microsoft To Do sync (tasks) and the signed-in user's profile, after sign-in",
        "www.apache.org" to "a licence URL in a debug helper's text",
    )

    private fun repoRoot(): File {
        // unit tests run in the module directory (app/)
        return File(".").absoluteFile.normalize().let { if (File(it, "kmp").isDirectory) it else it.parentFile }
    }

    private fun sources(): List<File> {
        val base = File(".").absoluteFile.normalize()
        return roots.map { File(base, it).normalize() }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") && !it.path.contains("/build/") }.toList() }
    }

    private fun rel(f: File) = f.relativeTo(repoRoot()).path.replace(File.separatorChar, '/')

    @Test
    fun onlyKnownFilesCanMakeNetworkCalls() {
        val found = sources().filter { networkCapable.containsMatchIn(it.readText()) }.map(::rel).toSortedSet()
        val unknown = found - knownNetworkFiles.keys
        assertTrue(
            "These files can make network calls and are not on the list in NoCallHomeTest (a call home may have come back " +
                "with an upstream merge): $unknown",
            unknown.isEmpty(),
        )
    }

    @Test
    fun onlyKnownFixedHostsAreNamedInThoseFiles() {
        val hosts = sources().filter { networkCapable.containsMatchIn(it.readText()) }
            .flatMap { fixedHost.findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }
            .toSortedSet()
        val unknown = hosts - knownHosts.keys
        assertTrue("New fixed hosts in network-capable code: $unknown. The privacy policy says only the user's server.", unknown.isEmpty())
    }

    @Test
    fun theTasksOrgBlogCheckStaysSwitchedOff() {
        val work = sources().first { it.name == "WorkManagerImpl.kt" }.readText()
        val body = work.substringAfter("override suspend fun scheduleBlogFeedCheck()").substringBefore("\n    }\n")
        assertTrue("scheduleBlogFeedCheck must only cancel", body.contains("cancelUniqueWork(TAG_BLOG_FEED)"))
        assertFalse("scheduleBlogFeedCheck must not enqueue anything", body.contains("enqueue"))
        val job = sources().first { it.name == "BlogFeedWork.kt" }.readText()
        assertFalse("the blog worker must not run the checker", job.contains("blogFeedChecker.check()"))
        assertEquals("the blog feed must not be fetched", false, work.contains("BlogFeedWork::class.java"))
    }
}
