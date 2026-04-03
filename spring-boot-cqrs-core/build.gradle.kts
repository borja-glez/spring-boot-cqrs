plugins {
    id("cqrs-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

dependencies {
    api(libs.spring.boot.starter)

    compileOnly(libs.spring.boot.starter.validation)
    compileOnly(libs.reactor.core)
    compileOnly(libs.micrometer.core)
    compileOnly(libs.micrometer.observation)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jackson.datatype.jsr310)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.reactor.core)
    testImplementation(libs.reactor.test)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.micrometer.observation)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
}
