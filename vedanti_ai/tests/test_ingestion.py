from uuid import uuid4

from app.ingestion.chunker import chunk_segments
from app.ingestion.extract import ExtractedSegment
from app.models import DepartmentCode, DocumentCategory


def test_chunker_preserves_source_metadata_and_overlap():
    document_id = uuid4()
    version_id = uuid4()
    chunks = chunk_segments(
        [ExtractedSegment("one two three four five six", page_number=2)],
        document_id=document_id,
        version_id=version_id,
        version=1,
        department=DepartmentCode.ENGINEERING,
        category=DocumentCategory.SOP,
        document_title="Runbook",
        chunk_size=4,
        overlap=1,
    )
    assert len(chunks) == 2
    assert chunks[0].page_number == 2
    assert chunks[0].document_id == document_id
    assert chunks[1].content.split()[0] == "four"


def test_docx_or_pdf_error_codes_are_contract_names():
    from app.ingestion.extract import extract_document

    try:
        extract_document("document.txt")
    except ValueError as error:
        assert str(error) == "UNSUPPORTED_FORMAT"
    else:
        raise AssertionError("unsupported format should fail")
