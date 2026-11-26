# 课程目录维护

APP 运行时直接读取 `app/src/main/assets/courses.json`，浏览目录不需要联网；视频播放需要联网。Excel 只在维护目录时使用，不打包进 APK。

## 重新导入

在项目根目录运行，Python 需要 3.10 或以上版本：

```powershell
python -m pip install -r tools/requirements.txt
python -X utf8 tools/convert_courses.py "课程清单.xlsx" app/src/main/assets/courses.json
python -X utf8 tools/validate_catalog.py app/src/main/assets/courses.json --strict
python -X utf8 -m unittest discover -s tools -p "test_*.py" -v
```

转换器只依赖 `openpyxl`，构建前的 JSON 校验仅使用 Python 标准库，不需要 `openpyxl`。校验成功退出码为 0，失败为 1，适合作为 Gradle `preBuild` 的前置命令。不要打印原始课程 JSON、播放 URL 或 token 到构建日志。

当前清单基线：红色文化类 33 条、非遗保护传承类 79 条、文旅融合类 19 条，总计 131 条、3 个分类、130 个不同视频。文旅融合类的前两条章节共用同一个视频，保留两条记录和各自标题，并共享本机学习状态。校验结果将其报告为 `sharedVideos`，不视为错误。

如果新增或删除课程，应同时审查来源 Excel 和预期数量，再明确传入新的 `--expected-records`、`--expected-categories`、`--expected-videos`。手动校验时的参数不会自动改变 Gradle：还必须在 `app/build.gradle.kts` 的 `validateCourseCatalog` 任务 `commandLine` 中加入相同的预期数量参数，或明确维护校验脚本的默认基线；否则下一次 APK 构建仍会检查原来的 131 / 3 / 130。不要为了让错误数据通过而关闭校验。

重新生成 JSON 并通过测试后，运行 `gradlew.bat :app:assembleDebug` 生成更新后的 APK。完整的软件安装、构建和发布签名步骤见 [Windows 编译手册](build.md)。

## 字段与转换规则

| 字段 | 用途 |
| --- | --- |
| `categoryId` / `categoryName` | 稳定分类标识与中文分类名 |
| `courseId` | 分类标识与表内序号组合；目录内必须唯一，用于列表条目身份 |
| `title` / `chapterOrTopic` | 课程或专题名称与章节标题 |
| `description` / `teacher` / `titleOrRank` | 简介、主讲教师、职称；缺失可选字段保留为空字符串 |
| `sourceSeries` | 来源系列，未提供时为空字符串 |
| `durationSeconds` / `durationDisplay` | 统一秒数与可读时长；无法解析时秒数为 null，显示保留原文或“时长未知” |
| `videoId` | 视频身份，用作本机学习状态键 |
| `playerUrl` | 原样保存的 HTTPS 百家云播放地址，包含访问 token |

红色文化类与其他两个工作表列顺序不同，转换器按工作表映射字段。仅真实合并单元格继承其左上角的值，不对普通空标题、空简介执行向前填充。转换会保留坏记录，以供 UI 显示可理解的错误，并在校验中指出问题。

分钟数字（`54`）、`MM:SS`（`48:35`）和 `HH:MM:SS`（`00:48:35`）均可转换；非法秒数/分钟数、负数和未识别的文字不会被当作有效时长。

视频 ID 优先读取 URL 中的数字 `vid`。缺少它时，使用主机、路径、白名单媒体身份参数和稳定课程信息生成后备哈希；token、签名、过期时间、未知认证参数、显示模式和 URL fragment 都不参与身份计算，避免 token 更新使学习状态丢失。后备 ID 会产生人工核对警告，正式数据优先补齐真实视频 ID。

## 校验结果

- `errors`：根结构、字段类型、重复 `courseId`、缺失身份字段或数量基线不符；任何错误均返回非零退出码。
- `warnings`：缺标题、非法/不受信任的播放 URL、无效时长、后备视频 ID；`--strict` 将这些警告作为构建失败条件。
- `sharedVideos`：多条章节引用同一视频的提示；这是允许的数据关系，不阻止构建。

诊断只显示记录号和问题类型，不回显 URL/token 或任意源字段内容。发布 APK 前必须运行严格校验；本机测试中的坏记录用于验证容错，不应无声混入正式目录。校验 URL 格式不代表已经在设备上验证播放权限或链接有效期。
