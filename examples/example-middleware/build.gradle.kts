plugins {
    id("cqrs-boot3-app-conventions")
}

dependencies {
    implementation(project(":spring-boot-cqrs-boot3-starter"))
    implementation(libs.spring.boot.starter.web)
}
