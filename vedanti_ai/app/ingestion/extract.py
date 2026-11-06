"""PDF and DOCX extraction with source-location metadata."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ExtractedSegment:
    text: str
    page_number: int | None = None
    section_title: str | None = None


def clean_text(text: str) -> str:
    text = text.replace("\u00a0", " ")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def extract_pdf(path: str | Path) -> list[ExtractedSegment]:
    from pypdf import PdfReader

    try:
        reader = PdfReader(str(path))
        segments: list[ExtractedSegment] = []
        for index, page in enumerate(reader.pages, start=1):
            text = clean_text(page.extract_text() or "")
            if text:
                segments.append(ExtractedSegment(text=text, page_number=index))
        if not segments:
            raise ValueError("NO_EXTRACTABLE_TEXT")
        return segments
    except ValueError:
        raise
    except Exception as exc:  # pypdf exposes several parser-specific exceptions.
        raise ValueError("CORRUPT_FILE") from exc


def extract_docx(path: str | Path) -> list[ExtractedSegment]:
    from docx import Document

    try:
        document = Document(str(path))
    except Exception as exc:
        raise ValueError("CORRUPT_FILE") from exc

    segments: list[ExtractedSegment] = []
    current_heading: str | None = None
    for paragraph in document.paragraphs:
        text = clean_text(paragraph.text)
        if not text:
            continue
        style_name = (paragraph.style.name or "").lower()
        if "heading" in style_name:
            current_heading = text
        else:
            segments.append(ExtractedSegment(text=text, section_title=current_heading))

    for table in document.tables:
        rows = []
        for row in table.rows:
            cells = [clean_text(cell.text) for cell in row.cells]
            if any(cells):
                rows.append(" | ".join(cells))
        if rows:
            segments.append(ExtractedSegment(text="\n".join(rows), section_title=current_heading))

    if not segments:
        raise ValueError("NO_EXTRACTABLE_TEXT")
    return segments


def extract_document(path: str | Path, content_type: str | None = None) -> list[ExtractedSegment]:
    suffix = Path(path).suffix.lower()
    if content_type == "application/pdf" or suffix == ".pdf":
        return extract_pdf(path)
    if content_type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" or suffix == ".docx":
        return extract_docx(path)
    raise ValueError("UNSUPPORTED_FORMAT")
