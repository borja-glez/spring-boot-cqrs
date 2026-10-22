plugins {
    java
    id("info.solidsoft.pitest")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// The Spring Boot 3 BOM manages JUnit Jupiter at an older version than the platform launcher this
// build declares; PIT's JUnit 5 plugin needs them aligned, so use the catalog JUnit version.
extra["junit-jupiter.version"] = libs.findVersion("junit").get().requiredVersion

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
