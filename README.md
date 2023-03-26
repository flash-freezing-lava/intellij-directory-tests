# Directory-based Test Framework for Intellij Plugins

## Give me code

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

Add the following to your `build.gradle.kts` and replace dirTestVersion with the current version `TODO add badge` and `org/your/test/packageName` with the path to your top-level package:
```kotlin
dependencies {
    testImplementation("io.github.flash-freezing-lava", "intellij-directory-tests", dirTestVersion)
}

val unitTestTask = task<Test>("dirTest") {
    isScanForTestClasses = false
    include("org/your/packageName/*Test.class")
    useJUnitPlatform()
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
