import org.gradle.api.tasks.wrapper.Wrapper.DistributionType

val ossrhUsername: String? by project
val ossrhPassword: String? by project

plugins {
    kotlin("jvm") version "2.1.20" // See https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library for the correct version
    `java-library`
    `maven-publish`
    signing
}

group = "me.ffl"
version = "2025.2-1.0"

repositories {
    mavenCentral()
//    maven("https://www.jetbrains.com/intellij-repository/snapshots")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

// Don't include coroutines, see https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#coroutinesLibraries
fun ExternalModuleDependency.excludeCoroutines() {
    exclude("org.jetbrains.kotlinx","kotlinx-coroutines-core")
    exclude("org.jetbrains.kotlinx","kotlinx-coroutines-core-jvm")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
    // Coroutines-test seems to be not bundled in intellij, so we allow it as dependency.
//    exclude("org.jetbrains.kotlinx","kotlinx-coroutines-test")
    exclude("org.jetbrains.kotlinx","kotlinx-coroutines-debug")
    exclude("org.jetbrains.kotlinx","kotlinx-coroutines-jdk8")
}

dependencies {
    val kotestVersion = "6.0.0.M17"
    api("io.kotest:kotest-runner-junit5:$kotestVersion") {
        excludeCoroutines()
    }
    api("io.kotest:kotest-assertions-core:$kotestVersion") {
        excludeCoroutines()
    }
    // Not used in this package, but included, so users of property tests
    // don't need the same setup with excludeCoroutines to use full kotest.
    api("io.kotest:kotest-property:$kotestVersion") {
        excludeCoroutines()
    }

    // See https://plugins.jetbrains.com/docs/intellij/intellij-artifacts.html
    val ideaVersion = "252.23892.515"

    // To find out which artifacts are needed, see download-intellij.md
    compileOnly("com.jetbrains.intellij.platform:test-framework:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:refactoring:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:analysis:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:analysis-impl:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:lang-impl:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:util-jdom:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:util:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:core:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:editor-ui:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:project-model:$ideaVersion")
    compileOnly("com.jetbrains.intellij.platform:core-ui:$ideaVersion")
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks {
    javadoc {
        if (JavaVersion.current().isJava9Compatible) {
            (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
        }
    }

    wrapper {
        gradleVersion = "8.14.3"
        distributionType = DistributionType.ALL
    }
}

val projectVersion = version as String

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.flash-freezing-lava"
            artifactId = "intellij-directory-tests"
            version = projectVersion

            from(components["java"])

            pom {
                name.set("Intellij Directory Tests")
                description.set("A test framework, that runs tests specified by directories with markup files")
                url.set("https://github.com/flash-freezing-lava/intellij-directory-tests")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                    license {
                        name.set("The MIT License")
                        url.set("https://www.opensource.org/licenses/mit-license.php")
                    }
                }
                developers {
                    developer {
                        id.set("freezinglava")
                        name.set("Lars Frost")
                        email.set("freezinglava@proton.me")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/flash-freezing-lava/intellij-directory-tests.git")
                    developerConnection.set("scm:git:ssh://github.com:flash-freezing-lava/intellij-directory-tests.git")
                    url.set("https://github.com/flash-freezing-lava/intellij-directory-tests")
                }
            }
        }
    }

    repositories {
        maven {
            name = "SonatypeMaven"

            url = uri(if (version.toString().endsWith("-SNAPSHOT")) "https://s01.oss.sonatype.org/content/repositories/snapshots/" else "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}

kotlin {
    jvmToolchain(17)
}