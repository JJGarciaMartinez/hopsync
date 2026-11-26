import java.util.Properties

plugins {
    application
}

// version.properties is the single source of truth for name and version (see VERSION_GUIDE.md)
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
val appName: String = versionProps.getProperty("app.name")
val vendor: String = versionProps.getProperty("app.vendor")
val winUpgradeUuid: String = versionProps.getProperty("app.win.upgrade.uuid")

version = versionProps.getProperty("app.version")

// jpackage takes only one to three numbers, and an installer replaces another only with a higher one, so every
// version maps to YY.MAJOR.(FIXES*100 + stage + N) with a = 0, b = 30, rc = 60, release = 90 (see VERSION_GUIDE.md)
val versionPattern = Regex("""(\d+)\.(\d+)\.(\d+)(?:-(a|b|rc)\.(\d+))?""")
fun installerVersion(version: String): String {
    val match = versionPattern.matchEntire(version)
        ?: throw GradleException("Version $version: expected YY.MAJOR.FIXES or YY.MAJOR.FIXES-(a|b|rc).N, e.g. 26.2.0-b.1")
    val (year, major, fixes, stage, n) = match.destructured
    val iteration = n.toIntOrNull() ?: 0
    if (stage.isNotEmpty() && iteration !in 1..29) throw GradleException("Version $version: N goes from 1 to 29")
    val build = fixes.toInt() * 100 + mapOf("a" to 0, "b" to 30, "rc" to 60).getOrDefault(stage, 90) + iteration
    // Windows installers allow at most 255.255.65535; macOS needs the first number above 0
    if (year.toInt() !in 1..255 || major.toInt() > 255 || build > 65535) {
        throw GradleException("Version $version: installer version $year.$major.$build is out of range")
    }
    return "$year.$major.$build"
}
check(listOf("26.2.0-a.1", "26.2.0-b.2", "26.2.0-rc.1", "26.2.0", "26.2.1-b.1").map(::installerVersion) ==
    listOf("26.2.1", "26.2.32", "26.2.61", "26.2.90", "26.2.131"))

dependencies {
    implementation(project(":core"))
    implementation(project(":platform"))
    implementation("com.formdev:flatlaf:3.7.2")
}

application {
    mainClass = "hopsync.Main"
}

tasks.processResources {
    from(rootProject.file("version.properties"))
}

tasks.jar {
    archiveFileName = "$appName.jar"
    manifest {
        attributes("Main-Class" to application.mainClass)
    }
    // Self-contained JAR: java -jar and jpackage only ship this file
    from(configurations.runtimeClasspath.map { files -> files.map { if (it.isDirectory) it else zipTree(it) } })
    // At the root: the dependencies already bring their own META-INF/LICENSE
    from(rootProject.files("LICENSE", "THIRD-PARTY-NOTICES.md"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.withType<JavaCompile>().configureEach {
    // Swing components are never serialized; the lint only asks for boilerplate serialVersionUIDs
    options.compilerArgs.add("-Xlint:-serial")
}

// Packaging. jpackage only builds installers for the OS it runs on: the release workflow runs it on all three.

val packagingDir = rootProject.layout.projectDirectory.dir("packaging")
val jpackageInput = layout.buildDirectory.dir("jpackage/input")
val installerDir = layout.buildDirectory.dir("installer")
val osName = System.getProperty("os.name").lowercase()
val jpackageTool = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    .map { it.metadata.installationPath.file(if (osName.startsWith("windows")) "bin/jpackage.exe" else "bin/jpackage") }

// jpackage bundles everything in --input, so it gets a folder with the JAR and the license files alone
val jpackageInputJar by tasks.registering(Sync::class) {
    from(tasks.jar)
    from(rootProject.files("LICENSE", "THIRD-PARTY-NOTICES.md"))
    into(jpackageInput)
}

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Builds the installer of the current OS (.msi, .dmg or .deb) into desktop/build/installer."
    dependsOn(jpackageInputJar)
    inputs.dir(jpackageInput)
    inputs.dir(packagingDir)
    inputs.file(rootProject.file("version.properties"))
    outputs.dir(installerDir)

    val dest = installerDir.get().asFile
    args("--name", appName, "--vendor", vendor,
        "--description", "Keeps your Minecraft modpacks up to date",
        "--input", jpackageInput.get().asFile.path, "--main-jar", "$appName.jar", "--main-class", "hopsync.Main",
        "--dest", dest.path, "--java-options", "-Xmx1024m", "--java-options", "-Dfile.encoding=UTF-8")
    when {
        osName.startsWith("windows") -> args("--type", "msi", "--icon", packagingDir.file("icon.ico").asFile.path,
            // Keeps upgrades in place: every HopSync installer shares this id
            "--win-upgrade-uuid", winUpgradeUuid,
            "--win-menu", "--win-menu-group", appName, "--win-shortcut", "--win-dir-chooser")
        osName.contains("mac") -> args("--type", "dmg", "--icon", packagingDir.file("icon.icns").asFile.path,
            "--mac-package-name", appName, "--mac-package-identifier", "app.hopsync.client",
            "--java-options", "-Dapple.awt.application.name=$appName")
        else -> args("--type", "deb", "--icon", packagingDir.file("icon.png").asFile.path,
            "--linux-package-name", appName.lowercase(), "--linux-shortcut", "--linux-menu-group", "Game")
    }

    val display = project.version.toString()
    doFirst {
        args("--app-version", installerVersion(display))
        executable = jpackageTool.get().asFile.path
        // jpackage refuses to overwrite an installer left by a previous run
        dest.deleteRecursively()
    }
    // Players download by the version they see, not the installer's number: HopSync-26.2.0-b.1.msi
    doLast {
        val number = installerVersion(display)
        dest.listFiles()?.forEach { it.renameTo(File(dest, it.name.replace(number, display))) }
    }
}

// Installer icons are drawn by LogoIcon at each exact size; regenerate them after changing the mark
tasks.register<JavaExec>("brandIcons") {
    group = "distribution"
    description = "Writes packaging/icon.png, icon.ico and icon.icns from the HopSync mark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hopsync.desktop.kit.IconExport")
    jvmArgs("-Djava.awt.headless=true")
    args(packagingDir.asFile.path)
}

// The brand sheet's assets (brand/): tokens, mark, app icon, logotype and pattern, drawn by the app's own code
tasks.register<JavaExec>("brandAssets") {
    group = "distribution"
    description = "Writes brand/tokens.json, brand/svg, brand/png and brand/pattern.svg from the HopSync theme and mark."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("hopsync.desktop.kit.BrandExport")
    jvmArgs("-Djava.awt.headless=true")
    args(rootProject.layout.projectDirectory.dir("brand").asFile.path)
}

tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Packs the JAR with double-click launchers into desktop/build/distributions, for Java 21 users."
    archiveFileName = "$appName-${project.version}-portable.zip"
    from(tasks.jar)
    from(rootProject.files("LICENSE", "THIRD-PARTY-NOTICES.md"))
    from(packagingDir.dir("portable")) {
        filesMatching(listOf("*.sh", "*.command")) {
            permissions { unix("rwxr-xr-x") }
        }
    }
}

tasks.register("updateVersion") {
    group = "versioning"
    description = "Sets the version: ./gradlew updateVersion -PnewVersion=26.2.0-b.1"
    val newVersion = providers.gradleProperty("newVersion")
    val file = rootProject.file("version.properties")
    doLast {
        val display = newVersion.orNull
            ?: throw GradleException("Usage: ./gradlew updateVersion -PnewVersion=26.2.0-b.1")
        val number = installerVersion(display)
        file.writeText(file.readText().replace(Regex("(?m)^app\\.version=.*$"), "app.version=$display"))
        logger.lifecycle("version.properties: app.version=$display (installers: $number)")
    }
}
