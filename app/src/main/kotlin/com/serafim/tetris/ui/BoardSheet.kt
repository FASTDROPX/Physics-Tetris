package com.serafim.tetris.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serafim.tetris.game.Nick
import com.serafim.tetris.game.PieceType
import com.serafim.tetris.game.formatRu
import com.serafim.tetris.game.pluralRu
import com.serafim.tetris.online.BoardKind
import com.serafim.tetris.online.BoardRow
import com.serafim.tetris.online.BoardView
import com.serafim.tetris.online.JoinIssue
import com.serafim.tetris.online.isDevNick
import com.serafim.tetris.online.isFriendNick
import com.serafim.tetris.online.Standing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random

/**
 * Радуга ника разработчика: полоса цветов бесконечно едет по буквам слева
 * направо. Концы списка совпадают, а повтор ([TileMode.Repeated]) замыкает
 * её в кольцо — шва не видно, и длина ника не важна.
 *
 * Фаза считается от часов Compose, а не от системных: в тесте снимок ловит
 * ровно тот кадр, который задан.
 */
@Composable
fun rainbowBrush(): Brush {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) phase = (phase + (now - last) / RAINBOW_MS) % 1f
                last = now
            }
        }
    }
    val band = with(LocalDensity.current) { RAINBOW_BAND.toPx() }
    val shift = phase * band
    return Brush.linearGradient(
        colors = RainbowColors,
        start = Offset(shift - band, 0f),
        end = Offset(shift, 0f),
        tileMode = TileMode.Repeated,
    )
}

/** Полный круг оттенков; последний равен первому, чтобы повтор не давал стыка. */
private val RainbowColors = List(13) { Color.hsv((it % 12) * 30f, 0.62f, 1f) }
private val RAINBOW_BAND = 150.dp
private const val RAINBOW_MS = 2600f

/**
 * Имя игрока в таблице: обычное, радужное (ник разработчика) или с
 * фигуркой перед ним (ник его друга). Метка достаётся ровно одному нику —
 * сравнение идёт тем же ключом занятости, так что и другой высотой букв
 * её не подобрать.
 */
@Composable
private fun RowScope.NickText(name: String, color: Color, weight: Float = 1f) {
    Row(
        Modifier.weight(weight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isFriendNick(name)) PieceBadge(20.dp)
        val dev = isDevNick(name)
        Text(
            name,
            color = if (dev) Color.Unspecified else color,
            fontSize = 15.sp,
            fontWeight = if (dev) FontWeight.Medium else FontWeight.Normal,
            style = if (dev) LocalTextStyle.current.merge(TextStyle(brush = rainbowBrush())) else LocalTextStyle.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Фигурка из стакана перед ником: раз в секунду поворот на четверть, и
 * ровно на середине поворота фигурка сменяется следующей. Момент выбран
 * не случайно: при кривой «expo in-out» середина — самое быстрое место
 * хода, и подмена там глазу не видна. Две одинаковые подряд не выпадают:
 * следующая берётся из шести оставшихся.
 *
 * Поворот копится: четверть за четвертью, как крутится фигура в стакане,
 * поэтому за четыре смены значок возвращается к началу.
 *
 * Время идёт от часов Compose (кадрами), а не от системных: так снимок в
 * тесте ловит ровно тот момент, который задан.
 */
@Composable
private fun PieceBadge(side: Dp) {
    val rnd = remember { Random(System.nanoTime()) }
    var type by remember { mutableStateOf(PieceType.ALL[rnd.nextInt(PieceType.ALL.size)]) }
    var quarter by remember { mutableIntStateOf(0) }
    var started by remember { mutableFloatStateOf(0f) }
    var swapped by remember { mutableStateOf(true) }
    var now by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                if (last != 0L) now += (t - last)
                last = t
                val gone = now - started
                // на середине поворота — та самая подмена
                if (!swapped && gone >= SPIN_MS / 2f) {
                    swapped = true
                    val rest = PieceType.ALL.filter { it != type }
                    type = rest[rnd.nextInt(rest.size)]
                }
                if (gone >= PIECE_MS) {
                    started = now
                    swapped = false
                    quarter++
                }
            }
        }
    }
    val p = expoInOut(((now - started) / SPIN_MS).coerceIn(0f, 1f))
    val angle = (quarter - 1 + p) * 90f
    Canvas(Modifier.size(side)) {
        rotate(angle, pivot = center) {
            drawMini(type, size.width / 2f, size.height / 2f, size.minDimension / 4f)
        }
    }
}

/**
 * Та же кривая, что `ease-in-out-expo` в CSS: с места трогается еле-еле,
 * в середине идёт рывком, к концу снова замирает.
 */
private fun expoInOut(t: Float): Float = when {
    t <= 0f -> 0f
    t >= 1f -> 1f
    t < 0.5f -> 2f.pow(20f * t - 10f) / 2f
    else -> (2f - 2f.pow(-20f * t + 10f)) / 2f
}

/** Как часто меняется фигурка и сколько длится сам поворот. */
private const val PIECE_MS = 1000f
private const val SPIN_MS = 520f

/**
 * Растянуть карточку на [extra] шире, чем даёт родитель, — по половине с
 * каждой стороны. Нужно ровно одному месту: карточка места стоит в меню
 * между его отступами, а подписи в ней длинные, и лишние два десятка
 * точек решают, влезет ник с разрывом целиком или начнёт мельчать.
 */
internal fun Modifier.wider(extra: Dp) = this.layout { measurable, constraints ->
    val add = extra.roundToPx()
    val wide = constraints.copy(
        minWidth = constraints.minWidth + add,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + add else constraints.maxWidth,
    )
    val p = measurable.measure(wide)
    layout(p.width - add, p.height) { p.place(-add / 2, 0) }
}

/** Первые три места — золото, серебро, бронза. */
private val Medal = listOf(Color(0xFFFFD166), Color(0xFFC9D1DC), Color(0xFFE3A873))

/**
 * Онлайн-таблица лидеров. Стоит полосой, как главное меню, и так же
 * закрывается тапом по фону. Пока у игрока нет ника, сверху форма
 * «вступить», после — его строка: ник, место и «сменить ник». Когда
 * открыта клавиатура, окно поджимается над ней ([Overlay] с `ime`).
 */
@Composable
fun BoardSheet(
    large: Boolean,
    view: BoardView,
    kind: BoardKind,
    nick: String,
    joining: Boolean,
    issue: JoinIssue?,
    onKind: (BoardKind) -> Unit,
    onJoin: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf(nick.isEmpty()) }
    var draft by remember { mutableStateOf(nick) }
    // форма закрывается не по нажатию, а когда ник действительно взят:
    // занятый не пройдёт, и жалоба должна остаться на глазах
    LaunchedEffect(nick) { if (nick.isNotEmpty()) editing = false }
    // новый список — новый въезд строк: и после загрузки, и при смене
    // вкладки, даже если вкладка отдала тот же список из памяти
    val gen = remember(kind, view) { Any() }
    val band = if (large) null else PHONE_MENU_BAND
    Overlay(closing = false, large = large, onScrim = onBack, band = band, ime = true) { t ->
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (band != null) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = if (large) 30.dp else 18.dp, vertical = if (large) 30.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RiseIn(t, 60f) {
                Text("Таблица лидеров", color = M3.OnSurface, fontSize = if (large) 26.sp else 22.sp)
            }
            RiseIn(t, 110f) {
                KindTabs(kind, onKind)
            }
            RiseIn(t, 160f) {
                if (editing) {
                    JoinForm(
                        draft = draft,
                        onDraft = { draft = it.take(24) },
                        first = nick.isEmpty(),
                        busy = joining,
                        // жалоба относится к тем буквам, на которые её получили:
                        // стоит поправить ник — и она уходит сама
                        error = issue?.takeIf { it.key == Nick.key(draft) }?.message,
                        onDone = { if (Nick.clean(draft).isNotEmpty()) onJoin(draft) },
                        onCancel = { draft = nick; editing = false },
                    )
                } else {
                    MeLine(nick, view, onRename = { draft = nick; editing = true })
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (band != null) Modifier.weight(1f) else Modifier.heightIn(min = 120.dp, max = 420.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when (view) {
                    BoardView.Loading -> CircularProgressIndicator(color = M3.Primary, modifier = Modifier.size(34.dp))
                    is BoardView.Failed ->
                        if (view.offline) {
                            OfflineNote(syncedAt = 0L, onRetry = onRetry, divider = false)
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    view.message,
                                    color = M3.OnSurfaceVariant,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(onClick = onRetry) {
                                    Text("Повторить", color = M3.Primary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    is BoardView.Ready -> Rows(view, gen, onRetry)
                }
            }
            RiseIn(t, 260f) {
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

@Composable
private fun KindTabs(kind: BoardKind, onKind: (BoardKind) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(100.dp))
            .border(1.dp, M3.OutlineVariant, RoundedCornerShape(100.dp)),
    ) {
        BoardKind.entries.forEachIndexed { i, k ->
            if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(M3.OutlineVariant))
            val on = k == kind
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (on) M3.SecondaryContainer else Color.Transparent)
                    .clickable { onKind(k) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    k.title,
                    color = if (on) M3.OnSecondaryContainer else M3.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun JoinForm(
    draft: String,
    onDraft: (String) -> Unit,
    first: Boolean,
    busy: Boolean,
    error: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val ok = Nick.clean(draft).isNotEmpty() && !busy
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (first) {
            Text(
                "Выберите ник — под ним вас увидят все. Очки уходят в таблицу после каждой партии.",
                color = M3.OnSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                singleLine = true,
                placeholder = { Text("Ник", color = M3.Outline) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = M3.OnSurface,
                    unfocusedTextColor = M3.OnSurface,
                    focusedBorderColor = M3.Primary,
                    unfocusedBorderColor = M3.OutlineVariant,
                    cursorColor = M3.Primary,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                isError = error != null,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onDone,
                enabled = ok,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(100.dp),
                // узкие поля у кнопки — чтобы место досталось полю ника
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = M3.Primary,
                    contentColor = M3.OnPrimary,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = M3.OnPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(if (first) "Участвовать" else "Готово", fontWeight = FontWeight.Medium)
                }
            }
        }
        if (error != null) {
            Text(
                error,
                color = M3.Danger,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (!first) {
            TextButton(onClick = onCancel) { Text("Отмена", color = M3.OnSurfaceVariant) }
        }
    }
}

@Composable
private fun MeLine(nick: String, view: BoardView, onRename: () -> Unit) {
    val ready = view as? BoardView.Ready
    val place = when {
        ready == null -> ""
        ready.myRank != null -> "${ready.myRank}-е место"
        else -> "пока не в таблице — сыграйте партию"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(M3.SurfaceContainer)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { NickText(nick, M3.OnSurface) }
            if (place.isNotEmpty()) Text(place, color = M3.OnSurfaceVariant, fontSize = 12.sp)
        }
        TextButton(onClick = onRename) { Text("Сменить ник", color = M3.Primary, fontSize = 13.sp) }
    }
}

@Composable
private fun Rows(view: BoardView.Ready, gen: Any, onRetry: () -> Unit) {
    if (view.rows.isEmpty()) {
        if (view.stale) {
            OfflineNote(view.syncedAt, onRetry, divider = false)
        } else {
            Text(
                "В таблице пока никого. Сыграйте партию — и вы первый.",
                color = M3.OnSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (view.stale) OfflineNote(view.syncedAt, onRetry, divider = true)
        // Строки, видные сразу, въезжают по очереди сверху вниз. Те, до
        // которых долистали позже, — сразу, без очереди, и только один раз:
        // LazyColumn забывает строки за краем, и без этого они въезжали бы
        // заново при каждой прокрутке туда-обратно.
        val seen = remember(gen) { HashSet<String>() }
        var settled by remember(gen) { mutableStateOf(false) }
        LaunchedEffect(gen) { androidx.compose.runtime.withFrameMillis { }; settled = true }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(view.rows, key = { _, r -> r.uid }) { i, r ->
                val fresh = remember(gen, r.uid) { seen.add(r.uid) }
                val wait = if (settled) 0 else ROW_LEAD + i * ROW_STEP
                Line(i + 1, r, gen, animate = fresh, wait = wait)
            }
        }
    }
}

/**
 * Нет связи. Прежняя строчка с кнопкой сбоку на узком экране ломалась:
 * кнопке не доставалось ширины, и «Обновить» вставало столбиком по букве.
 * Теперь всё в столбец по центру — перечёркнутый значок сети, две строки
 * словами, время последнего обновления и кнопка, — а список под этим
 * отделяется чертой: он старый, и это должно быть видно.
 */
@Composable
private fun OfflineNote(syncedAt: Long, onRetry: () -> Unit, divider: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(40.dp).padding(bottom = 2.dp)) { wifiOff(M3.OnSurfaceVariant) }
        Text(
            "Нет соединения с интернетом",
            color = M3.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Ваш прогресс синхронизируется при подключении",
            color = M3.OnSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (syncedAt > 0L) {
            Text(
                "Список обновлён от " + clockOf(syncedAt),
                color = M3.Outline,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                style = tabular(),
                // пустая строка между «синхронизируется» и временем
                modifier = Modifier.padding(top = 17.dp),
            )
        }
        TextButton(onClick = onRetry) {
            Text("Обновить", color = M3.Primary, fontWeight = FontWeight.Medium)
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp)
                    .height(1.dp)
                    .background(M3.OutlineVariant),
            )
        }
    }
}

/** Часы последнего обновления: 17:45, а если это было не сегодня — с датой. */
private fun clockOf(ms: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    val day = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = day.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        day.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return time
    return SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(ms))
}

/** Перечёркнутая сеть: три дуги, точка и косая черта поверх. */
private fun DrawScope.wifiOff(c: Color) {
    val k = size.minDimension / 24f
    val stroke = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // дуги идут снизу вверх, каждая шире предыдущей
    for ((i, r) in listOf(4f, 8.5f, 13f).withIndex()) {
        val open = 46f - i * 3f
        drawArc(
            color = c,
            startAngle = 180f + open,
            sweepAngle = 180f - open * 2f,
            useCenter = false,
            topLeft = Offset((12f - r) * k, (19f - r) * k),
            size = androidx.compose.ui.geometry.Size(r * 2f * k, r * 2f * k),
            style = stroke,
        )
    }
    drawCircle(c, radius = 1.3f * k, center = Offset(12f * k, 19f * k))
    // черта поверх: сначала прорезь цветом окна, потом сама линия
    drawLine(M3.SurfaceContainer, Offset(4f * k, 3.4f * k), Offset(21f * k, 20.4f * k), strokeWidth = 4.6f * k, cap = StrokeCap.Round)
    drawLine(c, Offset(4.6f * k, 4f * k), Offset(20.4f * k, 19.8f * k), strokeWidth = 2f * k, cap = StrokeCap.Round)
}

/** Строки ждут, пока въедет само окно, и дальше идут с таким шагом. */
private const val ROW_LEAD = 240
private const val ROW_STEP = 45

/**
 * Строка таблицы. Появляется так: выезжает слева на своё место, а мягкая
 * граница прозрачности бежит по ней слева направо — сначала проступает
 * место, потом ник, последними очки. Очки при этом набегают от нуля по
 * той же кривой expo, что и счёт в игре.
 */
@Composable
private fun Line(rank: Int, row: BoardRow, gen: Any, animate: Boolean, wait: Int) {
    val slide = remember(gen) { Animatable(if (animate) 0f else 1f) }
    val count = remember(gen) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(gen) {
        if (!animate) return@LaunchedEffect
        launch { slide.animateTo(1f, tween(700, delayMillis = wait, easing = Expo)) }
        count.animateTo(1f, tween(1400, delayMillis = wait + 120, easing = Expo))
    }
    val p = slide.value
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = -32.dp.toPx() * (1f - p)
                // маска прозрачности должна резать строку целиком, с фоном
                compositingStrategy = if (p < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                if (p < 1f) {
                    val soft = size.width * 0.45f
                    val edge = -soft / 2f + (size.width + soft) * p
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Black, Color.Transparent),
                            startX = edge - soft / 2f,
                            endX = edge + soft / 2f,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (row.me) M3.SecondaryContainer else M3.SurfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            color = Medal.getOrNull(rank - 1) ?: M3.OnSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Medium,
            style = tabular(),
            modifier = Modifier.width(32.dp),
        )
        NickText(row.name, if (row.me) M3.OnSecondaryContainer else M3.OnSurface)
        Text(
            formatRu(row.value.tally(count.value)),
            color = if (row.me) M3.OnSecondaryContainer else M3.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            style = tabular(),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * Карточка места в главном меню — там, где раньше выбирали стартовый
 * уровень. Показывает, какой ты в таблице и сколько очков до ближайшего
 * соперника сверху; лидеру — наоборот, его отрыв от второго. Разрыв
 * набегает от нуля по той же кривой expo, что и очки в самой таблице.
 * Тап открывает таблицу.
 *
 * Карточка стоит всегда, даже когда сказать нечего (нет ника, нет сети,
 * нет очков): иначе меню дёргалось бы по высоте от захода к заходу.
 */
@Composable
fun StandingCard(large: Boolean, standing: Standing, onClick: () -> Unit) {
    val placed = standing as? Standing.Placed
    val rank = placed?.rank ?: 0L
    val title = if (placed == null) "Таблица лидеров" else "$rank-е место в таблице"
    val gap = placed?.gap ?: 0L
    val count = remember(gap) { Animatable(0f) }
    LaunchedEffect(gap) { count.animateTo(1f, tween(1400, delayMillis = 260, easing = Expo)) }
    // ник всегда стоит подлежащим: «до Кот Василий» по-русски не скажешь,
    // а склонять чужие ники некому
    val who = placed?.rival
    // склонение — по тому числу, что сейчас на экране: пока разрыв набегает,
    // оно меняется вместе с ним
    val shown = gap.tally(count.value)
    val sum = formatRu(shown) + " " + pluralRu(shown, "очко", "очка", "очков")
    // когда соперник есть, подпись начинается с его ника — и он выглядит
    // ровно так же, как в самой таблице: радуга разработчику, фигурка другу
    val tail = when {
        gap <= 0L -> " вровень с вами"
        placed?.leader == true -> " позади на $sum"
        else -> " впереди на $sum"
    }
    val plain = when {
        standing is Standing.NotJoined -> "Выберите ник — и вы в списке"
        standing is Standing.NoScore -> "Сыграйте партию — и вы в списке"
        standing is Standing.Offline -> "Нет связи с сервером"
        placed == null -> "Смотрим ваше место…"
        who == null -> "В таблице пока больше никого"
        else -> null
    }
    val devBrush = if (who != null && isDevNick(who)) rainbowBrush() else null
    val note = buildAnnotatedString {
        if (plain != null) {
            withStyle(SpanStyle(color = M3.OnSurfaceVariant)) { append(plain) }
            return@buildAnnotatedString
        }
        // у SpanStyle цвет и кисть — разные конструкторы, вместе их не задать
        val nick = if (devBrush != null) {
            SpanStyle(brush = devBrush, fontWeight = FontWeight.Medium)
        } else {
            SpanStyle(color = M3.OnSurface, fontWeight = FontWeight.Medium)
        }
        withStyle(nick) { append(who!!) }
        withStyle(SpanStyle(color = M3.OnSurfaceVariant)) { append(tail) }
    }
    Row(
        Modifier
            // карточке тесно в отступах меню, а подпись в ней длинная:
            // разрешаем ей выйти за них и встать вровень с краем окна
            .wider(20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(M3.SurfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (large) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(if (large) 24.dp else 20.dp)) {
            cup(Medal.getOrNull((rank - 1).toInt()) ?: M3.OnSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = M3.OnSurface,
                fontSize = if (large) 15.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (who != null && isFriendNick(who)) PieceBadge(if (large) 18.dp else 16.dp)
                // подпись не переносится и не обрезается: не влезла — мельчает
                FitText(
                    note,
                    maxSize = if (large) 13.sp else 12.sp,
                    minSize = 9.sp,
                )
            }
        }
        // стрелка: карточка нажимается и ведёт в таблицу
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val k = size.minDimension / 24f
            drawPath(
                Path().apply {
                    moveTo(9f * k, 5f * k)
                    lineTo(16f * k, 12f * k)
                    lineTo(9f * k, 19f * k)
                },
                M3.Outline,
                style = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Значок таблицы лидеров в углу меню: кубок теми же линиями, что и остальные. */
@Composable
fun BoardButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) { cup(M3.OnSurfaceVariant) }
    }
}

/** Сам рисунок кубка: им пользуются и кнопка в углу, и карточка места. */
private fun DrawScope.cup(c: Color) {
    val k = size.minDimension / 24f
    val stroke = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // чаша: верхний край и полукруг снизу
    drawPath(
        Path().apply {
            moveTo(7f * k, 4f * k)
            lineTo(17f * k, 4f * k)
            lineTo(17f * k, 9f * k)
            arcTo(Rect(7f * k, 4f * k, 17f * k, 14f * k), 0f, 180f, false)
            close()
        },
        c, style = stroke,
    )
    // ручки
    drawPath(
        Path().apply {
            moveTo(7f * k, 6f * k)
            cubicTo(3f * k, 6f * k, 3.5f * k, 11f * k, 8.2f * k, 11.5f * k)
            moveTo(17f * k, 6f * k)
            cubicTo(21f * k, 6f * k, 20.5f * k, 11f * k, 15.8f * k, 11.5f * k)
        },
        c, style = stroke,
    )
    // ножка и подставка
    drawPath(
        Path().apply {
            moveTo(12f * k, 14f * k)
            lineTo(12f * k, 18f * k)
            moveTo(8.5f * k, 20f * k)
            lineTo(15.5f * k, 20f * k)
        },
        c, style = stroke,
    )
}
