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

    testImplementation(libs.spring.boot3.starter.validation)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.micrometer.observation)
}
