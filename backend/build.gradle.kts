plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "com.commuteplus"
version = "0.1.0"

application {
    mainClass.set("com.commuteplus.ApplicationKt")
}

repositories {
    mavenCentral()
    maven("https://repo.entur.org/repository/maven-public/") // OTP releases
}

val ktorVersion = "2.3.7"
val otp2Version = "2.5.0"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // OpenTripPlanner 2 (routing engine)
    implementation("org.opentripplanner:otp:$otp2Version")

    // GraphHopper (road distance for auto/cab fare calculation)
    implementation("com.graphhopper:graphhopper-core:8.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
