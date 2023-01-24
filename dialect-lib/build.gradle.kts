plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.22"
    `java-library`
}

group = "blynx.thymeleaf"

dependencies {
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.thymeleaf:thymeleaf:3.1.1.RELEASE")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.1")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
