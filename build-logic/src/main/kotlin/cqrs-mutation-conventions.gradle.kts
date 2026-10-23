plugins {
    java
    id("info.solidsoft.pitest")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Report-only mutation testing: no threshold, and it is not wired into `check` or `quality`.
// Run it through the root `mutationReport` task.
pitest {
    pitestVersion.set(libs.findVersion("pitest").get().requiredVersion)
    junit5PluginVersion.set(libs.findVersion("pitest-junit5").get().requiredVersion)
    targetClasses.set(listOf("com.borjaglez.cqrs.*"))
    // Integration tests need Docker (Testcontainers); mutation runs must work without it.
    excludedTestClasses.set(listOf("*.integration.*", "*IntegrationTest"))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    threads.set(Runtime.getRuntime().availableProcessors())
}
