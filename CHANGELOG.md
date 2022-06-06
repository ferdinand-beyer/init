# Changelog

## [Unreleased]

Rework in `init.graph`:

- Compute dependency order once and reuse it
- **Breaking**: Use `com.stuartsierra/dependency` instead of `weavejester/dependency`
- **Breaking**: `init.graph/get-component` is gone
- **Breaking**: `init.graph/[reverse]-dependency-order` returns entries instead of keys

## [0.1.77] (2022-06-05)

- Recognise type hints as tags
- Wrap exceptions thrown on component start and stop
- Stop partially started system on exception

## [0.1.67] (2022-06-04)

First public release

[Unreleased]: https://github.com/ferdinand-beyer/init/compare/v0.1.77...HEAD
[0.1.77]: https://github.com/ferdinand-beyer/init/compare/v0.1.67...v0.1.77
[0.1.67]: https://github.com/ferdinand-beyer/init/releases/tag/v0.1.67
