plugins {
    base
    jacoco
}

allprojects {
    group = property("group") as String
    version = property("version") as String
}

tasks.register("coverage") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs coverage verification for library modules."
    dependsOn(
        ":spring-boot-cqrs-core:jacocoTestCoverageVerification",
        ":spring-boot-cqrs-rabbitmq:jacocoTestCoverageVerification",
        ":spring-boot-cqrs-boot3-starter:jacocoTestCoverageVerification",
        ":spring-boot-cqrs-boot4-starter:jacocoTestCoverageVerification"
    )
}

tasks.register("quality") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs tests, coverage verification, and formatting checks."
    dependsOn(
        ":spring-boot-cqrs-core:test",
        ":spring-boot-cqrs-rabbitmq:test",
        ":spring-boot-cqrs-boot3-starter:test",
        ":spring-boot-cqrs-boot4-starter:test",
        "coverage",
        "spotlessCheckAll"
    )
}

tasks.register("spotlessCheckAll") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs Spotless check on all modules that have the plugin applied."
    dependsOn(
        ":spring-boot-cqrs-core:spotlessCheck",
        ":spring-boot-cqrs-rabbitmq:spotlessCheck",
        ":spring-boot-cqrs-boot3-starter:spotlessCheck",
        ":spring-boot-cqrs-boot4-starter:spotlessCheck",
        ":examples:example-basic:spotlessCheck",
        ":examples:example-rabbitmq:spotlessCheck",
        ":examples:example-middleware:spotlessCheck",
        ":examples:boot4-demo:spotlessCheck"
    )
}

tasks.register("spotlessApplyAll") {
    group = "formatting"
    description = "Runs Spotless apply on all modules that have the plugin applied."
    dependsOn(
        ":spring-boot-cqrs-core:spotlessApply",
        ":spring-boot-cqrs-rabbitmq:spotlessApply",
        ":spring-boot-cqrs-boot3-starter:spotlessApply",
        ":spring-boot-cqrs-boot4-starter:spotlessApply",
        ":examples:example-basic:spotlessApply",
        ":examples:example-rabbitmq:spotlessApply",
        ":examples:example-middleware:spotlessApply",
        ":examples:boot4-demo:spotlessApply"
    )
}
