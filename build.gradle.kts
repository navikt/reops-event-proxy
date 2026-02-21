plugins {
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "0.10.6"
	kotlin("jvm") version "2.3.10"
	kotlin("plugin.spring") version "2.3.10"
}

group = "no.nav.reops"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(
			providers.gradleProperty("javaVersion").getOrElse("21")
		)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")

	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("io.micrometer:micrometer-registry-prometheus")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")

	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}


kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<Jar>("bootJar") {
	archiveFileName.set("app.jar")
}

graalvmNative {
	binaries {
		named("main") {
			imageName.set("app")
			mainClass.set("no.nav.reops.EventProxyApplicationKt")
		}
	}
	binaries.all {
		buildArgs.addAll(
			"-H:+ReportExceptionStackTraces",
			"-J-Xmx6g",

			// Reduce image size: exclude AWT (not needed for a REST/Kafka proxy)
			"--exclude-config", ".*/java\\.desktop/.*",

			// Strip debug symbols from the binary
			"-H:-IncludeMethodData",

			// Optimize for size over peak throughput
			"-Os",
		)
	}
}

