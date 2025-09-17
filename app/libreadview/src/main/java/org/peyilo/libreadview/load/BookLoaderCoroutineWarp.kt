package org.peyilo.libreadview.load

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter
import java.util.concurrent.atomic.AtomicInteger

class BookLoaderCoroutineWarp(val bookLoader: BookLoader) {

    suspend fun initToc(): Book = withContext(IO) {
        bookLoader.initToc()
    }

    suspend fun loadChap(chapter: Chapter): Chapter = withContext(IO) {
        bookLoader.loadChap(chapter)
    }

    /**
     *  —— 并发加载整本书的章节（限流并发度）——
     */
    suspend fun loadAllChapters(
        book: Book,
        concurrency: Int = 8,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Book = coroutineScope  {
        val sem = Semaphore(concurrency)
        val done = AtomicInteger(0)
        val total = book.chapCount

        val tasks = (0 until total).map { i ->
            val chap = book.getChap(i)
            async(IO) {
                with(sem) {
                    loadChap(chap)
                    onProgress?.invoke(done.incrementAndGet(), total)
                }
            }
        }

        tasks.awaitAll()
        book
    }

}