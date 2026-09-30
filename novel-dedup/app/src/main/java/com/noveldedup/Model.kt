package com.noveldedup

import java.io.File

/** 从文件名里解析出来的信息 */
data class ParsedName(
    val title: String,
    val titleKey: String,
    val author: String,
    val authorKey: String,
    val chap: Int,
    val extra: Int,
    val done: Boolean,
    val tags: List<String>
)

/** 一个 txt 文件 + 它的解析结果 */
class BookFile(val file: File, val parsed: ParsedName) {
    val name: String get() = file.name
    val size: Long get() = file.length()
    val path: String get() = file.absolutePath
    val title: String get() = parsed.title
    val titleKey: String get() = parsed.titleKey
    val author: String get() = parsed.author
    val authorKey: String get() = parsed.authorKey
    val chap: Int get() = parsed.chap
    val extra: Int get() = parsed.extra
    val done: Boolean get() = parsed.done
}

/** 同一本书（书名 + 作者）的一组文件 */
class Group(
    val title: String,
    val author: String,
    val titleKey: String,
    val authorKey: String,
    val members: MutableList<BookFile>
) {
    var keep: BookFile? = null
    val losers = ArrayList<BookFile>()
    val pending = ArrayList<BookFile>()
    var reason: String = ""
    var uncertain: Boolean = false
    var mergeNote: String = ""
    /** 界面上是否勾选：勾选了才会真的移动 */
    var selected: Boolean = true
}
