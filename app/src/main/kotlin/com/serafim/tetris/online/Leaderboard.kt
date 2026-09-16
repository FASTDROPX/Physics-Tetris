package com.serafim.tetris.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.serafim.tetris.Prefs
import com.serafim.tetris.game.Nick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Колонка, по которой строится таблица. */
enum class BoardKind(val field: String, val title: String) {
    TOTAL("total", "Всего очков"),
    BEST("best", "Рекорд партии"),
}

data class BoardRow(val uid: String, val name: String, val value: Long, val me: Boolean)

/**
 * Ник разработчика игры. Он один переливается радугой и закреплён за ним:
 * взять его другим игроком нельзя — ни этими буквами, ни другой их
 * высотой.
 */
const val DEV_NICK = "FastdropX"

/** Тот ли это ник, что переливается радугой. */
fun isDevNick(name: String): Boolean = Nick.same(name, DEV_NICK)

/**
 * Ник друга разработчика: перед ним бежит фигурка из стакана, каждую
 * секунду новая. Как и радуга, достаётся ровно одному нику.
 */
const val FRIEND_NICK = "wor1356"

/** Тот ли это ник, перед которым стоит меняющаяся фигурка. */
fun isFriendNick(name: String): Boolean = Nick.same(name, FRIEND_NICK)

/** Почему ник не взялся: [key] — к какому набору букв относится жалоба. */
data class JoinIssue(val key: String, val message: String)

/** Что показывает окно таблицы. */
sealed interface BoardView {
    data object Loading : BoardView

    /**
     * [myRank] — место игрока, если он в таблице; [stale] — сети нет, и
     * список взят из того, что сохранилось на телефоне с прошлого раза.
     */
    data class Ready(
        val rows: List<BoardRow>,
        val myRank: Long?,
        val myValue: Long,
        val stale: Boolean,
        /** Когда список в последний раз приходил с сервера; 0 — никогда. */
        val syncedAt: Long = 0L,
    ) : BoardView

    /** [offline] — дело в связи, а не в настройке проекта или правилах. */
    data class Failed(val message: String, val offline: Boolean = false) : BoardView
}

/**
 * Место игрока в таблице и ближайший соперник — для карточки в меню.
 * Считается по «Всего очков»: это главная вкладка таблицы.
 */
sealed interface Standing {
    /** Ещё не спрашивали. */
    data object Unknown : Standing

    /** Спрашивали, но сети не было. */
    data object Offline : Standing

    /** Ника нет — игрок в таблице не участвует. */
    data object NotJoined : Standing

    /** Очков нет: после сброса или до первой партии. */
    data object NoScore : Standing

    /**
     * [gap] — сколько очков до [rival]; у лидера ([leader]) это, наоборот,
     * отрыв от второго. [rival] пуст, когда в таблице больше никого.
     */
    data class Placed(
        val rank: Long,
        val rival: String?,
        val gap: Long,
        val leader: Boolean,
    ) : Standing
}

/**
 * Онлайн-таблица лидеров на Firebase: анонимный вход и коллекция
 * `leaderboard` в Firestore, по документу на игрока с ключом по его uid.
 * В документе ник, рекорд одной партии и сумма очков за всё время.
 *
 * В таблицу попадает только тот, кто сам выбрал ник: без ника ничего не
 * уходит. Отправляются абсолютные числа, а не прибавки, поэтому
 * потерянная отправка (не было сети при входе) исправляется следующей.
 * Запись и удаление Firestore держит в очереди на телефоне и досылает,
 * когда появится сеть, — ждать их не нужно.
 *
 * Firebase трогается только при первом обращении: пока окно таблицы не
 * открывали и ника нет, модуль не делает ничего.
 */
class Leaderboard(private val prefs: Prefs, private val scope: CoroutineScope) {

    var nick by mutableStateOf(prefs.nick)
        private set
    var kind by mutableStateOf(BoardKind.TOTAL)
        private set
    var view by mutableStateOf<BoardView>(BoardView.Loading)
        private set

    /** Место игрока для карточки в меню. */
    var standing by mutableStateOf<Standing>(Standing.Unknown)
        private set

    /** Идёт проверка ника: форма ждёт ответа сервера. */
    var joining by mutableStateOf(false)
        private set

    /** Ник не взялся — почему. */
    var issue by mutableStateOf<JoinIssue?>(null)
        private set

    private var standingFor = -1L
    private var standingAt = 0L

    private val auth get() = FirebaseAuth.getInstance()
    private val col get() = FirebaseFirestore.getInstance().collection(COLLECTION)
    private val nicks get() = FirebaseFirestore.getInstance().collection(NICKS)

    /** Ключ ника, бронь которого уже пытались поставить за этот запуск. */
    private var claimed = ""

    /** Последний ответ по каждой колонке и когда он получен. */
    private val cache = HashMap<BoardKind, Pair<Long, BoardView.Ready>>()

    val joined: Boolean get() = nick.isNotEmpty()

    // ---------- для окна ----------

    fun open() = load(kind, force = false)

    fun select(k: BoardKind) {
        if (k == kind) return
        kind = k
        load(k, force = false)
    }

    fun retry() = load(kind, force = true)

    /**
     * Вступить в таблицу или сменить ник. Сначала ник бронируется — занятый
     * не пройдёт, — и только потом уходят числа. Пока идёт проверка, форма
     * ждёт: [joining]. Отказ виден в [issue], и ник на телефоне не меняется.
     */
    fun join(raw: String, best: Long, total: Long) {
        val clean = Nick.clean(raw)
        if (!Nick.isValid(clean) || joining) return
        joining = true
        issue = null
        scope.launch {
            val problem = runCatching { withTimeout(CLAIM_MS) { claim(clean) } }
                .getOrElse { explain(it) }
            if (problem != null) {
                issue = JoinIssue(Nick.key(clean), problem)
                joining = false
                return@launch
            }
            nick = clean
            prefs.nick = clean
            // ждём, пока запись дойдёт до сервера, — иначе свежий список
            // ещё не увидит игрока; без сети ждём недолго
            val ok = runCatching { push(best, total, sync = true) }
            joining = false
            if (ok.isFailure) {
                view = BoardView.Failed(explain(ok.exceptionOrNull()!!))
            } else {
                load(kind, force = true)
                refreshStanding(total, force = true)
            }
        }
    }

    // ---------- из игры ----------

    /** Конец партии: свежие рекорд и сумма. Без ника — ничего. */
    fun submit(best: Long, total: Long) {
        if (!joined) return
        scope.launch { runCatching { push(best, total) } }
    }

    /** Сброс статистики: строка игрока уходит из таблицы. Ник остаётся. */
    fun remove() {
        cache.clear()
        standing = Standing.NoScore
        standingFor = -1L
        scope.launch { runCatching { erase() } }
    }

    /**
     * Место игрока и ближайший соперник — для карточки в меню. Считается от
     * [total] с телефона, а не от того, что уже долетело до сервера: сразу
     * после партии это одно и то же число, но местное известно точно.
     * Спрашивается при заходе в меню, не чаще раза в полминуты на одно и то
     * же число очков; без сети остаётся то, что показывали в прошлый раз.
     */
    fun refreshStanding(total: Long, force: Boolean = false) {
        if (!joined) { standing = Standing.NotJoined; return }
        if (total <= 0L) { standing = Standing.NoScore; return }
        val now = System.currentTimeMillis()
        val fresh = standing is Standing.Placed && standingFor == total && now - standingAt < STANDING_MS
        if (!force && fresh) return
        scope.launch {
            runCatching { place(total) }
                .onSuccess {
                    standing = it
                    standingFor = total
                    standingAt = System.currentTimeMillis()
                }
                .onFailure { if (standing !is Standing.Placed) standing = Standing.Offline }
        }
    }

    /**
     * Сколько игроков выше и кто из них ближайший. Своя строка на сервере
     * может быть устаревшей и оказаться «выше» местного счёта — тогда она не
     * соперник и в место не считается.
     */
    suspend fun place(total: Long): Standing.Placed {
        val me = uid()
        val f = BoardKind.TOTAL.field
        val above = col.whereGreaterThan(f, total).count().get(AggregateSource.SERVER).await().count
        val up = col.whereGreaterThan(f, total)
            .orderBy(f, Query.Direction.ASCENDING)
            .limit(2)
            .get(Source.SERVER).await()
        val mineAbove = up.documents.any { it.id == me }
        val rank = (above + 1 - (if (mineAbove) 1 else 0)).coerceAtLeast(1L)
        if (rank > 1) {
            val rival = up.documents.first { it.id != me }
            return Standing.Placed(rank, rival.getString("name"), (rival.getLong(f) ?: 0L) - total, leader = false)
        }
        // лидер: интересен отрыв от второго
        val down = col.whereLessThan(f, total)
            .orderBy(f, Query.Direction.DESCENDING)
            .limit(2)
            .get(Source.SERVER).await()
        val second = down.documents.firstOrNull { it.id != me }
        return Standing.Placed(
            rank = 1,
            rival = second?.getString("name"),
            gap = second?.let { total - (it.getLong(f) ?: 0L) } ?: 0L,
            leader = true,
        )
    }

    // ---------- занятость ников ----------

    /**
     * Проверить, свободен ли ник, и закрепить его за игроком. Возвращает
     * null, если всё вышло, иначе — что сказать игроку.
     *
     * Занятость живёт отдельной коллекцией `nicks`, где ключ документа —
     * ник маленькими буквами ([Nick.key]). Создать документ поверх чужого
     * правила базы не дают, поэтому гонка двух игроков за один ник
     * решается сама собой: второй получит отказ.
     *
     * Те, кто вступил в таблицу до этой версии, брони не имеют — их ники
     * ищутся по самой таблице ([SCAN] верхних строк), а бронь им ставится
     * при первой же отправке очков ([ensureClaim]).
     */
    private suspend fun claim(clean: String): String? {
        val me = uid()
        val key = Nick.key(clean)
        val mine = Nick.key(nick)
        // свой же ник другими буквами по высоте — это не смена ника
        if (key == mine) {
            ensureClaim(key, clean, me)
            return null
        }
        val booked = runCatching { nicks.document(key).get(Source.SERVER).await() }.getOrNull()
        if (booked != null && booked.exists() && booked.getString("uid") != me) return TAKEN
        // игроки, вступившие до брони: ищем те же буквы в самой таблице
        val rows = runCatching {
            col.orderBy(BoardKind.TOTAL.field, Query.Direction.DESCENDING)
                .limit(SCAN.toLong())
                .get(Source.SERVER).await()
        }.getOrNull()
        val clash = rows?.documents?.any { Nick.same(it.getString("name") ?: "", clean) && it.id != me }
        if (clash == true) return TAKEN
        // ник разработчика закреплён за ним: без брони и без строки в
        // таблице его не берёт никто, даже если он оттуда пропал
        if (isDevNick(clean) && booked?.exists() != true) return DEV_TAKEN
        val put = runCatching { nicks.document(key).set(mapOf("uid" to me, "name" to clean)).await() }
        if (put.isFailure) {
            // либо бронь только что увели, либо правила базы ещё не
            // опубликованы; второе не повод не пускать в таблицу
            val again = runCatching { nicks.document(key).get(Source.SERVER).await() }.getOrNull()
            if (again != null && again.exists() && again.getString("uid") != me) return TAKEN
        }
        if (mine.isNotEmpty()) runCatching { nicks.document(mine).delete().await() }
        claimed = key
        return null
    }

    /**
     * Поставить бронь, если её ещё нет, — по разу за запуск. Нужно старым
     * игрокам: они выбрали ник до того, как появилась занятость, и их ник
     * закрепляется при первой же отправке очков. Отказ (бронь уже есть)
     * ничего не значит и молча забывается.
     */
    private suspend fun ensureClaim(key: String, name: String, me: String) {
        if (key.isEmpty() || claimed == key) return
        claimed = key
        runCatching { nicks.document(key).set(mapOf("uid" to me, "name" to name)).await() }
    }

    /** Снять бронь — нужно только тестам, которые за собой убирают. */
    suspend fun releaseClaim(raw: String) {
        val key = Nick.key(raw)
        if (key.isEmpty()) return
        if (claimed == key) claimed = ""
        nicks.document(key).delete().await()
    }

    // ---------- работа с базой ----------

    private fun load(k: BoardKind, force: Boolean) {
        val hit = cache[k]
        if (!force && hit != null && System.currentTimeMillis() - hit.first < CACHE_MS) {
            view = hit.second
            return
        }
        view = BoardView.Loading
        scope.launch {
            val r = runCatching { fetch(k) }
            if (k != kind) return@launch                 // пока грузилось, переключили
            view = r.fold(
                // список из памяти телефона не запоминаем: «Обновить» и
                // повторное открытие должны снова идти на сервер
                onSuccess = { if (!it.stale) cache[k] = System.currentTimeMillis() to it; it },
                onFailure = { BoardView.Failed(explain(it), offline(it)) },
            )
        }
    }

    private suspend fun uid(): String {
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid
            ?: error("Firebase не вернул пользователя")
    }

    /** Верх таблицы и место игрока, даже если он ниже верхних [TOP]. */
    suspend fun fetch(k: BoardKind): BoardView.Ready {
        val me = uid()
        val snap = col.whereGreaterThan(k.field, 0L)
            .orderBy(k.field, Query.Direction.DESCENDING)
            .limit(TOP.toLong())
            .get().await()
        val rows = snap.documents.map {
            BoardRow(it.id, it.getString("name") ?: "—", it.getLong(k.field) ?: 0L, it.id == me)
        }
        val idx = rows.indexOfFirst { it.me }
        var rank: Long? = if (idx >= 0) idx + 1L else null
        var mine = if (idx >= 0) rows[idx].value else 0L
        if (idx < 0 && joined && !snap.metadata.isFromCache) {
            runCatching {
                mine = col.document(me).get().await().getLong(k.field) ?: 0L
                if (mine > 0) {
                    val above = col.whereGreaterThan(k.field, mine).count()
                        .get(AggregateSource.SERVER).await().count
                    rank = above + 1
                }
            }
        }
        val stale = snap.metadata.isFromCache
        // время помним на телефоне: у списка из кеша Firestore своего нет
        if (!stale) prefs.boardSyncedAt = System.currentTimeMillis()
        return BoardView.Ready(rows, rank, mine, stale = stale, syncedAt = prefs.boardSyncedAt)
    }

    /**
     * Запись встаёт в очередь Firestore и уйдёт, когда будет сеть. С [sync]
     * ждём подтверждения сервера, но не дольше [SYNC_MS]: отказ правил
     * тогда всплывает ошибкой, а отсутствие сети — нет.
     */
    suspend fun push(best: Long, total: Long, sync: Boolean = false) {
        val me = uid()
        cache.clear()
        // те, кто взял ник до появления брони, закрепляют его здесь
        ensureClaim(Nick.key(nick), nick, me)
        val task = col.document(me).set(
            mapOf(
                "name" to nick,
                "best" to best,
                "total" to total,
                "updated" to FieldValue.serverTimestamp(),
            ),
        )
        if (sync) withTimeoutOrNull(SYNC_MS) { task.await() }
    }

    suspend fun erase(sync: Boolean = false) {
        val me = auth.currentUser?.uid ?: return          // не входил — нечего стирать
        val task = col.document(me).delete()
        if (sync) withTimeoutOrNull(SYNC_MS) { task.await() }
    }

    /** Пропала связь — это одно, а неверная настройка проекта — другое. */
    private fun offline(e: Throwable): Boolean = e is FirebaseNetworkException ||
        (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE)

    /** Ошибки — словами. Часть из них про настройку проекта в консоли Firebase. */
    private fun explain(e: Throwable): String = when {
        e is FirebaseNetworkException -> "Нет связи с сервером. Проверьте интернет."
        // сборка из исходников без app/google-services.json: Firebase не поднялся
        e is IllegalStateException && e.message?.contains("FirebaseApp") == true ->
            "Сборка без google-services.json — онлайн-таблица в ней не работает."
        e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "В Firebase не включён анонимный вход: Authentication → Sign-in method → Anonymous."
        // Authentication в консоли ни разу не открывали — «Get started» не нажат
        e.message?.contains("CONFIGURATION_NOT_FOUND") == true ->
            "В Firebase не включён вход: Authentication → Get started → Anonymous."
        e is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Нет связи с сервером. Проверьте интернет."
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                // либо правила, либо пропуск App Check: у второго в тексте
                // отказа всегда назван он сам
                if (e.message?.contains("App Check", ignoreCase = true) == true)
                    "Сервер не признал сборку: не пройдена проверка App Check."
                else "База отказала в доступе: в Firestore не опубликованы правила таблицы."
            FirebaseFirestoreException.Code.NOT_FOUND -> "В Firebase не создана база Firestore."
            else -> "Таблица недоступна: " + (e.message ?: e.code.name)
        }
        else -> "Таблица недоступна: " + (e.message ?: e.javaClass.simpleName)
    }

    companion object {
        const val COLLECTION = "leaderboard"

        /** Занятые ники: ключ документа — ник маленькими буквами. */
        const val NICKS = "nicks"
        const val TOP = 50

        /** Сколько строк таблицы просматривается в поисках тех же букв. */
        private const val SCAN = 200
        private const val TAKEN = "Этот ник уже занят — возьмите другой."
        private const val DEV_TAKEN = "Этот ник закреплён за разработчиком игры."
        private const val CLAIM_MS = 12_000L
        private const val CACHE_MS = 60_000L
        private const val STANDING_MS = 30_000L
        private const val SYNC_MS = 6_000L
    }
}

/** Задача Play services как приостановка — без отдельной библиотеки. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnCompleteListener { t ->
        val e = t.exception
        when {
            e != null -> c.resumeWithException(e)
            t.isCanceled -> c.cancel()
            else -> c.resume(t.result)
        }
    }
}
