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
