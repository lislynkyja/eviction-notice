// Runs once for each Minecraft target in settings.gradle.kts. The values for each target are
// in stonecutter.properties.toml.
plugins {
    id("dev.kikugie.loom-back-compat")
    id("me.modmuss50.mod-publish-plugin")
}

fun prop(key: String): String = property(key).toString()

// For example eviction-notice-1.2.0+26.1-26.3.jar. Everything after the "+" is SemVer build
// metadata.
version = "${prop("mod.version")}+${prop("mod.mc_label")}"
group = prop("mod.group")
base.archivesName = prop("mod.archives_name")

val javaVersion = prop("mod.java").toInt()

loom {
    // No client source set, the mod only runs on the server.
    mods {
        register(prop("mod.id")) {
            sourceSet(sourceSets["main"])
        }
    }

    runConfigs.all {
        // One run folder for all targets.
        runDirectory = rootProject.file("run")
    }
}

// Server GameTests in src/gametest. `./gradlew build` runs them too, through `check`.
// They have their own source set, so nothing of them ends up in the mod jar.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "eviction_notice_test"
        enableGameTests = true
        enableClientGameTests = false
    }
}
// runConfigs.all above would move the test server into the shared run/ folder.
val gameTestRunDir = layout.buildDirectory.dir("run/gameTest")
loom.runConfigs.named("gameTest") {
    runDirectory = gameTestRunDir.get().asFile
    // JUnit report of the results, uploaded by CI when a build fails.
    systemProperties.put("fabric-api.gametest.report-file", layout.buildDirectory.file("test-results/gameTest/TEST-gametest.xml").get().asFile.absolutePath)
}
// Loom doesn't clear the folder for server tests, so zones from earlier runs would pile up.
val deleteGameTestRunDir = tasks.register<Delete>("deleteGameTestRunDir") {
    delete(gameTestRunDir)
}
tasks.named("runGameTest") { dependsOn(deleteGameTestRunDir) }

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojang mappings for the obfuscated 1.21.x versions. Does nothing on 26.x.
    loomx.applyMojangMappings()

    // loom-back-compat turns modImplementation into implementation on 26.x.
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = javaVersion
    }

    processResources {
        val props = mapOf(
            "version" to project.version.toString(),
            "minecraft" to prop("mod.mc_compat"),
            "loader" to prop("mod.loader_min"),
            "java" to javaVersion.toString(),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    withType<Jar>().configureEach {
        val projectName = rootProject.name
        inputs.property("projectName", projectName)
        from(rootProject.file("LICENSE")) {
            rename { "${it}_$projectName" }
        }
    }

    // The workflows upload every jar in build/libs/. The sources jar stays in
    // versions/<target>/build/libs/ and isn't published.
    val collectJars = register<Copy>("collectJars") {
        group = "build"
        description = "Copies the mod jar to the root build/libs/ directory."
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
    named("build") { finalizedBy(collectJars) }
}

// The "## <mod.version>" section of CHANGELOG.md, without the heading.
val changelogSection = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText.map { text ->
    val heading = "## ${prop("mod.version")}"
    val lines = text.lines()
    val start = lines.indexOfFirst { it.trimEnd() == heading }
    check(start >= 0) { "CHANGELOG.md has no '$heading' section" }
    val end = lines.drop(start + 1).indexOfFirst { it.startsWith("## ") }
    lines.subList(start + 1, if (end < 0) lines.size else start + 1 + end).joinToString("\n").trim()
}

// One Modrinth version per jar. Without MODRINTH_TOKEN, `./gradlew publishMods` only prints
// what it would upload.
publishMods {
    file = loomx.modJar.flatMap { it.archiveFile }
    version = project.version.toString()
    displayName = "Eviction Notice v${prop("mod.version")} (${prop("mod.mc_label")})"
    changelog = changelogSection
    type = STABLE
    modLoaders.add("fabric")
    dryRun = providers.environmentVariable("MODRINTH_TOKEN").map { it.isBlank() }.orElse(true)

    modrinth {
        projectId = "3rtYrvAU" // eviction-notice
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(sc.properties.raw("mod", "mc_releases").asList().map { it.toString() })
        // Shown on Modrinth as server-side. fabric.mod.json keeps "*" so the mod also loads in
        // singleplayer, where the server runs inside the client.
        environment = SERVER_ONLY
        requires("fabric-api")
    }
}
