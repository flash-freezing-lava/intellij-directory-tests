# Directory-based Test Framework for Intellij Plugins

## Give me code

Add the following to your `build.gradle.kts` and replace dirTestVersion with the current version `TODO add badge` and `org/your/test/packageName` with the path to your top-level package:
```kotlin
dependencies {
    testImplementation("me.ffl", "intellijDirectoryTests", dirTestVersion)
}

val unitTestTask = task<Test>("unitTest") {
    isScanForTestClasses = false
    include("org/your/test/packageName/*Test.class")
    useJUnitPlatform()
}
```

Create the following class in `src/main/test/org/your/test/packageName`:
```kotlin
// You can change the executor's behavior by passing something other
// than [DirectoryTestConfig.default] to the [DirectoryTests] constructor.
@Suppress("unused")
class MyPluginTest: DirectoryTests()
```