import org.gradle.api.tasks.wrapper.Wrapper.DistributionType

val ossrhUsername: String? by project
val ossrhPassword: String? by project

plugins {
    kotlin("jvm") version "1.9.10" // See https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library for the correct version
    `java-library`
    `maven-publish`
    signing
}

group = "me.ffl"
version = "0.3.0"

repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    val kotestVersion = "5.6.0"
    api("io.kotest:kotest-runner-junit5:$kotestVersion")
    api("io.kotest:kotest-assertions-core:$kotestVersion")
    compileOnlyApi("com.jetbrains.intellij.platform:test-framework:233.11799.259")
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
        gradleVersion = "8.0.2"
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
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                    license {
                        name.set("The MIT License")
                        url.set("http://www.opensource.org/licenses/mit-license.php")
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