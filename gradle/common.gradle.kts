val repo = rootDir.resolve("../..").canonicalFile
fun prop(name: String) = providers.gradleProperty(name).get()
fun flag(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val eras = prop("eras").split(",").map { it.trim() }.filter { it.isNotEmpty() }
val searchOnly = flag("search_only")
val sourceSets = the<SourceSetContainer>()
val main = sourceSets["main"]

version = prop("mod_version") + "+mc" + prop("mc")
the<BasePluginExtension>().archivesName = "dripleaf-finder"

main.java.setSrcDirs(listOf(repo.resolve("src/core")) + eras.map { repo.resolve("src/era/$it/main") })
if (searchOnly) {
    main.java.include(listOf("Searcher", "SearchCompat", "SpawnCompat", "Splice", "WorldCompat").map { "dripleaffinder/$it.java" })
    main.resources.setSrcDirs(listOf(repo.resolve("src/search-resources")))
} else {
    main.resources.setSrcDirs(listOf(repo.resolve("src/resources")) + eras.map { repo.resolve("src/era/$it/resources") })
    val stubs = sourceSets.create("stubs") {
        java.setSrcDirs(listOf(repo.resolve("src/stubs")))
        resources.setSrcDirs(listOf<File>())
    }
    main.compileClasspath += stubs.output
}
if (flag("autism")) {
    main.java.srcDir(repo.resolve("src/autism"))
    dependencies.add("compileOnly", files(prop("autism_jar").replace("~", System.getProperty("user.home"))))
}
sourceSets.create("check") {
    java.setSrcDirs(listOf(repo.resolve("src/check")) + eras.map { repo.resolve("src/era/$it/check") })
    if (searchOnly) java.exclude("dripleaffinder/ClientCheck.java")
    resources.setSrcDirs(listOf<File>())
    compileClasspath += main.output + main.compileClasspath
    runtimeClasspath += output + compileClasspath + main.runtimeClasspath
}

tasks.withType<JavaCompile>().configureEach {
    options.release = prop("java").toInt()
    options.encoding = "UTF-8"
}

val metadata = mapOf("version" to version.toString(), "mc" to prop("mc"), "java" to prop("java"))
tasks.named<ProcessResources>("processResources") {
    inputs.properties(metadata)
    filesMatching("fabric.mod.json") { expand(metadata) }
}

if (!searchOnly) {
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
}

val selfCheck = tasks.register<JavaExec>("selfCheck") {
    classpath = sourceSets["check"].runtimeClasspath
    mainClass = if (searchOnly) "dripleaffinder.SelfCheck" else "dripleaffinder.ClientCheck"
    workingDir = layout.buildDirectory.dir("selfcheck").get().asFile.also { it.mkdirs() }
}
tasks.named("build") { dependsOn(selfCheck) }

tasks.register("checkClasspath") {
    val path = sourceSets["check"].runtimeClasspath
    doLast { println(path.asPath) }
}
