plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // dyn4j — чистая Java без единой зависимости, лицензия BSD-3.
    // Живёт в :core, поэтому физика проверяется теми же юнит-тестами,
    // что и остальная логика, без эмулятора.
    implementation("org.dyn4j:dyn4j:6.0.0")

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
