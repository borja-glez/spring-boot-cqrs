plugins {
    id("cqrs-boot3-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

description = "Test utilities (spy/in-memory buses, AssertJ assertions, @CqrsTest slice) for spring-boot-cqrs"

dependencies {
    api(project(":spring-boot-cqrs-core"))

    compileOnly(libs.spring.boot.starter.test)
    compileOnly(libs.assertj.core)
    compileOnly(libs.junit.jupiter)
    compileOnly(libs.archunit)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
}
