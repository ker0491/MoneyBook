#!/usr/bin/env python3
"""3.5 像素版冒烟测试：三 tab + 快捷键盘记账 + 自定义小类 + 我的地点 + 账单 + 统计 + 报表 + 导出 + 语音手输 + 小票。

用法：python3 ui_test.py [serial]
默认跑 emulator-5554，也可以传真机序列号。
"""
import base64
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ADB = os.environ.get("ADB") or os.path.expanduser("~/projects/toolchain/sdk/platform-tools/adb")
SHOTS = os.environ.get("SHOTS") or os.path.join(HERE, "screenshots")
PKG = "com.k.moneybook"
RECEIPT = os.path.join(HERE, "tests", "receipt_sample.png")

if len(sys.argv) > 1:
    os.environ["ANDROID_SERIAL"] = sys.argv[1]

IS_EMULATOR = os.environ.get("ANDROID_SERIAL", "emulator-5554").startswith("emulator-")

os.makedirs(SHOTS, exist_ok=True)


def sh(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True)


def dump(tag="ui"):
    path = f"/tmp/opencode/{tag}.xml"
    for _ in range(3):
        sh("shell", "rm", "-f", "/sdcard/ui.xml")
        sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        sh("pull", "/sdcard/ui.xml", path)
        if os.path.exists(path) and os.path.getsize(path) > 100:
            try:
                return ET.parse(path).getroot()
            except ET.ParseError:
                pass
        time.sleep(0.8)
    raise RuntimeError(f"uiautomator dump 失败: {tag}")


def nodes(root):
    return list(root.iter("node"))


def find_id(root, rid, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes(root):
            if n.attrib.get("resource-id", "").endswith(":id/" + rid):
                return n
        time.sleep(0.4)
        root.clear()
        root.extend(dump())
    raise RuntimeError(f"找不到控件 {rid}")


def find_text(root, text, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes(root):
            if n.attrib.get("text", "") == text:
                return n
        time.sleep(0.4)
        root.clear()
        root.extend(dump())
    raise RuntimeError(f"找不到文字 {text}")


def find_part(root, part, timeout=10):
    """找文字里包含 part 的控件（图标 + 名字可能在同一行）。"""
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes(root):
            if part in n.attrib.get("text", ""):
                return n
        time.sleep(0.4)
        root.clear()
        root.extend(dump())
    raise RuntimeError(f"找不到包含 {part} 的控件")


def edit_texts(root):
    return [n for n in nodes(root)
            if n.attrib.get("class", "").endswith("EditText")]


def clear_text():
    """小米 HyperOS 会拦截 adb 的 input text，改用 ADBKeyboard 输入法的广播接口。"""
    sh("shell", "am", "broadcast", "-a", "ADB_CLEAR_TEXT")
    time.sleep(0.4)


def type_text(text, clear=True):
    if clear:
        clear_text()
    msg = base64.b64encode(text.encode("utf-8")).decode()
    sh("shell", "am", "broadcast", "-a", "ADB_INPUT_B64", "--es", "msg", msg)
    time.sleep(0.6)


PERMS = ["android.permission.ACCESS_COARSE_LOCATION",
         "android.permission.ACCESS_FINE_LOCATION",
         "android.permission.RECORD_AUDIO"]


def grant_perms():
    """MIUI 会在 force-stop 后回收敏感权限，每次重启 App 前补授一次。"""
    for perm in PERMS:
        sh("shell", "pm", "grant", PKG, perm)


def dismiss_system_dialog(root):
    """系统弹窗（定位授权 / 崩溃提示）会挡住界面，出现就点掉。"""
    for label in ["仅在使用中允许", "本次使用允许", "允许", "Wait"]:
        if has_text(root, label):
            tap(find_text(root, label), wait=1.5)
            return True
    return False


def hide_keyboard():
    # 返回键先收键盘；ESC 会把整个 AlertDialog 取消掉，不能用
    sh("shell", "input", "keyevent", "4")
    time.sleep(0.8)


def wait_text(root, text, timeout=15):
    """OCR 是异步的，等目标文字出现。"""
    end = time.time() + timeout
    while time.time() < end:
        if has_text(root, text):
            return True
        time.sleep(1.0)
        root.clear()
        root.extend(dump())
    return False


def wait_part(root, part, timeout=20):
    """OCR 识别异步且结果有波动，等「包含某段文字」出现。"""
    end = time.time() + timeout
    while time.time() < end:
        if has_part(root, part):
            return True
        time.sleep(1.0)
        root.clear()
        root.extend(dump())
    return False


def has_text(root, text):
    return any(n.attrib.get("text", "") == text for n in nodes(root))


def has_part(root, part):
    return any(part in n.attrib.get("text", "") for n in nodes(root))


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.attrib["bounds"]))
    return (x1 + x2) // 2, (y1 + y2) // 2


def app_in_front():
    """有人在用手机时前台不是记账本；这时绝不点击，避免误触别人的界面。"""
    for line in sh("shell", "dumpsys", "window").stdout.splitlines():
        if "mCurrentFocus" in line:
            return PKG in line
    return False


def tap(n, wait=0.8):
    if not app_in_front():
        raise AssertionError("前台不是记账本（可能有人在用手机），已停止自动点击")
    x, y = center(n)
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def long_press(n, ms=900):
    x, y = center(n)
    sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), str(ms))
    time.sleep(1.2)


def back(wait=1.2):
    sh("shell", "input", "keyevent", "4")
    time.sleep(wait)


def snap(name):
    subprocess.run(f"{ADB} exec-out screencap -p > {SHOTS}/{name}.png", shell=True)
    print("截图:", name)


def texts(root):
    return [n.attrib["text"] for n in nodes(root) if n.attrib.get("text")]


def expect(condition, message):
    if not condition:
        raise AssertionError(message)
    print("  ✓", message)


def key(root, label):
    """点键盘上的一个键（按文字找）。"""
    tap(find_text(root, label))
    time.sleep(0.25)


def main():
    print("== 启动 ==")
    # 每次从干净数据开始跑
    sh("shell", "pm", "clear", PKG)
    # 自动化测试直接授权，避免首次启动的系统弹窗挡住界面
    grant_perms()
    if IS_EMULATOR:
        # 郑州附近给一个固定坐标，后面「我的地点」可以命中
        sh("emu", "geo", "fix", "114.020000", "32.980000")
    sh("shell", "input", "keyevent", "224")
    sh("shell", "wm", "dismiss-keyguard")
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(2.5)
    root = dump("q0")
    if dismiss_system_dialog(root):
        root = dump("q0b")
    snap("32-01-quick")

    print("== 记账页默认状态 ==")
    expect(has_text(root, "手动"), "记账页有「手动」入口")
    expect(has_text(root, "语音"), "记账页有「语音」入口")
    expect(has_text(root, "拍照"), "记账页有「拍照」入口")
    expect(has_text(root, "最近记的"), "记账页有「最近记的」卡片")
    expect(has_text(root, "保存"), "记账页有「保存」键")
    expect(find_id(root, "quickAmount") is not None, "金额卡存在")

    print("== 键盘记一笔 12.50 ==")
    for label in ["1", "2", ".", "5"]:
        key(root, label)
    root = dump("q1")
    expect("¥12.5" in "".join(texts(root)), "金额随按键变化")
    tap(find_id(root, "keySave"), wait=1.6)
    root = dump("q2")
    expect(has_part(root, "12.50"), "保存后「最近记的」出现 12.50")
    snap("32-02-quick-saved")

    print("== 账单页 ==")
    tap(find_id(root, "tabBills"), wait=1.5)
    root = dump("b1")
    expect(has_text(root, "账单"), "切到账单页")
    expect(has_part(root, "12.50"), "账单列表里有这笔")
    expect(find_id(root, "expenseText") is not None, "汇总卡存在")
    snap("32-03-bills")

    print("== 统计页 ==")
    tap(find_id(root, "btnStats"), wait=2.0)
    root = dump("s1")
    expect(has_text(root, "统计"), "打开统计页")
    expect(has_part(root, "支出合计"), "统计页有支出合计")
    expect(has_part(root, "大类排行"), "统计页有大类排行")
    expect(not has_part(root, "小类排行"), "没有小类数据时不显示空的小类排行")
    for label in ["本月", "上月", "近三月", "今年", "全部", "自定义"]:
        expect(has_text(root, label), f"范围标签「{label}」完整可见")
    snap("32-04-stats")
    back()
    root = dump("s2")

    print("== 报表页 ==")
    tap(find_id(root, "btnReport"), wait=2.0)
    root = dump("r1")
    expect(has_text(root, "报表"), "打开报表页")
    expect(has_part(root, "支出合计"), "报表有合计卡")
    for label in ["地点", "大类", "小类", "关键词", "导出 ›"]:
        expect(has_text(root, label), f"报表有「{label}」")
    expect(has_text(root, "本月"), "报表有本月范围")
    snap("33-01-report")

    print("== 报表：按大类筛选 ==")
    tap(find_id(root, "rowSceneFilter"), wait=1.2)
    root = dump("r2")
    expect(has_text(root, "筛选大类"), "打开大类筛选弹窗")
    options = [n for n in nodes(root) if n.attrib.get("text") == "其他"]
    expect(len(options) > 0, "大类弹窗里有「其他」")
    tap(options[0])
    tap(find_text(root, "确定"), wait=1.5)
    root = dump("r3")
    expect(has_part(root, "已选") or has_text(root, "其他"), "大类筛选已生效")
    expect(not has_part(root, "没有符合筛选条件"), "筛选后还有记录")
    expect(has_part(root, "支出合计"), "筛选后仍显示合计")
    snap("33-02-report-filter")

    print("== 报表：导出入口 ==")
    tap(find_id(root, "btnExport"), wait=1.2)
    root = dump("r4")
    expect(has_text(root, "保存 CSV 到文件（Excel 打开）"), "报表可保存 CSV")
    expect(has_text(root, "保存 JSON 到文件（完整备份）"), "报表可保存 JSON")
    expect(has_text(root, "分享 CSV"), "报表可分享 CSV")
    snap("33-03-report-export")
    back()
    back()

    print("== 设置页 ==")
    root = dump("g1")
    tap(find_id(root, "tabSettings"), wait=1.5)
    root = dump("g1")
    expect(has_text(root, "只存在这台手机"), "设置页有存储说明")
    expect(has_text(root, "归类管理"), "设置页有归类管理")
    expect(has_text(root, "商品记忆"), "设置页有商品记忆")
    expect(has_text(root, "商家记忆"), "设置页有商家记忆")
    expect(has_text(root, "我的地点"), "设置页有「我的地点」")
    expect(has_text(root, "使用说明"), "设置页有使用说明")
    expect(has_text(root, "导出数据"), "设置页有导出入口")
    snap("32-05-settings")

    print("== 设置页：导出弹窗 ==")
    tap(find_id(root, "setExport"), wait=1.2)
    root = dump("g0")
    expect(has_text(root, "保存 CSV 到文件（Excel 打开）"), "导出弹窗有 CSV")
    expect(has_text(root, "保存 JSON 到文件（完整备份）"), "导出弹窗有 JSON")
    expect(has_text(root, "分享 CSV"), "导出弹窗有分享")
    snap("33-04-export-dialog")
    back()

    tap(find_id(root, "setScenes"), wait=1.5)
    root = dump("g2")
    expect(has_text(root, "支出归类"), "归类管理弹窗有支出归类")
    snap("32-06-scenes")
    sh("shell", "input", "swipe", "540", "1500", "540", "950", "300")
    time.sleep(1.0)
    root = dump("g3")
    expect(has_text(root, "收入归类"), "弹窗往下滚能看到收入归类")
    back()

    print("== 自定义小类 ==")
    tap(find_id(root, "setScenes"), wait=1.5)
    root = dump("u1")
    food = None
    for n in nodes(root):
        t = n.attrib.get("text", "")
        if t.endswith("食") and "支出" not in t:
            food = n
            break
    expect(food is not None, "归类管理里有「食」")
    tap(food, wait=1.2)
    root = dump("u2")
    expect(has_text(root, "食 · 小类"), "打开食的小类管理")
    tap(find_text(root, "＋ 添加小类"), wait=1.0)
    root = dump("u3")
    edits = edit_texts(root)
    expect(len(edits) >= 1, "自定义小类弹窗有输入框")
    tap(edits[0], wait=0.4)
    type_text("coffee")
    root = dump("u4")
    tap(find_text(root, "添加"), wait=1.4)
    root = dump("u5")
    expect(wait_part(root, "coffee", timeout=6), "自定义小类 coffee 已创建")
    snap("35-01-custom-sub")
    # 删掉测试小类，保持数据干净
    tap(find_text(root, "coffee"), wait=1.0)
    root = dump("u6")
    tap(find_text(root, "删除"), wait=1.5)
    root = dump("u7")
    expect(not has_text(root, "coffee"), "测试小类已删除")
    back()
    time.sleep(0.6)
    back()

    print("== 我的地点 ==")
    grant_perms()  # MIUI 会回收权限，点到定位/录音前补一次
    root = dump("p1")
    tap(find_id(root, "setSpots"), wait=1.3)
    root = dump("p2")
    expect(has_text(root, "我的地点"), "打开「我的地点」管理")
    expect(has_text(root, "＋ 添加地点"), "有添加地点入口")
    tap(find_text(root, "＋ 添加地点"), wait=1.2)
    root = dump("p3")
    expect(has_text(root, "添加地点"), "打开添加地点弹窗")
    edits = edit_texts(root)
    expect(len(edits) >= 2, "地点弹窗有名称和坐标输入框")
    tap(edits[0], wait=0.4)
    type_text("e2e_spot")
    tap(edits[1], wait=0.4)
    type_text("32.98,114.02")
    root = dump("p4")
    tap(find_text(root, "保存"), wait=1.6)
    root = dump("p5")
    expect(wait_part(root, "e2e_spot", timeout=6), "地点已保存到列表")
    expect(has_part(root, "10km"), "默认范围是 10km")
    snap("35-02-spots")
    back()

    print("== 定位自动填地点 ==")
    if IS_EMULATOR:
        sh("emu", "geo", "fix", "114.020000", "32.980000")
        sh("shell", "am", "force-stop", PKG)
        grant_perms()
        sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
        time.sleep(2.0)
        root = dump("p6")
        expect(wait_part(root, "e2e_spot", timeout=12), "记账页定位自动填「e2e_spot」")
        snap("35-03-located")
    else:
        # 真机：先「使用当前位置」建一个点，再重启 App 看记账页能不能自动命中
        root = dump("p6")
        tap(find_id(root, "tabSettings"), wait=1.2)
        root = dump("p6a")
        tap(find_id(root, "setSpots"), wait=1.2)
        root = dump("p6b")
        tap(find_text(root, "＋ 添加地点"), wait=1.2)
        root = dump("p6c")
        tap(find_text(root, "使用当前位置"), wait=1.0)
        got = ""
        for _ in range(20):
            root = dump("p6d")
            edits = edit_texts(root)
            if len(edits) >= 2:
                got = edits[1].attrib.get("text", "").strip()
                if re.match(r"^-?\d+(\.\d+)?\s*,\s*-?\d+(\.\d+)?$", got):
                    break
            time.sleep(1.0)
        expect(bool(re.match(r"^-?\d+(\.\d+)?\s*,\s*-?\d+(\.\d+)?$", got)),
               "「使用当前位置」拿到当前坐标 " + got)
        root = dump("p6e")
        edits = edit_texts(root)
        tap(edits[0], wait=0.4)
        type_text("e2e_here")
        root = dump("p6f")
        tap(find_text(root, "保存"), wait=1.6)
        root = dump("p6g")
        expect(not edit_texts(root), "地点弹窗已保存并关闭")
        expect(wait_part(root, "e2e_here", timeout=6), "当前位置已保存成地点")
        back()
        time.sleep(0.6)

        # 重启后记账页应自动定位并填上这个地点
        sh("shell", "am", "force-stop", PKG)
        grant_perms()
        sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
        time.sleep(2.5)
        root = dump("p6h")
        if dismiss_system_dialog(root):
            root = dump("p6i")
        expect(wait_part(root, "e2e_here", timeout=25), "记账页定位自动填「e2e_here」")
        snap("35-03-located")

    # 清理测试地点
    tap(find_id(root, "tabSettings"), wait=1.4)
    root = dump("p7")
    tap(find_id(root, "setSpots"), wait=1.2)
    root = dump("p8")
    for name in ["e2e_spot", "e2e_here"]:
        if not has_text(root, name):
            continue
        tap(find_text(root, name), wait=1.0)
        root = dump("p9")
        tap(find_text(root, "删除"), wait=1.0)
        root = dump("p10")
        tap(find_text(root, "删除"), wait=1.5)
        root = dump("p11")
    expect(not has_text(root, "e2e_spot") and not has_text(root, "e2e_here"),
           "测试地点已删除")
    back()
    time.sleep(0.5)
    back()

    print("== 语音手输（多件）==")
    grant_perms()
    tap(find_id(root, "tabRecord"), wait=1.5)
    root = dump("v0")
    long_press(find_id(root, "modeVoice"))
    root = dump("v1")
    expect(has_text(root, "解析"), "长按语音弹出「说一句 / 敲一句」")
    tap(find_text(root, "今天在永辉超市买了薯片20，可乐5块"))
    root = dump("v2")
    tap(find_text(root, "解析"), wait=2.0)
    root = dump("v3")
    expect(has_part(root, "语音记账"), "多件解析进入确认页")
    expect(has_part(root, "保存"), "确认页有保存")
    snap("32-07-voice-confirm")
    back()

    print("== 小票页 ==")
    if os.environ.get("NO_OCR"):
        # release 包不能用 run-as 传图片；OCR 已经在 debug 包上完整跑过
        print("  (跳过：NO_OCR=1，release 包传不了测试图片)")
        print("\n全部通过 ✅")
        return
    sh("shell", "am", "force-stop", PKG)
    grant_perms()
    sh("push", RECEIPT, "/data/local/tmp/rc.png")
    sh("shell", "run-as", PKG, "cp", "/data/local/tmp/rc.png", "cache/rc.png")
    sh("shell", "am", "start", "-n", PKG + "/.ScanActivity",
       "-e", "image_uri", f"file:///data/user/0/{PKG}/cache/rc.png")
    time.sleep(5)
    root = dump("c1")
    expect(has_text(root, "小票录入"), "打开小票页")
    # 商家名来自 OCR，示例图偶尔把「辉」认成「起」，只校验关键片段
    expect(wait_part(root, "永辉"), "识别出商家（永辉…）")
    expect(has_text(root, "商品清单"), "有商品清单")
    expect(has_part(root, "保存(4)"), "识别出 4 件商品")
    snap("32-08-scan")

    print("== 小票页：商家名自动记忆 ==")
    tap(find_id(root, "scanMerchant"), wait=0.6)
    type_text("TestMarket")
    root = dump("c2")
    tap(find_part(root, "保存("), wait=3.0)
    time.sleep(1.5)

    # 再扫同一张：商家名应该自动变成上次手动改的 TestMarket
    sh("shell", "am", "start", "-n", PKG + "/.ScanActivity",
       "-e", "image_uri", f"file:///data/user/0/{PKG}/cache/rc.png")
    time.sleep(5)
    root = dump("c3")
    expect(wait_text(root, "TestMarket", timeout=12), "第二次扫描自动显示更正后的商家名")
    snap("35-04-merchant-memory")
    back()

    print("== 商家记忆列表 ==")
    # 小票页是独立启动的，返回后回到桌面，先把主界面拉起来
    grant_perms()
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(2.0)
    root = dump("m0")
    if dismiss_system_dialog(root):
        root = dump("m0b")
    tap(find_id(root, "tabSettings"), wait=1.5)
    root = dump("m1")
    tap(find_id(root, "setMerchants"), wait=1.3)
    root = dump("m2")
    expect(has_text(root, "商家记忆 · 1 个"), "商家记忆里有 1 条纠正")
    expect(has_part(root, "TestMarket"), "商家记忆显示正确名字")
    snap("35-05-merchant-memory-list")
    back()

    print("\n全部通过 ✅")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("\n失败 ❌", e)
        snap("32-fail")
        sys.exit(1)
