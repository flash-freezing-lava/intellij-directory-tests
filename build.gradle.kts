import org.gradle.api.tasks.wrapper.Wrapper.DistributionType

plugins {
    kotlin("jvm") version "1.8.10"
    `java-library`
    `maven-publish`
}

group = "me.ffl"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    val kotestVersion = "5.5.0"
    api("io.kotest:kotest-runner-junit5:$kotestVersion")
    api("io.kotest:kotest-assertions-core:$kotestVersion")
    compileOnlyApi("com.jetbrains.intellij.platform:test-framework:223.8617.56")
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.flash-freezing-lava"
            artifactId = "intellij-directory-tests"
            version = "0.1.0"

            from(components["java"])
        }
    }
}

kotlin {
    jvmToolchain(17)
}