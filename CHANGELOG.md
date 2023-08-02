# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Fixed
- File content difference was not shown in the error message, when comparing files to the expected file after a mutating operation
- Error is shown when no reference was found, even when the caret shouldn't resolve to anything

## [0.2.1] 2023-07-30

### Fixed
- Position of erroneously found references were wrong

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
