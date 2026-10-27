plugins {
    java
}

group = "com.htk"
version = "3.17.0"

val paperVersion: String by project
val javaVersion: String by project

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().compileClasspath
}

val verifyLandRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.land.LandRulesTest")
}

tasks.check { dependsOn(verifyLandRules) }

val verifyEnhancementRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.UpgradeRulesTest")
}
tasks.check { dependsOn(verifyEnhancementRules) }

// Tests use a dependency-free assertion runner via verifyLandRules, rather than JUnit.
tasks.test { failOnNoDiscoveredTests = false }

// Windows native console encoding may differ from Gradle's UTF-8 output decoder.
tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
