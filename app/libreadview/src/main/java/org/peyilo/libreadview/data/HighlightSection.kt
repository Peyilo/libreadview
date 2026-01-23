package org.peyilo.libreadview.data

// 描述了一个字符在章节中的位置
data class CharacterPosition (
    val para: Int,
    val offset: Int
)

// 描述了一块需要高亮的章节部分
data class HighlightSection (
    // 章节索引
    val chapterIndex: Int,
    // 高亮部分的起始和结束位置
    val start: CharacterPosition,
    val end: CharacterPosition,
    // 高亮内容
    val quote: String,
    // 可选的笔记内容
    val note: String?,
    // 章节内容的哈希值，用于验证章节内容是否被修改
    val chapterHash: String,
)