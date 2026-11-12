from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import quote

from docx import Document
from docx.enum.text import WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt


ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "docs" / "contest-application-consent.official.docx"
CONTENT = ROOT / "docs" / "PROPOSAL_CONTENT_V1.md"
OUTPUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs" / "SafeLoop_공모전_제안서_v1.docx"
REPO = "https://github.com/lood34970-afk/safeloop"
RELEASE = f"{REPO}/releases"


def parse_sections(markdown: str) -> dict[int, list[str]]:
    sections: dict[int, list[str]] = {}
    current: int | None = None
    for raw_line in markdown.splitlines():
        heading = re.match(r"^##\s+([1-6])\.\s+", raw_line)
        if heading:
            current = int(heading.group(1))
            sections[current] = []
            continue
        if raw_line.startswith("## ") and current is not None:
            current = None
            continue
        if current is not None:
            sections[current].append(raw_line.rstrip())
    missing = sorted(set(range(1, 7)) - set(sections))
    if missing:
        raise ValueError(f"Missing proposal sections: {missing}")
    return sections


def set_cell_margins(cell, top=90, start=120, bottom=90, end=120) -> None:
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for name, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{name}"))
        if node is None:
            node = OxmlElement(f"w:{name}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def clear_cell(cell) -> None:
    tc = cell._tc
    for child in list(tc):
        if child.tag != qn("w:tcPr"):
            tc.remove(child)
    tc.append(OxmlElement("w:p"))


def style_paragraph(paragraph, *, heading=False, compact=False) -> None:
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(4 if heading else 0)
    fmt.space_after = Pt(2 if heading else (1 if compact else 3))
    fmt.line_spacing_rule = WD_LINE_SPACING.SINGLE
    fmt.keep_with_next = heading
    fmt.widow_control = True


def add_text_run(paragraph, text: str, *, bold=False, size=9.2) -> None:
    run = paragraph.add_run(text)
    run.bold = bold
    run.font.name = "맑은 고딕"
    run.font.size = Pt(size)
    run._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")


LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)\s]+)\)")


def add_hyperlink_run(paragraph, text: str, url: str, *, size=9.2) -> None:
    rel_id = paragraph.part.relate_to(
        url,
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink",
        is_external=True,
    )
    hyperlink = OxmlElement("w:hyperlink")
    hyperlink.set(qn("r:id"), rel_id)
    run = OxmlElement("w:r")
    r_pr = OxmlElement("w:rPr")
    fonts = OxmlElement("w:rFonts")
    for attr in ("w:ascii", "w:hAnsi", "w:eastAsia"):
        fonts.set(qn(attr), "맑은 고딕")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "0563C1")
    underline = OxmlElement("w:u")
    underline.set(qn("w:val"), "single")
    font_size = OxmlElement("w:sz")
    font_size.set(qn("w:val"), str(round(size * 2)))
    r_pr.extend([fonts, color, underline, font_size])
    run.append(r_pr)
    text_el = OxmlElement("w:t")
    text_el.text = text
    text_el.set(qn("xml:space"), "preserve")
    run.append(text_el)
    hyperlink.append(run)
    paragraph._p.append(hyperlink)


def add_rich_text(paragraph, text: str, *, size=9.2) -> None:
    """Add text, turning markdown [label](url) into clickable hyperlinks."""
    pos = 0
    for match in LINK.finditer(text):
        if match.start() > pos:
            add_text_run(paragraph, text[pos:match.start()], size=size)
        add_hyperlink_run(paragraph, match.group(1), match.group(2), size=size)
        pos = match.end()
    if pos < len(text):
        add_text_run(paragraph, text[pos:], size=size)


def fill_cell_from_markdown(cell, lines: list[str], section_number: int) -> None:
    clear_cell(cell)
    set_cell_margins(cell)
    first = True
    in_code = False

    for raw in lines:
        line = raw.strip()
        if line.startswith("```"):
            in_code = not in_code
            continue
        if not line:
            continue

        if first:
            paragraph = cell.paragraphs[0]
            first = False
        else:
            paragraph = cell.add_paragraph()

        if line.startswith("### "):
            style_paragraph(paragraph, heading=True)
            add_text_run(paragraph, line[4:], bold=True, size=9.6)
            continue

        bullet = re.match(r"^-\s+(.*)", line)
        numbered = re.match(r"^(\d+)\.\s+(.*)", line)
        if bullet:
            style_paragraph(paragraph, compact=True)
            paragraph.paragraph_format.left_indent = Pt(10)
            paragraph.paragraph_format.first_line_indent = Pt(-8)
            add_text_run(paragraph, "• ", bold=True)
            add_rich_text(paragraph, bullet.group(1))
            continue
        if numbered:
            style_paragraph(paragraph, compact=True)
            paragraph.paragraph_format.left_indent = Pt(12)
            paragraph.paragraph_format.first_line_indent = Pt(-12)
            add_text_run(paragraph, f"{numbered.group(1)}. ", bold=True)
            add_text_run(paragraph, numbered.group(2))
            continue

        style_paragraph(paragraph, compact=in_code)
        add_text_run(paragraph, line, size=9.0 if section_number in (3, 5, 6) else 9.2)

    if first:
        add_text_run(cell.paragraphs[0], "")


def replace_app_name(paragraph) -> None:
    paragraph.clear()
    add_text_run(paragraph, "앱명 / 프로젝트명: SafeLoop(세이프루프)", bold=True, size=10.5)


def main() -> int:
    if not TEMPLATE.exists() or not CONTENT.exists():
        print("Required template or content file is missing.", file=sys.stderr)
        return 2

    sections = parse_sections(CONTENT.read_text(encoding="utf-8"))
    document = Document(TEMPLATE)
    if len(document.tables) < 9 or len(document.paragraphs) <= 28:
        print("Official template structure is different from the expected version.", file=sys.stderr)
        return 3

    replace_app_name(document.paragraphs[12])
    replace_app_name(document.paragraphs[28])
    replace_app_name(document.paragraphs[66])
    for number in range(1, 7):
        fill_cell_from_markdown(document.tables[number].cell(0, 0), sections[number], number)
    fill_cell_from_markdown(
        document.tables[7].cell(0, 0),
        [
            f"- GitHub 저장소(소스 코드·release APK): [{REPO}]({REPO})",
            f"- 시연 영상(2분 2초, 자막 포함): [SafeLoop_demo.mp4]({RELEASE}/download/v1.0/SafeLoop_demo.mp4)",
            f"- 제출용 release APK: [설계3.5팀_SafeLoop_release.apk]({REPO}/blob/main/apk/{quote('설계3.5팀_SafeLoop_release.apk')}) · [Release v1.0]({RELEASE}/tag/v1.0)",
            f"- 기술 검증 결과: [TECH_SPIKE_RESULTS.md]({REPO}/blob/main/docs/TECH_SPIKE_RESULTS.md)",
            f"- 핵심 API 진단 화면 캡처: [safeloop-capability-spike.png]({REPO}/blob/main/docs/assets/safeloop-capability-spike.png)",
        ],
        7,
    )

    properties = document.core_properties
    properties.title = "SafeLoop 공모전 제안서"
    properties.subject = "현대자동차그룹 차량용 앱 개발 공모전"
    properties.keywords = "SafeLoop, Pleos, 운전 습관, 차량용 앱"
    properties.comments = "공식 양식을 보존한 제안서 내용 초안"

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    document.save(OUTPUT)
    print(OUTPUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
