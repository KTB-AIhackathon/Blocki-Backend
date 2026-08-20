package com.blocki.blocki_backend.document.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

@Service
public class DocumentPdfService {

    private static final float FONT_SIZE = 11;
    private static final float MARGIN = 48;
    private static final float LINE_HEIGHT = 17;
    private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - (MARGIN * 2);

    public byte[] render(DocumentQueryService.DocumentContentResult document) {
        try (PDDocument pdf = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSansKR-VF.ttf");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (fontStream == null) {
                throw new IllegalStateException("Korean PDF font is unavailable");
            }
            PDType0Font font = PDType0Font.load(pdf, fontStream, true);
            List<String> lines = wrap(document.markdown(), font);
            write(pdf, font, lines);
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("PDF rendering failed", exception);
        }
    }

    private void write(PDDocument pdf, PDType0Font font, List<String> lines) throws IOException {
        PDPage page = null;
        PDPageContentStream content = null;
        float y = 0;
        try {
            for (String line : lines) {
                if (content == null || y < MARGIN) {
                    if (content != null) {
                        content.close();
                    }
                    page = new PDPage(PDRectangle.A4);
                    pdf.addPage(page);
                    content = new PDPageContentStream(pdf, page);
                    y = page.getMediaBox().getHeight() - MARGIN;
                    content.setFont(font, FONT_SIZE);
                }
                content.beginText();
                content.newLineAtOffset(MARGIN, y);
                try {
                    content.showText(line);
                } catch (IllegalArgumentException exception) {
                    // wrap()에서 걸러지지 않고 남아있는, 폰트가 인코딩하지 못하는 문자가 있어도
                    // 문서 생성 전체가 500으로 죽지 않도록 이 줄만 건너뛴다(안전망).
                } finally {
                    content.endText();
                }
                y -= LINE_HEIGHT;
            }
        } finally {
            if (content != null) {
                content.close();
            }
        }
    }

    private List<String> wrap(String markdown, PDType0Font font) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String sourceLine : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String line = sourceLine.replaceFirst("^#{1,6}\\s*", "").replace("**", "").replace("`", "");
            if (line.isBlank()) {
                lines.add(" ");
                continue;
            }
            StringBuilder current = new StringBuilder();
            // char(UTF-16 code unit) 단위가 아니라 Unicode code point 단위로 순회한다.
            // 대부분의 이모지는 BMP 밖에 있어 surrogate pair(char 2개)로 인코딩되는데,
            // charAt()으로 순회하면 이 쌍이 반쪽(lone surrogate)으로 잘려 PDFBox 인코딩 단계에서
            // IllegalArgumentException을 던지고, 그게 PDF 다운로드 요청의 500 에러로 이어졌었다.
            int index = 0;
            while (index < line.length()) {
                int codePoint = line.codePointAt(index);
                index += Character.charCount(codePoint);
                String character = new String(Character.toChars(codePoint));

                if (!isRenderable(font, character)) {
                    // code point를 온전히 넘겨도, NotoSansKR에 애초에 없는 글리프(이모지 등)는
                    // 여전히 인코딩할 수 없다. 이런 문자는 PDF에 그릴 수 없으므로 제거하고 넘어간다.
                    continue;
                }

                if (!current.isEmpty() && exceedsContentWidth(font, current, character)) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(character);
            }
            lines.add(current.toString());
        }
        return lines;
    }

    private boolean exceedsContentWidth(PDType0Font font, StringBuilder current, String character) throws IOException {
        return font.getStringWidth(current + character) / 1000 * FONT_SIZE > CONTENT_WIDTH;
    }

    /**
     * 폰트가 이 문자를 실제로 인코딩(glyph 매핑)할 수 있는지 확인한다.
     * 지원하지 않는 문자에 대해 PDType0Font#encode는 IllegalArgumentException을,
     * 드물게는 IOException을 던지므로 둘 다 "렌더링 불가"로 취급한다.
     */
    private static boolean isRenderable(PDType0Font font, String character) {
        try {
            font.encode(character);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }
}
