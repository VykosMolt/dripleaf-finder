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
}

apply(from = rootDir.resolve("../../gradle/common.gradle.kts"))
