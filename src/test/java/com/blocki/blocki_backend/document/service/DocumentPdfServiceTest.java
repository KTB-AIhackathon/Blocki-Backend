package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    // 이모지(대부분 surrogate pair + NotoSansKR 미지원 glyph)가 섞인 markdown이 들어와도
    // 예외 없이 PDF가 만들어지고, 이모지를 제외한 나머지 텍스트는 정상적으로 남아있는지 확인.
    // 회귀 재현: 포트폴리오 markdown에 이모지 4개가 섞여 있으면 charAt() 기반 순회가
    // surrogate pair를 반으로 잘라 PDFBox 인코딩 단계에서 IllegalArgumentException -> 500.
    @Test
    void renders_markdown_containing_emojis_without_throwing() {
        String markdownWithFourEmojis = "# 김블로 🚀\n\n"
                + "문제 해결 능력 💡\n"
                + "협업 지향 🤝\n"
                + "꾸준한 학습 🔥";

        assertThatCode(() -> {
            byte[] pdf = new DocumentPdfService().render(new DocumentQueryService.DocumentContentResult(
                    UUID.randomUUID(), DocumentType.PORTFOLIO, "포트폴리오", 1,
                    markdownWithFourEmojis, Instant.now(), "AI_GENERATED"));

            try (var document = Loader.loadPDF(pdf)) {
                String text = new PDFTextStripper().getText(document);
                // 이모지는 폰트가 지원하지 않아 제거되지만, 나머지 한글 텍스트는 그대로 남아야 한다.
                assertThat(text).contains("김블로", "문제 해결 능력", "협업 지향", "꾸준한 학습");
            }
        }).doesNotThrowAnyException();
    }
}
