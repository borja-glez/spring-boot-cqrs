plugins {
    id("cqrs-boot3-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

description = "JDBC support for spring-boot-cqrs: transactional idempotency store"

dependencies {
    api(project(":spring-boot-cqrs-core"))
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.jdbc)
    compileOnly(libs.micrometer.tracing)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(project(":spring-boot-cqrs-boot3-starter"))
    testImplementation(libs.micrometer.tracing)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.awaitility)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.postgresql)
}
