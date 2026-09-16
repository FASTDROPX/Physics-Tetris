import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Настройки Firebase лежат в app/google-services.json. Файл в репозиторий
    // не кладётся (в нём ключи конкретного проекта), поэтому плагин
    // подключается только тогда, когда файл на месте: без него игра
    // собирается и работает целиком, молчит лишь онлайн-таблица.
    id("com.google.gms.google-services") apply false
}

val firebaseConfig = file("google-services.json")
if (firebaseConfig.exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("app/google-services.json не найден — сборка без онлайн-таблицы")
}

// Ключ подписи лежит вне проекта и берётся из local.properties
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = localProps.getProperty("releaseStoreFile")?.let { file(it).exists() } == true

/**
 * ггДДДЧЧмм — год, день года, час и минута сборки по UTC: 11 сентября
 * 2026, 13:52 — это 262541352. Раньше номер был ггММддЧЧ, с точностью до
 * часа, и две выдачи в один час получали одинаковый номер — такое MIUI
 * тоже не ставит. Новые номера девятизначные, то есть больше любого
 * прежнего восьмизначного, и до 2099 года остаются меньше предела
 * Google Play в 2 100 000 000.
 */
val buildStamp: Int = LocalDateTime.now(ZoneOffset.UTC)
    .format(DateTimeFormatter.ofPattern("yyDDDHHmm"))
    .toInt()

android {
    namespace = "com.serafim.tetris"
    compileSdk = 37

    defaultConfig {
        // С 2.0 игра живёт под этим пакетом — под ним она заведена в Firebase
        // (проект physic-tetris). Для телефона это новое приложение: прежнее,
        // com.serafim.tetris, осталось отдельной игрой со своими данными.
        // Пространство имён кода (namespace) прежнее — пакет установки от
        // него не зависит.
        applicationId = "com.fastdropx.tetris"
        minSdk = 26
        targetSdk = 37
        // Номер сборки обязан расти с каждой выдачей. Пока он стоял на
        // единице, установщик MIUI отказывался ставить обновление поверх
        // прежней версии — «Приложение не установлено», — и обновиться
        // можно было только с удалением, то есть потеряв весь прогресс.
        // Дата в формате ггДДДЧЧмм растёт сама и укладывается в предел
        // Google Play (меньше 2 100 000 000).
        versionCode = buildStamp
        versionName = "3.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(localProps.getProperty("releaseStoreFile"))
                storePassword = localProps.getProperty("releaseStorePassword")
                keyAlias = localProps.getProperty("releaseKeyAlias")
                keyPassword = localProps.getProperty("releaseKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDir("src/androidTest/kotlin")
        }
        // поставщик пропусков App Check у отладочной и выпускаемой сборки
        // разный: см. AppCheckProvider.kt в каждой из этих папок
        getByName("debug") {
            kotlin.srcDir("src/debug/kotlin")
        }
        getByName("release") {
            kotlin.srcDir("src/release/kotlin")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // онлайн-таблица: анонимный вход и база Firestore, бесплатный тариф Spark
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // подтверждение подлинности игры: выпускаемая сборка ходит через
    // Play Integrity, отладочная — по секрету из logcat, и эта библиотека
    // в выпускаемую сборку не попадает
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
