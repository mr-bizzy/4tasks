package org.tasks.fourlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.mr_biz.fourlink.Catalogue
import uk.mr_biz.fourlink.Effect
import uk.mr_biz.fourlink.FourLink

class TasksCatalogueTest {
    private val functions = TasksCatalogue.CATALOGUE.functions

    @Test fun `phase 1 offers these four functions and no delete`() {
        assertEquals(listOf("tasks.add", "tasks.list", "tasks.complete", "lists.list"), functions.map { it.id })
        assertFalse(functions.any { it.effect == Effect.DELETE })
    }

    @Test fun `reads read and changes change`() {
        assertEquals(
            mapOf("tasks.add" to Effect.CREATE, "tasks.list" to Effect.READ, "tasks.complete" to Effect.CHANGE, "lists.list" to Effect.READ),
            functions.associate { it.id to it.effect },
        )
    }

    @Test fun `titles and descriptions are within the spec's limits`() {
        for (f in functions) {
            assertTrue("${f.id} title", f.title.length <= FourLink.TITLE_MAX)
            assertTrue("${f.id} description ${f.description.length}", f.description.length <= FourLink.DESCRIPTION_MAX)
            for ((name, schema) in f.input.properties + f.output.properties) {
                assertTrue("${f.id}.$name description", (schema.description?.length ?: 0) <= FourLink.PROPERTY_DESCRIPTION_MAX)
            }
        }
    }

    @Test fun `every function survives the reader, so none uses anything outside the schema subset`() {
        val read = Catalogue.parse(TasksCatalogue.CATALOGUE.toJson().toString())!!
        assertEquals(functions.map { it.id }, read.catalogue.functions.map { it.id })
    }

    @Test fun `only title is required to add a task`() {
        assertEquals(setOf("title"), functions.first { it.id == "tasks.add" }.input.required)
    }

    @Test fun `a list result has a count and a text, as arrays are outside the subset`() {
        for (id in listOf("tasks.list", "lists.list")) {
            assertEquals(setOf("count", "tasks").takeIf { id == "tasks.list" } ?: setOf("count", "lists"),
                functions.first { it.id == id }.output.properties.keys)
        }
    }

    @Test fun `the string fields have the maximums the door enforces`() {
        val add = functions.first { it.id == "tasks.add" }.input.properties
        assertEquals(TasksCatalogue.TITLE_MAX, (add["title"] as uk.mr_biz.fourlink.Schema.Str).maxLength)
        assertEquals(TasksCatalogue.NOTES_MAX, (add["notes"] as uk.mr_biz.fourlink.Schema.Str).maxLength)
    }
}
