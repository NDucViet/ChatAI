package com.chatbot.chatbotJava.controllers;

import com.chatbot.chatbotJava.models.User;
import com.chatbot.chatbotJava.util.DBUtil;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final DBUtil dbUtil = new DBUtil();

    @GetMapping
    public List<User> getUsers() throws SQLException {
        return dbUtil.fetchUsers();
    }
}
