package com.chatbot.chatbotJava.util;

import java.sql.*;
import java.util.*;

import com.chatbot.chatbotJava.models.User;

public class DBUtil {
    public List<User> fetchUsers() throws SQLException {
        return fetchUsersWithQuery("SELECT name, email FROM users");
    }

    public List<User> fetchUsersWithQuery(String sql) throws SQLException {
        List<User> users = new ArrayList<>();
        String url = "jdbc:mysql://localhost:3306/ai";
        String username = "root";
        String password = "conmeno11";

        try (Connection conn = DriverManager.getConnection(url, username, password);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(new User(rs.getString("name"), rs.getString("email")));
            }
        }
        return users;
    }
}
