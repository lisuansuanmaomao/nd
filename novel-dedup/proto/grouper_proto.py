# -*- coding: utf-8 -*-
"""
分组 + 取舍 + 待确认判定。对应 Android 侧的 Grouper.kt / Planner.kt。
"""
from dataclasses import dataclass, field
from typing import List, Dict, Tuple
from parser_proto import Entry


@dataclass
class Group:
    title: str
    author: str
    title_key: str
    author_key: str = ''
    members: List[Entry] = field(default_factory=list)
    keep: Entry = None
    losers: List[Entry] = field(default_factory=list)
    pending: List[Entry] = field(default_factory=list)   # 不自动处理
    reason: str = ''
    uncertain: bool = False          # 归并/取舍没把握，界面高亮
    auto_merge_note: str = ''


def group_entries(entries: List[Entry]) -> List[Group]:
    """两级分组：书名键 -> 作者键。无作者的文件做安全归并。"""
    by_title: Dict[str, List[Entry]] = {}
    for e in entries:
        if not e.title_key:
            continue
        by_title.setdefault(e.title_key, []).append(e)

    groups: List[Group] = []
    for tkey, items in by_title.items():
        with_author: Dict[str, List[Entry]] = {}
        no_author: List[Entry] = []
        for e in items:
            if e.author_key:
                with_author.setdefault(e.author_key, []).append(e)
            else:
                no_author.append(e)

        # 情况 A：这一批文件全都没写作者。
        # 没有任何冲突来源，就是同一本书的不同版本 -> 正常去重。
        if not with_author:
            groups.append(Group(title=items[0].title, author='',
                                title_key=tkey, author_key='',
                                members=list(items)))
            continue

        # 情况 B：书名下只有一个作者 -> 没写作者的那些安全归并进去
        if len(with_author) == 1 and no_author:
            only_key = next(iter(with_author))
            with_author[only_key].extend(no_author)
            for e in no_author:
                e.merged_from_no_author = True  # type: ignore
            no_author = []

        for akey, members in with_author.items():
            g = Group(title=members[0].title, author=members[0].author,
                      title_key=tkey, author_key=akey, members=list(members))
            if len(members) > 1 and any(
                    getattr(x, 'merged_from_no_author', False) for x in members):
                g.auto_merge_note = '含未写作者的副本，已按"同名且唯一作者"归并'
            groups.append(g)

        # 情况 C：同名书有 >=2 个不同作者，且还有没写作者的文件
        # -> 这些文件到底属于哪个作者无法判断，全部不动
        if no_author:
            g = Group(title=no_author[0].title, author='',
                      title_key=tkey, author_key='', members=list(no_author))
            g.uncertain = True
            g.reason = (f'同名书存在 {len(with_author)} 个不同作者，'
                        f'无法判断这些没写作者的文件属于谁')
            groups.append(g)

    return groups


def plan_group(g: Group):
    """在一个组内决定留谁、移谁。"""
    if g.uncertain:
        g.pending = list(g.members)
        g.reason = g.reason or '信息不足'
        return

    if len(g.members) <= 1:
        g.keep = g.members[0] if g.members else None
        g.reason = '组内只有 1 个文件，不动'
        return

    done = [e for e in g.members if e.done]
    if done:
        # 完结的里面留体积最大的
        g.keep = max(done, key=lambda e: (e.size, e.extra, e.chap))
        g.losers = [e for e in g.members if e is not g.keep]
        g.reason = f'有 {len(done)} 个完结版，保留体积最大的'
        return

    numbered = [e for e in g.members if e.chap > 0]
    unnumbered = [e for e in g.members if e.chap == 0]

    if not numbered:
        # 全都读不出章数 -> 不猜
        g.uncertain = True
        g.pending = list(g.members)
        g.reason = '都读不出章节数，需人工判断'
        return

    g.keep = max(numbered, key=lambda e: (e.chap, e.size))
    g.losers = [e for e in numbered if e is not g.keep]
    g.reason = f'无完结版，保留章数最大的（{g.keep.chap} 章）'
    for e in unnumbered:
        g.pending.append(e)
    if unnumbered:
        g.uncertain = True
        g.reason += f'；另有 {len(unnumbered)} 个读不出章数的文件，未自动处理'
