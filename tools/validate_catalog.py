#!/usr/bin/env python3
"""Validate the packaged catalog using Python's standard library only."""

import argparse
import json
from pathlib import Path

from convert_courses import catalog_report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--expected-records", type=int, default=131)
    parser.add_argument("--expected-categories", type=int, default=3)
    parser.add_argument("--expected-videos", type=int, default=130)
    parser.add_argument("--strict", action="store_true", help="Fail on data warnings; shared video references remain allowed")
    args = parser.parse_args()
    try:
        courses = json.loads(args.catalog.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        print(json.dumps({"errors": ["课程文件不可读取或 JSON 格式无效"]}, ensure_ascii=False))
        return 1
    report = catalog_report(courses, args.expected_records, args.expected_categories, args.expected_videos)
    print(json.dumps(report, ensure_ascii=False))
    return 1 if report["errors"] or (args.strict and report["warnings"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
