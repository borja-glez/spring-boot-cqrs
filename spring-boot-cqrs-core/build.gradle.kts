plugins {
    id("cqrs-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

dependencies {
    api(libs.spring.context)
    implementation(libs.spring.tx)

    api(libs.jakarta.validation.api)
    compileOnly(libs.micrometer.core)
    compileOnly(libs.micrometer.observation)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.spring.boot3.starter.validation)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.micrometer.observation)
    testImplementation(libs.micrometer.observation.test)
    testImplementation(libs.slf4j.api)
}
