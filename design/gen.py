#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成三套像素风 UI 设计稿（纯静态 HTML，用浏览器截图看效果）。"""
import string

CSS = string.Template("""
*{box-sizing:border-box;margin:0;padding:0}
body{background:$page;padding:24px 28px 40px;
  font-family:"Noto Sans CJK SC","Source Han Sans SC","WenQuanYi Zen Hei","Microsoft YaHei",sans-serif;
  color:$pageText}
.title b{display:block;font-size:20px;letter-spacing:2px;color:$accent}
.title p{font-size:12px;line-height:1.7;margin-top:6px;opacity:.85}
.stage{display:flex;gap:30px;margin-top:20px;align-items:flex-start}
.wrap{width:360px}
.cap{display:flex;gap:8px;align-items:center;margin-bottom:8px;
  font:700 12px/1 "DejaVu Sans Mono",monospace;letter-spacing:1px;color:$pageText}
.cap i{background:$accent;color:$onAccent;padding:3px 7px;font-style:normal;font-size:11px}

.phone{width:360px;height:760px;background:$bg;border:4px solid $ink;
  box-shadow:10px 10px 0 $shadow;display:flex;flex-direction:column;overflow:hidden}
.scr{flex:1;display:flex;flex-direction:column;min-height:0}
.sb{height:22px;display:flex;justify-content:space-between;align-items:center;
  padding:0 10px;font:400 10px/1 "DejaVu Sans Mono",monospace;color:$sub}

.hd{display:flex;justify-content:space-between;align-items:center;
  padding:6px 12px 8px;border-bottom:3px solid $ink}
.hd-t{font-size:19px;font-weight:800;letter-spacing:2px;line-height:1.1}
.hd-s{font:400 10px/1.5 "DejaVu Sans Mono",monospace;color:$sub;margin-top:2px}
.hd-badge{border:2px solid $line;color:$sub;font:700 10px/1 "DejaVu Sans Mono",monospace;
  padding:5px 6px}

.body{flex:1;display:flex;flex-direction:column;gap:8px;padding:10px;min-height:0}
.card{border:3px solid $ink;background:$panel;box-shadow:4px 4px 0 $shadow;padding:10px}

.seg{display:flex;gap:6px;font-size:11px;font-weight:700}
.seg span{padding:4px 9px;border:2px solid $line;color:$sub}
.seg span.on{background:$accent;color:$onAccent;border-color:$ink}

.amt-n{display:flex;align-items:baseline;gap:3px;margin-top:8px;
  font:800 42px/1 "DejaVu Sans Mono",monospace;letter-spacing:-1px;color:$text}
.amt-n i{font-size:23px;font-style:normal;color:$sub}
.amt-n em{font-style:normal;font-size:30px;color:$accent;animation:none}
.amt-h{font-size:10px;line-height:1.5;color:$sub;margin-top:7px}

.modes{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
.mode{border:3px solid $ink;background:$panel2;box-shadow:4px 4px 0 $shadow;
  display:flex;flex-direction:column;align-items:center;gap:4px;padding:7px 0 5px;
  font-size:12px;font-weight:700;color:$text}
.mode i{font-style:normal;font:400 9px/1 "DejaVu Sans Mono",monospace;opacity:.75}
.mode.on{background:$accent;color:$onAccent}

.scenes{display:flex;flex-wrap:wrap;gap:6px}
.chip{display:flex;align-items:center;gap:4px;border:2px solid $line;background:$panel;
  padding:5px 8px;font-size:12px;font-weight:700;color:$sub}
.chip.on{border-color:$ink;background:$accent2;color:$onAccent2;box-shadow:3px 3px 0 $shadow}
.chip .e{font-size:12px}

.more{font:400 10px/1 "DejaVu Sans Mono",monospace;color:$sub;text-align:right}
.spacer{flex:1}

.pad{display:grid;grid-template-columns:repeat(4,1fr);gap:6px}
.key{height:52px;display:flex;align-items:center;justify-content:center;
  border:3px solid $ink;background:$panel2;box-shadow:4px 4px 0 $shadow;
  font:800 20px/1 "DejaVu Sans Mono",monospace;color:$text}
.key.fn{font-size:15px;color:$accent}
.key.save{grid-row:span 2;height:auto;flex-direction:column;gap:3px;
  background:$accent;color:$onAccent;font-family:inherit;font-size:14px;font-weight:800}
.key.save small{font:700 10px/1 "DejaVu Sans Mono",monospace;opacity:.8}

.tabs{display:grid;grid-template-columns:repeat(3,1fr);border-top:3px solid $ink;background:$panel}
.tab{display:flex;flex-direction:column;align-items:center;gap:3px;padding:7px 0 9px;
  font-size:11px;font-weight:700;color:$sub;border-right:2px solid $line}
.tab:last-child{border-right:0}
.tab.on{color:$text;background:$panel2;box-shadow:inset 0 3px 0 $accent}

/* 账单 */
.sum-top{display:flex;justify-content:space-between;align-items:center;
  font:700 12px/1 "DejaVu Sans Mono",monospace}
.sum-top .nav{color:$sub;letter-spacing:4px}
.sum-big{font:800 30px/1 "DejaVu Sans Mono",monospace;margin-top:10px}
.sum-big small{font-size:12px;color:$sub;font-weight:700;margin-right:5px}
.sum-cols{display:flex;justify-content:space-between;margin-top:10px;
  border-top:2px dashed $line;padding-top:8px}
.sum-cols div{font:700 10px/1.4 "DejaVu Sans Mono",monospace;color:$sub}
.sum-cols b{display:block;font-size:13px;color:$text;margin-top:3px}
.sum-cols b.up{color:$accent2}

.days{display:flex;flex-direction:column;gap:6px;overflow:hidden}
.dayhd{display:flex;justify-content:space-between;padding:2px 2px 0;
  font:700 10px/1.4 "DejaVu Sans Mono",monospace;color:$sub}
.row{display:flex;align-items:center;gap:8px;padding:8px 2px;border-bottom:2px dashed $line}
.row .ico{width:30px;height:30px;flex:none;border:2px solid $ink;background:$panel2;
  display:flex;align-items:center;justify-content:center;font-size:15px}
.row .t{flex:1;min-width:0}
.row .t b{display:block;font-size:13px;line-height:1.3}
.row .t s{display:block;font:400 10px/1.3 "DejaVu Sans Mono",monospace;
  color:$sub;text-decoration:none;margin-top:2px}
.row .v{font:800 14px/1 "DejaVu Sans Mono",monospace}
.row .v.up{color:$accent2}

/* 设置 */
.me{display:flex;gap:10px;align-items:center}
.me .box{width:46px;height:46px;border:3px solid $ink;background:$accent2;
  display:flex;align-items:center;justify-content:center;font-size:22px;flex:none}
.me b{font-size:14px}
.me p{font:400 10px/1.5 "DejaVu Sans Mono",monospace;color:$sub;margin-top:3px}
.item{display:flex;justify-content:space-between;align-items:center;
  padding:13px 2px;border-bottom:2px dashed $line;font-size:13px;font-weight:700}
.item:last-child{border-bottom:0}
.item i{font-style:normal;font:400 11px/1 "DejaVu Sans Mono",monospace;color:$sub}
.item.danger{color:$danger}
.tip{font:400 10px/1.6 "DejaVu Sans Mono",monospace;color:$sub;text-align:center;padding:4px}

$extra
""")

ICONS_JS = """
<script>
const ICONS={
 keyboard:["........","########","#.#.#.##","########","#.#.#.##","########","........","........"],
 ledger:["........",".#.#.#..",".######.",".#....#.",".#.##.#.",".#....#.",".######.","........"],
 mic:["..####..","..#..#..","..#..#..","..#..#..","..#..#..",".######.","..#..#..","...##..."],
 camera:["........","..####..",".######.","########","##.##.##","##.##.##","########","........"],
 book:[".#....#.",".#....#.","..#..#..","...#....","..####..","...##...","..####..","........"],
 list:["........","########","#......#","#.####.#","#......#","#.####.#","#......#","########"],
 gear:["...##...",".######.","##.##.##","########","##.##.##","##.##.##",".######.","...##..."]
};
document.querySelectorAll('.pix').forEach(function(el){
  var p=ICONS[el.dataset.icon]; if(!p) return;
  var s=parseInt(el.dataset.size||'22',10);
  var c=document.createElement('canvas');
  c.width=8;c.height=8;c.style.width=s+'px';c.style.height=s+'px';
  c.style.imageRendering='pixelated';c.style.display='block';
  var g=c.getContext('2d');
  g.fillStyle=el.dataset.color||getComputedStyle(el).color;
  p.forEach(function(row,y){for(var x=0;x<8;x++){if(row[x]==='#')g.fillRect(x,y,1,1);}});
  el.appendChild(c);
});
</script>
"""

PALETTES = {
  "A": dict(
    name="A", title="暗夜街机",
    desc="深色底 + 荧光绿，最像 8-bit 掌机；晚上看不刺眼，数字最醒目。",
    bg="#14142B", panel="#20203D", panel2="#2C2C52", ink="#05050C", line="#44446E",
    text="#ECECFF", sub="#8F8FC4", accent="#8CFF3F", on_accent="#0B0B16",
    accent2="#FFD447", on_accent2="#14142B", danger="#FF5F6D",
    shadow="#05050C", page="#0A0A12", page_text="#9A9AC8",
    extra="""
.scr{background-image:repeating-linear-gradient(0deg,rgba(255,255,255,.035) 0 1px,transparent 1px 3px)}
.amt-n b{color:#8CFF3F;text-shadow:0 0 10px rgba(140,255,63,.35)}
"""),
  "B": dict(
    name="B", title="奶油像素",
    desc="奶油浅底 + 橙红重点块，白天清爽、字看得清；像素感靠硬边框和实心投影。",
    bg="#FBF3E4", panel="#FFFCF2", panel2="#F1E6D0", ink="#2A2117", line="#CBB99A",
    text="#2A2117", sub="#8A7A62", accent="#F2724B", on_accent="#FFF9F0",
    accent2="#4B9FD6", on_accent2="#FFF9F0", danger="#D8434E",
    shadow="#2A2117", page="#E8DCC6", page_text="#6B5B45",
    extra="""
.scr{background-image:radial-gradient(rgba(42,33,23,.07) 1px,transparent 1px);background-size:6px 6px}
.amt-n b{color:#2A2117}
"""),
  "C": dict(
    name="C", title="小票质感",
    desc="米白纸 + 印章红，像一张会算账的小票；虚线分隔、等宽数字，最「记账」。",
    bg="#F7F2E4", panel="#FFFEF7", panel2="#EFE7D2", ink="#1B1712", line="#B5A88B",
    text="#1B1712", sub="#7A6E58", accent="#C9433C", on_accent="#FFFDF5",
    accent2="#2F6B57", on_accent2="#FFFDF5", danger="#C9433C",
    shadow="#1B1712", page="#E5DCC6", page_text="#6B5B45",
    extra="""
.scr{background-image:repeating-linear-gradient(90deg,rgba(27,23,18,.025) 0 1px,transparent 1px 5px)}
.card{border-width:2px;box-shadow:3px 3px 0 #1B1712}
.amt-n b{color:#C9433C}
.hd-t{letter-spacing:4px}
"""),
}

TAB = """
<div class="tabs">
  <div class="tab on"><span class="pix" data-icon="book" data-size="20"></span>记账</div>
  <div class="tab"><span class="pix" data-icon="list" data-size="20"></span>账单</div>
  <div class="tab"><span class="pix" data-icon="gear" data-size="20"></span>设置</div>
</div>"""


def ledger(p):
    scenes = [("🍜", "吃饭", True), ("🍿", "零食", False), ("🥬", "买菜", False),
              ("🛒", "网购", False), ("⚡", "充电", False), ("🚕", "打车", False),
              ("🧴", "日用", False), ("📦", "其他", False)]
    chips = "\n".join(
        '<div class="chip%s"><span class="e">%s</span>%s</div>' % (" on" if on else "", e, n)
        for e, n, on in scenes)
    keys = ["7", "8", "9", "⌫", "4", "5", "6", "×"]
    pad = "".join('<div class="key">%s</div>' % k for k in keys)
    pad += ('<div class="key">1</div><div class="key">2</div><div class="key">3</div>'
            '<div class="key save">保存<small>¥0.00</small></div>'
            '<div class="key fn">.</div><div class="key">0</div><div class="key">00</div>')
    return f"""
<div class="wrap">
  <div class="cap"><i>{p['name']}</i>记账 · 默认手动</div>
  <div class="phone"><div class="scr">
    <div class="sb"><span>11:47</span><span>▮▮▮ 100%</span></div>
    <div class="hd">
      <div><div class="hd-t">记账本</div><div class="hd-s">11月2日 周六</div></div>
      <div class="hd-badge">3.2</div>
    </div>
    <div class="body">
      <div class="card">
        <div class="seg"><span class="on">支出</span><span>收入</span></div>
        <div class="amt-n">¥ <b>0</b><i>.00</i><em>▏</em></div>
        <div class="amt-h">现在 12:30 · 自动归到「吃饭」· 点归类可改</div>
      </div>

      <div class="modes">
        <div class="mode on"><span class="pix" data-icon="keyboard" data-size="22"></span>手动<i>默认</i></div>
        <div class="mode"><span class="pix" data-icon="mic" data-size="22"></span>语音<i>说一句</i></div>
        <div class="mode"><span class="pix" data-icon="camera" data-size="22"></span>拍照<i>小票</i></div>
      </div>

      <div class="scenes">
        {chips}
        <div class="chip">⋯ 更多</div>
      </div>

      <div class="more">⋯ 地点 · 数量 · 日期 · 备注</div>
      <div class="spacer"></div>
      <div class="pad">{pad}</div>
    </div>
    {TAB}
  </div></div>
</div>"""


def bills(p):
    rows = [
        ("11月2日 周六", "支 ¥63.90", [
            ("🍜", "午饭", "永辉超市 · 吃饭 · 12:30", "−15.70", ""),
            ("🍿", "咖啡", "Manner · 零食 · 09:10", "−20.00", ""),
            ("🥬", "青菜水果", "永辉超市 · 买菜 · 18:20", "−28.20", ""),
        ]),
        ("11月1日 周日", "支 ¥151.50", [
            ("⚡", "充电", "特来电 · 充电 · ×30度", "−50.00", ""),
            ("🛒", "洗衣液", "淘宝 · 网购 · 21:05", "−78.00", ""),
            ("🚕", "打车", "滴滴 · 打车 · 08:40", "−23.50", ""),
        ]),
    ]
    html = ""
    for day, total, items in rows:
        html += f'<div class="dayhd"><span>{day}</span><span>{total}</span></div>'
        for ico, name, sub, val, up in items:
            html += (f'<div class="row"><div class="ico">{ico}</div><div class="t">'
                     f'<b>{name}</b><s>{sub}</s></div><div class="v {up}">{val}</div></div>')
    return f"""
<div class="wrap">
  <div class="cap"><i>{p['name']}</i>账单 · 按月</div>
  <div class="phone"><div class="scr">
    <div class="sb"><span>11:47</span><span>▮▮▮ 100%</span></div>
    <div class="hd">
      <div><div class="hd-t">账单</div><div class="hd-s">2026年11月</div></div>
      <div class="hd-badge">统计 ›</div>
    </div>
    <div class="body">
      <div class="card">
        <div class="sum-top"><span>‹ 11月 ›</span><span class="nav">◀ ▶</span></div>
        <div class="sum-big"><small>支出</small>¥215.40</div>
        <div class="sum-cols">
          <div>收入<b class="up">¥8,000</b></div>
          <div>结余<b>¥7,784.60</b></div>
          <div>笔数<b>6</b></div>
        </div>
      </div>
      <div class="days">{html}</div>
      <div class="spacer"></div>
      <div class="card"><div class="sum-top"><span>本月最大头</span><span>🛒 网购 36%</span></div></div>
    </div>
    {TAB}
  </div></div>
</div>"""


def settings(p):
    return f"""
<div class="wrap">
  <div class="cap"><i>{p['name']}</i>设置</div>
  <div class="phone"><div class="scr">
    <div class="sb"><span>11:47</span><span>▮▮▮ 100%</span></div>
    <div class="hd">
      <div><div class="hd-t">设置</div><div class="hd-s">本地账本 · 不联网</div></div>
      <div class="hd-badge">v3.2</div>
    </div>
    <div class="body">
      <div class="card me">
        <div class="box"><span class="pix" data-icon="ledger" data-size="26" data-color="{p['ink']}"></span></div>
        <div><b>只存在这台手机</b><p>SQLite 本地存储 · 无账号 · 无广告</p></div>
      </div>
      <div class="card">
        <div class="item"><span>归类管理</span><i>支出 16 · 收入 6 ›</i></div>
        <div class="item"><span>语音与定位</span><i>麦克风 已允许 ›</i></div>
        <div class="item"><span>常用归类排序</span><i>近 90 天 ›</i></div>
        <div class="item"><span>外观</span><i>像素风 ›</i></div>
        <div class="item"><span>关于</span><i>3.2 · 离线 ›</i></div>
        <div class="item danger"><span>清空所有记录</span><i>不可恢复 ›</i></div>
      </div>
      <div class="card">
        <div class="sum-top"><span>本月回顾</span><span>11月</span></div>
        <div class="sum-cols">
          <div>笔数<b>6</b></div>
          <div>支出<b>¥215.40</b></div>
          <div>结余<b>¥7,784.60</b></div>
        </div>
      </div>
      <div class="spacer"></div>
      <div class="tip">长按归类可删除 · 每笔只归一个场景</div>
    </div>
    {TAB}
  </div></div>
</div>"""


HEAD = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>{title}</title>
<style>{css}</style></head><body>
<div class="title"><b>{name} · {title}</b><p>{desc}</p></div>
<div class="stage">{screens}</div>
{js}
</body></html>
"""


def build(p):
    css = CSS.substitute({
        "bg": p["bg"], "panel": p["panel"], "panel2": p["panel2"],
        "ink": p["ink"], "line": p["line"], "text": p["text"], "sub": p["sub"],
        "accent": p["accent"], "onAccent": p["on_accent"],
        "accent2": p["accent2"], "onAccent2": p["on_accent2"],
        "danger": p["danger"], "shadow": p["shadow"], "page": p["page"],
        "pageText": p["page_text"], "extra": p["extra"],
    })
    screens = ledger(p) + bills(p) + settings(p)
    return HEAD.format(name=p["name"], title=p["title"], desc=p["desc"],
                       css=css, screens=screens, js=ICONS_JS)


if __name__ == "__main__":
    import os
    out = os.path.dirname(os.path.abspath(__file__))
    for key, p in PALETTES.items():
        path = os.path.join(out, "v%s.html" % p["name"].lower())
        with open(path, "w", encoding="utf-8") as f:
            f.write(build(p))
        print("wrote", path)
