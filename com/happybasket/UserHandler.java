package com.happybasket;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;


public class UserHandler {

  
    //  REGISTER  –  POST /register
   
    public static void handleRegister(HttpExchange ex) throws IOException {
       
        String body = readBody(ex);
        Map<String, String> params = parseFormData(body);

        String fullName = params.getOrDefault("fullname", "").trim();
        String email    = params.getOrDefault("email",    "").trim();
        String phone    = params.getOrDefault("phone",    "").trim();
        String password = params.getOrDefault("password", "").trim();
        String confirm  = params.getOrDefault("confirm",  "").trim();

        // Validation
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            redirect(ex, "/register.html?msg=emptyfields");
            return;
        }
        if (!password.equals(confirm)) {
            redirect(ex, "/register.html?msg=passmismatch");
            return;
        }
        if (password.length() < 6) {
            redirect(ex, "/register.html?msg=shortpass");
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;

        try {
            con = DBConnection.getConnection();

            // Check email not already used
            PreparedStatement check = con.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE email = ?");
            check.setString(1, email);
            ResultSet rs = check.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                check.close();
                redirect(ex, "/register.html?msg=emailexists");
                return;
            }
            check.close();

            // INSERT new user
            ps = con.prepareStatement(
                "INSERT INTO users (full_name, email, phone, password) VALUES (?, ?, ?, ?)");
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, password);   // plain text for college project
            ps.executeUpdate();

            redirect(ex, "/login.html?msg=registered");

        } catch (SQLException e) {
            e.printStackTrace();
            redirect(ex, "/register.html?msg=dberror");
        } finally {
            try { if (ps  != null) ps.close();  } catch (Exception ignored) {}
            try { if (con != null) con.close(); } catch (Exception ignored) {}
        }
    }

    //  LOGIN  –  POST /login
 
    public static void handleLogin(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        Map<String, String> params = parseFormData(body);

        String email    = params.getOrDefault("email",    "").trim();
        String password = params.getOrDefault("password", "").trim();

        if (email.isEmpty() || password.isEmpty()) {
            redirect(ex, "/login.html?msg=empty");
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;

        try {
            con = DBConnection.getConnection();

            // SELECT user by email and password
            ps = con.prepareStatement(
                "SELECT user_id, full_name, email FROM users WHERE email = ? AND password = ?");
            ps.setString(1, email);
            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int    userId   = rs.getInt("user_id");
                String fullName = rs.getString("full_name");
                String userEmail = rs.getString("email");

                // Create session
                String sessionId = SessionManager.createSession(userId, fullName, userEmail);

                // Set cookie and redirect to home
                ex.getResponseHeaders().add("Set-Cookie",
                    "HBSESSION=" + sessionId + "; Path=/; HttpOnly");
                redirect(ex, "/index.html");

            } else {
                redirect(ex, "/login.html?msg=invalid");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            redirect(ex, "/login.html?msg=dberror");
        } finally {
            try { if (ps  != null) ps.close();  } catch (Exception ignored) {}
            try { if (con != null) con.close(); } catch (Exception ignored) {}
        }
    }

    //  LOGOUT  –  GET /logout

    public static void handleLogout(HttpExchange ex) throws IOException {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        String sessionId = SessionManager.parseSessionId(cookie);
        SessionManager.removeSession(sessionId);

        // Clear cookie
        ex.getResponseHeaders().add("Set-Cookie",
            "HBSESSION=; Path=/; Max-Age=0");
        redirect(ex, "/login.html?msg=loggedout");
    }

    //  Helpers


    /** Read POST body as a String */
    static String readBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Parse URL-encoded form data:  key=value&key2=value2 */
    static Map<String, String> parseFormData(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            try {
                String key = URLDecoder.decode(kv[0], "UTF-8");
                String val = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
                map.put(key, val);
            } catch (Exception ignored) {}
        }
        return map;
    }

    /** Send HTTP redirect (302) */
    static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    /** Send HTML response */
    static void sendHtml(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
