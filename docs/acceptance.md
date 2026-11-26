# 文旅学堂 1.0 验收记录

验收日期：2026-11-25（Asia/Shanghai）。本次交付为可直接安装的 debug APK。

## 交付文件

- 文件：`outputs/文旅学堂-1.0-debug.apk`
- 名称：文旅学堂；包名：`com.yzp.videolearning`
- 版本：`1.0`（versionCode 1）；最低 Android 8.0 / API 26；目标 API 35。
- 文件大小：10,313,420 字节，约 9.84 MiB。
- SHA-256：`D0D5E0DEA5586D5D9836F4E6B1D16FB077D84B6E3F318EA63C608AABB89FDD8B`
- `apksigner verify --verbose`：通过，v2 签名有效，1 个签名者。

## 数据与自动检查

| 检查 | 结果 |
| --- | --- |
| Excel 导入 | 3 个分类、131 条课程记录、130 个不同视频 |
| 分类数量 | 红色文化类 33；非遗保护传承类 79；文旅融合类 19 |
| 目录严格校验 | 0 错误、0 警告；原表两条章节共用视频的情况保留并共享学习状态 |
| Python 导入和校验测试 | 11 项通过 |
| Kotlin/JVM 测试 | 15 项通过，0 失败、0 错误、0 跳过 |
| Node 播放桥接检查 | 通过：官方实例筛选、暂停、前后台、触摸、进度恢复、边界和降级 |
| Android Compose 界面测试 | 5 项通过；分类与课程顺序、完整详情与完成操作、缺失字段、滚动位置、无效地址后重试与返回 |
| Android lint | 通过，0 errors、3 warnings、1 information |
| 负向构建门禁 | 空目录输入使 `validateCourseCatalog` 失败，Gradle 返回 1，未进入编译打包；生产课程 JSON 保持不变 |

JVM 报告位于 `app/build/test-results/testDebugUnitTest/`。界面测试 APK 使用 `AndroidJUnitRunner` 直接在 ADB 设备上运行，输出 `OK (5 tests)`；修复全屏返回后的最终包回归日志为 `.tools/ui-tests-delivery.log`，执行时间 15.399 秒。Gradle 的 UTP 依赖下载曾阻塞 `connectedDebugAndroidTest`，因此本次不把该 Gradle 任务声称为通过，设备上的同一组已编译测试实际执行成功。

最后一轮 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 全部成功，Gradle 退出码 0，耗时 1 分 56 秒，日志为 `.tools/build-delivery.log`。主包与测试包签名均已验证，交付目录中的 APK 与构建产物 SHA-256 一致。

Lint 报告位于 `app/build/reports/lint-results-debug.html`。剩余提示为 Android 12 数据提取规则、最低 API 26 下冗余的资源版本目录、启动图标 monochrome 和整数 Compose 状态装箱，均未阻止构建。

负向检查通过临时 Gradle init script 将校验任务指向 `.tools/invalid-catalog.json`，没有修改生产目录。日志为 `.tools/catalog-gate-negative.log`。

## 安装与实际学习流程

环境为项目专用 Pixel 5 模拟器，Android 15 / API 35，1080 × 2340，WebView 124.0.6367.219。最终交付 APK 已成功安装，冷启动、分类、列表、详情和真实网页播放通过。

已在安装包中打开首条红色文化课程，实际显示讲师和课件，恢复到之前保存的约 15:12。播放器使用原课程网页的公开 BPlayer 能力；无需提取媒体地址。布局适配和触摸控制已打包在 APK 中。

最终交付包已实际进入横向全屏，`document.fullscreenElement` 为 `#app`，canvas 为 2340 × 1080。一次系统返回退出全屏并留在竖屏播放页，第二次系统返回回到课程详情，路径通过。Android 首次进入沉浸式全屏会显示系统引导，需要先点“Got it”；引导覆盖层曾消费返回键，已在确认引导后复测，不把这次系统提示误记为应用失败。最终包截图位于 `docs/screenshots/fullscreen.png` 与 `docs/screenshots/player.png`。

触屏暂停实测通过：播放器时间为 476 秒，间隔 3 秒再次读取仍为 476，且 `paused=true`。原生横/竖按钮双向切换通过，视口由 `393 × 714` 变为 `851 × 281`，再恢复 `393 × 714`；横屏截图为 `docs/screenshots/landscape.png`。本轮切到 Home 前已暂停，后台时间保持不变；此前播放中切到后台的验证为 15:11 → 15:12 后保持暂停，使用相同播放桥接实现。

设备上的本机状态检查已全部通过（`.tools/device-state.log`）：

1. 播放后标记“已学完”，强制停止 APP，再次打开课程仍显示“继续学习”和“取消已学完”。
2. 取消完成后恢复“标记为已学完”按钮。
3. 同时关闭模拟器 Wi-Fi 和移动数据，重新启动仍能加载 3 个分类和 131 条记录。
4. 在专用测试设备执行 `pm clear` 后，断网重新打开仍能浏览目录；原学习记录清除，课程恢复“开始学习”和“标记为已学完”。
5. 断网开始学习出现加载失败提示、重新加载和返回课程详情。重新连接模拟器 AndroidWifi 后刷新课程，真实 BPlayer 和 canvas 恢复加载，并恢复到约 122 秒。

## 验收范围

- 本次检查覆盖模拟器及抽样课程；未逐条观看全部 131 条记录，未完成实体手机兼容性验收。
- 最终包未逐项实测进度条拖动，也未抽样 `baijiacloud` 域名下的课程；公开 seek、恢复位置及时间推进已在真实播放器和脚本测试中验证。其余链接仍可能受网络、token 或提供方服务影响。
- 模拟器以无音频模式运行，因此没有用耳听验证实体设备音频；后台暂停通过播放器状态和时间是否停止确认。
- 原课程页面可显示“课程仅供试看”水印，观看范围、token 有效期和授权由课程提供方控制。APP 保留这些限制。
- 视频需要网络；课程目录和学习记录位于本机。支持的位置约每 2 秒保存一次；不支持读取的页面仍保存最近打开和手动完成记录。
- 清除数据或卸载会删除本机学习记录。此版本使用调试证书，正式发行需配置发布方的长期签名证书。

## 复现命令

在项目根目录、JDK 17 和 Android SDK 35 环境中执行：

```powershell
python -X utf8 -m unittest discover -s tools -p 'test_*.py'
python -X utf8 tools/validate_catalog.py app/src/main/assets/courses.json --strict
node tools/test_player_bridge.mjs
.\.tools\gradle-8.7\bin\gradle.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\.tools\android-sdk\platform-tools\adb.exe -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
.\.tools\android-sdk\platform-tools\adb.exe -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
.\.tools\android-sdk\platform-tools\adb.exe -s emulator-5554 shell am instrument -w -r com.yzp.videolearning.test/androidx.test.runner.AndroidJUnitRunner
```

日常也可使用 `gradlew.bat`；本机使用已下载的 Gradle 以避免重复下载。测试设备 serial 应替换为实际验收设备。

OpenSpec change `android-course-learning-app` 的 22/22 项任务已完成，严格校验通过；本次未归档 change。
