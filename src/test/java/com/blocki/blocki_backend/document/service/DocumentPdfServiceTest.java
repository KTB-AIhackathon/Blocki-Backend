package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocki.blocki_backend.document.entity.DocumentType;
import java.time.Instant;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class DocumentPdfServiceTest {
    @Test
    void embeds_korean_markdown_in_a_pdf() throws Exception {
        byte[] pdf = new DocumentPdfService().render(new DocumentQueryService.DocumentContentResult(
                UUID.randomUUID(), DocumentType.RESUME, "이력서", 1,
                "# 김블로\n\n프로젝트 경험과 문제 해결", Instant.now(), "AI_GENERATED"));

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("김블로", "프로젝트 경험");
        }
    }
}
