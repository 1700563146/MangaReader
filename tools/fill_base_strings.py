"""补齐 values-en / values-zh-rTW 中缺失的基础文案（导航、空态、设置、规则页等）。"""
import pathlib
import re

BASE = {
    'nav_shelf': ('Shelf', '書架'),
    'nav_history': ('History', '歷史'),
    'nav_browse': ('Browse', '瀏覽'),
    'nav_me': ('Me', '我的'),
    'shelf_empty_title': ('Your shelf is empty', '書架還是空的'),
    'shelf_empty_body': (
        'Import a source rule, then search and favorite the comics you follow.\nThis app ships no comic content.',
        '匯入一份源規則後，搜尋並收藏你正在追的漫畫。\n本應用不內建任何漫畫資源。',
    ),
    'history_empty_title': ('No reading history yet', '還沒有閱讀記錄'),
    'history_empty_body': (
        'Chapters you read show up here; progress is stored on this device.',
        '讀過的章節會出現在這裡，進度會自動保存在本機。',
    ),
    'browse_empty_title': ('Start with a rule', '從規則開始'),
    'browse_empty_body': (
        'Import a JSON rule under "Me → Rules", then come back to search.\nSee docs/examples for a sample rule.',
        '在「我的 → 規則管理」匯入 JSON 規則，然後回來搜尋。\n範例規則見倉庫 docs/examples。',
    ),
    'me_title': ('Me', '我的'),
    'settings_appearance': ('Appearance', '外觀'),
    'settings_theme': ('Theme', '主題'),
    'theme_system': ('Follow system', '跟隨系統'),
    'theme_light': ('Light', '淺色'),
    'theme_dark': ('Dark', '深色'),
    'settings_about': ('About', '關於'),
    'settings_version': ('Version', '版本'),
    'settings_legal': ('License and disclaimer', '授權與免責聲明'),
    'settings_legal_hint': ('GPL-3.0 · ships no comic content', 'GPL-3.0 · 不提供任何漫畫內容'),
    'action_retry': ('Retry', '重試'),
    'search_hint': ('Search comics', '搜尋漫畫'),
    'search_action': ('Search', '搜尋'),
    'search_empty_title': ('Type a keyword to start', '輸入關鍵字開始'),
    'search_empty_body': (
        'Results are grouped by source and streamed as they arrive. Import and enable a rule first.',
        '結果會依來源分組、先返回先顯示。請先匯入並啟用一份規則。',
    ),
    'rules_manage': ('Rules', '規則管理'),
    'rules_import': ('Import rule', '匯入規則'),
    'rules_paste': ('Paste', '貼上'),
    'rules_confirm': ('Import', '匯入'),
    'rules_cancel': ('Cancel', '取消'),
    'rules_delete': ('Delete', '刪除'),
    'rules_import_hint': ('Paste rule JSON', '貼上規則 JSON'),
    'rules_empty_title': ('No rules imported', '還沒有匯入規則'),
    'rules_empty_body': (
        'A rule is a JSON file describing how a site is searched and parsed. '
        'This app ships no sources.',
        '規則是 JSON 資料檔，描述一個站點如何搜尋、解析詳情與圖片。本應用不內建任何來源。',
    ),
    'placeholder_m0': ('M0 skeleton: features land in later milestones', 'M0 骨架：功能將在後續里程碑接入'),
}

base = pathlib.Path('app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
existing = set(re.findall(r'<string name="([^"]+)"', base))

for folder, index, label in (('values-en', 0, 'en'), ('values-zh-rTW', 1, 'zh-rTW')):
    path = pathlib.Path(f'app/src/main/res/{folder}/strings.xml')
    text = path.read_text(encoding='utf-8')
    have = set(re.findall(r'<string name="([^"]+)"', text))
    additions = []
    for key, values in BASE.items():
        if key in have or key not in existing:
            continue
        value = values[index].replace("'", "\\'")
        additions.append(f'    <string name="{key}">{value}</string>')
    if additions:
        text = text.replace('</resources>', '\n'.join(additions) + '\n</resources>')
        path.write_text(text, encoding='utf-8')
    print(folder, 'added', len(additions))
