package com.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Service
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    // Danh sách các bảng được phép truy vấn và cấu hình tìm kiếm
    private final Map<String, TableConfig> allowedTables = new HashMap<>();

    public DatabaseService() {
        // Cấu hình cho bảng users
        TableConfig usersConfig = new TableConfig();
        usersConfig.setSearchableColumns(Arrays.asList("name", "email"));
        allowedTables.put("users", usersConfig);

        // Thêm cấu hình cho các bảng khác nếu cần
        // Ví dụ:
        // TableConfig productsConfig = new TableConfig();
        // productsConfig.setSearchableColumns(Arrays.asList("name", "description"));
        // allowedTables.put("products", productsConfig);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
    }

    public void addAllowedTable(String tableName, List<String> searchableColumns) {
        TableConfig config = new TableConfig();
        config.setSearchableColumns(searchableColumns);
        allowedTables.put(tableName, config);
    }

    public void removeAllowedTable(String tableName) {
        allowedTables.remove(tableName);
    }

    public String getRelevantDataForQuery(String userQuery) {
        StringBuilder result = new StringBuilder();

        try (Connection conn = getConnection()) {
            for (Map.Entry<String, TableConfig> entry : allowedTables.entrySet()) {
                String tableName = entry.getKey();
                TableConfig config = entry.getValue();

                // Xây dựng câu query với các cột được phép tìm kiếm
                StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName + " WHERE ");
                List<String> searchColumns = config.getSearchableColumns();

                for (int i = 0; i < searchColumns.size(); i++) {
                    if (i > 0) {
                        sql.append(" OR ");
                    }
                    sql.append(searchColumns.get(i)).append(" LIKE ?");
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    String searchPattern = "%" + userQuery + "%";
                    // Set parameter cho mỗi cột tìm kiếm
                    for (int i = 0; i < searchColumns.size(); i++) {
                        stmt.setString(i + 1, searchPattern);
                    }

                    result.append("\nTìm kiếm trong bảng ").append(tableName)
                            .append(" (các cột: ").append(String.join(", ", searchColumns))
                            .append("):\n");

                    try (ResultSet rs = stmt.executeQuery()) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        boolean hasResults = false;
                        while (rs.next()) {
                            hasResults = true;
                            result.append("---\n");
                            for (int i = 1; i <= columnCount; i++) {
                                String columnName = metaData.getColumnName(i);
                                String value = rs.getString(i);
                                result.append(columnName).append(": ").append(value).append("\n");
                            }
                        }

                        if (!hasResults) {
                            result.append("Không tìm thấy kết quả nào.\n");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database error: ", e);
            return "Error accessing database: " + e.getMessage();
        }

        return result.length() > 0 ? result.toString()
                : "Không tìm thấy dữ liệu phù hợp trong các bảng được phép truy vấn.";
    }

    // Class để lưu cấu hình cho mỗi bảng
    private static class TableConfig {
        private List<String> searchableColumns;

        public List<String> getSearchableColumns() {
            return searchableColumns;
        }

        public void setSearchableColumns(List<String> searchableColumns) {
            this.searchableColumns = searchableColumns;
        }
    }
}