plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.jbac"
version = "0.1.0"

// Single sources of truth, expanded into plugin.yml by processResources.
val mainClass = "com.jbac.cosmetics.CosmeticPlugin"
val sqliteJdbc = "org.xerial:sqlite-jdbc:3.53.4.0"   // loaded at runtime via plugin.yml `libraries`, never shaded
val paperApi = "io.papermc.paper:paper-api:26.2.build.129-stable"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

dependencies {
    compileOnly(paperApi)
    compileOnly("net.luckperms:api:5.5")
    compileOnly(sqliteJdbc)

    testImplementation(paperApi)                       // YamlConfiguration / Material in pure unit tests
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    processResources {
        val props = mapOf("version" to project.version.toString(), "main" to mainClass, "sqliteJdbc" to sqliteJdbc)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    test {
        useJUnitPlatform()
    }
    runServer {
        // Downloads Paper 26.2 into run/ and starts it with the freshly built jar in run/plugins.
        minecraftVersion("26.2")
    }
}
