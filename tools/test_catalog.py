import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from openpyxl import Workbook
from convert_courses import catalog_report, convert, duration_display, duration_seconds, valid_player_url, video_id


def fixture(**overrides):
    value = dict(categoryId="demo", categoryName="示例", courseId="demo-1", title="课程",
                 chapterOrTopic="", description="", teacher="", titleOrRank="", durationSeconds=60,
                 durationDisplay="1分钟", videoId="123", playerUrl="https://sample.at.baijiayun.com/web/video/player?vid=123&token=PRIVATE")
    value.update(overrides)
    return value


class DurationTests(unittest.TestCase):
    def test_minute_and_clock_formats(self):
        for value, expected in [(54, 3240), (" 54 ", 3240), ("48:35", 2915), ("00:48:35", 2915), ("1:02:03", 3723)]:
            with self.subTest(value=value):
                self.assertEqual(duration_seconds(value), expected)

    def test_invalid_duration_is_not_zero(self):
        for value in [None, "", "unknown", "1:60", "1:60:00", "-1", "1:20:60"]:
            self.assertIsNone(duration_seconds(value))
        self.assertEqual(duration_display(None, None), "时长未知")
        self.assertEqual(duration_display(None, "待定"), "待定")


class IdentityTests(unittest.TestCase):
    def test_vid_is_stable_across_tokens(self):
        self.assertEqual(video_id("https://x.at.baijiayun.com/player?vid=123&token=A"), "123")
        self.assertEqual(video_id("https://x.at.baijiayun.com/player?token=B&vid=123"), "123")

    def test_fallback_excludes_access_and_display_parameters(self):
        first = "https://x.at.baijiayun.com/player?id=55&token=PRIVATE&signature=first&dsp=pc#fragment"
        second = "https://x.at.baijiayun.com/player?new_auth_key=new&id=55&token=SECOND&dsp=mobile"
        self.assertEqual(video_id(first, "课程甲"), video_id(second, "课程甲"))
        self.assertNotEqual(video_id(first, "课程甲"), video_id(first, "课程乙"))
        self.assertTrue(video_id(first).startswith("url-"))

    def test_player_url_validation(self):
        for value in ["", None, "http://x.at.baijiayun.com/player", "https://baijiayun.com.attacker.test/player", "https://[broken", "javascript:alert(1)", "https://user:secret@x.at.baijiayun.com/player"]:
            self.assertFalse(valid_player_url(value))
        self.assertTrue(valid_player_url("https://x.at.baijiayun.com/player?vid=1"))
        self.assertTrue(valid_player_url("https://x.at.baijiacloud.com/player?vid=1"))


class ConversionTests(unittest.TestCase):
    def test_only_real_merged_cells_are_inherited(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.xlsx"
            workbook = Workbook()
            sheet = workbook.active
            sheet.title = "文旅融合类"
            sheet.append(["序号", "专题", "节", "介绍", "老师", "职称", "时长", "试看地址"])
            for serial in range(1, 5):
                sheet.append([serial, "专题甲" if serial == 1 else None, f"章节{serial}",
                              "介绍甲" if serial == 1 else None, "老师", None, "48:35",
                              f"https://x.at.baijiayun.com/player?vid={serial}"])
            sheet.merge_cells("B2:B3")
            sheet.merge_cells("D2:D3")
            workbook.save(source)
            workbook.close()
            courses = convert(source)
            self.assertEqual(len(courses), 4)
            self.assertEqual(courses[1]["title"], "专题甲")
            self.assertEqual(courses[1]["description"], "介绍甲")
            self.assertEqual(courses[2]["title"], "")
            self.assertEqual(courses[2]["description"], "")
            self.assertEqual(courses[3]["description"], "")
            self.assertEqual(courses[0]["durationSeconds"], 2915)


class ReportTests(unittest.TestCase):
    def test_bad_data_warns_without_printing_url_tokens(self):
        report = catalog_report([fixture(title="", playerUrl="broken?token=PRIVATE", durationSeconds=None)])
        self.assertEqual(len(report["warnings"]), 3)
        self.assertNotIn("PRIVATE", json.dumps(report))
        self.assertNotIn("broken", json.dumps(report))

    def test_duplicate_video_is_allowed_but_duplicate_course_is_error(self):
        report = catalog_report([fixture(), fixture(courseId="demo-2")], 2, 1, 1)
        self.assertEqual(report["errors"], [])
        self.assertEqual(report["warnings"], [])
        self.assertEqual(len(report["sharedVideos"]), 1)
        report = catalog_report([fixture(), fixture()], 2, 1, 1)
        self.assertTrue(any("courseId" in value for value in report["errors"]))

    def test_count_mismatch_is_error(self):
        report = catalog_report([fixture()], 131, 3, 130)
        self.assertEqual(len(report["errors"]), 3)

    def test_structurally_invalid_entries_are_reported(self):
        self.assertTrue(catalog_report({})["errors"])
        self.assertTrue(catalog_report([None, {"courseId": ["invalid"]}])["errors"])

    def test_prebuild_cli_exit_codes(self):
        command = [sys.executable, str(Path(__file__).with_name("validate_catalog.py"))]
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "catalog.json"
            args = [str(target), "--expected-records", "2", "--expected-categories", "1", "--expected-videos", "1", "--strict"]
            target.write_text(json.dumps([fixture(), fixture(courseId="demo-2")]), encoding="utf-8")
            success = subprocess.run(command + args, capture_output=True)
            self.assertEqual(success.returncode, 0, success.stdout)
            target.write_text(json.dumps([fixture(), fixture(courseId="demo-2", title="")]), encoding="utf-8")
            failure = subprocess.run(command + args, capture_output=True)
            self.assertEqual(failure.returncode, 1)
            self.assertNotIn(b"PRIVATE", failure.stdout)


if __name__ == "__main__":
    unittest.main()
