package org.peyilo.libreadview.data

import android.os.Build
import android.os.Parcel
import android.os.Parcelable

// 兼容读取：API 33+ 用新签名，旧版走 deprecated 并做安全转换
private fun <T : Parcelable> Parcel.readParcelableCompat(
    loader: ClassLoader?,
    clazz: Class<T>
): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        readParcelable(loader, clazz)
    } else {
        @Suppress("DEPRECATION")
        readParcelable(loader) as? T
    }
}

/**
 * 携带额外的数据
 */
open class AdditionalData(): Parcelable {

    var id: Long = 0L
    var what: String? = null
    var obj: Parcelable? = null

    constructor(source: Parcel) : this() {
        id = source.readLong()
        what = source.readString()
        obj = source.readParcelableCompat(
            AdditionalData::class.java.classLoader,
            Parcelable::class.java
        )
    }

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeLong(id)
        writeString(what)
        writeParcelable(obj, flags)
    }

    // 如果 obj 里有 FileDescriptor，需要把标记往外传
    override fun describeContents(): Int {
        return if (((obj?.describeContents() ?: 0) and Parcelable.CONTENTS_FILE_DESCRIPTOR) != 0)
            Parcelable.CONTENTS_FILE_DESCRIPTOR
        else 0
    }

    companion object CREATOR: Parcelable.Creator<AdditionalData> {
        override fun createFromParcel(source: Parcel) = AdditionalData(source)
        override fun newArray(size: Int) = arrayOfNulls<AdditionalData>(size)
    }

}

/**
 *  Component: 所有元素的共同接口
 */
sealed interface BookContent {
    val title: String
    fun formatString(indent: String = ""): String  // 增加缩进参数

    fun isTitleEmpty(): Boolean {
        return title.isEmpty()
    }
}

sealed interface BookNode: BookContent, Parcelable

/**
 * Leaf: 叶子节点 - Chapter（章节）
 */
data class Chapter(
    override val title: String,
    private val paragraphs: MutableList<String> = mutableListOf()               // 章节中的段落
) : BookNode, AdditionalData() {

    // 段落数量
    val paragraphCount: Int get() = paragraphs.size

    private constructor(parcel: Parcel) : this(
        title = parcel.readString().orEmpty(),
        paragraphs = parcel.createStringArrayList() ?: mutableListOf()
    ) {
        // 在这里手动读取父类字段
        id = parcel.readLong()
        what = parcel.readString()
        obj = parcel.readParcelableCompat(
            AdditionalData::class.java.classLoader,
            Parcelable::class.java
        )
    }

    fun clearParagraph() {
        paragraphs.clear()
    }

    fun addParagraph(paragraph: String) {
        paragraphs.add(paragraph)
    }

    /**
     * @param paraIndex 段落的序号，从0开始
     */
    fun getParagraph(paraIndex: Int): String {
        return paragraphs[paraIndex]
    }

    override fun formatString(indent: String): String {
        return "$indent${if(isTitleEmpty()) "No title chapter" else title}\n" + paragraphs.joinToString("\n") { "$indent    $it" }
    }

    override fun toString(): String = formatString("")

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeStringList(paragraphs)
        // 写父类
        super.writeToParcel(dest, flags)
    }

    override fun describeContents(): Int = super.describeContents()

    companion object CREATOR: Parcelable.Creator<Chapter> {
        override fun createFromParcel(parcel: Parcel) = Chapter(parcel)
        override fun newArray(size: Int) = arrayOfNulls<Chapter>(size)
    }
}

/**
 * Composite: 组合节点 - Volume（卷）
 */
data class Volume(
    override val title: String,
    private val chapters: MutableList<Chapter> = mutableListOf()                // 一个卷包含多个章节
) : BookNode, AdditionalData() {
    override fun formatString(indent: String): String {
        val indentForChildren = "$indent    "  // 子元素多一个缩进
        val chaptersPrint = chapters.joinToString("\n\n") { it.formatString(indentForChildren) }
        return "$indent${if(isTitleEmpty()) "No title volume" else title}\n$chaptersPrint"
    }

    private constructor(parcel: Parcel) : this(
        title = parcel.readString().orEmpty(),
        chapters = parcel.createTypedArrayList(Chapter.CREATOR)?.toMutableList() ?: mutableListOf()
    ) {
        // 在这里手动读取父类字段
        id = parcel.readLong()
        what = parcel.readString()
        obj = parcel.readParcelableCompat(
            AdditionalData::class.java.classLoader,
            Parcelable::class.java
        )
    }

    override fun toString(): String = formatString("")

    val chapCount = chapters.size

    fun addChapter(chapter: Chapter) {
        chapters.add(chapter)
    }

    fun getChapter(chapterIndex: Int): Chapter {
        return chapters[chapterIndex]
    }

    fun clearChapter() {
        chapters.clear()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)            // 写父类
        dest.writeString(title)               // 写子类
        dest.writeTypedList(chapters)         // 写子类的列表（每个 Chapter 自带 CREATOR）
    }

    override fun describeContents(): Int = super.describeContents()

    companion object CREATOR: Parcelable.Creator<Volume> {
        override fun createFromParcel(parcel: Parcel) = Volume(parcel)
        override fun newArray(size: Int) = arrayOfNulls<Volume>(size)
    }
}

/**
 * Composite: 顶层组合节点 - Book（书籍）
 */
data class Book(
    override val title: String,
    private val bookNodes: MutableList<BookNode> = mutableListOf()               // 书籍包含多个卷或者章节(卷和章节都可以作为直接子节点)
) : BookContent, AdditionalData() {
    private var chapCountDirty = true
    private var allChaptersDirty = true

    // 将所有章节平铺到一个列表中，以避免getChap(chapIndex)会遍历全部章节
    private var allChapters: List<Chapter>? = null
        get() {
            if (allChaptersDirty) {
                field = rebuildAllChapters()
            }
            if (field == null) {
                throw IllegalStateException("Book.allChapters is null.")
            }
            return field
        }

    /**
     * 章节数量
     */
    var chapCount: Int = 0
        private set
        get() {
            if (chapCountDirty) {                        // 如果数据脏了，就重新计算chapCount
                field = recomputeChapCount()
            }
            return field
        }

    private constructor(parcel: Parcel) : this(
        // —— 子类字段 ——（读）
        title = parcel.readString().orEmpty(),
        bookNodes = MutableList(parcel.readInt()) {
            when (parcel.readInt()) { // 类型标签
                1 -> {
                    val chapter = parcel.readParcelableCompat(Chapter::class.java.classLoader, Chapter::class.java)
                    chapter!!
                }
                2 -> {
                    val volume = parcel.readParcelableCompat(Volume::class.java.classLoader, Volume::class.java)
                    volume!!
                }
                else -> error("Unknown BookNode type tag")
            }
        }
    ) {
        // —— 父类字段 ——（读）
        id = parcel.readLong()
        what = parcel.readString()
        obj = parcel.readParcelableCompat(
            AdditionalData::class.java.classLoader,
            Parcelable::class.java
        )
    }


    private fun recomputeChapCount(): Int {
        var count = 0
        bookNodes.forEach { node ->
            count += when(node) {
                is Volume -> node.chapCount
                is Chapter -> 1
            }
        }
        chapCountDirty = false
        return count
    }

    private fun rebuildAllChapters(): List<Chapter> {
        val res = bookNodes.flatMap { node ->
            when (node) {
                is Chapter -> listOf(node) // 直接返回章节
                is Volume -> {
                    // 展开卷中的所有章节
                    val list = mutableListOf<Chapter>()
                    for (i in 0 until node.chapCount) {
                        list.add(node.getChapter(i))
                    }
                    list
                }
            }
        }
        allChaptersDirty = false
        return res
    }

    override fun formatString(indent: String): String {
        val indentForChildren = "$indent    "   // 子元素多一个缩进
        val contentPrint = bookNodes.joinToString("\n\n") { it.formatString(indentForChildren) }
        return "$indent${if(isTitleEmpty()) "No title book" else title}\n$contentPrint"
    }

    override fun toString(): String = formatString("")

    fun getBookNode(nodeIndex: Int): BookNode {
        return bookNodes[nodeIndex]
    }

    fun addBookNode(bookNode: BookNode) {
        bookNodes.add(bookNode)
    }

    fun clearBookNode() {
        bookNodes.clear()
    }

    /**
     * 是否包含分卷
     */
    fun hasVolume(): Boolean {
        return bookNodes.any { it is Volume }
    }

    /**
     * 在Book对象实例化以后，如果book中添加了新的chap、volume或者直接往volume引用中添加了新的chap，都应该调用这个函数，标记数据已经脏了，
     * 从而需要重新计算内部数据表示，从而保证book.chapCount、book.getChap(chapIndex)获取到的数据是正确的。
     * 如果Book对象实例化以后，book内部数据没有任何改变，无需调用这个函数。
     */
    fun invalidate() {
        chapCountDirty = true
        allChaptersDirty = true
    }

    /**
     * 根据给定的 index 获取章节，index 从 0 开始
     */
    fun getChap(index: Int): Chapter {
        if (index < 0 || index >= chapCount) {
            throw IndexOutOfBoundsException("Chapter at index $index not found, the chapCount of this book is $chapCount")
        }
        return allChapters!![index]
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        // —— 子类字段 ——（写）
        dest.writeString(title)
        dest.writeInt(bookNodes.size)
        for (node in bookNodes) {
            when (node) {
                is Chapter -> {
                    dest.writeInt(1)              // 类型标签
                    dest.writeParcelable(node, flags)
                }
                is Volume -> {
                    dest.writeInt(2)
                    dest.writeParcelable(node, flags)
                }
            }
        }
        // —— 父类字段 ——（写，顺序与上面的读保持一致）——
        dest.writeLong(id)
        dest.writeString(what)
        dest.writeParcelable(obj, flags)
    }

    override fun describeContents(): Int {
        // 若父类的 obj 可能包含 FileDescriptor，需把标记带出来
        val fd = (obj?.describeContents() ?: 0) and Parcelable.CONTENTS_FILE_DESCRIPTOR
        return if (fd != 0) Parcelable.CONTENTS_FILE_DESCRIPTOR else 0
    }

    companion object CREATOR: Parcelable.Creator<Book> {
        override fun createFromParcel(parcel: Parcel) = Book(parcel)
        override fun newArray(size: Int) = arrayOfNulls<Book>(size)
    }
}
