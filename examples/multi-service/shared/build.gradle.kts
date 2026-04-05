plugins {
    id("cqrs-java-conventions")
}

dependencies {
    implementation(project(":spring-boot-cqrs-core"))
    compileOnly(libs.jakarta.validation.api)
    testImplementation(platform(libs.junit.bom))
}
