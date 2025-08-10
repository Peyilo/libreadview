package org.peyilo.readview.loader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.peyilo.libreadview.data.Book
import org.peyilo.libreadview.data.Chapter
import org.peyilo.libreadview.loader.BookLoader

class BiqugeBookLoader(val bookId: Long): BookLoader {

    var timeout = 20_000

    companion object {
        private const val BASE_URL = "https://www.yuyouku.com/"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari"
    }

    override fun initToc(): Book {
        val url = "$BASE_URL/book/$bookId/"
        val document = fetch(url)
        val bookTitle = document.select("article h1").first()!!.ownText().trim()
        val book = Book(bookTitle)
        book.id = bookId
        document.select("#chapters-list li a").forEach {
            book.addBookNode(Chapter(it.text()).apply {
                what = it.attr("href")
            })
        }
        return book
    }

    override fun loadChap(chapter: Chapter): Chapter {
        val url = "$BASE_URL${chapter.what}"
        val document = fetch(url)
        val txtContent = document.select("#txtContent")
        // 文本净化操作
        txtContent.select("div.gad2").remove()      // 删除无用的标签
        // 选中 #txtContent 里的所有 <p> 标签，并去掉标签外壳
        txtContent.select("#txtContent p").unwrap()
        val html = txtContent.html()
        val splits = html.split("<br>")
        splits.map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { chapter.addParagraph(it) }
        return chapter
    }

    // —— 工具方法 ——
    private fun fetch(url: String): Document =
        Jsoup.connect(url)
            .userAgent(UA)
            .timeout(timeout)
            .get()

}
