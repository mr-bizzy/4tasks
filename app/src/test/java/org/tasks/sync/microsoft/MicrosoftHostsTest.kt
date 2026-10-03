package org.tasks.sync.microsoft

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 4Tasks talks to exactly two Microsoft hosts, and the privacy page names them: login.microsoftonline.com (sign-in) and
 * graph.microsoft.com (the tasks). NoCallHomeTest allows them; this test makes sure no third Microsoft host appears in the code.
 * learn.microsoft.com is allowed because comments cite Microsoft's documentation there; it is never called.
 */
class MicrosoftHostsTest {
    @Test
    fun onlyTheTwoDocumentedMicrosoftHostsAreNamed() {
        val root = File(".").absoluteFile.normalize().let { if (File(it, "kmp").isDirectory) it else it.parentFile }
        val host = Regex("https?://([A-Za-z0-9.-]*(?:microsoft|windows|live|office|outlook)[A-Za-z0-9.-]*\\.[A-Za-z]{2,})")
        val found = listOf("app/src/main", "app/src/generic", "kmp/src/commonMain", "kmp/src/jvmCommonMain", "kmp/src/androidMain", "kmp/src/jvmMain", "data/src")
            .map { File(root, it) }.filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && (f.extension == "kt" || f.extension == "java") && !f.path.contains("/build/") }.toList() }
            .flatMap { f -> host.findAll(f.readText()).map { it.groupValues[1] }.toList() }
            .toSortedSet()
        // documentation links in comments and help text are not calls
        val called = found.filterNot { it.startsWith("learn.") || it.startsWith("techcommunity.") }.toSet()
        assertEquals(setOf("graph.microsoft.com", "login.microsoftonline.com"), called)
    }
}
