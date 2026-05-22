package com.happybasket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class SessionManager {

    // sessionId  →  user info map (userId, fullName, email)
    private static final Map<String, Map<String, String>> sessions = new HashMap<>();

    /** Create a new session for a logged-in user. Returns the session ID. */
    public static String createSession(int userId, String fullName, String email) {
        String sessionId = UUID.randomUUID().toString();
        Map<String, String> data = new HashMap<>();
        data.put("userId",   String.valueOf(userId));
        data.put("fullName", fullName);
        data.put("email",    email);
        sessions.put(sessionId, data);
        return sessionId;
    }

    /** Get session data by session ID. Returns null if not found. */
    public static Map<String, String> getSession(String sessionId) {
        if (sessionId == null) return null;
        return sessions.get(sessionId);
    }

  
    public static void removeSession(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
    }

    /** Parse session ID from Cookie header value. */
    public static String parseSessionId(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            part = part.trim();
            if (part.startsWith("HBSESSION=")) {
                return part.substring("HBSESSION=".length());
            }
        }
        return null;
    }
}
