val repo = rootDir.resolve("../..").canonicalFile
fun prop(name: String) = providers.gradleProperty(name).get()
fun flag(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val eras = prop("eras").split(",").map { it.trim() }.filter { it.isNotEmpty() }
val sourceSets = the<SourceSetContainer>()
val main = sourceSets["main"]

version = prop("mod_version") + "+mc" + prop("range_name")
the<BasePluginExtension>().archivesName = "dripleaf-finder"

main.java.setSrcDirs(listOf(repo.resolve("src/core")) + eras.map { repo.resolve("src/era/$it/main") })
main.resources.setSrcDirs(listOf(repo.resolve("src/resources")) + eras.map { repo.resolve("src/era/$it/resources") })
val stubs = sourceSets.create("stubs") {
    java.setSrcDirs(listOf(repo.resolve("src/stubs")))
    resources.setSrcDirs(listOf<File>())
}
main.compileClasspath += stubs.output
sourceSets.create("check") {
    java.setSrcDirs(listOf(repo.resolve("src/check")) + eras.map { repo.resolve("src/era/$it/check") })
    resources.setSrcDirs(listOf<File>())
    compileClasspath += main.output + main.compileClasspath
    runtimeClasspath += output + compileClasspath + main.runtimeClasspath
}

tasks.withType<JavaCompile>().configureEach {
    options.release = prop("java").toInt()
    options.encoding = "UTF-8"
}

fun metadata(autismVariant: Boolean) = mapOf(
    "version" to version.toString(),
    "mc_range" to prop("mc_range"),
    "java" to prop("java"),
    "autism_entry" to (if (autismVariant) ", \"autism\": [\"dripleaffinder.autism.AutismFront\"]" else ""),
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(metadata(false))
    filesMatching("fabric.mod.json") { expand(metadata(false)) }
}

val smokeSet = sourceSets.create("smoke") {
    java.setSrcDirs(listOf(repo.resolve("src/smoke"), repo.resolve("src/stubs")) + eras.map { repo.resolve("src/era/$it/smoke") })
    resources.setSrcDirs(listOf(repo.resolve("src/smoke-resources")))
    compileClasspath += main.output + main.compileClasspath
}
tasks.register<Jar>("smokeDevJar") {
    archiveBaseName = "dripleaf-finder-smoke"
    archiveClassifier = "dev"
    destinationDirectory = layout.buildDirectory.dir("devlibs")
    from(smokeSet.output)
}

val selfCheck = tasks.register<JavaExec>("selfCheck") {
    classpath = sourceSets["check"].runtimeClasspath
    mainClass = "dripleaffinder.SelfCheck"
    workingDir = layout.buildDirectory.dir("selfcheck").get().asFile.also { it.mkdirs() }
}
tasks.named("build") { dependsOn(selfCheck) }

if (flag("autism")) {
    val autismSet = sourceSets.create("autism") {
        java.setSrcDirs(listOf(repo.resolve("src/autism")))
        resources.setSrcDirs(listOf<File>())
        compileClasspath += main.output + main.compileClasspath +
            files(prop("autism_jar").replace("~", System.getProperty("user.home")))
    }
    val autismMetadata = tasks.register<Sync>("autismMetadata") {
        from(repo.resolve("src/resources/fabric.mod.json"))
        into(layout.buildDirectory.dir("autism-metadata"))
        inputs.properties(metadata(true))
        expand(metadata(true))
    }
    val autismJar = tasks.register<Jar>("autismJar") {
        archiveBaseName = "dripleaf-finder-autism"
        from(main.output.classesDirs, autismSet.output.classesDirs, autismMetadata)
        from(tasks.named<ProcessResources>("processResources")) { exclude("fabric.mod.json") }
    }
    tasks.named("build") { dependsOn(autismJar) }
}

tasks.register("checkClasspath") {
    val path = sourceSets["check"].runtimeClasspath
    doLast { println(path.asPath) }
}
