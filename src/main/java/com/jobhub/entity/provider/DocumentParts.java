package com.jobhub.entity.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.provider.*;
import java.util.List;

/** Stored JSON is private configuration/evidence, never a public asset URL. */
public final class DocumentParts {
    private static final ObjectMapper JSON = new ObjectMapper();
    private DocumentParts() {}
    public static List<DocumentPartDefinition> definitions(String value) {
        return read(value, new TypeReference<List<DocumentPartDefinition>>() {});
    }
    public static List<DocumentPartFiles> files(String value) {
        return read(value, new TypeReference<List<DocumentPartFiles>>() {});
    }
    private static <T> List<T> read(String value, TypeReference<List<T>> type) {
        if(value==null || value.isBlank()) return List.of();
        try { return JSON.readValue(value,type); }
        catch(Exception e) { throw new IllegalStateException("Stored document parts are invalid",e); }
    }
    public static String write(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch(Exception e) { throw new IllegalArgumentException("Invalid document parts",e); }
    }
}
