
package com.happybasket;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
   private static final String URL = "jdbc:mysql://localhost:3306/happybasket_db?useSSL=false&serverTimezone=UTC";
   private static final String USER = "root";
   private static final String PASS = "";

   public DBConnection() {
   }

  public static Connection getConnection() throws SQLException {
   return DriverManager.getConnection(URL, USER, PASS);
}

   static {
      try {
         Class.forName("com.mysql.cj.jdbc.Driver");
      } catch (ClassNotFoundException var1) {
         System.out.println("ERROR: mysql-connector-j.jar missing from classpath!");
      }

   }
}
