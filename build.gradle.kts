import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val slf4jVersion = "1.7.25"
val ktorVersion = "1.2.4"
val jacksonVersion = "2.9.8"
val kafkaVersion = "2.3.0"
val arrowVersion = "0.9.0"
val prometheusVersion = "0.6.0"
val cxfVersion = "3.3.1"
val orgJsonVersion = "20180813"


val micrometerRegistryPrometheusVersion = "1.1.5"
val fuelVersion = "1.15.1"
val wireMockVersion = "2.19.0"
val mockkVersion = "1.9.3.kotlin12"
val junitJupiterVersion = "5.4.0"
val mainClass = "no.nav.helse.sparkel.personopplysninger.AppKt"

val tjenestespesifikasjonerVersion = "1.2019.01.16-21.19-afc54bed6f85"


plugins {
    kotlin("jvm") version "1.3.50"
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

fun tjenestespesifikasjon(name: String) = "no.nav.tjenestespesifikasjoner:$name:$tjenestespesifikasjonerVersion"



dependencies {
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.logstash.logback:logstash-logback-encoder:5.2")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

    implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")

    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")

    implementation("io.arrow-kt:arrow-core-data:$arrowVersion")
    compile("org.json:json:$orgJsonVersion")

    compile("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-ws-policy:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    compile("javax.activation:activation:1.1.1")
    compile(tjenestespesifikasjon("arbeidsforholdv3-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-fim-inntekt-v3-tjenestespesifikasjon"))

    compile("no.nav.helse:cxf-prometheus-metrics:dd7d125")

    compile("com.sun.xml.ws:jaxws-rt:2.3.2")


    testCompile("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/ktor")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.named<Jar>("jar") {
    baseName = "app"

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }

    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    kotlinOptions.jvmTarget = "11"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.6.2"
}
