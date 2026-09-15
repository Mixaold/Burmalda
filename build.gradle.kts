plugins {
    // loom-back-compat applies the correct Fabric Loom variant for the active MC version
    id("dev.kikugie.loom-back-compat")
}

// Global properties (top-level in stonecutter.properties.toml)
val modId = property("mod.id") as String
val modName = property("mod.name") as String
val modVersion = property("mod.version") as String
val fabricLoader = property("deps.fabric_loader") as String

// Per-version properties (under each ["<version>"] table)
val fabricApi: String = sc.properties["deps.fabric_api"]
val yarn: String = sc.properties["deps.yarn"]
val mcCompat: String = sc.properties["mod.mc_compat"]

// Artifact carries the MC version suffix: burmalda-1.2.0-1.21.1.jar
version = "$modVersion-${sc.current.version}"
base.archivesName = modId

repositories {
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Phase 1 uses Yarn mappings (available for the whole 1.21 line)
    mappings("net.fabricmc:yarn:$yarn:v2")

    modImplementation("net.fabricmc:fabric-loader:$fabricLoader")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApi")
}

loom {
    runConfigs.all {
        runDir = "../../run"
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    // The advancement-tab "background" field format changed in 1.21.5:
    //   <=1.21.3 : full texture path  "minecraft:textures/gui/advancements/backgrounds/<name>.png"
    //   >=1.21.5 : sprite id          "minecraft:gui/advancements/backgrounds/<name>"
    // JSON can't carry Stonecutter comments, so inject the right value per version here.
    val verParts = sc.current.version.split(".")
    val major = verParts.getOrNull(1)?.toIntOrNull() ?: 21
    val patch = verParts.getOrNull(2)?.toIntOrNull() ?: 0
    val spriteFormat = major > 21 || (major == 21 && patch >= 5)
    val advancementBg = if (spriteFormat)
        "minecraft:gui/advancements/backgrounds/adventure"
    else
        "minecraft:textures/gui/advancements/backgrounds/adventure.png"

    val props = mapOf(
        "id" to modId,
        "name" to modName,
        "version" to modVersion,
        "minecraft" to mcCompat,
        "advancement_bg" to advancementBg
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("data/burmalda/advancement/root.json") { expand(props) }
}
