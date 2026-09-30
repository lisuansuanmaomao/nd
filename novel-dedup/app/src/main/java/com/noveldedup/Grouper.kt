package com.noveldedup

/**
 * 分组：先按书名键分组，再按作者键分组。
 * 对应 proto/grouper_proto.py 里的 group_entries()。
 */
object Grouper {

    fun group(files: List<BookFile>): List<Group> {
        val byTitle = LinkedHashMap<String, MutableList<BookFile>>()
        for (f in files) {
            if (f.titleKey.isEmpty()) continue
            byTitle.getOrPut(f.titleKey) { ArrayList() }.add(f)
        }

        val groups = ArrayList<Group>()
        for ((tkey, items) in byTitle) {
            val withAuthor = LinkedHashMap<String, MutableList<BookFile>>()
            val noAuthor = ArrayList<BookFile>()
            for (f in items) {
                if (f.authorKey.isNotEmpty()) {
                    withAuthor.getOrPut(f.authorKey) { ArrayList() }.add(f)
                } else {
                    noAuthor.add(f)
                }
            }

            // 情况 A：这批文件全都没写作者。没有任何冲突来源，
            // 就是同一本书的不同版本 -> 正常去重。
            if (withAuthor.isEmpty()) {
                groups.add(Group(items[0].title, "", tkey, "", items))
                continue
            }

            // 情况 B：书名下只有一个作者 -> 没写作者的那些安全归并进去
            var merged = false
            if (withAuthor.size == 1 && noAuthor.isNotEmpty()) {
                withAuthor.values.first().addAll(noAuthor)
                merged = true
                noAuthor.clear()
            }

            for ((akey, members) in withAuthor) {
                val g = Group(members[0].title, members[0].author, tkey, akey, members)
                if (merged && members.size > 1) {
                    g.mergeNote = "含未写作者的副本，已按「同名且唯一作者」归并"
                }
                groups.add(g)
            }

            // 情况 C：同名书有 >=2 个不同作者，且还有没写作者的文件
            // -> 这些文件到底属于哪个作者无法判断，全部不动
            if (noAuthor.isNotEmpty()) {
                val g = Group(noAuthor[0].title, "", tkey, "", noAuthor)
                g.uncertain = true
                g.reason = "同名书有 " + withAuthor.size +
                    " 个不同作者，无法判断这些没写作者的文件属于谁"
                groups.add(g)
            }
        }
        return groups
    }
}
