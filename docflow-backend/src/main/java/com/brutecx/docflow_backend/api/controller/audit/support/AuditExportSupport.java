package com.brutecx.docflow_backend.api.controller.audit.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class AuditExportSupport {

    public static final String NDJSON = "application/x-ndjson";
    public static final String CSV = "text/csv";
    public static final String UTF_8 = "UTF-8";

    private AuditExportSupport() {
    }

    public static void prepareNdjson(HttpServletResponse response, String filename) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(filename, "filename");
        response.setHeader(HttpHeaders.CONTENT_TYPE, NDJSON);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding(UTF_8);
    }

    public static void prepareCsv(HttpServletResponse response, String filename) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(filename, "filename");
        response.setHeader(HttpHeaders.CONTENT_TYPE, CSV);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding(UTF_8);
    }

    public static PrintWriter newUtf8Writer(HttpServletResponse response) {
        try {
            return new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to open response output stream", ex);
        }
    }

    public static void writeJsonlLine(PrintWriter w, ObjectMapper objectMapper, Object dto) {
        Objects.requireNonNull(w, "writer");
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(dto, "dto");
        try {
            w.println(objectMapper.writeValueAsString(dto));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize JSONL export record", ex);
        }
    }

    public static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needsQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
