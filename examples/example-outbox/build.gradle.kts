plugins {
    id("cqrs-boot3-app-conventions")
}

dependencies {
    implementation(project(":spring-boot-cqrs-boot3-starter"))
    implementation(project(":spring-boot-cqrs-jdbc"))
    implementation(project(":spring-boot-cqrs-kafka"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.docker.compose)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.awaitility)
}
