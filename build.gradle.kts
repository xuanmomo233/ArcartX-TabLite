import org.gradle.jvm.tasks.Jar

plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("ArcartX-TabLite")
    archiveVersion.set("1.0.0")
    from("src/main/resources") {
        include("plugin.yml")
        include("config.yml")
        include("tab.yml")
        include("tabs/online-tab.yml")
        filter { line: String ->
            line.replace("\${project.version}", "1.0.0")
        }
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
