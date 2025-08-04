package org.peyilo.libreadview.pagination

interface ChapterPageIndexer {

    /**
     * 获取全部page数量
     */
    fun getAllChapPageCount(): Int

    /**
     * 更新第 [chapIndex] 章的页数
     * @param chapIndex 从1开始
     * @param newPageCount
     */
    fun updateChapterPageCount(chapIndex: Int, newPageCount: Int)

    /**
     * 根据 pageIndex（全局）查找所属章节和章节内页码（两者均从 1 开始）
     * @param pageIndex 从1开始
     * @return Pair(chapterIndex, pageInChapter)
     */
    fun findChapterForPage(pageIndex: Int): Pair<Int, Int>

    /**
     * 获取第 [chapIndex] 章在全局 page 列表中的范围（1-based）
     * @return 如1..20，表示从第一页到第二十页都是第chapIndex章节的内容
     */
    fun getChapterPageRange(chapIndex: Int): IntRange

}