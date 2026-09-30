package com.noveldedup

import java.io.File

/** 递归扫描一个大文件夹（里面可能有很多层小文件夹），只挑 .txt */
object Scanner {

    /** 旧版本文件会被移进这个文件夹 */
    const val QUARANTINE = "_重复待删"

    fun scan(root: File, onProgress: (Int) -> Unit): List<BookFile> {
        val out = ArrayList<BookFile>()
        val stack = ArrayDeque<File>()
        val seen = HashSet<String>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            if (!seen.add(dir.absolutePath)) continue

            val children: Array<File>? = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            }
            if (children == null) continue

            for (c in children) {
                val n = c.name
                if (c.isDirectory) {
                    // 跳过隐藏目录和上一次的回收站，避免把已经搬走的又扫一遍
                    if (n.startsWith(".") || n == QUARANTINE) continue
                    stack.addLast(c)
                } else if (n.endsWith(".txt", ignoreCase = true)) {
                    out.add(BookFile(c, NameParser.parse(n)))
                    if (out.size % 200 == 0) onProgress(out.size)
                }
            }
        }
        onProgress(out.size)
        return out
    }
}
