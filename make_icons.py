#!/usr/bin/env python3
"""生成应用图标（渐变底 + 白色 ¥），输出旧版 PNG 与自适应图标前景。"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ROOT, "app", "res")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

TOP = (23, 181, 122)
BOTTOM = (14, 159, 110)


def gradient(size):
    img = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        c = tuple(round(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=c)
    return img


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def draw_symbol(img, size, scale=0.52, dy=0.0):
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, int(size * scale))
    text = "¥"
    box = d.textbbox((0, 0), text, font=font)
    w, h = box[2] - box[0], box[3] - box[1]
    x = (size - w) / 2 - box[0]
    y = (size - h) / 2 - box[1] + size * dy
    d.text((x, y), text, font=font, fill=(255, 255, 255, 255))


def legacy(size):
    pad = max(1, size // 16)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = gradient(size - 2 * pad).convert("RGBA")
    mask = rounded_mask(size - 2 * pad, int((size - 2 * pad) * 0.24))
    canvas.paste(bg, (pad, pad), mask)
    draw_symbol(canvas, size, scale=0.5)
    return canvas


def foreground(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # 自适应图标前景只占中间 66% 安全区
    draw_symbol(img, size, scale=0.30)
    return img


def main():
    for dpi, s in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = os.path.join(RES, "mipmap-" + dpi)
        os.makedirs(folder, exist_ok=True)
        legacy(s).save(os.path.join(folder, "ic_launcher.png"))
    fg_folder = os.path.join(RES, "mipmap-xxxhdpi")
    foreground(432).save(os.path.join(fg_folder, "ic_launcher_fg.png"))
    print("icons written")


if __name__ == "__main__":
    main()
