# 项目 Android 模拟器

本项目使用独立 SDK 和 AVD，均位于 `.tools/`，不依赖系统 Android Studio 配置。2026-11-25 已完成首次启动，`sys.boot_completed` 返回 `1`。

## 验收环境

| 项目 | 配置 |
| --- | --- |
| SDK | `D:\PycharmProjects\Video_Learning\.tools\android-sdk` |
| 模拟器 | Android Emulator 37.3.10（build 16970787） |
| 镜像 | `system-images;android-35;google_apis;x86_64`，revision 9 |
| Android | API 35，Google APIs，x86_64 |
| AVD | `VideoLearning_API35`，Pixel 5 配置 |
| AVD 目录 | `D:\PycharmProjects\Video_Learning\.tools\avd` |
| 屏幕 | 1080 × 2340，density 440 |
| 资源 | 2GB RAM，2 个 CPU 核 |
| 加速 | WHPX 可用，SwiftShader 图形渲染 |
| ADB serial | `emulator-5554`（固定控制台端口 5554） |

设备为无窗口、无音频后台模式；未安装虚拟化驱动，也未修改 Windows 虚拟化设置。启动前检查 ADB，避免重复占用端口。下面命令从项目根目录执行。

## 安装和创建

```powershell
$env:ANDROID_HOME = Join-Path $PWD '.tools\android-sdk'
$env:ANDROID_USER_HOME = Join-Path $PWD '.tools\android-home'
$env:ANDROID_AVD_HOME = Join-Path $PWD '.tools\avd'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME | Out-Null

& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" --install 'emulator' 'system-images;android-35;google_apis;x86_64' *> '.tools\emulator-sdk-install.log'
& "$env:ANDROID_HOME\emulator\emulator.exe" -accel-check
'no' | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd -n VideoLearning_API35 -k 'system-images;android-35;google_apis;x86_64' -d pixel_5 -p "$env:ANDROID_AVD_HOME\VideoLearning_API35.avd" *> '.tools\avd-create.log'
```

创建命令不带 `-f`，已有 AVD 时不要重复创建或覆盖。

## 后台启动

```powershell
$env:ANDROID_HOME = Join-Path $PWD '.tools\android-sdk'
$env:ANDROID_USER_HOME = Join-Path $PWD '.tools\android-home'
$env:ANDROID_AVD_HOME = Join-Path $PWD '.tools\avd'
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l

$taskEmulatorProcess = Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList @('-avd','VideoLearning_API35','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader','-memory','2048','-cores','2','-port','5554','-no-boot-anim') -WindowStyle Hidden -RedirectStandardOutput "$PWD\.tools\emulator-stdout.log" -RedirectStandardError "$PWD\.tools\emulator-stderr.log" -PassThru
Set-Content -LiteralPath '.tools\emulator.pid' -Value $taskEmulatorProcess.Id
```

首次启动需要初始化系统数据。使用以下只读命令确认就绪，不能仅凭设备出现在 ADB 列表中判定启动完成：

```powershell
& '.tools\android-sdk\platform-tools\adb.exe' -s emulator-5554 shell getprop sys.boot_completed
& '.tools\android-sdk\platform-tools\adb.exe' -s emulator-5554 shell wm size
& '.tools\android-sdk\platform-tools\adb.exe' -s emulator-5554 shell wm density
```

第一条应输出 `1`。日志位于 `.tools/emulator-stdout.log` 和 `.tools/emulator-stderr.log`。结束该测试设备时明确指定 serial：

```powershell
& '.tools\android-sdk\platform-tools\adb.exe' -s emulator-5554 emu kill
```

命令依据 Android 官方的 Emulator command line、avdmanager、sdkmanager 和 Environment variables 文档。此环境记录仅说明模拟器已启动，不代表课程视频或 APP 功能已通过验收。

APP 的实际安装和功能检查另见 [首版验收记录](acceptance.md)。
