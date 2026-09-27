plugins {
    // Auto-provisions the JDK 25 toolchain that Paper 26.2 requires (host JDK is 26).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CosmeticPlugin"
