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
                content.showText(line);
                content.endText();
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
            for (int index = 0; index < line.length(); index++) {
                String character = String.valueOf(line.charAt(index));
                if (font.getStringWidth(current + character) / 1000 * FONT_SIZE > CONTENT_WIDTH && !current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(character);
            }
            lines.add(current.toString());
        }
        return lines;
    }
}
