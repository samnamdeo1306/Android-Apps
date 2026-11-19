package dev.notesmaker.buildlogic

import dev.notesmaker.buildlogic.ModuleGraphRules.Edge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleGraphRulesTest {

    private fun violations(vararg edges: Pair<String, String>) =
        ModuleGraphRules.violations(edges.map { (from, to) -> Edge(from, to, "implementation") })

    @Test
    fun `the designed graph has no violations`() {
        val designed = arrayOf(
            ":app" to ":core:data",
            ":app" to ":feature:capture",
            ":app" to ":engine:whisper",
            ":core:data" to ":core:domain",
            ":core:media" to ":core:domain",
            ":core:processing" to ":core:domain",
            ":core:ui" to ":core:domain",
            ":core:ui" to ":core:designsystem",
            ":feature:capture" to ":core:domain",
            ":feature:capture" to ":core:ui",
            ":feature:capture" to ":core:designsystem",
            ":engine:whisper" to ":core:domain",
            ":engine:notes-basic" to ":core:domain",
            ":export:text" to ":core:domain",
        )
        assertEquals(emptyList(), violations(*designed))
    }

    @Test
    fun `domain depends on nothing`() {
        assertEquals(1, violations(":core:domain" to ":core:data").size)
    }

    @Test
    fun `a module may use its own test fixtures`() {
        assertEquals(emptyList(), violations(":core:domain" to ":core:domain"))
    }

    @Test
    fun `features never depend on adapters, engines or other features`() {
        val found = violations(
            ":feature:sessions" to ":core:data",
            ":feature:sessions" to ":core:media",
            ":feature:sessions" to ":engine:whisper",
            ":feature:sessions" to ":feature:session",
        )
        assertEquals(4, found.size)
        assertTrue(found.all { it.contains("feature") })
    }

    @Test
    fun `engines depend only on the domain`() {
        assertEquals(2, violations(":engine:whisper" to ":core:media", ":engine:notes-basic" to ":engine:whisper").size)
    }

    @Test
    fun `adapters do not depend on each other`() {
        assertEquals(1, violations(":core:processing" to ":core:data").size)
    }

    @Test
    fun `an unknown kind of module is reported so a rule gets added on purpose`() {
        val found = violations(":benchmark" to ":app")
        assertEquals(1, found.size)
        assertTrue(found.single().contains("no module rule"))
    }

    @Test
    fun `violation names the configuration`() {
        val found = ModuleGraphRules.violations(listOf(Edge(":feature:capture", ":core:data", "androidTestImplementation")))
        assertTrue(found.single().endsWith("[androidTestImplementation]"))
    }
}
