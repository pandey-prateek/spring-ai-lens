package com.bluntyrod.springailens.util.diff;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class PromptHasher {

    public String hash(String prompt) {
        if (prompt == null || prompt.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalize(prompt).getBytes());
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public String normalize(String prompt) {
        if (prompt == null) return "";
        // strip variable content — numbers, UUIDs, emails — to detect template changes
        return prompt
                .replaceAll("\\d+", "N")
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "UUID")
                .replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "EMAIL")
                .trim()
                .toLowerCase();
    }
}
