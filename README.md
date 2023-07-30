# Directory-based Test Framework for Intellij Plugins

## Version compatibility


| Intellij | Directory tests |
|----------|-----------------|
| 2023.1   | 0.1.*           |
| 2023.2   | 0.2.*           |



## Code Setup

For now, you will have to build the plugin locally, because it is not published on any maven repo.
First initialize the gradle wrapper with `gradle wrapper`.
Then use `./gradlew publishToLocalMaven` to install intellij-directory-tests.
Afterwards, add the following lines to your `build.gradle.kts` file:
```kotlin
repositories {
    mavenLocal()
}
```
You can then follow the below instructions.

## Including directory tests in your project

Add the following to your `build.gradle.kts` and replace `dirTestVersion` with the current version:
```kotlin
dependencies {
    testImplementation("io.github.flash-freezing-lava", "intellij-directory-tests", dirTestVersion)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
```

Create the following class in `src/main/test/org/your/packageName`:
```kotlin
// You can change the executor's behavior by passing something other
// than [DirectoryTestConfig.default] to the [DirectoryTests] constructor.
@Suppress("unused")
class MyPluginTest: DirectoryTests()
```
You can then execute tests by calling `./gradlew dirTest`.

## Creating Test Data

Tests are placed in `src/test/testData` by default.
For every tests executor (`parser`, `hints`, `resolve`, etc.), you create a subdirectory in `src/test/testData`.

In the executors directory, every subdirectory is a test case.
The structure of a test case is specified by its executor ([see below for their definitions](#executors)).

Often, you have many tests with the same executor and want to group them. For this, you can create a file `context.txt` in the executor's directory. Now all subdirectories are interpreted as groups and their subdirectories as tests. Similar, you can create subgroups, by placing a `context.txt` in a group.

## Executors

### Parser
A parser test must have a file, that should be parsed.
This file must have the test name plus an extension as name.
An additional file named `${TESTNAME}.txt` must contain the expected psi tree.

### Resolve
A resolve test case must contain a project, where `<caret>` marks the caret position and `<ref>` marks all expected references.

Example:  
The following file shows, how to test the class resolution in a kotlin file:
```kotlin
data class <ref>Abc(val f: Int)

typealias Foo = <caret>Abc
```

If references are ambiguous and should resolve to multiple symbols, just mark multiple symbols with `ref`.

If a reference should resolve to an external symbol,
you can create a file named `external_references.txt` in the test directory, with the fully-qualified names of the symbols.
You then have to adapt the `externalReferenceToString` function in your `DirectoryTestConfig` to yield the fully-qualified names, for the expected psi elements.

Example:  
If you test only kotlin code, you can use the following lambda for `DirectoryTestConfig::externalReferenceToString`
```kotlin
{ (it as? KtElement)?.fqName?.toString() }
```

## Multiple carets
Instead of writing `<caret>` and `<ref>` for resolve tests,
you can also give names to the caret.

```rust
#[cfg_attr(feature = "serde-kebab", serde(rename_all = "kebab-case"))]
#[cfg_attr(feature = "serde-upper", serde(rename_all = "UPPERCASE"))]
enum MyEnum {
  <ref var1><ref var2>MyVariant,
}

// language=RON
const STR1: &str = "<caret var1>my-variant";
// language=RON
const STR2: &str = "<caret var2>MYVARIANT";
```

For separation of concerns, it is generally discouraged to combine tests in this way.
However, it can be useful, to make certain, a testcase is not accidentally passed, because of the internals of a pseudo-randomized data-structure like the iteration order of a hashmap.

## License

Copyright 2023 Lars Frost <freezinglava@proton.me>

This project is licensed under the Apache License, Version 2.0 <[LICENSE-APACHE](LICENSE-APACHE)> or the MIT license
<[LICENSE-MIT](LICENSE-MIT)>, at your
option. Files in the project may not be
copied, modified, or distributed except according to those terms.