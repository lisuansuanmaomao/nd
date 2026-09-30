package com.noveldedup

/**
 * 取舍规则：
 *   1. 组内只要有"完结"版 -> 完结版里保留体积最大的，其余（含连载版）全部移走。
 *   2. 没有完结版 -> 保留章数最大的；章数相同则保留体积大的。
 *   3. 读不出章数的文件 -> 绝不自动移动，列为"待确认"交给人看。
 *   4. 拿不准的组（同名多作者）-> 整组不动。
 */
object Planner {

    fun plan(groups: List<Group>) {
        for (g in groups) planOne(g)
    }

    private fun planOne(g: Group) {
        // 拿不准的组：整组不动
        if (g.uncertain) {
            g.pending.addAll(g.members)
            if (g.reason.isEmpty()) g.reason = "信息不足"
            g.selected = false
            return
        }

        if (g.members.size <= 1) {
            g.keep = g.members.firstOrNull()
            g.reason = "组内只有 1 个文件，不动"
            g.selected = false
            return
        }

        val done = g.members.filter { it.done }
        if (done.isNotEmpty()) {
            g.keep = done.maxWithOrNull(
                compareBy<BookFile> { it.size }.thenBy { it.extra }.thenBy { it.chap }
            )
            g.losers.addAll(g.members.filter { it !== g.keep })
            g.reason = "有 ${done.size} 个完结版，保留体积最大的"
            g.selected = true
            return
        }

        val numbered = g.members.filter { it.chap > 0 }
        val unnumbered = g.members.filter { it.chap == 0 }

        if (numbered.isEmpty()) {
            g.uncertain = true
            g.pending.addAll(g.members)
            g.reason = "都读不出章节数，需人工判断"
            g.selected = false
            return
        }

        g.keep = numbered.maxWithOrNull(compareBy<BookFile> { it.chap }.thenBy { it.size })
        g.losers.addAll(numbered.filter { it !== g.keep })
        g.reason = "无完结版，保留章数最大的（${g.keep?.chap} 章）"
        g.selected = true

        if (unnumbered.isNotEmpty()) {
            g.pending.addAll(unnumbered)
            g.uncertain = true
            g.reason = g.reason + "；另有 ${unnumbered.size} 个读不出章数的文件，未自动处理"
        }
    }
}
