# 第三方组件与许可

本项目自身代码以 [MIT](LICENSE) 授权。下面是随 APK 一起分发的第三方组件。

## 1. Zpix（最像素）像素字体

- 文件：`app/res/font/zpix.ttf`（v3.3.0，未修改）
- 版权：SolidZORO
- 授权：个人项目 / 教育项目免费；商业使用需购买授权；禁止修改、反编译、未经许可的分发
- 详情见 [licenses/Zpix-LICENSE.md](licenses/Zpix-LICENSE.md) 与作者的 [项目主页](https://github.com/SolidZORO/zpix-pixel-font)

> 字体文件不随本仓库分发，克隆后运行 `./fetch_zpix.sh` 下载。

## 2. Google ML Kit 文字识别（中文）

- 依赖：`com.google.mlkit:text-recognition-chinese:16.0.1`
- 版权：Google LLC
- 授权：Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
- 说明：模型随 APK 内置，离线运行，不调用 Google 服务、不上传图片

## 3. AndroidX Core

- 依赖：`androidx.core:core:1.12.0`
- 版权：The Android Open Source Project
- 授权：Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## 4. Kotlin 标准库

- 依赖：`org.jetbrains.kotlin:kotlin-stdlib:1.8.22`（ML Kit 的传递依赖）
- 版权：JetBrains s.r.o.
- 授权：Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

---

完整的 Apache License 2.0 文本见 https://www.apache.org/licenses/LICENSE-2.0 。
