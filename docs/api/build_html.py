# -*- coding: utf-8 -*-
"""把 tk-platform-api.md 渲染为离线单页 HTML（只用标准库，无外部依赖）。

用法：python docs/api/build_html.py
输出：docs/api/tk-platform-api.html
"""
import html
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'tk-platform-api.md')
OUT = os.path.join(HERE, 'tk-platform-api.html')
DEFAULT_TITLE = 'API 参考文档 · v2.0 · 2026-11-11'


def inline(text):
    """渲染行内 Markdown：代码、链接、粗体。"""
    codes = []

    def keep(m):
        codes.append(m.group(1))
        return '\x00C%d\x00' % (len(codes) - 1)

    text = re.sub(r'`([^`]+)`', keep, text)
    text = html.escape(text, quote=False)
    text = re.sub(
        r'\[([^\]]+)\]\(([^)]+)\)',
        lambda m: '<a href="%s">%s</a>' % (html.escape(m.group(2), quote=True), m.group(1)),
        text,
    )
    text = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', text)
    for i, code in enumerate(codes):
        text = text.replace('\x00C%d\x00' % i, '<code>%s</code>' % html.escape(code, quote=False))
    return text


def is_table_sep(line):
    """判断是否为 Markdown 表格分隔行，如 | --- | :---: |。"""
    t = line.strip()
    if not (t.startswith('|') and t.endswith('|')):
        return False
    cells = [c.strip() for c in t.strip('|').split('|')]
    return bool(cells) and all(re.fullmatch(r':?-+:?', c) for c in cells)


def split_cells(line):
    return [c.strip() for c in line.strip().strip('|').split('|')]


def render(md):
    lines = md.split('\n')
    out, toc = [], []
    i, sec, n = 0, 0, len(lines)

    while i < n:
        line = lines[i]
        stripped = line.strip()

        # 代码块
        if stripped.startswith('```'):
            lang = stripped[3:].strip()
            buf = []
            i += 1
            while i < n and not lines[i].strip().startswith('```'):
                buf.append(lines[i])
                i += 1
            i += 1
            out.append('<pre class="code" data-lang="%s"><code>%s</code></pre>' % (
                html.escape(lang), html.escape('\n'.join(buf), quote=False)))
            continue

        # 标题
        m = re.match(r'^(#{1,6})\s+(.*)$', stripped)
        if m:
            lvl, text = len(m.group(1)), m.group(2).strip()
            sec += 1
            hid = 'sec-%d' % sec
            out.append('<h%d id="%s">%s</h%d>' % (lvl, hid, inline(text), lvl))
            if lvl in (2, 3):
                toc.append((lvl, text, hid))
            i += 1
            continue

        # 水平线
        if stripped == '---':
            out.append('<hr>')
            i += 1
            continue

        # 表格
        if stripped.startswith('|') and i + 1 < n and is_table_sep(lines[i + 1]):
            header = split_cells(stripped)
            i += 2
            rows = []
            while i < n and lines[i].strip().startswith('|'):
                rows.append(split_cells(lines[i]))
                i += 1
            th = ''.join('<th>%s</th>' % inline(c) for c in header)
            tb = ''.join('<tr>%s</tr>' % ''.join('<td>%s</td>' % inline(c) for c in r) for r in rows)
            out.append('<div class="table-wrap"><table><thead><tr>%s</tr></thead>'
                       '<tbody>%s</tbody></table></div>' % (th, tb))
            continue

        # 引用
        if stripped.startswith('>'):
            buf = []
            while i < n and lines[i].strip().startswith('>'):
                buf.append(lines[i].strip().lstrip('>').strip())
                i += 1
            out.append('<blockquote>%s</blockquote>' % inline(' '.join(buf)))
            continue

        # 列表（含 - [ ] 复选框）
        if re.match(r'^(\s*[-*]\s|\s*\d+\.\s)', line):
            ordered = bool(re.match(r'^\s*\d+\.\s', line))
            items = []
            while i < n and re.match(r'^(\s*[-*]\s|\s*\d+\.\s)', lines[i]):
                item = re.sub(r'^\s*(?:[-*]|\d+\.)\s+', '', lines[i]).strip()
                if item.startswith('[ ]'):
                    item = '<span class="checkbox">☐</span> ' + item[3:].strip()
                elif item.startswith(('[x]', '[X]')):
                    item = '<span class="checkbox">☑</span> ' + item[3:].strip()
                items.append('<li>%s</li>' % inline(item))
                i += 1
            tag = 'ol' if ordered else 'ul'
            out.append('<%s>%s</%s>' % (tag, ''.join(items), tag))
            continue

        # 空行
        if not stripped:
            i += 1
            continue

        # 段落
        buf = []
        while i < n and lines[i].strip() \
                and not lines[i].strip().startswith(('#', '```', '|', '>')) \
                and not re.match(r'^(\s*[-*]\s|\s*\d+\.\s)', lines[i]) \
                and lines[i].strip() != '---':
            buf.append(lines[i].strip())
            i += 1
        if buf:
            out.append('<p>%s</p>' % inline(' '.join(buf)))
        else:
            i += 1

    return '\n'.join(out), toc


TEMPLATE = '''<!doctype html>
<html lang="zh-CN" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TK 自动混剪 SaaS — API 参考文档 v2.0</title>
<style>
:root { --radius: 10px; }
html[data-theme="dark"] {
  --bg:#0f1420; --panel:#151c2b; --panel2:#1b2436; --text:#e6edf7; --muted:#93a3bd;
  --border:#2a3650; --accent:#5ea8ff; --accent2:#7ee0c0; --code:#0b101a; --mark:#ffd166;
}
html[data-theme="light"] {
  --bg:#f6f8fc; --panel:#ffffff; --panel2:#f0f4fa; --text:#1a2436; --muted:#5b6b84;
  --border:#dce4f0; --accent:#1668d6; --accent2:#087f6a; --code:#f2f5fa; --mark:#fff0a8;
}
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--text);
  font:15px/1.75 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif; }
a { color:var(--accent); text-decoration:none; }
a:hover { text-decoration:underline; }
.layout { display:flex; min-height:100vh; }
.sidebar { width:290px; flex:0 0 290px; background:var(--panel); border-right:1px solid var(--border);
  height:100vh; position:sticky; top:0; display:flex; flex-direction:column; }
.brand { padding:20px 20px 14px; border-bottom:1px solid var(--border); }
.brand b { display:block; font-size:16px; letter-spacing:.3px; }
.brand span { color:var(--muted); font-size:12px; }
.tools { padding:12px 14px; border-bottom:1px solid var(--border); display:flex; gap:8px; }
#search { flex:1; min-width:0; background:var(--panel2); border:1px solid var(--border); color:var(--text);
  border-radius:8px; padding:7px 10px; font-size:13px; outline:none; }
#search:focus { border-color:var(--accent); }
#theme { background:var(--panel2); border:1px solid var(--border); color:var(--muted);
  border-radius:8px; width:34px; cursor:pointer; font-size:14px; }
.toc { overflow-y:auto; padding:10px 10px 40px; flex:1; }
.toc-item { display:block; padding:5px 10px; border-radius:7px; color:var(--muted); font-size:13px;
  white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.toc-item:hover { background:var(--panel2); color:var(--text); text-decoration:none; }
.toc-item.active { background:var(--panel2); color:var(--accent); font-weight:600; }
.toc-item.lvl3 { padding-left:24px; font-size:12.5px; opacity:.88; }
.toc-item.hidden { display:none; }
.main { flex:1; min-width:0; }
.content { max-width:960px; margin:0 auto; padding:36px 44px 120px; }
h1 { font-size:30px; line-height:1.35; margin:0 0 24px; padding-bottom:18px; border-bottom:2px solid var(--accent); }
h2 { font-size:22px; margin:52px 0 18px; padding-top:10px; border-top:1px solid var(--border); }
h3 { font-size:18px; margin:32px 0 12px; color:var(--accent2); }
h4 { font-size:15px; margin:24px 0 10px; }
p { margin:12px 0; }
hr { border:0; border-top:1px solid var(--border); margin:28px 0; }
code { background:var(--panel2); border:1px solid var(--border); border-radius:5px;
  padding:1px 5px; font-size:.88em; font-family:"SFMono-Regular",Consolas,"Liberation Mono",monospace; }
pre.code { position:relative; background:var(--code); border:1px solid var(--border);
  border-radius:var(--radius); padding:16px 18px; overflow-x:auto; margin:16px 0; }
pre.code code { background:none; border:0; padding:0; font-size:13px; line-height:1.65; color:var(--text); }
pre.code[data-lang]:not([data-lang=""])::before { content:attr(data-lang); position:absolute; top:8px; right:12px;
  color:var(--muted); font-size:11px; text-transform:uppercase; letter-spacing:.6px; }
.table-wrap { overflow-x:auto; margin:16px 0; border:1px solid var(--border); border-radius:var(--radius); }
table { border-collapse:collapse; width:100%; font-size:13.5px; }
th, td { padding:9px 12px; text-align:left; border-bottom:1px solid var(--border); vertical-align:top; }
th { background:var(--panel2); font-weight:600; white-space:nowrap; }
tr:last-child td { border-bottom:0; }
tbody tr:hover { background:var(--panel2); }
blockquote { margin:16px 0; padding:10px 16px; background:var(--panel2);
  border-left:3px solid var(--accent); border-radius:0 var(--radius) var(--radius) 0; color:var(--muted); }
blockquote strong { color:var(--text); }
ul, ol { padding-left:24px; margin:12px 0; }
li { margin:5px 0; }
.checkbox { color:var(--accent2); }
@media (max-width: 900px) {
  .sidebar { display:none; } .content { padding:24px 18px 80px; }
}
</style>
</head>
<body>
<div class="layout">
  <aside class="sidebar">
    <div class="brand"><b>TK 自动混剪 SaaS</b><span>API 参考文档 · v2.0 · 2026-11-11</span></div>
    <div class="tools">
      <input id="search" type="search" placeholder="筛选目录… (Ctrl+K)" autocomplete="off">
      <button id="theme" title="切换深色 / 浅色">◐</button>
    </div>
    <nav class="toc" id="toc">__TOC__</nav>
  </aside>
  <main class="main"><article class="content" id="content">__BODY__</article></main>
</div>
<script>
document.getElementById('theme').addEventListener('click', function () {
  var h = document.documentElement;
  h.setAttribute('data-theme', h.getAttribute('data-theme') === 'dark' ? 'light' : 'dark');
});
var search = document.getElementById('search');
var items = Array.prototype.slice.call(document.querySelectorAll('.toc-item'));
search.addEventListener('input', function () {
  var q = this.value.trim().toLowerCase();
  items.forEach(function (a) {
    a.classList.toggle('hidden', q !== '' && a.textContent.toLowerCase().indexOf(q) === -1);
  });
});
document.addEventListener('keydown', function (e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); search.focus(); }
});
var links = Array.prototype.slice.call(document.querySelectorAll('.toc-item'));
if ('IntersectionObserver' in window) {
  var obs = new IntersectionObserver(function (entries) {
    entries.forEach(function (en) {
      if (!en.isIntersecting) return;
      links.forEach(function (a) { a.classList.toggle('active', a.dataset.id === en.target.id); });
      var active = document.querySelector('.toc-item.active');
      if (active) active.scrollIntoView({ block: 'nearest' });
    });
  }, { rootMargin: '-10% 0px -80% 0px' });
  links.forEach(function (a) {
    var t = document.getElementById(a.dataset.id);
    if (t) obs.observe(t);
  });
}
</script>
</body>
</html>
'''


def main():
    # 可选参数：python build_html.py <src.md> <out.html> [侧栏副标题]
    src = sys.argv[1] if len(sys.argv) > 1 else SRC
    out = sys.argv[2] if len(sys.argv) > 2 else OUT
    subtitle = sys.argv[3] if len(sys.argv) > 3 else DEFAULT_TITLE
    md = open(src, encoding='utf-8').read()
    body, toc = render(md)
    toc_html = ''.join(
        '<a class="toc-item lvl%d" href="#%s" data-id="%s">%s</a>' % (
            lvl, hid, hid, html.escape(text))
        for lvl, text, hid in toc
    )
    heading = re.search(r'^#\s+(.+)$', md, re.M)
    title = heading.group(1).strip() if heading else os.path.basename(src)
    doc = (TEMPLATE.replace('__TOC__', toc_html)
           .replace('__BODY__', body)
           .replace('<title>TK 自动混剪 SaaS — API 参考文档 v2.0</title>',
                    '<title>%s</title>' % html.escape(title))
           .replace(DEFAULT_TITLE, html.escape(subtitle)))
    open(out, 'w', encoding='utf-8').write(doc)
    print('generated:', out)
    print('  tables   :', body.count('<table>'))
    print('  code     :', body.count('<pre class="code"'))
    print('  headings :', len(re.findall(r'<h[1-6] id=', body)))
    print('  toc items:', len(toc))


if __name__ == '__main__':
    main()
