package com.serafim.tetris.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Куда ведёт значок GitHub в настройках. */
const val SOURCES_URL = "https://github.com/FASTDROPX/Physics-Tetris"

/**
 * Открыть ссылку в браузере телефона. Браузера может не оказаться вовсе —
 * тогда просто ничего не произойдёт, ронять игру из-за этого не за что.
 */
fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Настройки: громкость, сила отдачи и тема, а внизу ссылка на исходники.
 *
 * Переключатели «Звук» и «Вибрация» остались в меню и отвечают за «звучит
 * ли вообще»; здесь — насколько громко и насколько сильно. Ноль на
 * ползунке равносилен выключенному переключателю.
 */
@Composable
fun SettingsSheet(
    closing: Boolean,
    large: Boolean,
    soundOn: Boolean,
    volume: Float,
    vibrationOn: Boolean,
    power: Float,
    theme: ThemeKind,
    onVolume: (Float, Boolean) -> Unit,
    onPower: (Float, Boolean) -> Unit,
    onTheme: (ThemeKind) -> Unit,
    onGithub: () -> Unit,
    onBack: () -> Unit,
) {
    Overlay(closing, large, onScrim = onBack) { t ->
        Column(
            Modifier.fillMaxWidth().padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RiseIn(t, 80f) {
                Text("Настройки", color = M3.OnSurface, fontSize = if (large) 26.sp else 22.sp)
            }
            RiseIn(t, 130f) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LevelSlider(
                        title = "Громкость звука",
                        value = volume,
                        enabled = soundOn,
                        offNote = "Звук выключен в меню",
                        large = large,
                        onChange = onVolume,
                    )
                    LevelSlider(
                        title = "Сила вибрации",
                        value = power,
                        enabled = vibrationOn,
                        offNote = "Вибрация выключена в меню",
                        large = large,
                        onChange = onPower,
                    )
                }
            }
            RiseIn(t, 180f) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Тема",
                        color = M3.OnSurfaceVariant,
                        fontSize = if (large) 15.sp else 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                    )
                    for (kind in ThemeKind.entries) {
                        ThemeRow(
                            kind = kind,
                            selected = kind == theme,
                            large = large,
                            onClick = { onTheme(kind) },
                        )
                    }
                }
            }
            RiseIn(t, 240f) { GithubRow(large, onGithub) }
            RiseIn(t, 290f) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = M3.Primary,
                        contentColor = M3.OnPrimary,
                    ),
                ) { Text("Назад", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

/**
 * Ползунок с подписью и процентами. Пока палец ведёт, значение идёт в
 * игру сразу (иначе громкость крутили бы вслепую), но пробный звук и
 * толчок даются только по отпусканию — иначе вышла бы очередь щелчков.
 */
@Composable
private fun LevelSlider(
    title: String,
    value: Float,
    enabled: Boolean,
    offNote: String,
    large: Boolean,
    onChange: (Float, Boolean) -> Unit,
) {
    // своё значение на время перетаскивания: ползунок обязан идти за
    // пальцем ровно, не дожидаясь, пока состояние вернётся сверху
    var live by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = if (enabled) M3.OnSurface else M3.OnSurfaceVariant.copy(alpha = 0.5f),
                fontSize = if (large) 16.sp else 15.sp,
            )
            Text(
                if (enabled) "${(live * 100).roundToInt()} %" else offNote,
                color = M3.OnSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = if (large) 14.sp else 13.sp,
            )
        }
        Slider(
            value = live,
            onValueChange = { live = it; onChange(it, false) },
            onValueChangeFinished = { onChange(live, true) },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = M3.Primary,
                activeTrackColor = M3.Primary,
                inactiveTrackColor = M3.SurfaceContainerHighest,
                disabledThumbColor = M3.OutlineVariant,
                disabledActiveTrackColor = M3.OutlineVariant,
                disabledInactiveTrackColor = M3.SurfaceContainerHigh,
            ),
        )
    }
}

/**
 * Строка выбора темы: название, пояснение и три кружка — фон, кнопка и
 * значок, — чтобы было видно, во что она красит, ещё до нажатия.
 */
@Composable
private fun ThemeRow(kind: ThemeKind, selected: Boolean, large: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val usable = kind != ThemeKind.MONET || ThemeKind.monetAvailable
    val p = paletteOf(kind, context)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) M3.SurfaceContainerHigh else M3.SurfaceContainer)
            .then(
                if (selected) {
                    Modifier.border(BorderStroke(1.dp, M3.Primary), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = usable, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                kind.title,
                color = if (usable) M3.OnSurface else M3.OnSurfaceVariant.copy(alpha = 0.5f),
                fontSize = if (large) 16.sp else 15.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
            Text(
                if (usable) kind.note else "Нужен Android 12 или новее",
                color = M3.OnSurfaceVariant.copy(alpha = if (usable) 1f else 0.5f),
                fontSize = if (large) 13.sp else 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (c in listOf(p.surface, p.primary, p.secondaryContainer)) {
                Box(
                    Modifier
                        .size(if (large) 18.dp else 16.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(c)
                        .border(BorderStroke(1.dp, M3.OutlineVariant), RoundedCornerShape(100.dp))
                )
            }
        }
    }
}

/** Ссылка на исходники: значок GitHub, подпись и стрелка «наружу». */
@Composable
private fun GithubRow(large: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(M3.SurfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GithubMark(if (large) 24.dp else 22.dp, M3.OnSurface)
        Column(Modifier.weight(1f)) {
            Text(
                "Исходники на GitHub",
                color = M3.OnSurface,
                fontSize = if (large) 16.sp else 15.sp,
            )
            Text(
                "FASTDROPX/Physics-Tetris",
                color = M3.OnSurfaceVariant,
                fontSize = if (large) 13.sp else 12.sp,
            )
        }
        Canvas(Modifier.size(18.dp)) {
            val k = size.minDimension / 24f
            // холст ниже растянут на k, и толщина растянется вместе с ним:
            // умножить её на k ещё и здесь — значит сделать линию толще соседей
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = PathParser().parsePathString(OUT_ARROW).toPath()
            scale(k, k, pivot = Offset.Zero) {
                drawPath(path, M3.OnSurfaceVariant, style = stroke)
            }
        }
    }
}

/**
 * Значок GitHub — их собственный контур, как он выдан самим GitHub для
 * ссылок на него. Рисуется, а не лежит картинкой: в игре нет ни одного
 * файла-изображения, и этот не стал исключением.
 */
@Composable
fun GithubMark(side: Dp, color: Color) {
    Canvas(Modifier.size(side)) {
        val k = size.minDimension / 24f
        val path = PathParser().parsePathString(GITHUB_MARK).toPath()
        scale(k, k, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

/** Значок настроек в углу меню: шестерёнка теми же линиями, что и соседи. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Canvas(Modifier.size(22.dp)) {
            val k = size.minDimension / 24f
            // холст ниже растянут на k, и толщина растянется вместе с ним:
            // умножить её на k ещё и здесь — значит сделать линию толще соседей
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = PathParser().parsePathString(GEAR).toPath()
            scale(k, k, pivot = Offset.Zero) {
                drawPath(path, M3.OnSurfaceVariant, style = stroke)
            }
        }
    }
}

private const val GITHUB_MARK =
    "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 " +
        "0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 " +
        "3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 " +
        "1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466" +
        "-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 " +
        "1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 " +
        "3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 " +
        "5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69" +
        ".825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"

/** Стрелка «уходит наружу»: квадрат со срезанным углом и стрелка из него. */
private const val OUT_ARROW =
    "M14 4h6v6M20 4l-8 8M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"

/**
 * Шестерёнка: шесть узких зубцов и отверстие посередине. Посчитана, а не
 * срисована, и узость зубцов здесь главное: линия толщиной в два деления
 * заливала промежутки между широкими зубцами, и значок сливался в кляксу
 * рядом с тонкими соседями. Сейчас между зубцами по дуге около пяти
 * делений — после толщины линии остаётся почти три видимых.
 */
private const val GEAR =
    "M10.59 4.74L10.81 2.27L13.19 2.27L13.41 4.74A7.4 7.4 0 0 1 17.58 7.15" +
        "L19.83 6.1L21.02 8.17L19 9.59A7.4 7.4 0 0 1 19 14.41L21.02 15.83L19.83 17.9" +
        "L17.58 16.85A7.4 7.4 0 0 1 13.41 19.26L13.19 21.73L10.81 21.73L10.59 19.26" +
        "A7.4 7.4 0 0 1 6.42 16.85L4.17 17.9L2.98 15.83L5 14.41A7.4 7.4 0 0 1 5 9.59" +
        "L2.98 8.17L4.17 6.1L6.42 7.15A7.4 7.4 0 0 1 10.59 4.74 Z" +
        "M15.2 12a3.2 3.2 0 1 1-6.4 0 3.2 3.2 0 0 1 6.4 0Z"
