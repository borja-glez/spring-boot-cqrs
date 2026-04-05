pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "spring-boot-cqrs"

include(
    ":spring-boot-cqrs-core",
    ":spring-boot-cqrs-rabbitmq",
    ":spring-boot-cqrs-boot3-starter",
    ":spring-boot-cqrs-boot4-starter",
    ":examples:example-basic",
    ":examples:example-rabbitmq",
    ":examples:example-middleware",
    ":examples:boot4-demo",
    ":examples:multi-service:shared",
    ":examples:multi-service:order-service",
    ":examples:multi-service:inventory-service",
    ":examples:multi-service:notification-service"
)
