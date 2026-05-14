# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
## [0.2.0] - 2026-05-14

### Added
- Expose Spring Boot Actuator endpoints for CQRS (#8) (#27) (6b2ded3)
- Add distributed tracing middleware (#7) (#26) (0817320)
- Add spring-boot-cqrs-test module (#14) (#25) (3b4d806)
- Add MessageContext propagation and MDC integration (#15) (#24) (5de2929)
- Add handler introspection API (#22) (09b5967)
- Publish after commit and add outbox example (#13) (28654d7)
- Add Kafka transport module and generic serializer support (#9) (d1a4ba5)

### Miscellaneous
- Migrate Maven group ID to com.borjaglez.cqrs (#11) (#23) (05479da)
- Bump version to 0.1.1-SNAPSHOT (a110ed1)
## [0.1.0] - 2026-04-07

### Miscellaneous
- Release workflow update readme update (39d60cb)
- Pre releases set changelog as body (b7114a1)
## [0.1.0-rc.0] - 2026-04-06

### Miscellaneous
- Pre releases set changelog as body (848080e)
- Update actions versions (fb75ab8)
## [0.1.0-beta.1] - 2026-04-05

### Added
- Command and query bus now supports ParameterizedTypeReference (1385e6f)
- Add dispatch and wait to command bus (cb29628)
- Add middlewares support (abd5cb5)
- Core and rabbitmq remove required use jackson 2 dependency; now uses a factory to json message converter (27e63bb)
- Spring boot 4 jackson 3 message serializer (4c4fa6b)
- Initial version deployment (0ce5fdc)

### Miscellaneous
- Add new microservices example with cross service communication with rabbitmq bus (83bc121)
- Add pr simple template (c606d6a)
- Use spring framework in core libs instead spring boot (f4d6334)
- On release update README with new version (f73da0e)
- Add docker compose spring boot to example (abcc636)
- Add executable permissions to gradlew (7b8441a)

### Testing
- Spring boot application test remove scanBasePackages (232c1f7)
- Add CqrsAutoConfiguration to test application (67c8d78)
