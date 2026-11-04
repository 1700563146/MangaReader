import re, sys, pathlib, json
out = {}
for f in sys.argv[1:]:
    t = pathlib.Path(f).read_text(encoding='utf-8')
    out[f] = sorted(set(re.findall(r'"[^"\n]*[\u4e00-\u9fff][^"\n]*"', t)))
pathlib.Path('tools/literals.json').write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding='utf-8')
print('written', sum(len(v) for v in out.values()))
