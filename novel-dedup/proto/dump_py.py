# -*- coding: utf-8 -*-
"""把 corpus.txt 里每个文件名的解析结果导出成 JSON，供与 Kotlin 移植版对比。"""
import json
import sys
import io
from parser_proto import parse_filename

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

here = __file__.rsplit('/', 1)[0].rsplit('\\', 1)[0]
names = []
with open(here + '/corpus.txt', encoding='utf-8') as f:
    for line in f:
        line = line.rstrip('\r\n')
        if line.strip():
            names.append(line)

out = {}
for n in names:
    e = parse_filename(n)
    out[n] = {
        "title": e.title,
        "titleKey": e.title_key,
        "author": e.author,
        "authorKey": e.author_key,
        "chap": e.chap,
        "extra": e.extra,
        "done": e.done,
    }
with open(here + '/py_out.json', 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=1, sort_keys=True)
print('wrote py_out.json:', len(out), 'entries')
