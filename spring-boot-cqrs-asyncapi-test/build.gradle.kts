plugins {
    id("cqrs-boot3-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
    id("cqrs-mutation-conventions")
}

description = "Test support that verifies spring-boot-cqrs messages against an AsyncAPI 3.0 contract"

dependencies {
    api(project(":spring-boot-cqrs-core"))

    // Jackson 2 only reads the AsyncAPI document and the JSON Schemas; it is not exposed in the API.
    implementation(libs.json.schema.validator)
    implementation(libs.jackson.dataformat.yaml)

    // Another json-schema-validator 2.x user, to run the verifier on the classpath it resolves.
    testImplementation(libs.openapi.request.validator.core)
}
