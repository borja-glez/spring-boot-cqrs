plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.12")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("io.freefair.gradle:lombok-plugin:9.2.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.36.0")
}
