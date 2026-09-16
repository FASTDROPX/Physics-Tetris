package com.serafim.tetris.online

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Проверка подлинности приложения (Firebase App Check).
 *
 * Ключи проекта лежат внутри каждого apk и вытаскиваются оттуда за минуту,
 * так что прятать их бессмысленно: единственное, что отличает нашу игру от
 * чужой программы с теми же ключами, — подпись. App Check просит Google
 * подтвердить, что запрос идёт из настоящей, неизменённой игры на
 * настоящем устройстве, и прикладывает к каждому обращению в базу
 * короткоживущий пропуск.
 *
 * Пропуск выдаётся всегда, а вот требовать его от входящих запросов — это
 * отдельный выключатель в консоли (Firebase → App Check → Enforce). Пока
 * он выключен, ничего не ломается: непрошедшие проверку запросы просто
 * видны в консоли отдельным счётчиком. Включать его можно только после
 * того, как счётчик подтверждённых запросов пошёл с живого телефона.
 *
 * Поставщик пропусков разный у отладочной и у выпускаемой сборки — см.
 * `appCheckFactory` в src/debug и src/release: отладочная показывает себя
 * по секрету из logcat, выпускаемая идёт через Play Integrity.
 */
object AppCheck {

    /** Ставится один раз при запуске, до первого обращения к базе. */
    fun install(context: Context) {
        // сборка без google-services.json: Firebase не поднялся, и просить
        // у него пропуск не у кого — онлайн-таблица в такой сборке молчит
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.i(TAG, "Firebase не настроен — App Check не нужен")
            return
        }
        runCatching {
            FirebaseAppCheck.getInstance().apply {
                installAppCheckProviderFactory(appCheckFactory())
                // пропуск живёт около часа и обновляется сам, пока игра открыта
                setTokenAutoRefreshEnabled(true)
            }
        }.onFailure { Log.w(TAG, "App Check не встал: ${it.message}") }
    }

    private const val TAG = "AppCheck"
}
