plugins {
    id("cqrs-boot4-app-conventions")
}

dependencies {
    implementation(project(":spring-boot-cqrs-boot4-starter"))
    implementation(project(":spring-boot-cqrs-rabbitmq"))
    implementation(project(":examples:multi-service:shared"))
    implementation(libs.spring.boot4.starter.web)
    implementation(libs.spring.boot4.starter.amqp)
    implementation(libs.jackson.databind.versioned)
    implementation(libs.spring.boot4.docker.compose)
    testImplementation(libs.spring.boot4.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
