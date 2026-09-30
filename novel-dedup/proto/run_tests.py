# -*- coding: utf-8 -*-
"""用一批仿真的手机文件名跑通全流程，并断言结果符合预期。"""
import sys
import io
from parser_proto import parse_filename, Entry
from grouper_proto import group_entries, plan_group

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

MB = 1024 * 1024
ROOT = r'D:\小说'

# (相对路径, 字节数)
CORPUS = [
    # —— 1. 无《》、无作者，完结 + 不同章数版本
    (r'言情\霸道少爷爱上我 （1v1） 更120.txt', int(1.2 * MB)),
    (r'言情\霸道少爷爱上我 （1v1） 更340.txt', int(3.4 * MB)),
    (r'言情\霸道少爷爱上我 （1v1） (完结).txt', int(8.0 * MB)),
    # —— 2. 有《》、有作者、方括号里写更新度（用户给的原始样例）
    (r'下载\[1v1 更235]《梨汁软糖（甜文）》作者：abc.txt', int(1.1 * MB)),
    (r'下载\[1v1 更580]《梨汁软糖》作者：abc.txt', int(2.6 * MB)),
    (r'下载\梨汁软糖（甜文）作者：abc 完结.txt', int(5.0 * MB)),
    # —— 3. 完结版有两个，取体积最大；另有一个连载版
    (r'合集\[完结]春山寒 作者：江入玦明.txt', int(2.0 * MB)),
    (r'合集\[更400]春山寒 作者：江入玦明.txt', int(1.0 * MB)),
    (r'合集\[完结]春山寒 作者：江入玦明【精校版】.txt', int(2.4 * MB)),
    # —— 4. 多词标签 np 都市 高干 + 番外数
    (r'下载\[完结+16番外]《罪爱（np 都市 高干）》作者：九铃【补番】.txt', int(3.0 * MB)),
    (r'下载\[完结+2番外]《罪爱》作者：九铃.txt', int(2.1 * MB)),
    (r'下载\[更800]《罪爱》作者：九铃.txt', int(1.9 * MB)),
    # —— 5. 同名但作者不同：必须各自成组，绝不互删
    (r'别的\[完结]春山寒 作者：另一个人.txt', int(1.5 * MB)),
    # —— 6. 同名不同作者 + 一个没写作者：不敢猜，必须待确认
    (r'未知\春山寒.txt', int(1.3 * MB)),
    # —— 7. 无作者副本，但该书名下只有唯一作者：安全归并
    (r'未知\盗墓笔记 更500.txt', int(0.9 * MB)),
    (r'排雷\《盗墓笔记》作者：南派三叔 更1230.txt', int(4.0 * MB)),
    (r'排雷\盗墓笔记 更500 (1).txt', int(0.88 * MB)),
    # —— 8. 独苗：不该被碰
    (r'单本\《独一份》作者：某人 更50.txt', int(0.5 * MB)),
    # —— 9. 陷阱：书名里本来就带数字，不能被当成章数
    (r'陷阱\《1984》作者：奥威尔.txt', int(0.3 * MB)),
    (r'陷阱\《1984》作者：奥威尔 更12章.txt', int(0.6 * MB)),
]

entries = []
for rel, size in CORPUS:
    e = parse_filename(rel.split('\\')[-1])
    e.path = ROOT + '\\' + rel
    e.name = rel.split('\\')[-1]
    e.size = size
    entries.append(e)

groups = group_entries(entries)
for g in groups:
    plan_group(g)

# ------------------------------------------------------------------ 报告
print('=' * 100)
print('① 文件名解析结果')
print('=' * 100)
print(f'{"文件名":<52} {"书名":<18} {"作者":<10} {"章":>6} {"番外":>4} {"完结":>4}')
print('-' * 100)
for e in entries:
    print(f'{e.name[:50]:<52} {e.title[:16]:<18} {e.author[:8]:<10} {e.chap:>6} {e.extra:>4} {"是" if e.done else "":>4}')

print()
print('=' * 100)
print('② 分组与取舍')
print('=' * 100)
for g in groups:
    print(f'\n■ 书名「{g.title}」 作者「{g.author or "(未写)"}」  组内 {len(g.members)} 个文件'
          + ('   ⚠ 待确认' if g.uncertain else ''))
    print(f'   规则: {g.reason}')
    if g.auto_merge_note:
        print(f'   备注: {g.auto_merge_note}')
    if g.keep:
        print(f'   [保留] {g.keep.name}   {g.keep.size/1048576:.2f} MB'
              + (f'  第{g.keep.chap}章' if g.keep.chap else '')
              + ('  完结' if g.keep.done else ''))
    for e in g.losers:
        print(f'   [移走] {e.name}   {e.size/1048576:.2f} MB')
    for e in g.pending:
        print(f'   [不动·待确认] {e.name}   {e.size/1048576:.2f} MB')

# ------------------------------------------------------------------ 断言
def find(title, author):
    for g in groups:
        if g.title_key == title and g.author_key == author:
            return g
    return None

fails = []

def check(desc, cond):
    print(('  PASS  ' if cond else '  FAIL  ') + desc)
    if not cond:
        fails.append(desc)

print()
print('=' * 100)
print('③ 断言')
print('=' * 100)

g = find('霸道少爷爱上我', '')
check('霸道少爷爱上我：3 个文件归一组', g and len(g.members) == 3)
check('霸道少爷爱上我：完结版胜出（8MB）', g and g.keep and g.keep.done and abs(g.keep.size - 8.0 * MB) < 1000)
check('霸道少爷爱上我：移走 2 个', g and len(g.losers) == 2)

g = find('梨汁软糖', 'abc')
check('梨汁软糖：3 个版本归一组（书名号/括号标签都能对上）', g and len(g.members) == 3)
check('梨汁软糖：完结版 5MB 胜出', g and g.keep and g.keep.done and abs(g.keep.size - 5.0 * MB) < 1000)

g = find('春山寒', '江入玦明')
check('春山寒/江入玦明：3 个归一组', g and len(g.members) == 3)
check('春山寒/江入玦明：完结中取最大 2.4MB', g and abs(g.keep.size - 2.4 * MB) < 1000)

g = find('春山寒', '另一个人')
check('春山寒/另一个人：独立成组，未被误删', g and len(g.members) == 1 and not g.losers)

g = find('春山寒', '')
check('春山寒/无作者：两个作者并列 -> 待确认，不动', g and g.uncertain and len(g.pending) == 1)

g = find('罪爱', '九铃')
check('罪爱：3 个归一组（np 都市 高干 被识别为标签）', g and len(g.members) == 3)
check('罪爱：完结中取最大 3.0MB（16番外那本）', g and abs(g.keep.size - 3.0 * MB) < 1000)

g = find('盗墓笔记', '南派三叔')
check('盗墓笔记：无作者副本被安全归并', g and len(g.members) == 3)
check('盗墓笔记：保留章数最大 1230', g and g.keep.chap == 1230 and abs(g.keep.size - 4.0 * MB) < 1000)

g = find('独一份', '某人')
check('独一份：独苗不动', g and len(g.members) == 1 and not g.losers)

g = find('1984', '奥威尔')
check('1984：书名中的数字没被误当章数', g and len(g.members) == 2 and g.keep.chap == 12
      and abs(g.keep.size - 0.6 * MB) < 1000)

print()
print('=' * 100)
total_losers = sum(len(g.losers) for g in groups)
total_pending = sum(len(g.pending) for g in groups)
print(f'③ 汇总：解析 {len(entries)} 个文件 -> {len(groups)} 组；'
      f'将移走 {total_losers} 个；待确认 {total_pending} 个')
print(f'   断言失败 {len(fails)} 条')
for f in fails:
    print('   - ' + f)
print('=' * 100)
sys.exit(1 if fails else 0)
