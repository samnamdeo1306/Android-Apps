package dev.notesmaker.buildlogic

/**
 * The dependency rules of HLD Section 4.2 as data, so they can be unit tested without Gradle.
 *
 * Each module kind lists the modules it may depend on. A module that matches no kind is itself a
 * violation: adding a new kind of module means adding a rule here on purpose.
 */
object ModuleGraphRules {

    data class Edge(val from: String, val to: String, val configuration: String)

    private class Rule(val kind: String, val matches: (String) -> Boolean, val mayDependOn: (String) -> Boolean)

    private const val DOMAIN = ":core:domain"
    private val adapterModules = setOf(":core:data", ":core:media", ":core:processing")

    private val rules = listOf(
        Rule("app", { it == ":app" }, { true }),
        Rule("domain (rule 1)", { it == DOMAIN }, { false }),
        Rule("design system", { it == ":core:designsystem" }, { false }),
        Rule("ui helpers", { it == ":core:ui" }, { it == DOMAIN || it == ":core:designsystem" }),
        Rule("adapter", { it in adapterModules }, { it == DOMAIN }),
        Rule(
            "feature (rules 2 and 3)",
            { it.startsWith(":feature:") },
            { it == DOMAIN || it == ":core:ui" || it == ":core:designsystem" },
        ),
        Rule("engine (rule 4)", { it.startsWith(":engine:") }, { it == DOMAIN }),
        // Planned exporters (HLD Section 4.1) are plug-ins like engines.
        Rule("exporter", { it.startsWith(":export:") }, { it == DOMAIN }),
    )

    fun violations(edges: Collection<Edge>): List<String> = edges
        .filter { it.from != it.to } // a module's tests may use its own test fixtures
        .mapNotNull { edge ->
            val rule = rules.firstOrNull { it.matches(edge.from) }
                ?: return@mapNotNull "${edge.from} has no module rule; add one to ModuleGraphRules"
            if (rule.mayDependOn(edge.to)) {
                null
            } else {
                "${edge.from} (${rule.kind}) must not depend on ${edge.to} [${edge.configuration}]"
            }
        }
        .distinct()
}
