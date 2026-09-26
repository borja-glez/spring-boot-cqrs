plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set(project.name)
        description.set(project.findProperty("description")?.toString() ?: project.name)
        url.set("https://github.com/borja-glez/spring-boot-cqrs")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("borja-glez")
                name.set("Borja Gonzalez Enriquez")
                email.set("borja@borjaglez.com")
                url.set("https://github.com/borja-glez")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/borja-glez/spring-boot-cqrs.git")
            developerConnection.set("scm:git:ssh://git@github.com:borja-glez/spring-boot-cqrs.git")
            url.set("https://github.com/borja-glez/spring-boot-cqrs")
        }
    }
}

afterEvaluate {
    extensions.findByType<SigningExtension>()?.isRequired = System.getenv("CI") != null
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        versionMapping {
            allVariants {
                fromResolutionResult()
            }
        }
    }
}

// Maven Central counts every uploaded file. It needs the artifacts, their .asc signatures and md5/sha1
// checksums of the artifacts; the checksums Gradle also writes for the signatures and the sha256/sha512
// ones are optional, so they are removed from the staging directory before the deployment is uploaded.
tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToMavenCentralRepository")) {
        val stagingDirectory = project.layout.buildDirectory.dir("publishing/mavenCentral")
        doLast {
            stagingDirectory.get().asFile.walkTopDown()
                .filter { file ->
                    file.isFile && (file.name.contains(".asc.") ||
                        file.name.endsWith(".sha256") || file.name.endsWith(".sha512"))
                }
                .forEach { it.delete() }
        }
    }
}
