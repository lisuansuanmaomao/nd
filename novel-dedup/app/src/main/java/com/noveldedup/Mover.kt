package com.noveldedup

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把旧版本文件"移动"到 root/_重复待删/ 下面，并保持原来的相对目录结构。
 *
 * 为什么不是直接删：误判可以反悔。这个文件夹原样保留了层级，
 * 加上一份《_整理记录.txt》，随时能把文件放回原位。
 */
object Mover {

    data class Result(val moved: Int, val failed: Int, val log: File?)

    fun execute(root: File, files: List<File>, onProgress: (Int, Int) -> Unit): Result {
        val q = File(root, Scanner.QUARANTINE)
        if (!q.exists() && !q.mkdirs()) {
            return Result(0, files.size, null)
        }

        val rootPath = root.absolutePath
        val lines = ArrayList<String>()
        var moved = 0
        var failed = 0

        for ((i, src) in files.withIndex()) {
            val rel = src.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar)
            val dest = uniqueTarget(File(q, rel))
            dest.parentFile?.mkdirs()

            val ok = try {
                if (src.renameTo(dest)) {
                    true
                } else {
                    // 跨分区时 renameTo 会失败，退化成"复制 + 删原文件"
                    src.copyTo(dest, overwrite = false)
                    src.delete()
                }
            } catch (e: Exception) {
                false
            }

            if (ok) {
                moved++
                lines.add(src.absolutePath + "\t->\t" + dest.absolutePath)
            } else {
                failed++
                lines.add("[失败]\t" + src.absolutePath)
            }
            onProgress(i + 1, files.size)
        }

        val log = File(q, "_整理记录.txt")
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            log.writeText(
                buildString {
                    append("整理时间: ").append(stamp).append('\n')
                    append("根目录  : ").append(rootPath).append('\n')
                    append("成功移动: ").append(moved).append(" 个，失败: ").append(failed).append(" 个\n")
                    append("撤销方法: 把下面每行左边「原路径」的文件，从右边移回左边即可。\n")
                    append("-".repeat(70)).append('\n')
                    for (l in lines) append(l).append('\n')
                },
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            return Result(moved, failed, null)
        }
        return Result(moved, failed, log)
    }

    /** 目标已存在时加 __1 __2 后缀，绝不覆盖任何东西 */
    private fun uniqueTarget(f: File): File {
        if (!f.exists()) return f
        val base = f.nameWithoutExtension
        val ext = f.extension
        var i = 1
        while (true) {
            val n = if (ext.isEmpty()) base + "__" + i else base + "__" + i + "." + ext
            val c = File(f.parentFile, n)
            if (!c.exists()) return c
            i++
        }
    }
}
