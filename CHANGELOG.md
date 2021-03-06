# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

**Note:** you might notice that `1.0.x` tags were used early on in the life of this project. Ignore them. No release artifacts were published for those tags. The first published artifact was version `0.4.0`.


## [Unreleased][unreleased]
### Changed
- Improved CHANGELOG.md format
- Updated Scala & SBT versions
- Improved type specificity of config values for batched writes, like time durations

## [0.6.0] - 2016-02-26
### Added
- In-memory queueing - #38 (terryhorner)

## [0.5.0] - 2015-01-20
### Added
- Configuration with Typesafe Config - #25 (ches)
- Added extraction method - #28 (gphat)

## [0.4.0] - 2015-01-03
### Added
* Began CHANGELOG
* Added MIT license - #22 (gphat)
* Added bits for artifact publishing to maven central - #23 (ches)
* Added HTTP adapter based on Dispatch as an alternative to Spray - #14 (ZhangBanger)

### Changed
* Switched to trait-based key setup when creating client - #20 (ches)
* Removed unnecessary StandardCharset import (gphat)

[unreleased]: https://github.com/keenlabs/KeenClient-Scala/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/keenlabs/KeenClient-Scala/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/keenlabs/KeenClient-Scala/compare/v1.0.3...v0.5.0
[0.4.0]: https://github.com/keenlabs/KeenClient-Scala/compare/v1.0.0...v0.5.0
