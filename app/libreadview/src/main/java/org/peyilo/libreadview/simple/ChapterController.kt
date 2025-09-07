package org.peyilo.libreadview.simple

import android.os.Handler
import android.os.Looper
import org.peyilo.libreadview.util.LogHelper
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ChapterController"

enum class ChapterState {
    // 初始
    UNLOADED,            // 未加载

    // 加载中 / 已完成
    LOADING,
    UNPAGINATED,         // 文本已加载，但未分页

    // 分页中 / 已完成
    PAGINATING,
    UNINFLATED,          // 分页完成，但未填充

    // 填充中 / 已完成
    INFLATING,
    DONE                 // 填充完成
}

// 会影响分页结果的“版式快照“
data class LayoutKey(val textSize: Int)

/** 页面模型 */
data class Page(val index: Int /* 以及渲染需要的数据 */)

/* ---------- 线程池与主线程执行器示例（Android） ---------- */
object Pipelines {
    // IO：磁盘/网络
    val ioExecutor: ExecutorService = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "reader-io").apply { isDaemon = true }
    }

    // CPU：分页（可用 CPU 数量）
    val cpuExecutor: ExecutorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    ) { r -> Thread(r, "reader-cpu").apply { isDaemon = true } }

    // 主线程：Android Handler
    val mainExecutor: Executor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }
}

/* ---------- 依赖接口（通过依赖注入以便替换/测试） ---------- */
fun interface Loader { @Throws(Exception::class) fun load(chapterId: Int): String }
fun interface Paginator { @Throws(Exception::class) fun paginate(text: String, key: LayoutKey): List<Page> }
fun interface Inflater { fun inflate(pages: List<Page>) /* 必须在主线程调用 */ }

class ChapterController(
    private val chapterId: Int,
    private val loader: Loader,
    private val paginator: Paginator,
    private val inflater: Inflater,
    private val ioExecutor: ExecutorService,
    private val cpuExecutor: ExecutorService,
    private val mainExecutor: Executor // Android: command -> Handler(Looper.getMainLooper()).post(command)
) {
    // 章节当前状态
    @Volatile private var state: ChapterState = ChapterState.UNLOADED

    // 最新的版式快照
    private val latestLayout = AtomicReference<LayoutKey?>(null)

    // 当前在跑的流水线任务，用于取消
    private val running: AtomicReference<Future<*>?> = AtomicReference(null)

    // 缓存：与 generation / layout 绑定，完全一致才复用
    @Volatile private var cachedText: String? = null

    @Volatile private var cachedPages: List<Page>? = null
    @Volatile private var cachedPagesKey: LayoutKey? = null

    /**
     * 加载正文：成功后推进到 UNPAGINATED；失败回退到 UNLOADED
     */
    @Synchronized fun load() {
        // 如果章节已经加载过了，就不需要重复加载
        if (state != ChapterState.UNLOADED) return
        state = ChapterState.LOADING        // 标记为加载中
        val task = ioExecutor.submit {
            try {
                val text = loader.load(chapterId)
                synchronized(this) {
                    cachedText = text
                    // 加载完成，进入“待分页”
                    state = ChapterState.UNPAGINATED
                    running.set(null)
                }
            } catch (e: Exception) {
                // 加载失败，回退状态
                synchronized(this) {
                    state = ChapterState.UNLOADED
                    running.set(null)
                }
                LogHelper.printStackTrace(TAG, e)
            }
        }
        running.set(task)
    }

    /**
     * 分页：需要已加载文本。
     * - 成功：写入 cachedPages，推进到 UNINFLATED
     * - 失败：回退到 UNPAGINATED
     */
    @Synchronized fun paginate(layout: LayoutKey) {
        // 如果章节还没加载，直接报错
        // 如果章节已经分页过了，就不需要重复分页
        if (state == ChapterState.UNLOADED || state == ChapterState.LOADING) {
            throw IllegalStateException("Chapter not loaded")
        }
        if (state != ChapterState.UNPAGINATED) return

        val text = cachedText ?: throw IllegalStateException("Text not loaded")
        latestLayout.set(layout)
        state = ChapterState.PAGINATING     // 标记为分页中

        val task = cpuExecutor.submit {
            try {
                val pages = paginator.paginate(text, layout)
                synchronized(this) {
                    cachedPages = pages
                    state = ChapterState.UNINFLATED
                    running.set(null)
                }
            } catch (e: Exception) {
                synchronized(this) {
                    // 分页失败，回退到“待分页”
                    state = ChapterState.UNPAGINATED
                    running.set(null)
                }
                LogHelper.printStackTrace(TAG, e)
            }
        }
        running.set(task)
    }

    /**
     * 填充：需要已有分页结果。
     * - 成功：推进到 DONE
     * - 失败：回退到 UNINFLATED
     */
    @Synchronized fun inflate() {
        if (state == ChapterState.UNLOADED || state == ChapterState.LOADING
            || state == ChapterState.UNPAGINATED || state == ChapterState.PAGINATING) {
            throw IllegalStateException("Chapter not inflatable state")
        }
        if (state != ChapterState.UNINFLATED) return

        val pages = cachedPages ?: throw IllegalStateException("Pages not inflatable state")
        state = ChapterState.INFLATING

        // 主线程任务无法取消；执行完成后推进到 DONE
        mainExecutor.execute {
            try {
                inflater.inflate(pages)
                synchronized(this) {
                    state = ChapterState.DONE
                }
            } catch (e: Exception) {
                synchronized(this) {
                    // 填充失败，保持“已分页未填充”
                    state = ChapterState.UNINFLATED
                }
                LogHelper.printStackTrace(TAG, e)
            }
        }
        running.set(null)
    }

}