# 课程数据导入

使用 `convert_courses.py` 将原始 Excel 清单转换为 APP 内置目录：

```powershell
python tools/convert_courses.py "课程清单.xlsx" app/src/main/assets/courses.json
```

首次导入需要 `python -m pip install -r tools/requirements.txt`。脚本只展开真实合并单元格，普通空值不会从前一条课程继承。

构建前严格检查（只需 Python 标准库）：

```powershell
python -X utf8 tools/validate_catalog.py app/src/main/assets/courses.json --strict
python -X utf8 -m unittest discover -s tools -p "test_*.py" -v
```

当前清单预期为 3 个分类、131 条记录、130 个不同视频。重复 `courseId` 是错误；同一个视频 ID 被多个章节引用属于允许的提示。所有检查日志均不打印播放地址或 token。字段、告警和重新导入规则见 `docs/catalog.md`。
