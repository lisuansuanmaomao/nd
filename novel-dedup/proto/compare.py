# -*- coding: utf-8 -*-
"""语义对比 Python 版与 Kotlin 移植版（经 Node 跑）的解析结果。"""
import json
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

here = __file__.rsplit('/', 1)[0].rsplit('\\', 1)[0]
with open(here + '/py_out.json', encoding='utf-8') as f:
    py = json.load(f)
with open(here + '/js_out.json', encoding='utf-8') as f:
    js = json.load(f)

fields = ["title", "titleKey", "author", "authorKey", "chap", "extra", "done"]

bad = 0
for name in sorted(set(py) | set(js)):
    a = py.get(name)
    b = js.get(name)
    if a is None or b is None:
        print(f'[缺行] {name}  py={a is not None} js={b is not None}')
        bad += 1
        continue
    for k in fields:
        if a.get(k) != b.get(k):
            print(f'[不一致] {name}\n    {k}: py={a.get(k)!r}  kotlin={b.get(k)!r}')
            bad += 1

print()
print(f'对比 {len(py)} 个文件名 x {len(fields)} 个字段 = {len(py)*len(fields)} 项')
print('不一致数量:', bad)
sys.exit(1 if bad else 0)
