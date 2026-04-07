plugins {
    id("cqrs-boot3-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

dependencies {
    api(project(":spring-boot-cqrs-core"))
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.kafka)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":spring-boot-cqrs-boot3-starter"))
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.jackson.databind)
}
