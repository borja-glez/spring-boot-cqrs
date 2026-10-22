plugins {
    id("cqrs-library-conventions")
    id("io.spring.dependency-management")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        // Imported after the Spring Boot BOM so the catalog JUnit version wins over the one Boot manages.
        mavenBom("org.junit:junit-bom:${libs.findVersion("junit").get().requiredVersion}")
    }
}
