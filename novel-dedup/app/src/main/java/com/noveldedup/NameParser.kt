package com.noveldedup

import java.text.Normalizer

/**
 * 只靠文件名识别：书名 / 作者 / 更新到第几章 / 是否完结。
 *
 * 这个文件是 proto/parser_proto.py 的逐条移植，那份 Python 脚本上跑过 15 条断言。
 * 改这里的规则时，请同步改 Python 并重跑 proto/run_tests.py。
 *
 * 支持的写法举例：
 *   [1v1 更235]《梨汁软糖（甜文）》作者：abc.txt      -> 梨汁软糖 / abc / 235章 / 连载
 *   霸道少爷爱上我 （1v1） (完结).txt                  -> 霸道少爷爱上我 / 无 / 完结
 *   [完结]春山寒 作者：江入玦明.txt                    -> 春山寒 / 江入玦明 / 完结
 *   [完结+16番外]《罪爱（np 都市 高干）》作者：九铃【补番】.txt -> 罪爱 / 九铃 / 完结 + 16番外
 *   盗墓笔记 更500 (1).txt                            -> 盗墓笔记 / 无 / 500章（副本序号被忽略）
 */
object NameParser {

    /** 出现在书名括号里的"标签"，不是书名的一部分，用于跨版本匹配 */
    private val TAG_WORDS: Set<String> = setOf(
        "1v1", "np", "双洁", "双处", "sc", "甜文", "虐文", "爽文", "宠文", "甜宠",
        "都市", "高干", "校园", "娱乐圈", "豪门", "总裁", "种田", "修仙", "玄幻",
        "言情", "耽美", "百合", "无cp", "重生", "穿书", "穿越", "快穿", "系统",
        "年下", "年上", "强强", "骨科", "追妻", "火葬场", "破镜重圆", "先婚后爱",
        "青梅竹马", "he", "be", "gb", "bg", "gl", "bl", "abo", "正剧", "轻松",
        "搞笑", "短篇", "中篇", "长篇", "免费", "首发", "独家", "精校", "精校版",
        "全本", "未删减", "慎入", "排雷", "完结", "番外", "补番", "连载", "更新中",
        "清水", "狗血", "沙雕", "无限流", "年代文", "家长里短", "女主", "男主"
    )

    /**
     * 顺序要紧："已完结" 必须排在 "完结" 前面。
     * 否则 "[已完结]" 这类结尾会被砍成 "…已"。
     */
    private val STATUS_WORDS = listOf(
        "已完结", "完结文", "完结", "全本", "完本", "连载中", "连载", "更新中", "太监"
    )

    private const val SEP_CHARS = " \t\n_—–·|/\\,，、;；:：~～-"

    /** 拼成可放进正则字符类的分隔符集合（把反斜杠转义一下） */
    private val SEP_CLASS: String = "[" + SEP_CHARS.replace("\\", "\\\\") + "]"
    private val SEP_TAIL: String = "(?:" + SEP_CLASS + ")*"

    private val RE_WS = Regex("\\s+")
    private val RE_NUM2 = Regex("^\\d{1,2}$")
    private val RE_TOKENS = Regex("[\\s,、/+&;；]+")
    private val RE_URL = Regex("(?:https?://|www\\.)[^\\s\\u4e00-\\u9fff]*", RegexOption.IGNORE_CASE)
    private val RE_LEAD_BRACKET = Regex("^\\s*[\\[【]\\s*([^\\[\\]【】]*?)\\s*[\\]】]\\s*")
    private val RE_CORNER_BRACKET = Regex("【\\s*([^【】]*?)\\s*】")
    private val RE_SQUARE_BRACKET = Regex("\\[\\s*([^\\[\\]]*?)\\s*\\]")
    private val RE_AUTHOR = Regex("作者\\s*[:：]?\\s*([^\\s()\\[\\]【】《》（）|,，、;；]+)")
    private val RE_BOOKNAME = Regex("《\\s*([^》]+?)\\s*》")
    private val RE_PAREN_TAIL =
        Regex("[\\[【(（]\\s*([^()\\[\\]（）【】]{0,48}?)\\s*[)）\\]】]\\s*$")
    private val RE_ANY_PAREN = Regex("[\\[【(（]\\s*([^()\\[\\]（）【】]{0,48}?)\\s*[)）\\]】]")
    private val RE_STATUS_TAIL = Regex(SEP_TAIL + "(?:" + STATUS_WORDS.joinToString("|") + ")\\s*$")
    private val RE_DONE = Regex("完结|全本|完本|全书完|已完结")
    private val RE_TAG_ONLY = Regex("^[\\d\\s,、/+&a-z]*(?:完结|全本|番外|补番)[\\d\\s,、/+&a-z]*$")
    private val RE_EXTRA = Regex("(\\d{1,4})\\s*番外")
    private val RE_AUTHOR_TAIL = Regex("(著|作品|出品|写)$")
    private val RE_SEP_RUN = Regex(SEP_CLASS + "+")

    /** [更235] / [更新至235章] / [第235章] / [235章] / [至235章] */
    private val CHAP_PATTERNS = listOf(
        Regex("更(?:新)?(?:至|到)?\\s*(\\d{1,6})"),
        Regex("第\\s*(\\d{1,6})\\s*[章回节]"),
        Regex("(\\d{1,6})\\s*[章回节]"),
        Regex("至\\s*(\\d{1,6})\\s*[章回节]")
    )

    /**
     * 结尾的章节标记。必须带 更/更新/第/至/正文/共 或 章/回/节 关键字，
     * 绝不能只凭"结尾是数字"就砍 —— 否则《1984》《第8号当铺》这类书名会被误伤。
     */
    private val CHAP_TAIL = listOf(
        Regex(SEP_TAIL + "(?:更(?:新)?(?:至|到)?|第|至|正文|共|全书)\\s*\\d{1,6}\\s*[章回节]?\\s*$"),
        Regex(SEP_TAIL + "\\d{1,6}\\s*[章回节]\\s*$"),
        Regex(SEP_TAIL + "\\d{1,4}\\s*番外\\s*$")
    )

    private fun trimSep(s: String): String = s.trim { SEP_CHARS.indexOf(it) >= 0 }

    /** 全角转半角、去所有空白、统一括号、转小写 */
    fun normalize(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        var t = Normalizer.normalize(s, Normalizer.Form.NFKC)
        t = t.replace('（', '(').replace('）', ')').replace('【', '[').replace('】', ']')
        t = RE_WS.replace(t, "")
        return t.lowercase()
    }

    /**
     * 判断一个括号里的内容是不是"标签"而不是书名的一部分。
     * 注意：这里不能先把空白全删掉再切词，否则 "np 都市 高干" 会变成一个词而漏判。
     */
    fun isTag(content: String): Boolean {
        var c = Normalizer.normalize(content, Normalizer.Form.NFKC).lowercase()
        c = c.replace('（', '(').replace('）', ')')
        c = RE_WS.replace(c, " ").trim()
        if (c.isEmpty()) return true
        if (RE_NUM2.matches(c)) return true // (1) (2) 副本序号
        val tokens = c.split(RE_TOKENS).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        if (tokens.all { RE_NUM2.matches(it) || it in TAG_WORDS }) return true
        if (tokens.any { it in TAG_WORDS }) return true
        if (RE_TAG_ONLY.matches(c)) return true
        return false
    }

    /**
     * 从结尾反复剥掉 标签括号 / 状态词 / 章节标记。
     * 这是"没有《》时也能拿到干净书名"的关键：
     *   盗墓笔记 更500 (1)  ->  盗墓笔记
     *   霸道少爷爱上我 （1v1） 更120  ->  霸道少爷爱上我
     */
    private fun stripTrailingNoise(input: String, tags: MutableList<String>): String {
        var s = input
        var changed = true
        while (changed) {
            changed = false

            val pm = RE_PAREN_TAIL.find(s)
            if (pm != null && isTag(pm.groupValues[1])) {
                tags.add(pm.groupValues[1])
                s = trimSep(s.substring(0, pm.range.first))
                changed = true
                continue
            }

            val sm = RE_STATUS_TAIL.find(s)
            if (sm != null) {
                tags.add(trimSep(sm.value))
                s = trimSep(s.substring(0, sm.range.first))
                changed = true
                continue
            }

            for (p in CHAP_TAIL) {
                val m = p.find(s)
                if (m != null) {
                    tags.add(trimSep(m.value))
                    s = trimSep(s.substring(0, m.range.first))
                    changed = true
                    break
                }
            }
        }
        return s
    }

    /** 把书名压成"匹配键"：去掉夹在里面的标签括号、状态词、所有分隔符 */
    fun keyifyTitle(raw: String): String {
        var s = RE_ANY_PAREN.replace(raw) { mr ->
            if (isTag(mr.groupValues[1])) "" else mr.value
        }
        for (w in STATUS_WORDS) s = s.replace(w, "")
        s = normalize(s)
        s = RE_SEP_RUN.replace(s, "")
        return s
    }

    fun parse(fileName: String): ParsedName {
        val original = fileName
        var name = fileName
        if (name.lowercase().endsWith(".txt")) name = name.dropLast(4)
        name = name.trim()

        // 1) 去掉文件名里夹的网址
        name = RE_URL.replace(name, " ")

        val tags = ArrayList<String>()

        // 2) 开头的 [完结+16番外] / 【xxx】 方括号块 -> 状态
        while (true) {
            val m = RE_LEAD_BRACKET.find(name) ?: break
            if (m.range.first != 0) break
            tags.add(m.groupValues[1])
            name = name.substring(m.range.last + 1)
        }

        // 3) 其余位置的 【...】 / [...] 也当标签
        name = RE_CORNER_BRACKET.replace(name) { mr -> tags.add(mr.groupValues[1]); " " }
        name = RE_SQUARE_BRACKET.replace(name) { mr -> tags.add(mr.groupValues[1]); " " }

        // 4) 作者：xxx
        var author = ""
        val am = RE_AUTHOR.find(name)
        if (am != null) {
            author = RE_AUTHOR_TAIL.replace(am.groupValues[1].trim(), "")
            name = name.substring(0, am.range.first) + " " + name.substring(am.range.last + 1)
        }

        // 5) 书名
        val bm = RE_BOOKNAME.find(name)
        var titleRaw = if (bm != null) bm.groupValues[1] else name
        titleRaw = trimSep(stripTrailingNoise(titleRaw, tags))
        if (titleRaw.isEmpty()) titleRaw = trimSep(name)

        // 6) 章数：在"原始文件名"上找，因为章数常常写在开头的方括号里
        var chap = 0
        for (p in CHAP_PATTERNS) {
            for (m in p.findAll(original)) {
                val v = m.groupValues[1].toIntOrNull() ?: continue
                if (v > chap) chap = v
            }
        }

        // 7) 番外数
        var extra = 0
        for (m in RE_EXTRA.findAll(original)) {
            val v = m.groupValues[1].toIntOrNull() ?: continue
            if (v > extra) extra = v
        }

        // 8) 是否完结
        val done = RE_DONE.containsMatchIn(original)

        return ParsedName(
            title = titleRaw,
            titleKey = keyifyTitle(titleRaw),
            author = author,
            authorKey = normalize(author),
            chap = chap,
            extra = extra,
            done = done,
            tags = tags
        )
    }
}
