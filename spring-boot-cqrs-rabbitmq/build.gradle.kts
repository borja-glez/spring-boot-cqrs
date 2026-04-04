plugins {
    id("cqrs-boot3-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

dependencies {
    api(project(":spring-boot-cqrs-core"))
    api(libs.spring.boot.starter.amqp)
    implementation(libs.jackson.databind)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(project(":spring-boot-cqrs-boot3-starter"))
    testImplementation(libs.testcontainers.rabbitmq)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.awaitility)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
}
