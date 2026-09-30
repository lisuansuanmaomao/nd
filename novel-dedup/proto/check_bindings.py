# -*- coding: utf-8 -*-
"""
没有 Android SDK 也要查的那些低级错误：
  1. Kotlin 里 b.xxx 用到的 ViewBinding 字段，在对应 layout 里是否真的存在
  2. AndroidManifest 里 @style/@string/@mipmap/@drawable/@color 引用的资源是否真的存在
  3. layout 之间是否有重复 id 导致的绑定错乱（同一 layout 内重复 id）
"""
import os
import re
import sys
import io
import xml.etree.ElementTree as ET

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, 'app', 'src', 'main')
JAVA = os.path.join(MAIN, 'java', 'com', 'noveldedup')
RES = os.path.join(MAIN, 'res')

problems = []


def snake(name: str) -> str:
    out = []
    for i, ch in enumerate(name):
        if ch.isupper() and i > 0:
            out.append('_')
        out.append(ch.lower())
    return ''.join(out)


# ---------- 1. 收集 layout id ----------
layout_ids = {}
for fn in os.listdir(os.path.join(RES, 'layout')):
    if not fn.endswith('.xml'):
        continue
    path = os.path.join(RES, 'layout', fn)
    tree = ET.parse(path)
    ids = []
    for el in tree.iter():
        v = el.get('{http://schemas.android.com/apk/res/android}id')
        if v and v.startswith('@+id/'):
            ids.append(v.split('/', 1)[1])
    dup = [i for i in set(ids) if ids.count(i) > 1]
    if dup:
        problems.append(f'[重复id] {fn}: {dup}')
    layout_ids[fn[:-4]] = set(ids)

print('layout 及其中 id：')
for k, v in sorted(layout_ids.items()):
    print(f'  {k}.xml -> {sorted(v)}')

# ---------- 2. 检查 Kotlin 里的绑定字段 ----------
print()
print('Kotlin 绑定字段检查：')
for fn in sorted(os.listdir(JAVA)):
    if not fn.endswith('.kt'):
        continue
    src = open(os.path.join(JAVA, fn), encoding='utf-8').read()
    binds = set(re.findall(r'\b([A-Z][A-Za-z0-9]*Binding)\b', src))
    binds.discard('ViewBinding')
    binds.discard('LayoutInflaterBinding')
    if not binds:
        continue
    for b in binds:
        layout = snake(b[:-len('Binding')])
        if layout not in layout_ids:
            problems.append(f'[缺layout] {fn} 用了 {b}，但找不到 layout/{layout}.xml')
            continue
        ids = layout_ids[layout]
        used = set(re.findall(r'\bb\.([a-z][A-Za-z0-9_]*)', src))
        used.discard('root')
        missing = sorted(used - ids)
        ok = sorted(used & ids)
        print(f'  {fn}: {b} -> layout/{layout}.xml')
        print(f'      存在的字段: {ok}')
        if missing:
            print(f'      找不到的字段: {missing}')
            problems.append(f'[缺字段] {fn}: {b}.{missing}')

# ---------- 3. 清单资源引用 ----------
print()
print('AndroidManifest 资源引用检查：')
manifest = os.path.join(MAIN, 'AndroidManifest.xml')
msrc = open(manifest, encoding='utf-8').read()
declared = {}
for sub in ('layout', 'drawable', 'mipmap-anydpi', 'mipmap-anydpi-v26', 'values'):
    d = os.path.join(RES, sub)
    if not os.path.isdir(d):
        continue
    for fn in os.listdir(d):
        if fn.endswith('.xml'):
            declared.setdefault(sub, set()).add(fn[:-4])

# values/*.xml 里声明的 name
value_names = {'color': set(), 'string': set(), 'style': set()}
for fn in os.listdir(os.path.join(RES, 'values')):
    t = ET.parse(os.path.join(RES, 'values', fn))
    for el in t.getroot():
        tag = el.tag
        nm = el.get('name')
        if tag in value_names and nm:
            value_names[tag].add(nm)

for m in re.finditer(r'@(style|string|mipmap|drawable|color)/([A-Za-z0-9_.]+)', msrc):
    kind, nm = m.group(1), m.group(2)
    found = False
    if kind in ('style', 'string', 'color'):
        found = nm in value_names[kind]
    else:
        for sub in ('drawable', 'mipmap-anydpi', 'mipmap-anydpi-v26', 'layout'):
            if nm in declared.get(sub, set()):
                found = True
    print(f'  @{kind}/{nm}: {"OK" if found else "找不到"}')
    if not found:
        problems.append(f'[缺资源] AndroidManifest 引用了 @{kind}/{nm}')

print()
print('=' * 70)
if problems:
    print(f'发现 {len(problems)} 个问题：')
    for p in problems:
        print('  - ' + p)
else:
    print('全部检查通过')
print('=' * 70)
sys.exit(1 if problems else 0)
