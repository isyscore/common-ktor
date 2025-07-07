/**
 * to relaase a new library, just do this:
 * gradle clean publish jreleaserDeploy
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val ktorVersion: String by project
val signingPassword: String by project
val centralUsername: String by project
val centralPassword: String by project

plugins {
    java
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
    `maven-publish`
    signing
    id("org.jreleaser") version "1.18.0"
}

group = "com.github.isyscore"
version = "3.0.0.4"

repositories {
    mavenCentral()
}

dependencies {
    api("com.github.isyscore:common-jvm:3.0.0.0")
    api("io.ktor:ktor-server-core-jvm:$ktorVersion")
    api("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    api("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    api("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    api("io.ktor:ktor-serialization-gson-jvm:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")
    api("io.ktor:ktor-server-resources:$ktorVersion")
    api("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    api("io.ktor:ktor-server-config-yaml:$ktorVersion")
    api("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    api("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    api("io.ktor:ktor-server-http-redirect-jvm:$ktorVersion")
    api("io.ktor:ktor-server-partial-content-jvm:$ktorVersion")
    api("io.ktor:ktor-server-host-common-jvm:$ktorVersion")
    api("io.ktor:ktor-server-compression-jvm:$ktorVersion")
    api("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    api("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    api("ch.qos.logback:logback-classic:1.5.18")
    testImplementation("junit:junit:4.13.2")
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin"){
            from(components["java"])
            pom {
                name.set("common-ktor")
                description.set("iSysCore Common Kotlin/Ktor Library")
                url.set("https://github.com/isyscore/common-ktor")
                packaging = "jar"

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/isyscore/common-ktor/blob/main/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("isyscore")
                        name.set("isyscore")
                        email.set("hexj@isyscore.com")
                    }
                }
                scm {
                    connection.set("https://github.com/isyscore/common-ktor")
                    developerConnection.set("https://github.com/isyscore/common-ktor")
                    url.set("https://github.com/isyscore/common-ktor")
                }
            }
        }
    }
    repositories {
        maven {
            name = "LocalMavenWithChecksums"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
        maven {
            name = "PreDeploy"
            url = uri(layout.buildDirectory.dir("pre-deploy"))
        }
    }
}

tasks.withType<Jar> {
    doLast {
        ant.withGroovyBuilder {
            "checksum"("algorithm" to "md5", "file" to archiveFile.get())
            "checksum"("algorithm" to "sha1", "file" to archiveFile.get())
        }
    }
}

jreleaser {
    project {
        copyright.set("isyscore.com")
        description.set("iSysCore Common Kotlin/Ktor Library")
    }
    signing {
        setActive("ALWAYS")
        armored = true
        setMode("FILE")
        publicKey = "public.key"
        secretKey = "private.key"
        passphrase = signingPassword
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    setActive("ALWAYS")
                    url = "https://central.sonatype.com/api/v1/publisher"
                    username = centralUsername
                    password = centralPassword
                    stagingRepository("build/pre-deploy")
                }
            }
        }
    }
}
