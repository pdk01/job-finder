package com.radar.agent.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;

public final class Pdf {
    private Pdf() {}

    public static String readText(Path path) {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text == null ? "" : text;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read PDF: " + path, e);
        }
    }
}