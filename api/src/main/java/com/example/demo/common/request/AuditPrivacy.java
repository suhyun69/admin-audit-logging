package com.example.demo.common.request;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AuditPrivacy {
    private static final String UUID_PATH = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern PAGE = Pattern.compile("/(?:login|dashboard|users(?:/[1-9][0-9]{0,18})?|audit-logs(?:/(?:me|" + UUID_PATH + "|interactions/" + UUID_PATH + "))?)");
    private static final Pattern API = Pattern.compile("/api/v1/(?:local/(?:accounts|login)|auth/(?:login|logout|me|csrf)|users(?:/[1-9][0-9]{0,18}(?:/status)?)?|audit-events/client|audit-logs(?:/(?:me|summary|" + UUID_PATH + "|interactions/" + UUID_PATH + "))?)");
    private AuditPrivacy() {}

    public static UUID uuid(String input) {
        if (input == null || input.length() != 36) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        try {
            UUID value = UUID.fromString(input);
            if (!value.toString().equalsIgnoreCase(input)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public static String requestPath(String input) {
        String path = withoutQuery(input);
        return API.matcher(path).matches() ? path : "/api/v1/[unmatched]";
    }

    public static String pagePath(String input) {
        String path = withoutQuery(input);
        if (!PAGE.matcher(path).matches()) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return path;
    }

    private static String withoutQuery(String input) {
        if (input == null || input.length() > 4096) return "";
        return input.split("[?#]", 2)[0];
    }

    public static String userAgent(String input) {
        if (input == null) return null;
        String cleaned = input.replaceAll("[\\p{Cntrl}]", "");
        return cleaned.substring(0, Math.min(cleaned.length(), 512));
    }

    public static Map<String, Object> metadata(Map<String, ?> input) {
        var clean = new LinkedHashMap<String, Object>();
        if (input == null) return clean;
        if (input.get("searched") instanceof Boolean value) clean.put("searched", value);
        if (input.get("status") instanceof String value && (value.equals("ACTIVE") || value.equals("SUSPENDED"))) clean.put("status", value);
        for (String key : new String[]{"size", "returnedCount"}) {
            Object value = input.get(key);
            if (value instanceof Integer number && number >= 0 && number <= 100) clean.put(key, number);
        }
        if (input.get("payloadDigest") instanceof String value && value.matches("[0-9a-f]{64}")) clean.put("payloadDigest", value);
        if(input.get("pagePath") instanceof String value) {
            try { clean.put("pagePath",pagePath(value)); }catch(ApiException ignored) { /* Discard non-allowlisted paths. */ }
        }
        return Map.copyOf(clean);
    }
}
