import re
import pathlib
import sys

rows = []
for p in pathlib.Path('.').rglob('*.kt'):
    s = str(p).replace('\\', '/')
    if '/build/' in s:
        continue
    try:
        t = p.read_text(encoding='utf-8')
    except Exception:
        continue
    n = len(re.findall(r'"[^"\n]*[\u4e00-\u9fff][^"\n]*"', t))
    if n:
        rows.append((n, s))
rows.sort(reverse=True)
for n, s in rows[:25]:
    print(n, s)
print('total files', len(rows), 'literals', sum(r[0] for r in rows))
