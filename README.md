# 文旅学堂 Android APP

一个供学生选择课程、观看视频并保存本机学习状态的 Android 应用。无需登录，也不需要部署业务服务器。课程目录随 APK 内置；观看视频需要联网。

- 分类浏览、课程列表、课程详情和应用内视频播放。
- 横竖屏、全屏和返回；可读取播放器进度时恢复上次位置。
- 本机保存最近打开、播放位置和手动“已学完”状态。
- 当前目录包含 3 个分类、131 条记录、130 个不同视频；两个章节共用一个视频并共享进度。
- 最低 Android 8.0（API 26）；包名 `com.yzp.videolearning`；当前版本 `1.0`。

## 从零开始编译

**第一次编译请先阅读 [Windows 中文编译手册](docs/build.md)。** 手册包含软件下载、安装版本、SDK 配置、Android Studio 操作、命令行编译、安装手机、测试、正式签名和常见报错。

仓库已经包含 Gradle Wrapper、应用源码和 `app/src/main/assets/courses.json`。普通编译不需要原 Excel，不需要安装 OpenSpec，也不需要安装全局 Gradle。SDK、JDK、Python、下载缓存以及维护者本机的 `.tools/` 不随仓库提供，应按手册在自己的电脑安装。私有仓库需要使用获得访问权限的 GitHub 账号克隆。

| 工具 | 本项目使用的版本 / 要求 |
| --- | --- |
| JDK | 17；Android Studio 的 Gradle JDK 也应选择 17 |
| Android SDK | Platform API 35、Build-Tools 34.0.0、Platform-Tools |
| Android Gradle Plugin | 8.6.1，已固定在构建脚本 |
| Gradle | 8.7，由仓库中的 Wrapper 下载和运行 |
| Kotlin / Compose 编译插件 | 2.0.21，已固定在构建脚本 |
| Python | 3.10 或以上；`python` 命令必须可用，构建前用它校验目录 |
| Node.js | 可选，仅运行播放器脚本检查时需要；已使用 Node.js 24 验证 |

完成手册中的环境准备、进入项目根目录，并配置本机 `local.properties` 后，可用 PowerShell 执行：

```powershell
java -version
python --version
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug --console plain
```

看到 `BUILD SUCCESSFUL` 后，可安装的调试包位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

首次构建需要联网下载 Gradle 和 Android 依赖。每次构建都会执行课程数据严格校验；数量基线或字段质量检查失败时构建会停止。

## 测试与维护

无需设备的 Android 单元测试和静态检查：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Python 数据工具测试会创建 Excel 样例，需要先安装 `openpyxl`；普通 APK 构建的目录校验只使用 Python 标准库：

```powershell
python -m pip install -r tools/requirements.txt
python -X utf8 -m unittest discover -s tools -p 'test_*.py'
```

其他维护文档：

- [完整编译、安装、测试和签名步骤](docs/build.md)
- [课程数据格式与 Excel 重新导入](docs/catalog.md)
- [播放器限制与排障](docs/player-troubleshooting.md)
- [本机进度与清除数据验收](docs/progress.md)
- [首版 APK 的具体验收结果和未覆盖范围](docs/acceptance.md)
- [维护者本地模拟器环境记录](docs/emulator.md)

## 使用与发布说明

调试包使用当前电脑的 Android 调试证书签名，可直接安装测试。正式发行应按编译手册生成发布方自己的签名 APK，并妥善保管签名密钥；当前项目没有配置 release 签名，单独执行 `assembleRelease` 不会得到可直接安装的已签名发布包。

播放器使用课程提供方的官方网页地址，访问 token 随课程目录保存在 APK 中。原页面的试看水印、授权、观看范围和链接有效期由提供方控制。链接失效时需更新目录并重新构建。清除应用数据或卸载会删除本机学习记录。
