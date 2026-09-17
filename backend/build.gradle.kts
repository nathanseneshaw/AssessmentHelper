plugins {
	java
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
	alias(libs.plugins.spotless)
}

group = "edu.utdallas.cs4485"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.bundles.spring.boot.starters)
	implementation(libs.springdoc.openapi.starter.webmvc.ui)
	developmentOnly(libs.spring.boot.devtools)
	runtimeOnly(libs.h2)
	annotationProcessor(libs.spring.boot.configuration.processor)
	testImplementation(libs.bundles.spring.boot.test.starters)
	testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
	useJUnitPlatform()
}

spotless {
	java {
		target("src/**/*.java")
		palantirJavaFormat(libs.versions.palantir.java.format.get())
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
	kotlinGradle {
		target("*.gradle.kts")
		trimTrailingWhitespace()
		endWithNewline()
	}
	format("misc") {
		target("src/**/*.properties", "*.properties", "gradle/*.toml", ".gitattributes", ".gitignore")
		trimTrailingWhitespace()
		endWithNewline()
	}
}
