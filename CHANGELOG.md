# Changelog

## [Unreleased]

## [0.2.90] (2022-10-27)

- Reimplemented classpath scanning without `clojure.tools.namespace`.  
  Uses a simpler heuristic without reading `ns` macros, and also works
  with ahead-of-time compiled `<name>__init.class` files.
- Don't load found namespaces at compile time in `static-scan`, as this often
  causes runtime errors such as `java.lang.NoSuchFieldError: __thunk__0__`.  
  Instead, just `require` the namespaces and build the config at runtime using
  `from-namespaces`.

## [0.2.86] (2022-10-26)

- [#1](https://github.com/ferdinand-beyer/init/issues/1):
  Support vars in `:init/inject`
- Fix: Components without dependencies are not started

## [0.2.83] (2022-07-15)

- Automatically close `java.lang.AutoCloseable` components

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

[Unreleased]: https://github.com/ferdinand-beyer/init/compare/v0.2.90...HEAD
[0.2.90]: https://github.com/ferdinand-beyer/init/compare/v0.2.86...v0.2.90
[0.2.86]: https://github.com/ferdinand-beyer/init/compare/v0.2.83...v0.2.86
[0.2.83]: https://github.com/ferdinand-beyer/init/compare/v0.1.77...v0.2.83
[0.1.77]: https://github.com/ferdinand-beyer/init/compare/v0.1.67...v0.1.77
[0.1.67]: https://github.com/ferdinand-beyer/init/releases/tag/v0.1.67
