plugins {
    id("cqrs-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

dependencies {
    api(project(":spring-boot-cqrs-core"))
    api(libs.spring.boot.starter.amqp)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(project(":spring-boot-cqrs-boot3-starter"))
    testImplementation(libs.testcontainers.rabbitmq)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
}
