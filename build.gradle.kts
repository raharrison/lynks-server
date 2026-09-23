val ktorVersion = "3.6.0"

val postgresVersion = "42.7.13"
val exposedVersion = "1.5.0"
val hikariVersion = "7.1.0"
val flywayVersion = "13.7.0"

val flexmarkVersion = "0.64.8"
val bcryptVersion = "0.10.2"
val totpVersion = "2.4.1"
val logbackVersion = "1.6.3"
val konfVersion = "0.0.8"
val commonslangVersion = "3.14.0"
val skeduleVersion = "0.4.0"

val testcontainersVersion = "1.21.4"
val kotlinxCoroutinesTestVersion = "1.11.0"
val restAssuredVersion = "6.0.1"
val junitVersion = "6.1.3"
val assertjVersion = "3.27.7"
val mockkVersion = "1.14.11"
val wiremockVersion = "3.13.2"

plugins {
    application
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "9.3.1"
}

version = "2.1.0"

sourceSets {
    create("testIntegration") {
        java.srcDir("src/testIntegration/java")
        kotlin.srcDir("src/testIntegration/kotlin")
        resources.srcDir("src/testIntegration/resources")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

configurations {
    named("testIntegrationImplementation") {
        extendsFrom(configurations["testImplementation"])
    }
    named("testIntegrationRuntimeOnly") {
        extendsFrom(configurations["testRuntimeOnly"])
    }
}

tasks.register<Test>("testIntegration") {
    testClassesDirs = sourceSets["testIntegration"].output.classesDirs
    classpath = sourceSets["testIntegration"].runtimeClasspath
    mustRunAfter(tasks.test)
    useJUnitPlatform()
    systemProperty("CONFIG_MODE", "TEST")
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    val appVersion = version.toString()
    filesMatching("version.properties") {
        expand("version" to appVersion)
    }
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    systemProperty("CONFIG_MODE", "TEST")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

application {
    mainClass.set("lynks.MainKt")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
    implementation("io.ktor:ktor-server-partial-content:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:$flexmarkVersion")

    implementation("com.github.shyiko.skedule:skedule:$skeduleVersion")
    implementation("at.favre.lib:bcrypt:$bcryptVersion")
    implementation("dev.turingcomplete:kotlin-onetimepassword:$totpVersion")

    implementation("org.apache.commons:commons-lang3:$commonslangVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.voltstorage:konf-core:$konfVersion")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesTestVersion")
    testImplementation("io.rest-assured:rest-assured:$restAssuredVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.wiremock:wiremock:$wiremockVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
}
