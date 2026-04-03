plugins {
    id("cqrs-boot3-app-conventions")
}

dependencies {
    implementation(project(":spring-boot-cqrs-boot3-starter"))
    implementation(project(":spring-boot-cqrs-rabbitmq"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.docker.compose)
    developmentOnly(libs.spring.boot.devtools)
}
