# 🌸 HappyBasket – Full Stack E-Commerce Web Application

HappyBasket is a **mini full-stack e-commerce web application** developed for online flower shopping.  
The project allows users to register, log in, browse products, add items to cart, place orders, view order history, and manage profile details.

The main goal of this project was to understand how a real-world shopping website works by implementing **frontend pages, backend request handling, database integration, session management, and order processing** using Java and MySQL.

---

## 🚀 Features

- ✅ User Registration and Login
- ✅ Session-based Authentication
- ✅ Product Listing
- ✅ Add to Cart
- ✅ Update Cart Quantity
- ✅ Remove Items from Cart
- ✅ Checkout and Order Placement
- ✅ Order History
- ✅ Order Cancellation
- ✅ Profile Management
- ✅ Address Update
- ✅ Cart Count Badge
- ✅ Responsive UI with HTML/CSS

---

## 🛠️ Tech Stack

### **Frontend**
- HTML
- CSS

### **Backend**
- Java
- JDBC
- Java HttpServer

### **Database**
- MySQL

### **Tools**
- Eclipse
- Git
- GitHub

---

## 📂 Project Modules

### **1. Authentication Module**
Handles user registration, login, logout, and session-based access.

**Features:**
- Register a new user
- Login using email and password
- Logout functionality
- Session tracking using cookies

---

### **2. Product Module**
Displays available flower products on the homepage.

**Features:**
- Product listing
- Price display
- Add to cart option
- Sold-out item handling

---

### **3. Cart Module**
Manages user cart operations.

**Features:**
- Add items to cart
- Increase quantity of existing items
- Update quantity
- Remove item from cart
- Display grand total
- Show cart item count

---

### **4. Order Module**
Handles checkout and order management.

**Features:**
- Create order from cart
- Save order items
- Update stock quantity
- View order history
- Cancel orders
- Restore stock on cancellation

---

### **5. Profile Module**
Allows users to view and update their personal details.

**Features:**
- View full name and email
- Update phone number
- Update address, city, and pincode

---

## 🧠 Key Concepts Used

- Object-Oriented Programming (OOP)
- JDBC for database operations
- Session handling using cookies
- CRUD operations
- Dynamic HTML rendering
- Transaction handling using commit/rollback
- Input validation
- Backend routing using Java HttpServer

---

## 🗃️ Database Design

The project uses the following tables:

### **users**
Stores user account and profile details.

### **products**
Stores product name, price, and stock.

### **cart**
Stores temporary cart items for each logged-in user.

### **orders**
Stores overall order details such as user, total amount, status, and date.

### **order_items**
Stores the products included in each order.

---

## 🔄 Application Flow

### **1. User Registration / Login**
- User registers through the register page
- Login credentials are checked with MySQL
- On successful login, a session ID is generated
- Cookie is stored in browser for authenticated access

### **2. Add to Cart**
- User clicks "Add to Cart"
- Product is stored in the cart table
- If the item already exists, quantity is updated

### **3. View Cart**
- Cart page fetches items from database
- Quantity, line total, and grand total are displayed

### **4. Checkout**
- User clicks checkout
- System validates stock availability
- New order is created
- Order items are inserted
- Product stock is reduced
- Cart is cleared
- Transaction is committed

### **5. Cancel Order**
- User cancels order
- Order status is changed to `Cancelled`
- Product stock is restored

---

## 🔐 Session Management

This project uses **cookie-based session handling**.

- On successful login, a unique session ID is created using UUID
- Session data is stored in memory using `SessionManager`
- Browser sends the session cookie on every request
- Protected pages like cart, profile, and orders are accessible only for logged-in users

---

## 💾 Transaction Handling

One of the important parts of this project is the **checkout workflow**.

During checkout, the following steps are executed inside a **database transaction**:

1. Fetch cart items
2. Validate stock
3. Insert order
4. Insert order items
5. Update product stock
6. Clear cart

If any step fails, the transaction is **rolled back** to maintain data consistency.

This ensures:
- No partial order is saved
- Stock remains correct
- Cart is not cleared unless checkout succeeds

---

## 📁 Project Structure

```text
HappyBasket/
│
├── src/
│   └── com/happybasket/
│       ├── Main.java
│       ├── DBConnection.java
│       ├── UserHandler.java
│       ├── CartHandler.java
│       ├── PageHandler.java
│       └── SessionManager.java
│
├── web/
│   ├── index.html
│   ├── login.html
│   ├── register.html
│   ├── cart.html
│   ├── orders.html
│   ├── profile.html
│   ├── style.css
│   ├── cart.js
│   └── images/
│
└── README.md
