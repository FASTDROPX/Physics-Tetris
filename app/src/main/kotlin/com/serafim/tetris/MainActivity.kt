package com.serafim.tetris

import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.withFrameNanos
import com.serafim.tetris.game.GameState
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.online.AppCheck
import com.serafim.tetris.ui.BOOT_IN_MS
import com.serafim.tetris.ui.BOOT_REVEAL_MS
import com.serafim.tetris.ui.BootScreen
import com.serafim.tetris.ui.FallingBackground
import com.serafim.tetris.ui.GameScreen
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.TetrisTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var fx: AndroidFx
    private lateinit var game: TetrisGame
    private lateinit var prefs: Prefs
    private var controller: Controller? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goImmersive()

        fx = AndroidFx(this)
        game = TetrisGame(fx)
        game.reduceMotion = animationsDisabled()
        prefs = Prefs(this)
        // подлинность игры подтверждается до первого обращения к таблице
        AppCheck.install(this)

        setContent {
            val scope = rememberCoroutineScope()
            val ctrl = remember { Controller(game, fx, prefs, scope).also { controller = it } }
            TetrisTheme { App(ctrl) }
        }
    }

    /**
     * Игра занимает весь экран: строка состояния и кнопки навигации скрыты.
     * Системные полосы возвращаются по свайпу от края и снова прячутся сами.
     */
    private fun goImmersive() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    private fun animationsDisabled(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    override fun onPause() {
        super.onPause()
        controller?.onBackground()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        fx.release()
    }

    fun keepAwake(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    val hasHardwareKeyboard: Boolean
        get() = resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS

    // ================= клавиатура =================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ctrl = controller ?: return super.dispatchKeyEvent(event)
        if (ctrl.typing && event.keyCode != KeyEvent.KEYCODE_ESCAPE) return super.dispatchKeyEvent(event)
        val act = keyAction(event.keyCode) ?: return super.dispatchKeyEvent(event)
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> { onKeyAction(ctrl, act, event.repeatCount > 0); true }
            KeyEvent.ACTION_UP -> { onKeyRelease(ctrl, act); true }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private enum class Act { LEFT, RIGHT, DOWN, CW, CCW, DROP, HOLD, PAUSE, RESTART, SOUND }

    private fun keyAction(code: Int): Act? = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Act.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Act.RIGHT
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Act.DOWN
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_W -> Act.CW
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> Act.CCW
        KeyEvent.KEYCODE_SPACE -> Act.DROP
        KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Act.HOLD
        KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE -> Act.PAUSE
        KeyEvent.KEYCODE_R -> Act.RESTART
        KeyEvent.KEYCODE_M -> Act.SOUND
        else -> null
    }

    private fun onKeyAction(ctrl: Controller, act: Act, repeat: Boolean) {
        when (act) {
            Act.PAUSE -> { if (!ctrl.dismissTop()) ctrl.togglePause(); return }
            Act.RESTART -> { ctrl.restart(); return }
            Act.SOUND -> { ctrl.toggleSound(); return }
            else -> Unit
        }
        if (game.state != GameState.PLAYING) {
            if (act == Act.DROP) ctrl.dropOrStart()
            return
        }
        when (act) {
            Act.LEFT -> { ctrl.pressLeft(); return }
            Act.RIGHT -> { ctrl.pressRight(); return }
            Act.DOWN -> { ctrl.pressDown(); return }
            else -> Unit
        }
        if (repeat) return
        when (act) {
            Act.CW -> ctrl.rotateCw()
            Act.CCW -> ctrl.rotateCcw()
            Act.DROP -> ctrl.hardDrop()
            Act.HOLD -> ctrl.hold()
            else -> Unit
        }
    }

    private fun onKeyRelease(ctrl: Controller, act: Act) {
        when (act) {
            Act.LEFT -> ctrl.releaseLeft()
            Act.RIGHT -> ctrl.releaseRight()
            Act.DOWN -> ctrl.releaseDown()
            else -> Unit
        }
    }
}

/**
 * Корень приложения: фон, кадровый цикл, заставка и сам экран.
 * Интерфейс проявляется после заставки — как body.ready в оригинале.
 */
@Composable
private fun App(ctrl: Controller) {
    val activity = rememberActivity()
    var frame by remember { mutableIntStateOf(0) }
    var booted by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    // единый кадровый цикл: тот же loop(), что и в оригинале
    LaunchedEffect(Unit) {
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (prev == 0L) 16.0 else (now - prev) / 1_000_000.0
                prev = now
                ctrl.game.update(dt)
                ctrl.onStateChanged(ctrl.game.state)
                frame++
            }
        }
    }

    LaunchedEffect(Unit) {
        // заставка уходит сразу, как бегунок пройдёт полосу второй раз
        delay(BOOT_IN_MS)
        booted = true
        delay(BOOT_REVEAL_MS)
        ready = true
        // синтез эффектов запускаем, когда заставка уже растворилась,
        // иначе он съедает её кадры
        delay(700)
        withContext(Dispatchers.Default) { ctrl.fx.tones.prepare() }
    }

    LaunchedEffect(ctrl.game.state) {
        activity?.keepAwake(ctrl.game.state == GameState.PLAYING)
    }

    Box(Modifier.fillMaxSize().background(M3.Surface)) {
        if (booted) FallingBackground(ctrl.game.reduceMotion)
        // Экран собирается сразу, но невидимым: за время заставки он
        // успевает прогреться. К показу его собирают заново (`key`), и
        // меню выезжает тем же движением, что и при выходе из партии, —
        // иначе оно отыграло бы своё появление за непрозрачной заставкой.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (ready) 1f else 0f }) {
            key(ready) { GameScreen(ctrl, frame, activity?.hasHardwareKeyboard ?: false) }
        }
        BootScreen(visible = !booted)
    }
}

/** Достаём Activity из контекста — он может быть завёрнут в ContextWrapper. */
@Composable
private fun rememberActivity(): MainActivity? {
    val context = LocalContext.current
    return remember(context) {
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper) {
            if (c is MainActivity) return@remember c
            c = c.baseContext
        }
        null
    }
}
