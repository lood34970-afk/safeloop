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
TEAM = "설계3.5팀"
GITHUB = "https://github.com/lood34970-afk"


def _apk_link(repo: str, apk: str) -> str:
    return f"[{apk}]({repo}/blob/main/apk/{quote(apk)}) · [Release v1.0]({repo}/releases/tag/v1.0)"


SL = f"{GITHUB}/safeloop"
DC = f"{GITHUB}/drivecanvas"

# Usage: python scripts/create_proposal_docx.py [safeloop|drivecanvas] [output.docx]
PROFILES = {
    "safeloop": {
        "app_name": "SafeLoop(세이프루프)",
        "content": ROOT / "docs" / "PROPOSAL_CONTENT_V1.md",
        "output": ROOT / "docs" / "SafeLoop_공모전_제안서_v1.docx",
        "keywords": "SafeLoop, Pleos, 운전 습관, 차량용 앱",
        "references": [
            f"- GitHub 저장소(소스 코드·release APK): [{SL}]({SL})",
            f"- 시연 영상(2분 2초, 자막 포함): [SafeLoop_demo.mp4]({SL}/releases/download/v1.0/SafeLoop_demo.mp4)",
            f"- 제출용 release APK: {_apk_link(SL, f'{TEAM}_SafeLoop_release.apk')}",
            f"- 기술 검증 결과: [TECH_SPIKE_RESULTS.md]({SL}/blob/main/docs/TECH_SPIKE_RESULTS.md)",
            f"- 핵심 API 진단 화면 캡처: [safeloop-capability-spike.png]({SL}/blob/main/docs/assets/safeloop-capability-spike.png)",
        ],
    },
    "drivecanvas": {
        "app_name": "Drive Canvas(드라이브 캔버스)",
        "content": ROOT / "ideas" / "02-drive-canvas" / "docs" / "PROPOSAL_CONTENT_V1.md",
        "output": ROOT / "docs" / "DriveCanvas_공모전_제안서_v1.docx",
        "keywords": "Drive Canvas, Pleos, NaviHelper, GPS 아트, 차량용 앱",
        "references": [
            f"- GitHub 저장소(소스 코드·release APK): [{DC}]({DC})",
            f"- 시연 영상 1 — 완주 시나리오(2분 5초, 자막 포함): [DriveCanvas_demo_complete.mp4]({DC}/releases/download/v1.0/DriveCanvas_demo_complete.mp4)",
            f"- 시연 영상 2 — 경로 이탈 시나리오(1분 40초, 자막 포함): [DriveCanvas_demo_offroute.mp4]({DC}/releases/download/v1.0/DriveCanvas_demo_offroute.mp4)",
            f"- 제출용 release APK: {_apk_link(DC, f'{TEAM}_DriveCanvas_release.apk')}",
            f"- 프로토타입 검증 결과: [prototype/README.md]({DC}/blob/main/prototype/README.md) · 구현 설계: [prototype/SPEC.md]({DC}/blob/main/prototype/SPEC.md)",
            f"- 화면: [코스 비교]({DC}/blob/main/assets/drivecanvas-course.png) · [주행]({DC}/blob/main/assets/drivecanvas-driving.png) · [완주 결과]({DC}/blob/main/assets/drivecanvas-result-completed.png) · [경로 이탈 결과]({DC}/blob/main/assets/drivecanvas-result-offroute.png)",
        ],
    },
}


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


def replace_app_name(paragraph, app_name: str) -> None:
    paragraph.clear()
    add_text_run(paragraph, f"앱명 / 프로젝트명: {app_name}", bold=True, size=10.5)


def main() -> int:
    key = sys.argv[1] if len(sys.argv) > 1 else "safeloop"
    if key not in PROFILES:
        print(f"Unknown profile {key!r}; choose one of {sorted(PROFILES)}", file=sys.stderr)
        return 2
    profile = PROFILES[key]
    content = profile["content"]
    output = Path(sys.argv[2]) if len(sys.argv) > 2 else profile["output"]
    if not TEMPLATE.exists() or not content.exists():
        print("Required template or content file is missing.", file=sys.stderr)
        return 2

    sections = parse_sections(content.read_text(encoding="utf-8"))
    document = Document(TEMPLATE)
    if len(document.tables) < 9 or len(document.paragraphs) <= 28:
        print("Official template structure is different from the expected version.", file=sys.stderr)
        return 3

    app_name = profile["app_name"]
    replace_app_name(document.paragraphs[12], app_name)
    replace_app_name(document.paragraphs[28], app_name)
    replace_app_name(document.paragraphs[66], app_name)
    for number in range(1, 7):
        fill_cell_from_markdown(document.tables[number].cell(0, 0), sections[number], number)
    fill_cell_from_markdown(document.tables[7].cell(0, 0), profile["references"], 7)

    properties = document.core_properties
    properties.title = f"{app_name.split('(')[0]} 공모전 제안서"
    properties.subject = "현대자동차그룹 차량용 앱 개발 공모전"
    properties.keywords = profile["keywords"]
    properties.comments = "공식 양식을 보존한 제안서 내용 초안"
    # Keep personal names out of file metadata (the official template carries one).
    properties.author = TEAM
    properties.last_modified_by = TEAM

    output.parent.mkdir(parents=True, exist_ok=True)
    document.save(output)
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
