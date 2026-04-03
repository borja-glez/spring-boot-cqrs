plugins {
    id("cqrs-boot4-app-conventions")
}

dependencies {
    implementation(project(":spring-boot-cqrs-boot4-starter"))
    implementation(libs.spring.boot4.starter.web)
    testImplementation(libs.spring.boot4.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
