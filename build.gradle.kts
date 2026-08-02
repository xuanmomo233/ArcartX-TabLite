import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.jvm.tasks.Jar

plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    flatDir {
        dirs("libs")
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // ArcartX 本体（Kotlin 编译产物），仅编译期引用，运行时由服务端提供
    compileOnly(":ArcartX-2.5.36")
    // ArcartX 依赖 Kotlin stdlib，编译期需要
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// 资源处理：替换 plugin.yml 中的版本号占位符
tasks.processResources {
    filter(ReplaceTokens::class, "tokens" to mapOf("project.version" to "1.1.0"), "beginToken" to "\${", "endToken" to "}")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("ArcartX-TabLite")
    archiveVersion.set("1.1.0")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
