package org.peyilo.libreadview.pagination

class DirectMapChapIndexer(val chapCount: Int) {

    private val pageCounts = IntArray(chapCount + 1) // 1-based
    private var pageToChapMap = IntArray(0)
    private var chapStartPage = IntArray(chapCount + 1)
    private var totalPages = 0
    private var dirty = true

    /**
     * 获取全部page数量
     */
    fun getAllChapPageCount(): Int {
        rebuildIfDirty()
        return totalPages
    }

    /**
     * 更新第 [chapIndex] 章的页数
     * @param chapIndex 从1开始
     * @param newPageCount
     */
    fun updateChapPageCount(chapIndex: Int, newPageCount: Int) {
        require(chapIndex in 1..chapCount)
        if (pageCounts[chapIndex] != newPageCount) {
            pageCounts[chapIndex] = newPageCount
            dirty = true
        }
    }

    /**
     * 获取第 [chapIndex] 章在全局 page 列表中的范围（1-based）
     * @return 如1..20，表示从第一页到第二十页都是第chapIndex章节的内容
     */
    fun getChapPageRange(chapIndex: Int): IntRange {
        rebuildIfDirty()
        val start = chapStartPage[chapIndex] + 1
        val end = start + pageCounts[chapIndex] - 1
        return start..end
    }

    /**
     * 根据 pageIndex（全局）查找所属章节和章节内页码（两者均从 1 开始）
     * @param pageIndex 从1开始
     * @return Pair(chapterIndex, pageInChapter)
     */
    fun findChapForPage(pageIndex: Int): Pair<Int, Int> {
        rebuildIfDirty()
        require(pageIndex in 1..totalPages)
        val chapter = pageToChapMap[pageIndex - 1]
        val chapterStart = chapStartPage[chapter]
        return Pair(chapter, pageIndex - chapterStart)
    }

    /**
     * 获取指定章节的page数量
     * @param chapIndex 从1开始
     */
    fun getChapPageCount(chapIndex: Int): Int {
        require(chapIndex in 1..chapCount)
        return pageCounts[chapIndex]
    }

    fun rebuildIfDirty() {
        if (!dirty) return

        chapStartPage[0] = 0
        for (i in 1..chapCount) {
            chapStartPage[i] = chapStartPage[i - 1] + pageCounts[i - 1]
        }
        totalPages = chapStartPage[chapCount] + pageCounts[chapCount]

        pageToChapMap = IntArray(totalPages)
        for (i in 1..chapCount) {
            val start = chapStartPage[i]
            val end = start + pageCounts[i]
            for (j in start until end) {
                pageToChapMap[j] = i
            }
        }

        dirty = false
    }
}