plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    `java-library`
    `maven-publish`
}

group = "blynx.thymeleaf"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.thymeleaf:thymeleaf:3.1.5.RELEASE")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.14.4")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
