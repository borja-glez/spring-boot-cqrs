plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
    "testImplementation"(platform(libs.findLibrary("testcontainers-bom").get()))
    "testImplementation"(libs.findLibrary("testcontainers-junit").get())
    "testImplementation"(libs.findLibrary("awaitility").get())
    "testImplementation"(libs.findLibrary("reactor-test").get())
}
