package com.happybasket;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;


public class CartHandler {

  
    public static void handleAdd(HttpExchange ex) throws IOException {
        Map<String, String> session = getSession(ex);
        if (session == null) {
            // Not logged in → redirect to login
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }

        int userId = Integer.parseInt(session.get("userId"));
        String body = UserHandler.readBody(ex);
        Map<String, String> params = UserHandler.parseFormData(body);

        int productId = Integer.parseInt(params.getOrDefault("productId", "0"));
        int qty       = Integer.parseInt(params.getOrDefault("qty", "1"));

        if (productId <= 0) {
            UserHandler.redirect(ex, "/index.html");
            return;
        }

        Connection con = null;
        try {
            con = DBConnection.getConnection();

            // If already in cart → increase qty, else insert new row
            PreparedStatement check = con.prepareStatement(
                "SELECT cart_id, quantity FROM cart WHERE user_id=? AND product_id=?");
            check.setInt(1, userId);
            check.setInt(2, productId);
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                // Update quantity
                int newQty = rs.getInt("quantity") + qty;
                int cartId = rs.getInt("cart_id");
                PreparedStatement upd = con.prepareStatement(
                    "UPDATE cart SET quantity=? WHERE cart_id=?");
                upd.setInt(1, newQty);
                upd.setInt(2, cartId);
                upd.executeUpdate();
                upd.close();
            } else {
                // Insert new cart row
                PreparedStatement ins = con.prepareStatement(
                    "INSERT INTO cart (user_id, product_id, quantity) VALUES (?,?,?)");
                ins.setInt(1, userId);
                ins.setInt(2, productId);
                ins.setInt(3, qty);
                ins.executeUpdate();
                ins.close();
            }
            check.close();

            // Redirect back to previous page (index or wherever Add to Cart was clicked)
            String referer = ex.getRequestHeaders().getFirst("Referer");
            UserHandler.redirect(ex, referer != null ? referer : "/index.html");

        } catch (SQLException e) {
            e.printStackTrace();
            UserHandler.redirect(ex, "/index.html");
        } finally {
            try { if (con != null) con.close(); } catch (Exception ignored) {}
        }
    }

  
    public static void handleRemove(HttpExchange ex) throws IOException {
        Map<String, String> session = getSession(ex);
        if (session == null) {
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }

        int userId = Integer.parseInt(session.get("userId"));
        String body = UserHandler.readBody(ex);
        Map<String, String> params = UserHandler.parseFormData(body);
        int productId = Integer.parseInt(params.getOrDefault("productId", "0"));

        try (Connection con = DBConnection.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "DELETE FROM cart WHERE user_id=? AND product_id=?");
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        UserHandler.redirect(ex, "/cart.html");
    }


    //  UPDATE QUANTITY  –  POST /cart/update
  
    public static void handleUpdate(HttpExchange ex) throws IOException {
        Map<String, String> session = getSession(ex);
        if (session == null) {
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }

        int userId = Integer.parseInt(session.get("userId"));
        String body = UserHandler.readBody(ex);
        Map<String, String> params = UserHandler.parseFormData(body);

        int productId = Integer.parseInt(params.getOrDefault("productId", "0"));
        int qty       = Integer.parseInt(params.getOrDefault("qty", "1"));

        if (qty < 1) qty = 1;

        try (Connection con = DBConnection.getConnection()) {
            PreparedStatement ps = con.prepareStatement(
                "UPDATE cart SET quantity=? WHERE user_id=? AND product_id=?");
            ps.setInt(1, qty);
            ps.setInt(2, userId);
            ps.setInt(3, productId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        UserHandler.redirect(ex, "/cart.html");
    }

 
    //  JDBC Transaction: orders + order_items + stock
   
    public static void handleCheckout(HttpExchange ex) throws IOException {
        Map<String, String> session = getSession(ex);
        if (session == null) {
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }

        int userId = Integer.parseInt(session.get("userId"));

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);  // BEGIN TRANSACTION

            // 1. Fetch cart items for this user
            PreparedStatement cartPs = con.prepareStatement(
                "SELECT c.product_id, c.quantity, p.price, p.stock, p.name " +
                "FROM cart c JOIN products p ON c.product_id = p.product_id " +
                "WHERE c.user_id = ?");
            cartPs.setInt(1, userId);
            ResultSet cartRs = cartPs.executeQuery();

            BigDecimal total = BigDecimal.ZERO;

            // Store rows in lists (can't iterate ResultSet twice)
            java.util.List<Integer> productIds = new java.util.ArrayList<>();
            java.util.List<Integer> quantities = new java.util.ArrayList<>();
            java.util.List<BigDecimal> prices  = new java.util.ArrayList<>();
            java.util.List<Integer> stocks     = new java.util.ArrayList<>();

            while (cartRs.next()) {
                productIds.add(cartRs.getInt("product_id"));
                quantities.add(cartRs.getInt("quantity"));
                prices.add(cartRs.getBigDecimal("price"));
                stocks.add(cartRs.getInt("stock"));
                total = total.add(
                    cartRs.getBigDecimal("price")
                          .multiply(new BigDecimal(cartRs.getInt("quantity")))
                );
            }
            cartPs.close();

            if (productIds.isEmpty()) {
                con.rollback();
                UserHandler.redirect(ex, "/cart.html?msg=emptycart");
                return;
            }

            // 2. INSERT into orders
            PreparedStatement orderPs = con.prepareStatement(
                "INSERT INTO orders (user_id, total, status) VALUES (?, ?, 'Confirmed')",
                Statement.RETURN_GENERATED_KEYS);
            orderPs.setInt(1, userId);
            orderPs.setBigDecimal(2, total);
            orderPs.executeUpdate();

            ResultSet keys = orderPs.getGeneratedKeys();
            keys.next();
            int orderId = keys.getInt(1);
            orderPs.close();

            // 3. For each cart item: INSERT order_items + UPDATE stock
            for (int i = 0; i < productIds.size(); i++) {
                int productId = productIds.get(i);
                int qty       = quantities.get(i);
                BigDecimal price = prices.get(i);
                int stock     = stocks.get(i);

                // Check stock
                if (stock < qty) {
                    con.rollback();  // ROLLBACK
                    UserHandler.redirect(ex, "/cart.html?msg=outofstock");
                    return;
                }

                // INSERT order_items
                PreparedStatement itemPs = con.prepareStatement(
                    "INSERT INTO order_items (order_id, product_id, qty, price) VALUES (?,?,?,?)");
                itemPs.setInt(1, orderId);
                itemPs.setInt(2, productId);
                itemPs.setInt(3, qty);
                itemPs.setBigDecimal(4, price);
                itemPs.executeUpdate();
                itemPs.close();

                // UPDATE product stock
                PreparedStatement stockPs = con.prepareStatement(
                    "UPDATE products SET stock = stock - ? WHERE product_id = ?");
                stockPs.setInt(1, qty);
                stockPs.setInt(2, productId);
                stockPs.executeUpdate();
                stockPs.close();
            }

            // 4. Clear cart
            PreparedStatement clearPs = con.prepareStatement(
                "DELETE FROM cart WHERE user_id = ?");
            clearPs.setInt(1, userId);
            clearPs.executeUpdate();
            clearPs.close();

            con.commit();  // COMMIT
            UserHandler.redirect(ex, "/orders.html?msg=ordersuccess");

        } catch (SQLException e) {
            e.printStackTrace();
            try { if (con != null) con.rollback(); } catch (Exception ignored) {}
            UserHandler.redirect(ex, "/cart.html?msg=dberror");
        } finally {
            try { if (con != null) { con.setAutoCommit(true); con.close(); } }
            catch (Exception ignored) {}
        }
    }

    //  CANCEL ORDER  –  POST /order/cancel

    public static void handleCancelOrder(HttpExchange ex) throws IOException {
        Map<String, String> session = getSession(ex);
        if (session == null) {
            UserHandler.redirect(ex, "/login.html?msg=loginrequired");
            return;
        }
        int userId = Integer.parseInt(session.get("userId"));

        String body = UserHandler.readBody(ex);
        Map<String, String> params = UserHandler.parseFormData(body);
        int orderId = Integer.parseInt(params.getOrDefault("orderId", "0"));

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);  // BEGIN TRANSACTION

            // Verify this order belongs to this user
            PreparedStatement check = con.prepareStatement(
                "SELECT user_id, status FROM orders WHERE order_id=?");
            check.setInt(1, orderId);
            ResultSet rs = check.executeQuery();

            if (!rs.next() || rs.getInt("user_id") != userId) {
                con.rollback();
                check.close();
                UserHandler.redirect(ex, "/orders.html?msg=notfound");
                return;
            }

            String currentStatus = rs.getString("status");
            check.close();

            if (currentStatus.equals("Cancelled")) {
                con.rollback();
                UserHandler.redirect(ex, "/orders.html?msg=alreadycancelled");
                return;
            }

            // Restore stock for each item in this order
            PreparedStatement itemPs = con.prepareStatement(
                "SELECT product_id, qty FROM order_items WHERE order_id=?");
            itemPs.setInt(1, orderId);
            ResultSet items = itemPs.executeQuery();

            while (items.next()) {
                int productId = items.getInt("product_id");
                int qty       = items.getInt("qty");

                PreparedStatement restore = con.prepareStatement(
                    "UPDATE products SET stock = stock + ? WHERE product_id = ?");
                restore.setInt(1, qty);
                restore.setInt(2, productId);
                restore.executeUpdate();
                restore.close();
            }
            itemPs.close();

            // Set order status to Cancelled
            PreparedStatement cancelPs = con.prepareStatement(
                "UPDATE orders SET status='Cancelled' WHERE order_id=?");
            cancelPs.setInt(1, orderId);
            cancelPs.executeUpdate();
            cancelPs.close();

            con.commit();  // COMMIT
            UserHandler.redirect(ex, "/orders.html?msg=cancelled");

        } catch (SQLException e) {
            e.printStackTrace();
            try { if (con != null) con.rollback(); } catch (Exception ignored) {}
            UserHandler.redirect(ex, "/orders.html?msg=dberror");
        } finally {
            try { if (con != null) { con.setAutoCommit(true); con.close(); } }
            catch (Exception ignored) {}
        }
    }

    //  CART COUNT  –  GET /cart/count
    //  Returns plain number (used by JS to update badge)

    public static void handleCount(HttpExchange ex) throws IOException {
        Map<String, String> session = getSession(ex);
        int count = 0;

        if (session != null) {
            int userId = Integer.parseInt(session.get("userId"));
            try (Connection con = DBConnection.getConnection()) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT COALESCE(SUM(quantity),0) FROM cart WHERE user_id=?");
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) count = rs.getInt(1);
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        byte[] bytes = String.valueOf(count).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }


    //  Helper: get session from cookie
    static Map<String, String> getSession(HttpExchange ex) {
        String cookie    = ex.getRequestHeaders().getFirst("Cookie");
        String sessionId = SessionManager.parseSessionId(cookie);
        return SessionManager.getSession(sessionId);
    }
}
