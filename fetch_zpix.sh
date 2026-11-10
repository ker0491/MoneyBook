#!/usr/bin/env bash
# 下载 Zpix 像素字体到 app/res/font/zpix.ttf
#
# Zpix 不随本仓库分发（见 licenses/Zpix-LICENSE.md）：
# 字体版权归 SolidZORO 所有，个人项目免费使用，商业使用需向作者购买授权。
# 官方发布页：https://github.com/SolidZORO/zpix-pixel-font/releases
set -euo pipefail

cd "$(dirname "$0")"
DEST="app/res/font/zpix.ttf"
URL="https://github.com/SolidZORO/zpix-pixel-font/releases/download/v3.3.0/zpix.ttf"
SHA256="a67917d94bb597acbdf3bfe1cf97047cc3e1cdd3839d21da188e35cc0737e828"

mkdir -p "$(dirname "$DEST")"

if [ -f "$DEST" ]; then
  echo "已存在 $DEST，跳过下载（删掉它可重新下载）"
  exit 0
fi

echo "下载 $URL"
curl -fL --progress-bar -o "$DEST.tmp" "$URL"
mv "$DEST.tmp" "$DEST"

ACTUAL=$(sha256sum "$DEST" | awk '{print $1}')
if [ "$ACTUAL" != "$SHA256" ]; then
  echo "校验失败：SHA-256 不匹配（期望 $SHA256，实际 $ACTUAL）" >&2
  rm -f "$DEST"
  exit 1
fi
echo "完成：$DEST"
