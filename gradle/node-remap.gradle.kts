import java.util.zip.ZipFile

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.19.4"
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mc").get()}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api").get()}")
}

apply(from = rootDir.resolve("../../gradle/common.gradle.kts"))

val smokeJar = tasks.register<net.fabricmc.loom.task.RemapJarTask>("smokeJar") {
    val dev = tasks.named<Jar>("smokeDevJar")
    dependsOn(dev)
    inputFile = dev.flatMap { it.archiveFile }
    archiveBaseName = "dripleaf-finder-smoke"
    destinationDirectory = layout.buildDirectory.dir("smoke")
}

val fabricApiAll = configurations.detachedConfiguration(
    dependencies.create("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api").get()}"))
val fabricApiJar = files(provider {
    val artifacts = fabricApiAll.resolvedConfiguration.resolvedArtifacts.filter { it.moduleVersion.id.group == "net.fabricmc.fabric-api" }
    val main = artifacts.first { it.name == "fabric-api" }.file
    val bundled = ZipFile(main).use { zip -> zip.entries().asSequence().any { it.name.startsWith("META-INF/jars/") } }
    if (bundled) listOf(main) else artifacts.map { it.file }
})
tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("smokeClient") {
    val modJar = providers.gradleProperty("smoke_mod").orNull
    mods.setFrom(if (modJar != null) files(modJar) else tasks.named("remapJar"), tasks.named("smokeJar"), fabricApiJar)
    providers.gradleProperty("smoke_extra").orNull?.let { mods.from(files(it.split(":"))) }
    jvmArgs.addAll(providers.gradleProperty("smoke_jvm").map { it.split(" ").filter { a -> a.isNotEmpty() } }.orElse(listOf()))
    programArgs.addAll(providers.gradleProperty("smoke_args").map { it.split(" ").filter { a -> a.isNotEmpty() } }.orElse(listOf()))
    runDir = layout.projectDirectory.dir("smoke-run")
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(maxOf(17, providers.gradleProperty("java").get().toInt())) }
}
