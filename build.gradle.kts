val ktorVersion = "3.4.0"

val h2Version = "2.4.240"
val postgresVersion = "42.7.9"
val exposedVersion = "1.1.1"
val hikariVersion = "7.0.2"
val flywayVersion = "11.19.0"

val flexmarkVersion = "0.64.8"
val handlebarsVersion = "4.5.0"
val commonsEmailVersion = "1.6.0"
val bcryptVersion = "0.10.2"
val totpVersion = "2.4.1"
val logbackVersion = "1.5.25"
val konfVersion = "0.0.8"
val commonslangVersion = "3.14.0"
val skeduleVersion = "0.4.0"

val kotlinxCoroutinesTestVersion = "1.10.2"
val restAssuredVersion = "6.0.0"
val junitVersion = "5.10.1"
val assertjVersion = "3.27.3"
val mockkVersion = "1.14.9"
val wiremockVersion = "3.13.2"

plugins {
    application
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

version = "2.0.0"

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
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val createProperties = tasks.register("createProperties") {
    doLast {
        val file = File("${layout.buildDirectory.get()}/resources/main/version.txt")
        file.parentFile.mkdirs()
        file.writeText(project.version.toString())
    }
}

tasks.named("classes") {
    dependsOn(createProperties)
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

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
    implementation("io.ktor:ktor-server-compression:$ktorVersion")
    implementation("io.ktor:ktor-server-partial-content:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("com.h2database:h2:$h2Version")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:$flexmarkVersion")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:$flexmarkVersion")

    implementation("com.github.shyiko.skedule:skedule:$skeduleVersion")
    implementation("com.github.jknack:handlebars:$handlebarsVersion")
    implementation("org.apache.commons:commons-email:$commonsEmailVersion")
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
}
