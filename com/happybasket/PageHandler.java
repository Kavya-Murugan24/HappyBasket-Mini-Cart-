package com.happybasket;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Map;

public class PageHandler {

    // Folder where your HTML/CSS/JS files are kept
    private static final String WEB_DIR = "web";

    //  Serve any static file (CSS, JS, images)

    public static void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "index.html";

        File file = new File(WEB_DIR + path);
        if (!file.exists()) {
            String body = "404 Not Found";
            ex.sendResponseHeaders(404, body.length());
            ex.getResponseBody().write(body.getBytes());
            ex.close();
            return;
        }

        // Detect content type
        String ct = "text/plain";
        if (path.endsWith(".html")) ct = "text/html; charset=UTF-8";
        else if (path.endsWith(".css"))  ct = "text/css";
        else if (path.endsWith(".js"))   ct = "application/javascript";
        else if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".avif") || path.endsWith(".webp"))
            ct = "image/*";

        byte[] bytes = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().add("Content-Type", ct);
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

   
    //  index.html  –  shop page

    public static void serveIndex(HttpExchange ex) throws IOException {
        Map<String, String> session = CartHandler.getSession(ex);
        String html = readFile("index.html");
        html = injectNavbar(html, session);
        UserHandler.sendHtml(ex, html);
    }

   
    //  login.html  –  show msg from URL param
 
    public static void serveLogin(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        String msg = getMsgFromQuery(query);
        String html = readFile("login.html");
        html = html.replace("{{MSG}}", msg);
        UserHandler.sendHtml(ex, html);
    }


    //  register.html  –  show msg from URL param

    public static void serveRegister(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        String msg = getMsgFromQuery(query);
        String html = readFile("register.html");
        html = html.replace("{{MSG}}", msg);
        UserHandler.sendHtml(ex, html);
    }

   
    //  cart.html  –  show cart items from MySQL
    
    public static void serveCart(HttpExchange ex) throws IOException {
        Map<String, String> session = CartHandler.getSession(ex);

        if (session == null) {
            // Not logged in → redirect to login
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }

        int userId = Integer.parseInt(session.get("userId"));
        String html = readFile("cart.html");
        html = injectNavbar(html, session);

        // Build cart rows from MySQL
        String cartRows    = "";
        BigDecimal grandTotal = BigDecimal.ZERO;
        boolean   isEmpty  = true;

        try (Connection con = DBConnection.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "SELECT c.product_id, p.name, p.price, c.quantity " +
                "FROM cart c JOIN products p ON c.product_id = p.product_id " +
                "WHERE c.user_id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                isEmpty = false;
                int    productId = rs.getInt("product_id");
                String name      = rs.getString("name");
                BigDecimal price = rs.getBigDecimal("price");
                int    qty       = rs.getInt("quantity");
                BigDecimal lineTotal = price.multiply(new BigDecimal(qty));
                grandTotal = grandTotal.add(lineTotal);

                // Each row is a small form so Update and Remove work without JS
                cartRows += "<tr>" +
                    "<td>" + name + "</td>" +
                    "<td>&#8377;" + price + "</td>" +
                    "<td>" +
                    "  <form method='POST' action='/cart/update' style='display:inline'>" +
                    "    <input type='hidden' name='productId' value='" + productId + "'>" +
                    "    <input type='number' name='qty' value='" + qty + "' min='1' style='width:55px;padding:4px;border:1px solid #e0a0b0;border-radius:4px;font-family:Poppins,sans-serif;'>" +
                    "    <button type='submit' class='btn btn-update'>Update</button>" +
                    "  </form>" +
                    "</td>" +
                    "<td>&#8377;" + lineTotal + "</td>" +
                    "<td>" +
                    "  <form method='POST' action='/cart/remove' style='display:inline'>" +
                    "    <input type='hidden' name='productId' value='" + productId + "'>" +
                    "    <button type='submit' class='btn btn-remove'>Remove</button>" +
                    "  </form>" +
                    "</td>" +
                    "</tr>";
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Check for msg params
        String query = ex.getRequestURI().getQuery();
        String msgHtml = "";
        if (query != null) {
            if (query.contains("outofstock"))   msgHtml = "<p class='cart-msg error'>❌ Sorry, one item is out of stock. Order cancelled.</p>";
            else if (query.contains("emptycart")) msgHtml = "<p class='cart-msg error'>⚠ Your cart is empty.</p>";
            else if (query.contains("dberror"))  msgHtml = "<p class='cart-msg error'>❌ Database error. Please try again.</p>";
        }

        html = html.replace("{{CART_ROWS}}", cartRows);
        html = html.replace("{{GRAND_TOTAL}}", "&#8377;" + grandTotal);
        html = html.replace("{{CART_EMPTY}}", isEmpty ? "block" : "none");
        html = html.replace("{{CART_TABLE_DISPLAY}}", isEmpty ? "none" : "table");
        html = html.replace("{{CART_ACTIONS_DISPLAY}}", isEmpty ? "none" : "flex");
        html = html.replace("{{CART_MSG}}", msgHtml);
        UserHandler.sendHtml(ex, html);
    }

   
    //  orders.html  –  show user's orders from MySQL
    
    public static void serveOrders(HttpExchange ex) throws IOException {
        Map<String, String> session = CartHandler.getSession(ex);

        if (session == null) {
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }

        int userId = Integer.parseInt(session.get("userId"));
        String html = readFile("orders.html");
        html = injectNavbar(html, session);

        // Build orders HTML from MySQL
        StringBuilder ordersHtml = new StringBuilder();
        boolean noOrders = true;

        try (Connection con = DBConnection.getConnection()) {
            // Get all orders for this user newest first
            PreparedStatement orderPs = con.prepareStatement(
                "SELECT order_id, total, status, order_date FROM orders " +
                "WHERE user_id=? ORDER BY order_date DESC");
            orderPs.setInt(1, userId);
            ResultSet orderRs = orderPs.executeQuery();

            while (orderRs.next()) {
                noOrders = false;
                int    orderId    = orderRs.getInt("order_id");
                BigDecimal total  = orderRs.getBigDecimal("total");
                String status     = orderRs.getString("status");
                String date       = orderRs.getTimestamp("order_date").toString();

                // Fetch items for this order
                PreparedStatement itemPs = con.prepareStatement(
                    "SELECT p.name, oi.qty, oi.price " +
                    "FROM order_items oi JOIN products p ON oi.product_id=p.product_id " +
                    "WHERE oi.order_id=?");
                itemPs.setInt(1, orderId);
                ResultSet itemRs = itemPs.executeQuery();

                StringBuilder itemRows = new StringBuilder();
                while (itemRs.next()) {
                    BigDecimal lineTotal = itemRs.getBigDecimal("price")
                        .multiply(new BigDecimal(itemRs.getInt("qty")));
                    itemRows.append("<tr>")
                        .append("<td>").append(itemRs.getString("name")).append("</td>")
                        .append("<td>&#8377;").append(itemRs.getBigDecimal("price")).append("</td>")
                        .append("<td>").append(itemRs.getInt("qty")).append("</td>")
                        .append("<td>&#8377;").append(lineTotal).append("</td>")
                        .append("</tr>");
                }
                itemPs.close();

                String statusClass = status.equals("Cancelled") ? "status-cancelled" :
                                     status.equals("Confirmed")  ? "status-confirmed"  : "status-other";

                // Cancel button only if not already cancelled
                String cancelBtn = "";
                if (!status.equals("Cancelled")) {
                    cancelBtn = "<form method='POST' action='/order/cancel' style='display:inline'>" +
                                "  <input type='hidden' name='orderId' value='" + orderId + "'>" +
                                "  <button type='submit' class='btn btn-remove' " +
                                "    onclick=\"return confirm('Cancel this order?')\">Cancel Order</button>" +
                                "</form>";
                }

                ordersHtml.append("<div class='order-card'>")
                    .append("<div class='order-header'>")
                    .append("<div>")
                    .append("<span class='order-id'>Order #").append(orderId).append("</span>")
                    .append("<span class='order-date'>&#128197; ").append(date).append("</span>")
                    .append("</div>")
                    .append("<div style='display:flex;align-items:center;gap:10px;'>")
                    .append("<span class='order-status ").append(statusClass).append("'>").append(status).append("</span>")
                    .append(cancelBtn)
                    .append("</div>")
                    .append("</div>")
                    .append("<table class='order-items-table'><thead><tr>")
                    .append("<th>Product</th><th>Price</th><th>Qty</th><th>Total</th>")
                    .append("</tr></thead><tbody>")
                    .append(itemRows)
                    .append("</tbody></table>")
                    .append("<div class='order-footer'>Grand Total: <strong>&#8377;").append(total).append("</strong></div>")
                    .append("</div>");
            }
            orderPs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Check URL message param
        String query = ex.getRequestURI().getQuery();
        String msgHtml = "";
        if (query != null) {
            if (query.contains("ordersuccess"))       msgHtml = "<p class='cart-msg success'>&#127881; Order placed successfully!</p>";
            else if (query.contains("cancelled"))     msgHtml = "<p class='cart-msg success'>&#10004; Order cancelled and stock restored.</p>";
            else if (query.contains("alreadycancelled")) msgHtml = "<p class='cart-msg error'>&#9888; Order is already cancelled.</p>";
            else if (query.contains("dberror"))       msgHtml = "<p class='cart-msg error'>&#10060; Database error. Please try again.</p>";
        }

        html = html.replace("{{ORDERS_HTML}}", noOrders ? "" : ordersHtml.toString());
        html = html.replace("{{NO_ORDERS_DISPLAY}}", noOrders ? "block" : "none");
        html = html.replace("{{ORDERS_MSG}}", msgHtml);
        UserHandler.sendHtml(ex, html);
    }

   
    //  profile.html  –  user profile + address
  
    public static void serveProfile(HttpExchange ex) throws IOException {
        Map<String, String> session = CartHandler.getSession(ex);
        if (session == null) {
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }

        int userId = Integer.parseInt(session.get("userId"));
        String html = readFile("profile.html");
        html = injectNavbar(html, session);

        // Fetch user details + address from MySQL
        String fullName = "", email = "", phone = "", address = "", city = "", pincode = "";

        try (Connection con = DBConnection.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "SELECT full_name, email, phone, address, city, pincode FROM users WHERE user_id=?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                fullName = rs.getString("full_name");
                email    = rs.getString("email");
                phone    = rs.getString("phone")    != null ? rs.getString("phone")    : "";
                address  = rs.getString("address")  != null ? rs.getString("address")  : "";
                city     = rs.getString("city")     != null ? rs.getString("city")     : "";
                pincode  = rs.getString("pincode")  != null ? rs.getString("pincode")  : "";
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Check for save success message
        String query = ex.getRequestURI().getQuery();
        String msgHtml = query != null && query.contains("saved")
            ? "<p class='cart-msg success'>&#10004; Profile saved successfully!</p>" : "";

        html = html.replace("{{FULL_NAME}}", fullName);
        html = html.replace("{{EMAIL}}",     email);
        html = html.replace("{{PHONE}}",     phone);
        html = html.replace("{{ADDRESS}}",   address);
        html = html.replace("{{CITY}}",      city);
        html = html.replace("{{PINCODE}}",   pincode);
        html = html.replace("{{PROFILE_MSG}}", msgHtml);
        UserHandler.sendHtml(ex, html);
    }

  
    //  Handle profile SAVE  –  POST /profile/save
  
    public static void handleProfileSave(HttpExchange ex) throws IOException {
        Map<String, String> session = CartHandler.getSession(ex);
        if (session == null) {
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }
        int userId = Integer.parseInt(session.get("userId"));
        String body = UserHandler.readBody(ex);
        Map<String, String> params = UserHandler.parseFormData(body);

        String phone   = params.getOrDefault("phone",   "");
        String address = params.getOrDefault("address", "");
        String city    = params.getOrDefault("city",    "");
        String pincode = params.getOrDefault("pincode", "");

        try (Connection con = DBConnection.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "UPDATE users SET phone=?, address=?, city=?, pincode=? WHERE user_id=?");
            ps.setString(1, phone);
            ps.setString(2, address);
            ps.setString(3, city);
            ps.setString(4, pincode);
            ps.setInt(5, userId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        UserHandler.redirect(ex, "/profile.html?msg=saved");
    }

   
    //  Helpers
  

    /** Read an HTML file from the web/ folder */
    static String readFile(String filename) {
        try {
            return new String(Files.readAllBytes(Paths.get(WEB_DIR, filename)),
                              StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<h1>File not found: " + filename + "</h1>";
        }
    }

    /**
     * Replace {{NAVBAR}} placeholder in HTML with the right navbar.
     * If logged in  → shows Hi Name | My Orders | My Profile | Logout
     * If not logged → shows Login
     */
    static String injectNavbar(String html, Map<String, String> session) {
        if (session != null) {
            String name = session.get("fullName");
            String loggedInNav =
                "<a class='nav-link' href='index.html'>Home|</a>" +
                "<a class='nav-link' href='index.html#products'>Products|</a>" +
                "<a class='nav-link' href='index.html#about'>About|</a>" +
                "<a class='nav-link' href='index.html#contact'>Contact|</a>" +
                "<a class='nav-link' href='/orders.html'>My Orders|</a>" +
                "<a class='nav-link' href='/profile.html'>&#128100; " + name + "|</a>" +
                "<a class='nav-link' href='/logout'>Logout|</a>" +
                "<a class='nav-link cart' href='/cart.html'>Cart &#128722; <span id='cart-count' class='cart-badge'>0</span></a>";
            return html.replace("{{NAVBAR}}", loggedInNav);
        } else {
            String guestNav =
                "<a class='nav-link' href='index.html'>Home|</a>" +
                "<a class='nav-link' href='index.html#products'>Products|</a>" +
                "<a class='nav-link' href='index.html#about'>About|</a>" +
                "<a class='nav-link' href='index.html#contact'>Contact|</a>" +
                "<a class='nav-link' href='/login.html'>Login|</a>" +
                "<a class='nav-link cart' href='/cart.html'>Cart &#128722; <span id='cart-count' class='cart-badge'>0</span></a>";
            return html.replace("{{NAVBAR}}", guestNav);
        }
    }

    /** Parse msg from URL query string and return styled HTML */
    static String getMsgFromQuery(String query) {
        if (query == null) return "";
        if (query.contains("registered"))    return "<p class='auth-msg' style='color:green'>&#10004; Registered! Please login.</p>";
        if (query.contains("loggedout"))     return "<p class='auth-msg' style='color:#888'>You have been logged out.</p>";
        if (query.contains("loginrequired")) return "<p class='auth-msg' style='color:orange'>&#9888; Please login to continue.</p>";
        if (query.contains("invalid"))       return "<p class='auth-msg' style='color:red'>&#10060; Invalid email or password.</p>";
        if (query.contains("empty"))         return "<p class='auth-msg' style='color:red'>&#9888; Please fill in all fields.</p>";
        if (query.contains("dberror"))       return "<p class='auth-msg' style='color:red'>&#10060; Database error. Try again.</p>";
        if (query.contains("emailexists"))   return "<p class='auth-msg' style='color:red'>&#9888; Email already registered. <a href='/login.html'>Login here</a></p>";
        if (query.contains("passmismatch"))  return "<p class='auth-msg' style='color:red'>&#10060; Passwords do not match.</p>";
        if (query.contains("shortpass"))     return "<p class='auth-msg' style='color:red'>&#10060; Password must be at least 6 characters.</p>";
        if (query.contains("emptyfields"))   return "<p class='auth-msg' style='color:red'>&#9888; All fields are required.</p>";
        return "";
    }
}
