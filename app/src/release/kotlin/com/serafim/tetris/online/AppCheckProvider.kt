package com.serafim.tetris.online

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Выпускаемая сборка: пропуск выдаёт Play Integrity — служба Google,
 * которая сверяет подпись и номер сборки установленной игры с тем, что
 * зарегистрировано в проекте, и заодно смотрит, не подделано ли само
 * устройство. Отпечаток ключа подписи (SHA-256) должен стоять в консоли
 * Firebase: App Check → приложение → Play Integrity.
 */
internal fun appCheckFactory(): AppCheckProviderFactory =
    PlayIntegrityAppCheckProviderFactory.getInstance()
