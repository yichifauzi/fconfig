import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.versioning.VersioningConfiguration
import org.jetbrains.dokka.versioning.VersioningPlugin
import java.net.URI

plugins {
    id("fabric-loom")
    val kotlinVersion: String by System.getProperties()
    kotlin("jvm").version(kotlinVersion)
    kotlin("plugin.serialization") version "1.9.22"
    id("com.modrinth.minotaur") version "2.+"
    id("org.jetbrains.dokka") version "1.9.20"
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:1.9.20")
        classpath("org.jetbrains.dokka:versioning-plugin:1.9.20")
    }
}

base {
    val archivesBaseName: String by project
    archivesName.set(archivesBaseName)
}

val log: File = file("changelog.md")
val modVersion: String by project
version = modVersion
val mavenGroup: String by project
group = mavenGroup
println("## Changelog for FzzyConfig $modVersion \n\n" + log.readText())



repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = URI("https://maven.terraformersmc.com/releases/")
        content {
            includeGroup ("com.terraformersmc")
        }
    }
}

sourceSets{
    main{
        kotlin{
            val includeExamples: String by project
            if (!includeExamples.toBoolean()) {
                exclude("me/fzzyhmstrs/fzzy_config/examples/**")
            }
            val testMode: String by project
            if (!testMode.toBoolean()) {
                exclude("me/fzzyhmstrs/fzzy_config/test/**")
            }
        }
    }
    create("testmod"){
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
}

val testmodImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

idea {
    module {
        testSources.from(sourceSets["testmod"].java.srcDirs)
        testSources.from(sourceSets["testmod"].kotlin.srcDirs)
    }
}

dependencies {
    val minecraftVersion: String by project
    minecraft("com.mojang:minecraft:$minecraftVersion")
    val yarnMappings: String by project
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    val loaderVersion: String by project
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    val fabricVersion: String by project
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    val fabricKotlinVersion: String by project
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    val tomlktVersion: String by project
    implementation("net.peanuuutz.tomlkt:tomlkt:$tomlktVersion")
    include("net.peanuuutz.tomlkt:tomlkt-jvm:$tomlktVersion")

    val janksonVersion: String by project
    implementation("blue.endless:jankson:$janksonVersion")
    include("blue.endless:jankson:$janksonVersion")

    val modmenuVersion: String by project
    modCompileOnly("com.terraformersmc:modmenu:$modmenuVersion") {
        isTransitive = false
    }
    modLocalRuntime("com.terraformersmc:modmenu:$modmenuVersion") {
        isTransitive = false
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testmodImplementation(sourceSets.main.get().output)

    dokkaPlugin("me.fzzyhmstrs:internal-skip-plugin:1.0-SNAPSHOT")
    dokkaPlugin("org.jetbrains.dokka:versioning-plugin:1.9.20")
}

loom {
    runs {
        create("testmodClient"){
            client()
            name = "Testmod Client"
            source(sourceSets["testmod"])
        }
    }
}

tasks {
    val javaVersion = JavaVersion.VERSION_17
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
        options.release.set(javaVersion.toString().toInt())
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = javaVersion.toString()
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
        //java.sourceCompatibility = javaVersion
        //targetCompatibility = javaVersion.toString()
    }
    jar {
        from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } }
    }
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") { expand(mutableMapOf("version" to project.version)) }
    }
    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion.toString())) }
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        withSourcesJar()
    }
}

tasks.register("testmodJar", Jar::class) {
    from(sourceSets["testmod"].output)
    destinationDirectory =  File(project.layout.buildDirectory.get().asFile, "testmod")
    archiveClassifier = "testmod"
}

tasks.withType<DokkaTask>().configureEach {
    val docVersionsDir = projectDir.resolve("build/dokka/version")
    // The version for which you are currently generating docs
    val currentVersion = project.version.toString()

    // Set the output to a folder with all other versions
    // as you'll need the current version for future builds
    val currentDocsDir = docVersionsDir.resolve(currentVersion)
    outputDirectory.set(currentDocsDir)
    dokkaSourceSets.configureEach {
        perPackageOption {
            matchingRegex.set("me.fzzyhmstrs.fzzy_config.examples|me.fzzyhmstrs.fzzy_config.impl|me.fzzyhmstrs.fzzy_config.test|me.fzzyhmstrs.fzzy_config.updates|me.fzzyhmstrs.fzzy_config")
            suppress.set(true)
        }
        includes.from(project.files(), "dokka/module.md")
    }
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        moduleName = "Fzzy Config"
        customAssets = listOf(file("src/main/resources/assets/fzzy_config/banner.png"))
        customStyleSheets = listOf(file("dokka/style.css"),file("dokka/logo-styles.css"))
        templatesDir = file("dokka")
        footerMessage = "(c) 2024 fzzyhmstrs"
    }

    pluginConfiguration<VersioningPlugin, VersioningConfiguration> {
        olderVersionsDir = docVersionsDir
        version = currentVersion
    }

    doLast {
        // This folder contains the latest documentation with all
        // previous versions included, so it's ready to be published.
        // Make sure it's copied and not moved - you'll still need this
        // version for future builds
        currentDocsDir.copyRecursively(file("build/dokka/hosting"), overwrite = true)

        // Only once current documentation has been safely moved,
        // remove previous versions bundled in it. They will not
        // be needed in future builds, it's just overhead.
        currentDocsDir.resolve("older").deleteRecursively()
    }
}



modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("fzzy-config")
    versionNumber.set(modVersion)
    versionName.set("${base.archivesName.get()}-$modVersion")
    versionType.set("beta")
    uploadFile.set(tasks.remapJar.get())
    gameVersions.addAll("1.19.3")
    loaders.addAll("fabric","quilt")
    detectLoaders.set(false)
    changelog.set("## Changelog for Fzzy Config $modVersion \n\n" + log.readText())
    dependencies{
        required.project("fabric-api")
        required.project("fabric-language-kotlin")
        optional.project("trinkets")
    }
    debugMode.set(true)
}