plugins {
    id("net.fabricmc.fabric-loom") version "1.19.4"
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("mc").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api").get()}")
}

apply(from = rootDir.resolve("../../gradle/common.gradle.kts"))

val fabricApiJar = configurations.detachedConfiguration(
    dependencies.create("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api").get()}")).apply { isTransitive = false }
tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("smokeClient") {
    val modJar = providers.gradleProperty("smoke_mod").orNull
    mods.setFrom(if (modJar != null) files(modJar) else tasks.named("jar"), tasks.named("smokeDevJar"), fabricApiJar)
    providers.gradleProperty("smoke_extra").orNull?.let { mods.from(files(it.split(":"))) }
    jvmArgs.addAll(providers.gradleProperty("smoke_jvm").map { it.split(" ").filter { a -> a.isNotEmpty() } }.orElse(listOf()))
    programArgs.addAll(providers.gradleProperty("smoke_args").map { it.split(" ").filter { a -> a.isNotEmpty() } }.orElse(listOf()))
    runDir = layout.projectDirectory.dir("smoke-run")
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(maxOf(17, providers.gradleProperty("java").get().toInt())) }
}
