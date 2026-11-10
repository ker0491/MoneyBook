#!/usr/bin/env bash
# 在 JVM 上跑语音/小票解析的回归测试（不需要 Android 设备）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(ls -d "$HOME"/projects/toolchain/jdk-17* 2>/dev/null | head -1)"
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "找不到 JDK 17，请先 export JAVA_HOME=/path/to/jdk-17" >&2
  exit 1
fi
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

"$JAVA_HOME/bin/javac" -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/java/com/k/moneybook/Record.java" \
  "$ROOT/app/java/com/k/moneybook/Dates.java" \
  "$ROOT/app/java/com/k/moneybook/Subs.java" \
  "$ROOT/app/java/com/k/moneybook/Export.java" \
  "$ROOT/app/java/com/k/moneybook/VoiceParser.java" \
  "$ROOT/app/java/com/k/moneybook/Classifier.java" \
  "$ROOT/app/java/com/k/moneybook/Scenes.java" \
  "$ROOT/app/java/com/k/moneybook/ReceiptParser.java" \
  "$ROOT/app/java/com/k/moneybook/OcrRows.java" \
  "$ROOT/app/java/com/k/moneybook/Spot.java" \
  "$ROOT/app/java/com/k/moneybook/Spots.java" \
  "$ROOT/app/java/com/k/moneybook/Merchants.java" \
  "$ROOT/tests/VoiceParserTest.java" \
  "$ROOT/tests/ClassifierTest.java" \
  "$ROOT/tests/ReceiptParserTest.java" \
  "$ROOT/tests/OcrRowsTest.java" \
  "$ROOT/tests/SubsTest.java" \
  "$ROOT/tests/ExportTest.java" \
  "$ROOT/tests/SpotMatchTest.java" \
  "$ROOT/tests/MerchantsTest.java"

echo "== 语音解析 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" VoiceParserTest

echo "== 自动归类 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" ClassifierTest

echo "== 小票解析 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" ReceiptParserTest

echo "== OCR 行列还原 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" OcrRowsTest

echo "== 小类推断 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" SubsTest

echo "== 导出 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" ExportTest

echo "== 地点匹配 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" SpotMatchTest

echo "== 商家名纠正 =="
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" MerchantsTest
