# -*- coding: utf-8 -*-
"""
小说 TXT 去重 —— 文件名解析 / 分组 / 取舍规则 原型验证脚本
=========================================================
这个脚本是 Android App 里 NameParser.kt + Grouper.kt + Planner.kt 的
"可执行规格"：先在 PC 上把规则跑通、跑对，再原样翻译成 Kotlin。
只用文件名，不打开 txt 内容。
"""
import re
import unicodedata
import os
from dataclasses import dataclass, field

# ---------------------------------------------------------------- 词表
# 出现在书名括号里的"标签"，这些不是书名的一部分，用于跨版本匹配
TAG_WORDS = [
    '1v1', 'np', '双洁', '双处', 'sc', '甜文', '虐文', '爽文', '宠文', '甜宠',
    '都市', '高干', '校园', '娱乐圈', '豪门', '总裁', '种田', '修仙', '玄幻',
    '言情', '耽美', '百合', '无cp', '重生', '穿书', '穿越', '快穿', '系统',
    '年下', '年上', '强强', '骨科', '追妻', '火葬场', '破镜重圆', '先婚后爱',
    '青梅竹马', 'he', 'be', 'gb', 'bg', 'gl', 'bl', 'abo', '正剧', '轻松',
    '搞笑', '短篇', '中篇', '长篇', '免费', '首发', '独家', '精校', '精校版',
    '全本', '未删减', '慎入', '排雷', '完结', '番外', '补番', '连载', '更新中',
    '清水', '狗血', '沙雕', '无限流', '年代文', '家长里短', '女主', '男主',
]
# 出现在书名尾部、代表"状态"的词（不是书名）
STATUS_WORDS = ['已完结', '完结', '全本', '完本', '完结文', '连载中', '连载', '更新中', '太监']
# 代表"这本书已经完结"的词
DONE_PAT = re.compile(r'完结|全本|完本|全书完|已完结')

_TAG_SET = {t.lower() for t in TAG_WORDS}
_SEP = ' \t-_—–·|/\\,，、;；:：~～'


# ---------------------------------------------------------------- 工具
def normalize(s: str) -> str:
    """全角->半角、去所有空白、统一括号、转小写。用于生成匹配键。"""
    if not s:
        return ''
    s = unicodedata.normalize('NFKC', s)
    s = s.replace('（', '(').replace('）', ')').replace('【', '[').replace('】', ']')
    s = re.sub(r'\s+', '', s)
    return s.lower()


def is_tag(content: str) -> bool:
    """判断一个括号里的内容是不是"标签"而不是书名的一部分。

    注意：这里不能先把空白全删掉再切词，否则 'np 都市 高干' 会变成一个词而漏判。
    """
    c = unicodedata.normalize('NFKC', content).lower()
    c = c.replace('（', '(').replace('）', ')')
    c = re.sub(r'\s+', ' ', c).strip()
    if not c:
        return True
    # 纯数字 = 副本序号 (1) (2)
    if re.fullmatch(r'\d{1,2}', c):
        return True
    tokens = [t for t in re.split(r'[\s,、/+&;；]+', c) if t]
    if not tokens:
        return True
    # 全部由"标签词 / 小数字"组成  -> 是标签
    if all(re.fullmatch(r'\d{1,2}', t) or t in _TAG_SET for t in tokens):
        return True
    # 含任一已知标签词（如 'np 都市 高干'、'完结+16番外'、'甜文'）
    if any(t in _TAG_SET for t in tokens):
        return True
    # 形如 完结+16番外 / 16番外
    if re.fullmatch(r'[\d\s,、/+&a-z]*(?:完结|全本|番外|补番)[\d\s,、/+&a-z]*', c):
        return True
    return False


# ---------------------------------------------------------------- 解析
@dataclass
class Entry:
    path: str
    name: str
    size: int
    title: str = ''
    title_key: str = ''
    author: str = ''
    author_key: str = ''
    chap: int = 0
    extra: int = 0          # 番外数量
    done: bool = False      # 是否完结
    tags: list = field(default_factory=list)


_STATUS_TAIL = re.compile(r'[' + re.escape(_SEP) + r']*(?:' + '|'.join(STATUS_WORDS) + r')\s*$')
# 结尾的章节标记：必须带 更/更新/第/至/正文/共 或 章/回/节 关键字，
# 绝不能只凭结尾是数字就砍（否则《1984》《第8号当铺》这类会被误伤）
_CHAP_TAIL = [
    re.compile(r'[' + re.escape(_SEP) + r']*(?:更(?:新)?(?:至|到)?|第|至|正文|共|全书)\s*\d{1,6}\s*[章回节]?\s*$'),
    re.compile(r'[' + re.escape(_SEP) + r']*\d{1,6}\s*[章回节]\s*$'),
    re.compile(r'[' + re.escape(_SEP) + r']*\d{1,4}\s*番外\s*$'),
]
_PAREN_TAIL = re.compile(r'[\(\[（【]\s*([^\(\)\[\]（）【】]{0,48}?)\s*[\)\]）】]\s*$')


def _strip_trailing_tags(s: str):
    """从结尾反复剥掉 标签括号 / 状态词 / 章节标记，返回 (剩余, 标签列表)。
    这是"没有《》时也能拿到干净书名"的关键：`盗墓笔记 更500 (1)` -> `盗墓笔记`。"""
    tags = []
    changed = True
    while changed:
        changed = False
        m = _PAREN_TAIL.search(s)
        if m and is_tag(m.group(1)):
            tags.append(m.group(1))
            s = s[:m.start()].rstrip(_SEP)
            changed = True
            continue
        m2 = _STATUS_TAIL.search(s)
        if m2:
            tags.append(m2.group(0).strip(_SEP))
            s = s[:m2.start()].rstrip(_SEP)
            changed = True
            continue
        for pat in _CHAP_TAIL:
            m3 = pat.search(s)
            if m3:
                tags.append(m3.group(0).strip(_SEP))
                s = s[:m3.start()].rstrip(_SEP)
                changed = True
                break
    return s, tags


def keyify_title(title_raw: str) -> str:
    """把书名压成"匹配键"：去掉书里夹着的标签括号、状态词、所有分隔符。"""
    s = title_raw
    # 去掉夹在中间/结尾的标签括号（如 梨汁软糖（甜文） -> 梨汁软糖 ; 罪爱（np 都市 高干） -> 罪爱）
    def _rm(m):
        return '' if is_tag(m.group(1)) else m.group(0)
    s = re.sub(r'[\(\[（【]\s*([^\(\)\[\]（）【】]{0,48}?)\s*[\)\]）】]', _rm, s)
    # 去掉状态词
    for w in STATUS_WORDS:
        s = s.replace(w, '')
    s = normalize(s)
    s = re.sub(r'[' + re.escape(_SEP) + r']+', '', s)
    return s


def parse_filename(filename: str) -> Entry:
    original = filename
    name = filename
    if name.lower().endswith('.txt'):
        name = name[:-4]
    name = name.strip()

    # 1) 去掉网址
    name = re.sub(r'(?:https?://|www\.)[^\s\u4e00-\u9fff]*', ' ', name, flags=re.I)

    tags = []

    # 2) 开头的 [完结+16番外] / 【xxx】 方括号块 -> 状态
    while True:
        m = re.match(r'^\s*[\[【]\s*([^\[\]【】]*?)\s*[\]】]\s*', name)
        if not m:
            break
        tags.append(m.group(1))
        name = name[m.end():]

    # 3) 其余位置的 【...】 / [...] 也当标签
    def _collect(m):
        tags.append(m.group(1))
        return ' '
    name = re.sub(r'【\s*([^【】]*?)\s*】', _collect, name)
    name = re.sub(r'\[\s*([^\[\]]*?)\s*\]', _collect, name)

    # 4) 作者：xxx
    author = ''
    m = re.search(r'作者\s*[:：]?\s*([^\s\(\)\[\]【】《》|,，、;；]+)', name)
    if m:
        author = m.group(1).strip()
        author = re.sub(r'(著|作品|出品|写)$', '', author)
        name = name[:m.start()] + ' ' + name[m.end():]

    # 5) 书名
    m = re.search(r'《\s*([^》]+?)\s*》', name)
    if m:
        title_raw = m.group(1)
    else:
        title_raw = name
    title_raw, ttags = _strip_trailing_tags(title_raw)
    tags += ttags
    title_raw = title_raw.strip(_SEP)

    # 6) 章数（在"原始文件名"上找，因为可能在开头方括号里）
    chap = 0
    for pat in (r'更(?:新)?(?:至|到)?\s*(\d{1,6})',
                r'第\s*(\d{1,6})\s*[章回节]',
                r'(\d{1,6})\s*[章回节]',
                r'至\s*(\d{1,6})\s*[章回节]'):
        for mm in re.finditer(pat, original):
            chap = max(chap, int(mm.group(1)))

    # 7) 番外数
    extra = 0
    for mm in re.finditer(r'(\d{1,4})\s*番外', original):
        extra = max(extra, int(mm.group(1)))

    # 8) 是否完结
    done = bool(DONE_PAT.search(original))

    return Entry(path=original, name=original, size=0,
                 title=title_raw, title_key=keyify_title(title_raw),
                 author=author, author_key=normalize(author),
                 chap=chap, extra=extra, done=done, tags=tags)
