package com.serafim.tetris.online

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Отладочная сборка (и тесты на эмуляторе): Play Integrity здесь работать
 * не может — сборка подписана отладочным ключом, а эмулятор не проходит
 * проверку устройства. Вместо него — отладочный поставщик: при первом
 * запуске он пишет в logcat случайный секрет, который добавляется в
 * консоли (App Check → приложение → ⋮ → Manage debug tokens).
 *
 * В выпускаемую сборку этот поставщик не попадает вовсе: библиотека
 * подключена только к отладочной сборке (debugImplementation).
 */
internal fun appCheckFactory(): AppCheckProviderFactory =
    DebugAppCheckProviderFactory.getInstance()
