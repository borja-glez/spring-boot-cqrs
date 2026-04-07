# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
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
