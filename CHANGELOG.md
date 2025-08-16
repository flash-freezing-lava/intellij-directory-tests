# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Since `2025.1-1.0` the versioning scheme is `intellijRelease-major.minor`.
Intellij releases are automatically major releases and reset major version to 1.

## [Unreleased]

## [2025.2-1.0] 2025-08-16

### Changed
- Updated to intellij platform `2025.2`

## [2025.1-1.0] 2025-04-25

### Breaking
- Test no longer pass if any plugin failed to load

### Added
- `DirectoryTestConfig.overrideDocumentationOutput` allows to overwrite documentation similar to `overrideParserOutput`

### Changed
- Updated to intellij platform `2025.1`

## [0.6.0] 2024-12-22

### Changed
- Updated to intellij platform `2024.3`

## [0.5.0] 2024-08-10

### Changed
- Updated to intellij platform `2024.2`

## [0.4.0] 2024-04-05

### Changed
- Updated to intellij platform `2024.1`

### Fixed
- Now waits for indexing on project loading
- No longer give a false-positive if a there are references to an element that should have none
- Accept usages with multiple references in `find usages` tests

## [0.3.0] 2023-12-09

### Added
- New `highlighting` test executor

### Removed
- `DirectoryTestConfig.useNoWriteAction` was removed. Test executors are now responsible themselves to wrap code in write action.

### Fixed
- Can now handle inlay hints produced by `com.intellij.codeInsight.hints.declarative.InlayHintsProvider`

### Changed
- Updated to intellij platform `2023.3`

## [0.2.2] 2023-08-25

### Fixed
- File content difference was not shown in the error message, when comparing files to the expected file after a mutating operation
- Error is shown when no reference was found, even when the caret shouldn't resolve to anything
- Improved error message for unexpected caret position in `after`-projects

## [0.2.1] 2023-07-30

### Fixed
- Position of erroneously found references were wrong
- `Find Usage`, `Resolve`, and  `Documentation` did not consider injected language

## [0.2.0] 2023-07-29

### Changed
- Updated to intellij platform `2023.2`


## [0.1.0] 2023-07-24

### Added
- base framework with support for intellij `2023.1`
- `action` executor
- `completion` executor
- `executed completion` executor
- `rename` executor
- `parser` executor
- `documentation` executor
- `find usages` executor
- `resolve` executor
- `hints` executor
- `inline` executor
