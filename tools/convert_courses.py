#!/usr/bin/env python3
"""Convert the course workbook; only conversion requires openpyxl."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, parse_qsl, urlencode, urlsplit, urlunsplit


def clean(value: Any) -> str:
    return "" if value is None else str(value).strip()


def duration_seconds(value: Any) -> int | None:
    raw = clean(value)
    if re.fullmatch(r"[0-9]+", raw):
        return int(raw) * 60
    parts = raw.split(":")
    if len(parts) not in (2, 3) or not all(re.fullmatch(r"[0-9]+", p) for p in parts):
        return None
    numbers = [int(p) for p in parts]
    if len(numbers) == 2:
        minutes, seconds = numbers
        return minutes * 60 + seconds if seconds < 60 else None
    hours, minutes, seconds = numbers
    return hours * 3600 + minutes * 60 + seconds if minutes < 60 and seconds < 60 else None


def duration_display(seconds: int | None, original: Any) -> str:
    if seconds is None:
        return clean(original) or "时长未知"
    hours, rest = divmod(seconds, 3600)
    minutes, secs = divmod(rest, 60)
    if hours:
        return f"{hours}:{minutes:02d}:{secs:02d}"
    return f"{minutes}分{secs:02d}秒" if secs else f"{minutes}分钟"


def video_id(url: str, stable_hint: str = "") -> str:
    """Never key progress to expiring tokens, signatures, query ordering, or fragments."""
    try:
        parts = urlsplit(url)
        candidate = (parse_qs(parts.query).get("vid") or [""])[0].strip()
        if re.fullmatch(r"[0-9]+", candidate):
            return candidate
        # Only persistent media identity parameters contribute; unknown auth keys do not.
        identity_params = sorted((k.lower(), v) for k, v in parse_qsl(parts.query)
                                 if k.lower() in {"videoid", "video_id", "id", "mediaid", "media_id"})
        normalized = urlunsplit((parts.scheme.lower(), (parts.hostname or "").lower(),
                                 parts.path, urlencode(identity_params), ""))
    except ValueError:
        normalized = "invalid-url"
    digest = hashlib.sha256((normalized + "\n" + stable_hint).encode("utf-8")).hexdigest()[:16]
    return "url-" + digest


def valid_player_url(url: Any) -> bool:
    if not isinstance(url, str) or any(char.isspace() for char in url):
        return False
    try:
        parts = urlsplit(url)
        host = parts.hostname or ""
        return (parts.scheme == "https" and not parts.username and not parts.password
                and parts.port in (None, 443)
                and any(host == domain or host.endswith("." + domain)
                        for domain in ("baijiayun.com", "baijiacloud.com")))
    except ValueError:
        return False


def category_info(sheet_name: str) -> tuple[str, str]:
    mapping = {"红色文化类": "red-culture", "非遗保护传承类": "intangible-heritage", "文旅融合类": "culture-tourism"}
    return mapping.get(sheet_name, "category-" + hashlib.sha256(sheet_name.encode()).hexdigest()[:8]), sheet_name


def convert(source: Path) -> list[dict[str, Any]]:
    from openpyxl import load_workbook
    workbook = load_workbook(source, read_only=False, data_only=True)
    result: list[dict[str, Any]] = []
    try:
        for sheet in workbook.worksheets:
            category_id, category_name = category_info(sheet.title)
            merged_values = {}
            for region in sheet.merged_cells.ranges:
                value = sheet.cell(region.min_row, region.min_col).value
                for row in range(region.min_row, region.max_row + 1):
                    for col in range(region.min_col, region.max_col + 1):
                        merged_values[(row, col)] = value
            for row_number in range(2, sheet.max_row + 1):
                original = [sheet.cell(row_number, col).value for col in range(1, 9)]
                if not any(value not in (None, "") for value in original):
                    continue
                row = [merged_values.get((row_number, col), original[col - 1]) for col in range(1, 9)]
                if sheet.title == "红色文化类":
                    serial, title, description, teacher, rank, duration, series, player_url = row
                    chapter = ""
                else:
                    serial, title, chapter, description, teacher, rank, duration, player_url = row
                    series = ""
                url = clean(player_url)
                seconds = duration_seconds(duration)
                identity = "\n".join(clean(v) for v in (category_id, title, chapter, teacher))
                result.append({
                    "categoryId": category_id, "categoryName": category_name,
                    "courseId": f"{category_id}-{clean(serial) or row_number - 1}",
                    "title": clean(title), "chapterOrTopic": clean(chapter), "description": clean(description),
                    "teacher": clean(teacher), "titleOrRank": clean(rank), "sourceSeries": clean(series),
                    "durationSeconds": seconds, "durationDisplay": duration_display(seconds, duration),
                    "videoId": video_id(url, identity), "playerUrl": url,
                })
    finally:
        workbook.close()
    return result


def catalog_report(courses: Any, expected_records: int | None = None,
                   expected_categories: int | None = None, expected_videos: int | None = None) -> dict[str, Any]:
    """Diagnostics contain only row indices/codes, never URLs or arbitrary field values."""
    errors: list[str] = []
    warnings: list[str] = []
    shared: list[str] = []
    seen_courses: dict[str, int] = {}
    seen_videos: dict[str, list[int]] = {}
    categories: set[str] = set()
    if not isinstance(courses, list):
        return {"records": 0, "categories": 0, "uniqueVideos": 0,
                "errors": ["目录根节点必须是数组"], "warnings": [], "sharedVideos": []}
    string_fields = ("categoryId", "categoryName", "courseId", "title", "chapterOrTopic",
                     "description", "teacher", "titleOrRank", "durationDisplay", "videoId", "playerUrl")
    for index, course in enumerate(courses, 1):
        label = f"记录 {index}"
        if not isinstance(course, dict):
            errors.append(f"{label}: 记录必须是对象")
            continue
        for field in string_fields:
            if not isinstance(course.get(field), str):
                errors.append(f"{label}: {field} 必须是字符串")
        category = course.get("categoryId")
        if isinstance(category, str) and category:
            categories.add(category)
        else:
            errors.append(f"{label}: categoryId 为空")
        course_id = course.get("courseId")
        if not isinstance(course_id, str) or not course_id:
            errors.append(f"{label}: courseId 为空")
        elif course_id in seen_courses:
            errors.append(f"{label}: courseId 与记录 {seen_courses[course_id]} 重复")
        else:
            seen_courses[course_id] = index
        vid = course.get("videoId")
        if not isinstance(vid, str) or not vid:
            errors.append(f"{label}: videoId 为空")
        else:
            seen_videos.setdefault(vid, []).append(index)
            if vid.startswith("url-"):
                warnings.append(f"{label}: 使用后备视频 ID，请核对媒体身份")
        if not clean(course.get("title")):
            warnings.append(f"{label}: 标题为空")
        if not valid_player_url(course.get("playerUrl")):
            warnings.append(f"{label}: 播放地址不是有效的百家云 HTTPS 地址")
        seconds = course.get("durationSeconds")
        if not isinstance(seconds, int) or isinstance(seconds, bool) or seconds <= 0:
            warnings.append(f"{label}: 时长无法解析或不大于零")
    for indices in seen_videos.values():
        if len(indices) > 1:
            shared.append(f"记录 {', '.join(map(str, indices))} 引用同一个视频，保留全部章节并共享学习状态")
    counts = {"records": len(courses), "categories": len(categories), "uniqueVideos": len(seen_videos)}
    for key, expected in (("records", expected_records), ("categories", expected_categories), ("uniqueVideos", expected_videos)):
        if expected is not None and counts[key] != expected:
            errors.append(f"{key}: 预期 {expected}，实际 {counts[key]}")
    return {**counts, "errors": errors, "warnings": warnings, "sharedVideos": shared}


def validate(courses: list[dict[str, Any]]) -> list[str]:
    report = catalog_report(courses)
    return report["errors"] + report["warnings"] + report["sharedVideos"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    courses = convert(args.source)
    report = catalog_report(courses)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(courses, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 1 if report["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
