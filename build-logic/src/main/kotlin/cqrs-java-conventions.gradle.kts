plugins {
    `java-library`
    jacoco
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("java", "javax", "jakarta", "org", "com", "io")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-parameters"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
    }
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

// -PtestJavaVersion=<n> runs the tests on a JDK <n> launcher while compilation stays on the Java 21
// toolchain with release 21, proving the Java 21 bytecode also works on newer runtimes.
val testJavaVersion = providers.gradleProperty("testJavaVersion")
val javaToolchains = extensions.getByType<JavaToolchainService>()

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (testJavaVersion.isPresent) {
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(testJavaVersion.get()))
            }
        )
    }
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    violationRules {
        rule {
            limit {
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
