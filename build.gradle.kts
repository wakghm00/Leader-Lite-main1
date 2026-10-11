import org.apache.commons.lang3.SystemUtils
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.wagyourtail.jvmdowngrader") version "1.3.6"
}
//Constants:
val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val mixinGroup = "$baseGroup.mixin"
val modid: String by project
val jarName: String by project
val transformerFile = file("src/main/resources/accesstransformer.cfg")
// Toolchains:
java {
    // The final jar is downgraded to Java 8 by JvmDowngrader (see downgradeJar below).
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
// Minecraft configuration:
loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            // If you don't want mixins, remove these lines
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                // This argument causes a crash on macOS
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        // If you don't want mixins, remove this lines
        mixinConfig("mixins.$modid.json")
        mixinConfig("mixins.viaforge.json")
	    if (transformerFile.exists()) {
			println("Installing access transformer")
		    accessTransformer(transformerFile)
	    }
    }
    // If you don't want mixins, remove these lines
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}
sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}
// Dependencies:
repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    maven("https://repo.viaversion.com")
    // If you don't want to log in with your real minecraft account, remove this line
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}
val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}
dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    // If you don't want mixins, remove these lines
    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    // If you don't want to log in with your real minecraft account, remove this line
    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")

    // Compile-time netty API (runtime uses the one bundled with 1.8.9)
    compileOnly("io.netty:netty-all:4.2.2.Final")

    shadowImpl("com.viaversion:viaversion-common:5.8.0")
    shadowImpl("com.viaversion:viabackwards-common:5.8.0")
    shadowImpl("com.viaversion:viarewind-common:4.0.15")
    shadowImpl("com.viaversion:viaaprilfools-common:4.1.0")
    shadowImpl("net.raphimc:ViaLegacy:3.0.14") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    shadowImpl("org.slf4j:slf4j-api:2.0.17")
}
// Tasks:
tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
    options.release.set(17)
}
tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(jarName)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        // If you don't want mixins, remove these lines
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json,mixins.viaforge.json"
	    if (transformerFile.exists())
			this["FMLAT"] = "${modid}_at.cfg"
    }
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("basePackage", baseGroup)
    filesMatching(listOf("mcmod.info", "mixins.$modid.json","version.json")) {
        expand(inputs.properties)
    }
    rename("accesstransformer.cfg", "META-INF/${modid}_at.cfg")
}
val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}
tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}
tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    doLast {
        configurations.forEach {
            println("Copying dependencies into mod: ${it.files}")
        }
    }
    // If you want to include other dependencies and shadow them, you can relocate them in here
    fun relocate(name: String) = relocate(name, "$baseGroup.deps.$name")
    relocate("org.slf4j", "com.viaversion.viaforge.libs.slf4j")
}
// Downgrade the final (already remapped) jar to Java 8 for Minecraft 1.8.9.
tasks.named<DowngradeJar>("downgradeJar") {
    inputFile.set(tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar").get().archiveFile)
}
tasks.named<ShadeJar>("shadeDowngradedApi") {
    inputFile.set(tasks.named<DowngradeJar>("downgradeJar").get().archiveFile)
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    // NOTE: inside this task block, `version` resolves to ShadeJar.getVersion() (the
    // jvmdowngrader plugin version), so always use project.version explicitly.
    archiveFileName.set("$jarName-${project.version}.jar")
    doLast {
        // Belt & braces: the plugin may override the archive name; rename if needed.
        val out = archiveFile.get().asFile
        val finalFile = layout.buildDirectory.file("libs/$jarName-${project.version}.jar").get().asFile
        if (out.exists() && !out.absolutePath.equals(finalFile.absolutePath)) {
            out.copyTo(finalFile, overwrite = true)
            out.delete()
        }
    }
}
tasks.assemble.get().dependsOn(tasks.named("shadeDowngradedApi"))
