plugins {
    id("cqrs-library-conventions")
    id("cqrs-publish-conventions")
}

dependencies {
    api(project(":spring-boot-cqrs-core"))
    api(libs.spring.boot4.autoconfigure)

    compileOnly(libs.spring.boot4.starter.validation)
    compileOnly(libs.micrometer.core.versioned)
    compileOnly(libs.micrometer.observation.versioned)
    compileOnly(libs.jackson.databind.versioned)
    compileOnly(libs.jackson.datatype.jsr310.versioned)

    annotationProcessor(libs.spring.boot4.configuration.processor)
    annotationProcessor(libs.spring.boot4.autoconfigure.processor)

    testImplementation(libs.spring.boot4.starter.test)
    testImplementation(libs.spring.boot4.starter.validation)
    testImplementation(libs.micrometer.core.versioned)
    testImplementation(libs.micrometer.observation.versioned)
    testImplementation(libs.jackson.databind.versioned)
    testImplementation(libs.jackson.datatype.jsr310.versioned)
}
