# Windows 中文编译手册

本手册面向第一次接触该工程的人，使用 Windows 10/11 和 PowerShell。目标是从 GitHub 获取源码，在自己的电脑生成可以安装到安卓手机的 APK。所有命令都在项目根目录执行，除非步骤另有说明。

普通编译使用仓库里已经转换好的 `app/src/main/assets/courses.json`，不需要原始 Excel，不需要账号服务器或 OpenSpec。仓库不包含 Android SDK、JDK、Gradle 下载缓存或维护者的 `.tools/`；不能依赖其他电脑上的这些目录。

## 1. 安装编译软件

| 软件 | 安装内容与用途 |
| --- | --- |
| Git for Windows | 从仓库下载和更新源码；[官方下载](https://git-scm.com/install/windows) |
| JDK 17 | 运行 Gradle 和 Java/Kotlin 编译；可安装 [Eclipse Temurin 17](https://adoptium.net/temurin/releases/?version=17)，选择 Windows x64 JDK，避免只装 JRE |
| Android Studio | 用于 SDK Manager、查看工程、模拟器和图形化签名；[官方下载](https://developer.android.com/studio)。选择兼容 AGP 8.6.1 的版本；本项目的兼容性基线是 Koala Feature Drop 2024.1.2，版本对应关系见[官方说明](https://developer.android.com/studio/releases/past-releases/as-koala-feature-drop-release-notes) |
| Python 3.10 或以上 | 构建前校验课程目录；可安装 Python 3.12，并在 Windows 安装器中勾选添加到 PATH；[官方下载](https://www.python.org/downloads/windows/) |
| Node.js 24（可选） | 仅运行 `tools/test_player_bridge.mjs` 时使用；不参与 APK 编译 |

工程已经固定 AGP 8.6.1、Gradle 8.7、Kotlin/Compose 编译插件 2.0.21。AGP 8.6 的兼容要求是 JDK 17、Gradle 8.7，默认 SDK Build-Tools 为 34.0.0，支持到 API 35，见 [AGP 8.6 官方版本说明](https://developer.android.com/build/releases/past-releases/agp-8-6-0-release-notes)。**无需另外安装全局 Gradle，也不要因为 IDE 提示而先升级工程插件。**

安装后关闭并重新打开 PowerShell，检查：

```powershell
git --version
python --version
python -m pip --version
```

这里必须能够运行 `python`，仅能运行 `py` 还不够，因为项目构建任务直接调用 `python`。如果输入 `python` 打开 Microsoft Store，应检查 Python 安装路径、PATH 和 Windows 的应用执行别名。修改环境变量后也要重启 Android Studio，让 IDE 取得新的 PATH。

## 2. 安装 Android SDK

打开 Android Studio：欢迎页选择 **More Actions → SDK Manager**；工程打开后也可从 **Tools → SDK Manager** 进入。

1. 记下界面顶部的 **Android SDK Location**。常见路径是 `C:\Users\你的用户名\AppData\Local\Android\Sdk`，也可以选择容易识别的 `C:\Android\Sdk`。
2. 在 **SDK Platforms** 中安装 **Android 15 / API Level 35**。
3. 在 **SDK Tools** 中勾选 **Show Package Details**，安装 **Android SDK Build-Tools 34.0.0**。
4. 同时安装 **Android SDK Platform-Tools** 和 **Android SDK Command-line Tools**。
5. 点击 Apply，确认下载并接受许可证。需要模拟器时再安装 Android Emulator 和对应系统镜像；只生成 APK 不需要模拟器。

`compileSdk = 35` 和 Build-Tools `34.0.0` 是两项不同设置，应分别安装。SDK 包管理命令的说明见 [sdkmanager 官方文档](https://developer.android.com/tools/sdkmanager)。

已有 Command-line Tools 时，也可以在 PowerShell 中安装所需 SDK 包。命令行使用的 JDK 环境应先按第 4 节配置。下面假设 SDK 位于 `C:\Android\Sdk`；请替换为 SDK Manager 显示的真实路径：

```powershell
$sdkDir = 'C:\Android\Sdk'
$sdkManager = Join-Path $sdkDir 'cmdline-tools\latest\bin\sdkmanager.bat'
& $sdkManager "--sdk_root=$sdkDir" 'platform-tools' 'platforms;android-35' 'build-tools;34.0.0'
& $sdkManager "--sdk_root=$sdkDir" --licenses
```

按提示阅读并接受许可证。若不存在 `cmdline-tools\latest\bin\sdkmanager.bat`，先通过 SDK Manager 安装 Command-line Tools，再检查实际安装目录。

## 3. 下载源码

以下命令在当前用户的 `source` 文件夹下创建工程；如果已经克隆过仓库，直接进入已有目录即可，不要重复覆盖：

```powershell
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\source" | Out-Null
Set-Location "$env:USERPROFILE\source"
git clone https://github.com/yinzhipeng123/Video_Learning.git
Set-Location .\Video_Learning
```

这是私有仓库，需要仓库所有者授权你的 GitHub 账号访问。按 Git for Windows 的凭据管理器提示登录有权限的账号，不要把密码或访问 token 写进 clone URL、脚本或文档。下载 ZIP 也可以编译；解压后应进入同时包含 `settings.gradle.kts`、`app`、`tools` 和 `gradlew.bat` 的目录。

## 4. 配置 JDK 和 SDK 路径

### PowerShell 命令行环境

把下面两个路径替换为自己电脑的真实路径。JDK 的目录中必须存在 `bin\java.exe`；不要把 `JAVA_HOME` 指向 `bin` 文件夹。

```powershell
# 示例路径：请改成实际安装的 JDK 17 和 Android SDK 目录。
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

Test-Path "$env:JAVA_HOME\bin\java.exe"
Test-Path "$env:ANDROID_HOME\platforms\android-35\android.jar"
java -version
```

两个路径检查应返回 `True`，Java 版本应显示 `17`。上述环境变量只对当前 PowerShell 窗口有效；新开窗口时需要重新设置，或在 Windows 环境变量设置中持久保存。

在项目根目录创建 **`local.properties`**，写入 SDK 的真实路径。使用正斜杠，避免反斜杠转义问题，例如：

```properties
sdk.dir=C:/Android/Sdk
```

这是本机配置，不提交 Git。Android Studio 首次打开工程时通常也会创建该文件；确认它指向自己的 SDK。路径含中文等非 ASCII 字符时，优先让 Android Studio 生成该文件，避免手工保存的编码不正确。

### Android Studio 的 JDK

选择 **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**，把 **Gradle JDK** 指向安装的 **JDK 17**。命令行与 IDE 的 JDK 设置可能不同，不要仅凭命令行 `java -version` 就假设 IDE 也使用 17。说明见 [Android 构建 JDK 官方文档](https://developer.android.com/build/jdks)。

用 Android Studio 打开项目根目录，等待 Gradle Sync 完成。若打开的只是 `app` 子目录，应关闭并重新打开根目录。

## 5. 生成可安装的 debug APK

在配置好环境的 PowerShell 中，先确认当前位置是工程根目录：

```powershell
Get-Location
Test-Path .\gradlew.bat
python --version
.\gradlew.bat --version
```

`Test-Path` 应返回 `True`；Wrapper 会首次下载 Gradle 8.7，版本输出中的 JVM 应为 17。Wrapper 文件和分发包校验值已经包含在仓库中，见 [Gradle Wrapper 官方说明](https://docs.gradle.org/8.7/userguide/gradle_wrapper.html)。

然后编译：

```powershell
.\gradlew.bat :app:assembleDebug --console plain
```

等到终端出现 **`BUILD SUCCESSFUL`**，APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

可在资源管理器中打开产出目录：

```powershell
explorer.exe .\app\build\outputs\apk\debug
```

每次构建前都会运行 `validateCourseCatalog`，使用 Python 标准库检查内置课程 JSON。当前基线为 131 条记录、3 个分类、130 个不同视频。普通编译不需要安装 `openpyxl`；不要绕过目录校验来生成交付包。

首次构建会从 Gradle、Google Maven 和 Maven Central 下载依赖，通常比后续构建慢。生成 APK 不需要连接手机，也不需要启动模拟器。

Android Studio 用户可以在 Gradle Sync 完成后，打开 **View → Tool Windows → Terminal**，使用上面相同的 Wrapper 命令；也可以通过 IDE 的 Build APK 操作构建 debug 变体。命令行步骤和 APK 路径更便于复现，不依赖不同 IDE 版本的菜单命名。

## 6. 安装到手机或模拟器

手机至少需要 Android 8.0。可以把 `app-debug.apk` 复制到手机，在文件管理器中打开并按系统提示允许安装。

也可以启用手机开发者选项和 USB 调试，用数据线连接电脑，并在手机上确认 USB 调试授权：

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.yzp.videolearning/.MainActivity
```

`devices` 中应显示状态 `device`；`unauthorized` 表示尚未在手机上授权。如果连接多台设备，给每个 ADB 命令加 `-s 实际序列号`，例如 `& $adb -s 'emulator-5554' install -r ...`。

debug APK 由当前电脑的调试证书签名。不同电脑生成的 debug 包可能使用不同签名，不能总是覆盖安装；遇到 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 应先核对签名。卸载旧包会删除本机学习记录，不要把卸载作为无提示的自动步骤。

## 7. 运行测试

### 不需要手机或模拟器

Android JVM 单元测试、lint 和 APK 构建：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console plain
```

报告位置：

- 单元测试：`app/build/reports/tests/testDebugUnitTest/index.html`
- 静态检查：`app/build/reports/lint-results-debug.html`

数据导入工具测试会生成 Excel 样例，**因此需要 `openpyxl`**。它与普通构建时只用标准库的目录校验不同：

```powershell
python -m pip install -r tools/requirements.txt
python -X utf8 -m unittest discover -s tools -p 'test_*.py' -v
python -X utf8 tools/validate_catalog.py app/src/main/assets/courses.json --strict
```

已安装 Node.js 时，可额外检查播放器脚本；脚本只使用 Node 内置模块，无需 `npm install`：

```powershell
node --version
node tools/test_player_bridge.mjs
```

### 需要 Android 设备

在 Android Studio 的 Device Manager 创建并启动模拟器，或连接已授权的安卓手机。先确认 `adb devices` 显示设备，再运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console plain
```

界面测试报告位于 `app/build/reports/androidTests/connected/`。这组测试验证分类、详情、状态和异常路由；真实视频、声音、全屏、后台暂停和实际课程链接仍需在设备上检查。可照 [播放器验收步骤](player-troubleshooting.md) 和 [进度验收步骤](progress.md) 操作。

如果 Gradle 的设备测试启动组件下载受网络影响，可以先排查网络。维护时也可明确构建主 APK 和测试 APK，再用同一测试 Runner 直接执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb shell am instrument -w -r com.yzp.videolearning.test/androidx.test.runner.AndroidJUnitRunner
```

以 Runner 的实际结果为准，不能仅凭 ADB 命令退出就断定测试通过。

## 8. 生成正式签名的发布 APK

本工程的 `release` 构建类型**没有配置签名证书**。直接运行下面的命令只能生成未签名 release 包，不能把它当作可直接安装的正式发布包：

```powershell
.\gradlew.bat :app:assembleRelease
# 默认未签名产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

人类首次发行推荐使用 Android Studio 的签名向导，流程依据 [Android 官方应用签名文档](https://developer.android.com/studio/publish/app-signing)：

1. 打开工程并完成 Gradle Sync，选择 **Build → Generate Signed App Bundle or APK**，选择 **APK**。
2. 选择 `app` 模块。使用已有发布 keystore，或通过 **Create new** 新建；把文件保存到仓库之外的安全目录。
3. 填写 keystore 密码、key alias 和 key 密码，妥善备份文件与凭据。不要把它们写进源码、README 或 Git。
4. 选择 `release` 构建变体和输出目录，完成生成；以向导显示的输出位置为准。
5. 在干净的测试设备上安装生成的已签名 APK，验证播放、横屏、返回、学习记录和错误重试。

后续版本覆盖升级需要保持同一应用 ID 和签名，并在 `app/build.gradle.kts` 中递增 `versionCode`、更新 `versionName`。当前版本为 `versionCode = 1`、`versionName = "1.0"`。调试证书与正式证书不同，已安装的 debug 包不一定能被正式包直接覆盖。

## 9. 修改课程后重新编译

不改课程时跳过本节。拿到新版 Excel 后，在项目根目录运行：

```powershell
python -m pip install -r tools/requirements.txt
python -X utf8 tools/convert_courses.py 'C:\CourseData\课程清单.xlsx' app/src/main/assets/courses.json
python -X utf8 tools/validate_catalog.py app/src/main/assets/courses.json --strict
python -X utf8 -m unittest discover -s tools -p 'test_*.py' -v
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Excel 路径需替换为实际文件。若课程数量改变，还要同步构建前检查的预期数量；手动命令上的 `--expected-*` 参数不会自动改变 Gradle 的校验任务。字段规则和数量调整方式见 [课程目录维护](catalog.md)。课程地址带访问 token，不要把完整地址输出到日志；链接有效期和观看权限由提供方控制。

## 10. 常见报错

| 现象 | 检查方法 |
| --- | --- |
| 找不到 `python` / 启动 Microsoft Store | 确保 Python 已加入 PATH，关闭冲突的应用执行别名，并重开 PowerShell / Android Studio；Gradle 调用的是 `python` |
| `ModuleNotFoundError: openpyxl` | 正在做 Excel 导入或 Python 测试；执行 `python -m pip install -r tools/requirements.txt`，确认 pip 对应同一个 Python |
| JDK / Java 版本不兼容 | 检查 `JAVA_HOME`、`java -version`、`gradlew.bat --version` 与 IDE 的 Gradle JDK；都使用 17 |
| `SDK location not found` | 根目录创建或修正 `local.properties` 的 `sdk.dir`，使用实际 SDK 路径 |
| 找不到 `android-35` / Build-Tools | 按第 2 节安装 Platform 35、Build-Tools 34.0.0，并确认 IDE 和命令行使用同一 SDK |
| SDK licenses 未接受 | 运行对应 SDK 的 `sdkmanager.bat --licenses`，按提示接受 |
| Gradle 或 Maven 下载超时 | 检查网络、代理与证书；首次构建不能依靠空缓存离线完成。Gradle 代理可配置在用户级 `%USERPROFILE%\.gradle\gradle.properties`，不把代理凭据提交仓库 |
| `validateCourseCatalog FAILED` | 阅读不含 token 的校验摘要，修正 JSON 字段或经过确认的数量基线；不要禁用任务 |
| `No connected devices` | 只有设备界面测试需要连接设备；先用 `adb devices` 确认授权和设备状态 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 已安装版本与新 APK 签名不同；核对证书。卸载会清除学习记录 |
| 安装成功但视频失败 | 编译成功不代表课程 token 有效；检查网络、手机日期和课程提供方授权，参考播放器排障文档 |

若只有 Wrapper 下载受阻，可以从 [Gradle 官方分发地址](https://services.gradle.org/distributions/gradle-8.7-bin.zip) 获取 `gradle-8.7-bin.zip`，校验 SHA-256 后解压，用同一个版本的 Gradle 编译；不修改项目版本或依赖维护者的 `.tools/`。官方归档校验值应为 `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`，与仓库 `gradle-wrapper.properties` 一致。例如在项目根目录执行，下载路径按实际位置替换：

```powershell
Get-FileHash "$env:USERPROFILE\Downloads\gradle-8.7-bin.zip" -Algorithm SHA256
# 先人工确认上一步 Hash 完全一致，再解压并运行。
Expand-Archive -LiteralPath "$env:USERPROFILE\Downloads\gradle-8.7-bin.zip" -DestinationPath "$env:USERPROFILE\BuildTools"
& "$env:USERPROFILE\BuildTools\gradle-8.7\bin\gradle.bat" :app:assembleDebug --console plain
```

这只替代 Gradle 本体下载，Google Maven / Maven Central 的依赖仍需可访问的网络。已经存在解压目录时直接使用，不要无意覆盖其他工具文件。

本仓库的首版实际测试环境、检查结果和未覆盖范围记录在 [验收记录](acceptance.md)。其中 `.tools/` 路径仅表示维护者当时的本地环境，不是其他电脑编译的前置条件。
