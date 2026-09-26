# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Breaking changes are marked with **BREAKING:**; [docs/upgrading.md](docs/upgrading.md)
explains how to migrate.
## [0.4.0] - 2026-09-26

### Added
- **BREAKING:** Run outbound middleware on the sending side of remote buses (#106) (e5a33e0)
- **BREAKING:** Expose only @CqrsMessage messages and let handlers stay local (#103) (4c12138)
- **BREAKING:** Add configurable retry and per-application dead-letter topics to Kafka consumers (#102) (da73bcf)
- Propagate MessageContext to executor and @Async threads (#100) (80f444f)
- Add opt-in retry middleware with backoff for commands and queries (#99) (8a6bdf9)
- Key records by the key messages declare through KeyedMessage (#97) (6b0e333)
- Add a SpEL condition attribute to @HandleEvent (#96) (57423e5)
- Add cqrs.rabbitmq.{commands,queries,events}.enabled to enable each bus (#95) (9a99a39)

### Documentation
- Prepare the 0.4.0 release (#109) (ceb4338)

### Fixed
- **BREAKING:** Propagate broker failures from RabbitMqEventBus (#101) (e77e393)
- **BREAKING:** Propagate broker failures from KafkaEventBus (#98) (a07d97f)

### Build
- Keep the release workflow's own commits out of the changelog (#105) (3549fb0)
- Publish only the checksum files Maven Central requires (#94) (6f851cd)
## [0.3.1] - 2026-09-26

### Added
- Record the failure cause on dead-lettered messages (#89) (43eea54)

### Documentation
- Document and test that generic remote results need a ParameterizedTypeReference (#92) (5ed5bec)
- Add the Kafka adapter guide and document where middleware runs (#85) (053d772)
- Describe how to work on an issue (#78) (f1bbb3b)

### Fixed
- Send MessageContext headers with request-reply commands and queries (#91) (f8eb225)
- Reject abstract handler parameters and report unhandled message subclasses (#90) (ac7c3dd)
- Start the reply consumer at the instance start time (#88) (619454f)
- Fail at startup when a handler method cannot be invoked through the bean's proxy (#87) (dcf1427)
- Let a middleware call MiddlewareChain.proceed more than once (#86) (a0755bb)

### Ci
- Update workflow actions to their Node 24 releases (#77) (5e1a0d3)
## [0.3.0] - 2026-09-26

### Added
- Enable each bus separately and skip replies for event-only apps (#50) (f0cca6e)

### Fixed
- Answer 400 for an unknown section or handler kind (#75) (635d169)
- **BREAKING:** Retry a failed message in its own application and honour max-attempts (#73) (d80f5e5)
- Continue the sender's trace in the listener containers (#70) (8a5655e)
- Reply null results and failures without a message correctly (#58) (6db8b5e)
- Leave the commands and queries of other services alone (#56) (a295d5a)
- Carry the trace through the module's template and containers (#54) (04c7e40)
- Let the Kafka and RabbitMQ modules run in the same application (#52) (630ae40)
- Support Spring Boot 4 by not depending on Boot's KafkaProperties (#48) (b5e6165)
- Tell a missing reply from a null result (#46) (dbeb432)
- Deliver handler failures to RPC callers (#43) (8c235bd)
- Publish events raised after commit and keep REQUIRES_NEW events apart (#41) (44b370f)
- **BREAKING:** Give the dispatch observation its own name (#39) (399a50d)
- Order bus metrics and tracing after Spring Boot's registries (#37) (2cc1cd0)
- Register native hints for every message a native image serializes (#35) (9b3832f)
- Make the JSON converter's trusted packages configurable (#33) (ab5d5f0)
- Keep message ids when messages are deserialized with Jackson 3 (#31) (6124807)
- Create the message serializer after Spring Boot's Jackson auto-configuration (#29) (a708adb)
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
- **BREAKING:** Migrate Maven group ID to com.borjaglez.cqrs (#11) (#23) (05479da)
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
