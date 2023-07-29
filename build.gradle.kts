import org.gradle.api.tasks.wrapper.Wrapper.DistributionType

plugins {
    kotlin("jvm") version "1.8.20"
    `java-library`
    `maven-publish`
}

group = "me.ffl"
version = "0.2.0"

repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    val kotestVersion = "5.6.0"
    api("io.kotest:kotest-runner-junit5:$kotestVersion")
    api("io.kotest:kotest-assertions-core:$kotestVersion")
    compileOnlyApi("com.jetbrains.intellij.platform:test-framework:232.8660.185")
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
        }
    }
}

kotlin {
    jvmToolchain(17)
}