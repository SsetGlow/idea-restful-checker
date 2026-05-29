import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.io.File

fun parsePluginVersion(text: String, propertyName: String): String = text
    .lineSequence()
    .firstNotNullOfOrNull { line ->
        line.substringBefore('#')
            .trim()
            .takeIf { it.startsWith("$propertyName=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }
    ?: "0.1.0"

fun nextPatchVersion(version: String, propertyName: String): String {
    val parts = version.split('.')
    require(parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
        "$propertyName must use semantic MAJOR.MINOR.PATCH format, but was '$version'."
    }
    return "${parts[0]}.${parts[1]}.${parts[2].toInt() + 1}"
}

fun writePluginVersion(file: File, propertyName: String, version: String) {
    val lines = file.readLines().toMutableList()
    val versionLineIndex = lines.indexOfFirst { it.substringBefore('#').trim().startsWith("$propertyName=") }
    if (versionLineIndex >= 0) {
        lines[versionLineIndex] = "$propertyName=$version"
    } else {
        lines.add("$propertyName=$version")
    }
    file.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
}

plugins {
    java
    id("org.jetbrains.intellij.platform")
}

val pluginVersionProperty = "pluginVersion"
val gradlePropertiesFile = layout.projectDirectory.file("gradle.properties")

fun isRequestedTask(taskName: String) = gradle.startParameter.taskNames.any { requested ->
    requested == taskName || requested.endsWith(":$taskName")
}

fun readPluginVersion(): String = parsePluginVersion(gradlePropertiesFile.asFile.readText(), pluginVersionProperty)

val pluginVersionProvider = providers.fileContents(gradlePropertiesFile).asText.map {
    parsePluginVersion(it, pluginVersionProperty)
}
val autoBumpPluginVersion = tasks.register("autoBumpPluginVersion") {
    group = "versioning"
    description = "Increments the plugin patch version before creating or publishing a plugin distribution."
    outputs.upToDateWhen { false }
    doLast {
        val currentVersion = readPluginVersion()
        val nextVersion = nextPatchVersion(currentVersion, pluginVersionProperty)
        writePluginVersion(gradlePropertiesFile.asFile, pluginVersionProperty, nextVersion)
        logger.lifecycle("Plugin version bumped: $currentVersion -> $nextVersion")
    }
}

group = "com.ssetglow"
version = readPluginVersion()

dependencies {
    intellijPlatform {
        intellijIdea("2025.3.3")
        bundledPlugin("com.intellij.java")
    }

    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    projectName = "restful-checker"

    pluginConfiguration {
        id = "com.ssetglow.restful-checker"
        name = "restful-checker"
        version = pluginVersionProvider
        description = """
            <p>Search and call Spring-style REST endpoints directly from IntelliJ IDEA.</p>
        """.trimIndent()

        vendor {
            name = "SsetGlow"
            email = "ssetglow@outlook.com"
        }

        ideaVersion {
            sinceBuild = "253"
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

if (isRequestedTask("buildPlugin") || isRequestedTask("publishPlugin")) {
    tasks.named("patchPluginXml") {
        dependsOn(autoBumpPluginVersion)
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        dependsOn(autoBumpPluginVersion)
        archiveVersion.set(pluginVersionProvider)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnit()
}
