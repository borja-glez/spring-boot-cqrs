plugins {
    java
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
