plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.sonarqube)
    jacoco
    `java-library`
    `maven-publish`
}

group = "de.maibornwolff.treesitter.excavationsite"
version = "0.3.0"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    // TreeSitter core
    api(libs.treesitter)

    // Language bindings
    implementation(libs.treesitter.java)
    implementation(libs.treesitter.kotlin)
    implementation(libs.treesitter.typescript)
    implementation(libs.treesitter.javascript)
    implementation(libs.treesitter.python)
    implementation(libs.treesitter.go)
    implementation(libs.treesitter.php)
    implementation(libs.treesitter.ruby)
    implementation(libs.treesitter.swift)
    implementation(libs.treesitter.bash)
    implementation(libs.treesitter.csharp)
    implementation(libs.treesitter.cpp)
    implementation(libs.treesitter.c)
    implementation(libs.treesitter.objc)
    implementation(libs.treesitter.vue)
    implementation(libs.treesitter.abl)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.archunit.junit5)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

detekt {
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("TreeSitter ExcavationSite Library")
                description.set("A library for extracting code metrics and text using TreeSitter")
                url.set("https://github.com/MaibornWolff/treesitter-excavationsite")

                licenses {
                    license {
                        name.set("BSD-3-Clause")
                        url.set("https://opensource.org/licenses/BSD-3-Clause")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MaibornWolff/TreeSitterExcavationSite")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

sonar {
    properties {
        property("sonar.projectKey", project.findProperty("sonarProjectKey") ?: "TreesitterLibrary")
        property("sonar.projectName", "TreesitterLibrary")
        property("sonar.host.url", project.findProperty("sonarHostUrl") ?: "http://localhost:9000")
        property("sonar.token", project.findProperty("sonarToken") ?: "")
        property("sonar.sources", "src/main/kotlin")
        property("sonar.tests", "src/test/kotlin")
        property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
    }
}

tasks.sonar {
    dependsOn(tasks.jacocoTestReport)
}

tasks.register("sonarWithCoverage") {
    description = "Runs tests with coverage and uploads results to SonarQube"
    group = "verification"
    dependsOn(tasks.test, tasks.jacocoTestReport, tasks.sonar)
}
