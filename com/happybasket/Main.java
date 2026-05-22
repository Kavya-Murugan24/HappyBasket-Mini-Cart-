package com.happybasket;

import com.sun.net.httpserver.HttpServer;
import com.happybasket.PageHandler;
import com.happybasket.UserHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Main.java
 * ---------
 * Starts the HappyBasket web server on port 8080.
 *
 * HOW TO RUN:
 *   1. Compile all Java files (see README)
 *   2. Run:  java -cp ".;mysql-connector-j.jar" com.happybasket.Main
 *   3. Open browser: http://localhost:8080
 *
 * URL ROUTES:
 *   GET  /                  → index.html (shop page)
 *   GET  /index.html        → index.html
 *   GET  /login.html        → login page
 *   GET  /register.html     → register page
 *   GET  /cart.html         → cart page (login required)
 *   GET  /orders.html       → my orders (login required)
 *   GET  /profile.html      → my profile (login required)
 *   GET  /logout            → logout + redirect
 *   POST /login             → login form submit
 *   POST /register          → register form submit
 *   POST /cart/add          → add to cart
 *   POST /cart/remove       → remove from cart
 *   POST /cart/update       → update qty
 *   POST /cart/checkout     → place order
 *   POST /order/cancel      → cancel order
 *   POST /profile/save      → save profile address
 *   GET  /cart/count        → cart badge count (number)
 *   GET  /style.css etc.    → static files from web/ folder
 */
public class Main {

    public static void main(String[] args) throws IOException {

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // ── Page routes ──
        server.createContext("/",             ex -> route(ex));
        server.createContext("/index.html",   ex -> PageHandler.serveIndex(ex));
        server.createContext("/login.html",   ex -> PageHandler.serveLogin(ex));
        server.createContext("/register.html",ex -> PageHandler.serveRegister(ex));
        server.createContext("/cart.html",    ex -> PageHandler.serveCart(ex));
        server.createContext("/orders.html",  ex -> PageHandler.serveOrders(ex));
        server.createContext("/profile.html", ex -> PageHandler.serveProfile(ex));

        // ── Auth routes ──
        server.createContext("/login",    ex -> {
            if ("POST".equals(ex.getRequestMethod())) UserHandler.handleLogin(ex);
            else PageHandler.serveLogin(ex);
        });
        server.createContext("/register", ex -> {
            if ("POST".equals(ex.getRequestMethod())) UserHandler.handleRegister(ex);
            else PageHandler.serveRegister(ex);
        });
        server.createContext("/logout",   ex -> UserHandler.handleLogout(ex));

        // ── Cart routes ──
        server.createContext("/cart/add",      ex -> CartHandler.handleAdd(ex));
        server.createContext("/cart/remove",   ex -> CartHandler.handleRemove(ex));
        server.createContext("/cart/update",   ex -> CartHandler.handleUpdate(ex));
        server.createContext("/cart/checkout", ex -> CartHandler.handleCheckout(ex));
        server.createContext("/cart/count",    ex -> CartHandler.handleCount(ex));

        // ── Order routes ──
        server.createContext("/order/cancel",  ex -> CartHandler.handleCancelOrder(ex));

        // ── Profile route ──
        server.createContext("/profile/save",  ex -> PageHandler.handleProfileSave(ex));

        // ── Static files (CSS, JS, images) ──
        server.createContext("/style.css", ex -> PageHandler.serveStatic(ex));
        server.createContext("/cart.js",   ex -> PageHandler.serveStatic(ex));

        server.start();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   HappyBasket server started!        ║");
        System.out.println("║   Open: http://localhost:8080        ║");
        System.out.println("╚══════════════════════════════════════╝");
    }

    /** Root / handler */
    private static void route(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.equals("/index.html")) {
            PageHandler.serveIndex(ex);
        } else {
            PageHandler.serveStatic(ex);
        }
    }
}
