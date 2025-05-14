package com.chatbot.controller;

import com.chatbot.service.GeminiAIService;
import com.chatbot.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ChatbotController {

    private final GeminiAIService geminiAIService;
    private final DatabaseService databaseService;

    @Autowired
    public ChatbotController(GeminiAIService geminiAIService, DatabaseService databaseService) {
        this.geminiAIService = geminiAIService;
        this.databaseService = databaseService;
    }

    @GetMapping("/models")
    public ResponseEntity<String> listModels() {
        return ResponseEntity.ok(geminiAIService.listAvailableModels());
    }

    @PostMapping("/ask")
    public ResponseEntity<String> processRequest(@RequestBody ChatRequest request) {
        // Lấy context từ database dựa trên câu hỏi của người dùng
        String databaseContext = databaseService.getRelevantDataForQuery(request.getQuestion());

        // Sử dụng Gemini AI để xử lý câu hỏi với context từ database
        String response = geminiAIService.processCustomerRequest(request.getQuestion(), databaseContext);
        return ResponseEntity.ok(response);
    }
}

class ChatRequest {
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}