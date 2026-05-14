plugins {
    id("cqrs-boot3-app-conventions")
}

dependencies {
    implementation(project(":spring-boot-cqrs-boot3-starter"))
    implementation(libs.spring.boot.starter.web)

    testImplementation(project(":spring-boot-cqrs-test"))
    testImplementation(libs.spring.boot.starter.test)
}
