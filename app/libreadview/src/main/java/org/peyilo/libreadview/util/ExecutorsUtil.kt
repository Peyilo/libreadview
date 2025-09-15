package org.peyilo.libreadview.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 创建一个支持 LIFO（后进先出）策略的固定线程池
 */
fun newLifoFixedThreadPool(
    nThreads: Int,
    threadFactory: ThreadFactory
): ExecutorService {
    return object : ThreadPoolExecutor(
        nThreads,
        nThreads,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingDeque()
    ) {
        private val deque: LinkedBlockingDeque<Runnable>
            get() = super.getQueue() as LinkedBlockingDeque<Runnable>

        override fun execute(command: Runnable) {
            if (isShutdown) return
            // 把任务加到队列头部，实现 LIFO
            deque.offerFirst(command)
            // 确保线程提前启动
            prestartAllCoreThreads()
        }
    }.apply {
        setThreadFactory(threadFactory)
    }
}