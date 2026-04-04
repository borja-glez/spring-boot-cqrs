plugins {
    id("cqrs-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

dependencies {
    api(libs.spring.context)

    api(libs.jakarta.validation.api)
    compileOnly(libs.micrometer.core)
    compileOnly(libs.micrometer.observation)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jackson.datatype.jsr310)

    testImplementation(libs.spring.boot3.starter.validation)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.micrometer.observation)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
}
