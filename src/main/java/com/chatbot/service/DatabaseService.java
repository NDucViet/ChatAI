package com.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import jakarta.annotation.PostConstruct;
import com.chatbot.model.TableConfig;
import com.chatbot.model.RelationshipConfig;

@Service
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    // Danh sách các bảng được phép truy vấn
    private static final Set<String> ALLOWED_TABLE_NAMES = new HashSet<>(Arrays.asList(
            "users", // Bảng người dùng
            "user", // Bảng người dùng
            "khachhang", // Khách hàng
            "nguoidung", // Người dùng
            "customer", // Khách hàng
            "customers", // Khách hàng
            "account", // Tài khoản
            "accounts", // Tài khoản
            "member", // Thành viên
            "members", // Thành viên
            "client", // Khách hàng
            "clients", // Khách hàng
            "products", // Sản phẩm
            "categories", // Danh mục
            "orders", // Đơn hàng
            "order_items" // Chi tiết đơn hàng
    ));

    // Danh sách các bảng bị cấm truy cập
    private static final Set<String> RESTRICTED_TABLE_NAMES = new HashSet<>(Arrays.asList(
            "user_passwords", // Bảng mật khẩu
            "user_sessions", // Phiên đăng nhập
            "system_config", // Cấu hình hệ thống
            "audit_logs", // Log hệ thống
            "admin_users", // Tài khoản admin
            "security_tokens" // Token bảo mật
    ));

    private final Map<String, TableConfig> allowedTables = new HashMap<>();

    @PostConstruct
    public void init() {
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "%", new String[] { "TABLE" });

            logger.info("Available tables in database:");
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                logger.info("Found table: {}", tableName);
                // Kiểm tra xem bảng có trong danh sách cho phép hoặc không bị cấm
                if (isTableAllowed(tableName)) {
                    configureTable(conn, tableName);
                } else {
                    logger.info("Skipping restricted or non-whitelisted table: {}", tableName);
                }
            }

            // Cấu hình quan hệ giữa các bảng được phép
            configureRelationships(conn);

            logger.info("Initialized database service with allowed tables: {}", allowedTables.keySet());
        } catch (SQLException e) {
            logger.error("Error initializing database service", e);
        }
    }

    /**
     * Kiểm tra xem một bảng có được phép truy cập hay không
     */
    private boolean isTableAllowed(String tableName) {
        String lowerTableName = tableName.toLowerCase();

        // Kiểm tra các điều kiện loại trừ trước
        if (lowerTableName.contains("flyway") ||
                lowerTableName.contains("schema") ||
                RESTRICTED_TABLE_NAMES.contains(lowerTableName)) {
            return false;
        }

        // Nếu có danh sách cho phép, chỉ cho phép các bảng trong danh sách
        if (!ALLOWED_TABLE_NAMES.isEmpty()) {
            return ALLOWED_TABLE_NAMES.contains(lowerTableName);
        }

        // Nếu không có danh sách cho phép, cho phép tất cả trừ các bảng bị cấm
        return true;
    }

    /**
     * Kiểm tra và giới hạn các cột nhạy cảm
     */
    private boolean isSecureColumn(String columnName) {
        String lowerName = columnName.toLowerCase();
        return lowerName.contains("password") ||
                lowerName.contains("secret") ||
                lowerName.contains("token") ||
                lowerName.contains("key") ||
                lowerName.contains("salt") ||
                lowerName.contains("hash") ||
                lowerName.contains("pin") ||
                lowerName.contains("security") ||
                lowerName.contains("private");
    }

    private void configureTable(Connection conn, String tableName) throws SQLException {
        TableConfig config = new TableConfig();
        List<String> searchableColumns = new ArrayList<>();
        List<String> countableColumns = new ArrayList<>();
        Map<String, String> columnTypes = new HashMap<>();
        Map<String, String> columnDisplayNames = new HashMap<>();
        List<String> primaryKeys = new ArrayList<>();

        // Get primary keys
        ResultSet pks = conn.getMetaData().getPrimaryKeys(null, null, tableName);
        while (pks.next()) {
            primaryKeys.add(pks.getString("COLUMN_NAME"));
        }

        // Get column information
        ResultSet columns = conn.getMetaData().getColumns(null, null, tableName, null);
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String columnType = columns.getString("TYPE_NAME");
            String remarks = columns.getString("REMARKS");

            columnTypes.put(columnName, columnType);
            columnDisplayNames.put(columnName, formatColumnName(columnName));

            if (!isSecureColumn(columnName)) {
                countableColumns.add(columnName);
                if (isSearchableType(columnType)) {
                    searchableColumns.add(columnName);
                }
            }
        }

        config.setSearchableColumns(searchableColumns);
        config.setCountableColumns(countableColumns);
        config.setColumnTypes(columnTypes);
        config.setColumnDisplayNames(columnDisplayNames);
        config.setPrimaryKeys(primaryKeys);
        config.setDisplayName(formatTableName(tableName));

        allowedTables.put(tableName, config);
        logger.info("Configured table {}: searchable={}, countable={}, pk={}",
                tableName, searchableColumns, countableColumns, primaryKeys);
    }

    private void configureRelationships(Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();

        for (String tableName : allowedTables.keySet()) {
            TableConfig config = allowedTables.get(tableName);

            // Get foreign keys where this table is the source
            ResultSet exportedKeys = metaData.getExportedKeys(null, null, tableName);
            while (exportedKeys.next()) {
                String pkTable = exportedKeys.getString("PKTABLE_NAME");
                String fkTable = exportedKeys.getString("FKTABLE_NAME");
                String pkColumn = exportedKeys.getString("PKCOLUMN_NAME");
                String fkColumn = exportedKeys.getString("FKCOLUMN_NAME");

                if (allowedTables.containsKey(fkTable)) {
                    RelationshipConfig relationship = new RelationshipConfig(
                            pkTable, fkTable, pkColumn, fkColumn);
                    relationship.setJoinType("LEFT");
                    relationship.setRelationshipType("ONE_TO_MANY");
                    config.addRelationship(fkTable, relationship);
                }
            }

            // Get foreign keys where this table is the target
            ResultSet importedKeys = metaData.getImportedKeys(null, null, tableName);
            while (importedKeys.next()) {
                String pkTable = importedKeys.getString("PKTABLE_NAME");
                String fkTable = importedKeys.getString("FKTABLE_NAME");
                String pkColumn = importedKeys.getString("PKCOLUMN_NAME");
                String fkColumn = importedKeys.getString("FKCOLUMN_NAME");

                if (allowedTables.containsKey(pkTable)) {
                    RelationshipConfig relationship = new RelationshipConfig(
                            fkTable, pkTable, fkColumn, pkColumn);
                    relationship.setJoinType("INNER");
                    relationship.setRelationshipType("MANY_TO_ONE");
                    config.addRelationship(pkTable, relationship);
                }
            }
        }
    }

    private boolean isSearchableType(String columnType) {
        String type = columnType.toLowerCase();
        return type.contains("char") ||
                type.contains("text") ||
                type.contains("varchar") ||
                type.contains("string");
    }

    private String formatTableName(String tableName) {
        return Arrays.stream(tableName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(tableName);
    }

    private String formatColumnName(String columnName) {
        return Arrays.stream(columnName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(columnName);
    }

    private Connection getConnection() throws SQLException {
        try {
            logger.info("Attempting to connect to database at {}", dbUrl);
            Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            logger.info("Successfully connected to database");

            // Test the connection by running a simple query
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    logger.info("Database connection test successful");
                }
            }

            return conn;
        } catch (SQLException e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            throw e;
        }
    }

    public String getRelevantDataForQuery(String userQuery) {
        StringBuilder result = new StringBuilder();
        String lowercaseQuery = userQuery.toLowerCase();

        try (Connection conn = getConnection()) {
            // Handle combined queries (count + list)
            if (isCountQuery(lowercaseQuery) && isListQuery(lowercaseQuery)) {
                Set<String> relevantTables = findRelevantTables(lowercaseQuery);
                if (relevantTables.isEmpty()) {
                    return "Không tìm thấy bảng dữ liệu phù hợp với yêu cầu.";
                }

                for (String tableName : relevantTables) {
                    TableConfig config = allowedTables.get(tableName);
                    if (config != null && config.isCountable() && config.isViewable()) {
                        try {
                            // Get count first
                            String countSql = buildCountQuery(tableName, config, lowercaseQuery);
                            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                                try (ResultSet countRs = countStmt.executeQuery()) {
                                    if (countRs.next()) {
                                        int total = countRs.getInt("total");
                                        result.append(String.format("Số lượng %s: %d\n\n",
                                                config.getDisplayName(), total));

                                        // If there are records, show the list
                                        if (total > 0) {
                                            result.append("Danh sách chi tiết:\n");
                                            String listSql = buildListQuery(tableName, config, lowercaseQuery);
                                            try (PreparedStatement listStmt = conn.prepareStatement(listSql)) {
                                                try (ResultSet listRs = listStmt.executeQuery()) {
                                                    ResultSetMetaData metaData = listRs.getMetaData();
                                                    int columnCount = metaData.getColumnCount();

                                                    while (listRs.next()) {
                                                        result.append("  ");
                                                        for (int i = 1; i <= columnCount; i++) {
                                                            String columnName = metaData.getColumnName(i);
                                                            if (!isSecureColumn(columnName)) {
                                                                String value = listRs.getString(i);
                                                                result.append(String.format("%s: %s, ",
                                                                        config.getColumnDisplayName(columnName),
                                                                        value != null ? value : "N/A"));
                                                            }
                                                        }
                                                        result.append("\n");
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            logger.error("Error processing table {}: {}", tableName, e.getMessage());
                            result.append(String.format("Lỗi khi xử lý bảng %s: %s\n",
                                    config.getDisplayName(), e.getMessage()));
                        }
                    }
                }

                return result.length() > 0 ? result.toString() : "Không tìm thấy dữ liệu phù hợp trong database.";
            }

            // Handle single type queries
            if (isCountQuery(lowercaseQuery)) {
                return getCountInformation(conn, lowercaseQuery);
            } else if (isListQuery(lowercaseQuery)) {
                return getListInformation(conn, lowercaseQuery);
            } else {
                return searchInTables(conn, userQuery);
            }
        } catch (SQLException e) {
            logger.error("Database error: ", e);
            return "Lỗi khi truy cập database: " + e.getMessage();
        }
    }

    private boolean isCountQuery(String query) {
        return query.contains("bao nhiêu") ||
                query.contains("số lượng") ||
                query.contains("đếm") ||
                query.contains("count") ||
                query.contains("tổng số");
    }

    private boolean isListQuery(String query) {
        return query.contains("liệt kê") ||
                query.contains("danh sách") ||
                query.contains("list") ||
                query.contains("hiển thị") ||
                query.contains("xem");
    }

    private String getCountInformation(Connection conn, String query) throws SQLException {
        StringBuilder result = new StringBuilder();
        result.append("Thông tin số lượng trong database:\n\n");

        // Find relevant tables based on the query
        Set<String> relevantTables = findRelevantTables(query);

        if (relevantTables.isEmpty()) {
            return "Không tìm thấy bảng dữ liệu phù hợp với yêu cầu.";
        }

        boolean foundAnyResults = false;
        for (String tableName : relevantTables) {
            TableConfig config = allowedTables.get(tableName);
            if (config != null && config.isCountable()) {
                try {
                    // Build query with related tables if needed
                    String sql = buildCountQuery(tableName, config, query);

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                foundAnyResults = true;
                                int total = rs.getInt("total");
                                result.append(String.format("- %s: %d bản ghi\n",
                                        config.getDisplayName(), total));

                                // Show detailed information for small result sets
                                if (total > 0 && total <= config.getMaxResults()) {
                                    result.append(getDetailedInformation(conn, tableName, config, total));
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.warn("Error counting records in table {}: {}", tableName, e.getMessage());
                    // Continue with other tables instead of failing completely
                    continue;
                }
            }
        }

        if (!foundAnyResults) {
            return "Không tìm thấy dữ liệu phù hợp trong database.";
        }

        return result.toString();
    }

    private String getListInformation(Connection conn, String query) throws SQLException {
        StringBuilder result = new StringBuilder();

        // Find relevant tables based on the query
        Set<String> relevantTables = findRelevantTables(query);

        for (String tableName : relevantTables) {
            TableConfig config = allowedTables.get(tableName);
            if (config != null && config.isViewable()) {
                result.append(String.format("\n%s:\n", config.getDisplayName()));

                // Build query with related tables if needed
                String sql = buildListQuery(tableName, config, query);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        // Print column headers
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            result.append(String.format("%-20s",
                                    config.getColumnDisplayName(columnName)));
                        }
                        result.append("\n");

                        // Print data
                        int rowCount = 0;
                        while (rs.next() && rowCount < config.getMaxResults()) {
                            for (int i = 1; i <= columnCount; i++) {
                                String value = rs.getString(i);
                                if (isSecureColumn(metaData.getColumnName(i))) {
                                    value = "*****";
                                }
                                result.append(String.format("%-20s",
                                        value != null ? value : "N/A"));
                            }
                            result.append("\n");
                            rowCount++;
                        }
                    }
                }
            }
        }

        return result.toString();
    }

    private String searchInTables(Connection conn, String query) throws SQLException {
        StringBuilder result = new StringBuilder();
        Set<String> relevantTables = findRelevantTables(query);

        for (String tableName : relevantTables) {
            TableConfig config = allowedTables.get(tableName);
            if (config != null && config.isSearchable()) {
                String sql = buildSearchQuery(tableName, config, query);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        boolean hasResults = false;
                        while (rs.next()) {
                            if (!hasResults) {
                                result.append(String.format("\nKết quả tìm kiếm trong %s:\n",
                                        config.getDisplayName()));
                                hasResults = true;
                            }

                            for (int i = 1; i <= columnCount; i++) {
                                String columnName = metaData.getColumnName(i);
                                String value = rs.getString(i);
                                if (isSecureColumn(columnName)) {
                                    value = "*****";
                                }
                                result.append(String.format("%s: %s\n",
                                        config.getColumnDisplayName(columnName),
                                        value != null ? value : "N/A"));
                            }
                            result.append("\n");
                        }
                    }
                }
            }
        }

        return result.length() > 0 ? result.toString() : "Không tìm thấy thông tin phù hợp trong database.";
    }

    private Set<String> findRelevantTables(String query) {
        Set<String> relevantTables = new HashSet<>();
        String lowercaseQuery = query.toLowerCase();

        // Log all available tables for debugging
        logger.info("Searching through available tables: {}", allowedTables.keySet());

        // First try to find explicitly mentioned tables
        for (Map.Entry<String, TableConfig> entry : allowedTables.entrySet()) {
            String tableName = entry.getKey();
            TableConfig config = entry.getValue();

            // Check if query mentions table name or display name
            if (lowercaseQuery.contains(tableName.toLowerCase()) ||
                    lowercaseQuery.contains(config.getDisplayName().toLowerCase())) {
                relevantTables.add(tableName);
                logger.info("Found table by name match: {}", tableName);

                // Add related tables based on relationships
                for (String relatedTable : config.getRelatedTables()) {
                    if (allowedTables.containsKey(relatedTable)) {
                        relevantTables.add(relatedTable);
                        logger.info("Added related table: {}", relatedTable);
                    }
                }
            }
        }

        // If no tables found, try to infer from common terms
        if (relevantTables.isEmpty()) {
            // For user-related queries
            if (lowercaseQuery.contains("user") ||
                    lowercaseQuery.contains("người dùng") ||
                    lowercaseQuery.contains("tài khoản") ||
                    lowercaseQuery.contains("users") ||
                    lowercaseQuery.contains("khách hàng")) {

                // Try to find any table that might contain user information
                for (String tableName : allowedTables.keySet()) {
                    String lowerTableName = tableName.toLowerCase();
                    // Check for various possible table names
                    if (lowerTableName.contains("user") ||
                            lowerTableName.contains("users") ||
                            lowerTableName.contains("account") ||
                            lowerTableName.contains("customer") ||
                            lowerTableName.contains("khachhang") ||
                            lowerTableName.contains("nguoidung") ||
                            lowerTableName.contains("member") ||
                            lowerTableName.contains("client")) {
                        relevantTables.add(tableName);
                        logger.info("Found user-related table: {}", tableName);
                    }
                }
            }
        }

        // If still no tables found, log a warning
        if (relevantTables.isEmpty()) {
            logger.warn("No relevant tables found for query: {}", query);
            // For general queries about data/records, include all non-system tables
            if (lowercaseQuery.contains("dữ liệu") ||
                    lowercaseQuery.contains("bản ghi") ||
                    lowercaseQuery.contains("thông tin")) {
                for (String tableName : allowedTables.keySet()) {
                    if (!tableName.toLowerCase().contains("flyway") &&
                            !tableName.toLowerCase().contains("schema")) {
                        relevantTables.add(tableName);
                        logger.info("Added table for general query: {}", tableName);
                    }
                }
            }
        }

        logger.info("Final relevant tables for query '{}': {}", query, relevantTables);
        return relevantTables;
    }

    private String buildCountQuery(String tableName, TableConfig config, String query) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) as total FROM ").append(tableName);

        try {
            // Add joins for related tables if they are relevant to the query
            for (String relatedTable : config.getRelatedTables()) {
                if (query.toLowerCase().contains(relatedTable.toLowerCase()) ||
                        query.toLowerCase().contains(
                                allowedTables.get(relatedTable).getDisplayName().toLowerCase())) {
                    sql.append(config.buildJoinClause(relatedTable));
                }
            }
        } catch (Exception e) {
            logger.warn("Error building joins for table {}: {}", tableName, e.getMessage());
            // Return simple count query without joins if there's an error
        }

        return sql.toString();
    }

    private String buildListQuery(String tableName, TableConfig config, String query) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT ").append(tableName).append(".* FROM ").append(tableName);

        // Add joins for related tables if they are relevant to the query
        for (String relatedTable : config.getRelatedTables()) {
            if (query.toLowerCase().contains(relatedTable.toLowerCase()) ||
                    query.toLowerCase().contains(
                            allowedTables.get(relatedTable).getDisplayName().toLowerCase())) {
                sql.append(config.buildJoinClause(relatedTable));
            }
        }

        sql.append(" LIMIT ").append(config.getMaxResults());
        return sql.toString();
    }

    private String buildSearchQuery(String tableName, TableConfig config, String query) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT ").append(tableName).append(".* FROM ").append(tableName);

        // Add joins for related tables
        List<String> conditions = new ArrayList<>();
        for (String relatedTable : config.getRelatedTables()) {
            TableConfig relatedConfig = allowedTables.get(relatedTable);
            if (relatedConfig != null) {
                sql.append(config.buildJoinClause(relatedTable));

                // Add search conditions for searchable columns in related tables
                for (String column : relatedConfig.getSearchableColumns()) {
                    conditions.add(String.format("%s.%s LIKE '%%%s%%'",
                            relatedTable, column, query.replace("'", "''")));
                }
            }
        }

        // Add search conditions for current table
        for (String column : config.getSearchableColumns()) {
            conditions.add(String.format("%s.%s LIKE '%%%s%%'",
                    tableName, column, query.replace("'", "''")));
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" OR ", conditions));
        }

        sql.append(" LIMIT ").append(config.getMaxResults());
        return sql.toString();
    }

    private String getDetailedInformation(Connection conn, String tableName,
            TableConfig config, int limit) throws SQLException {
        StringBuilder result = new StringBuilder();
        String sql = String.format("SELECT * FROM %s LIMIT %d", tableName, limit);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    result.append("  ");
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        String value = rs.getString(i);

                        if (isSecureColumn(columnName)) {
                            value = "*****";
                        }

                        result.append(String.format("%s: %s, ",
                                config.getColumnDisplayName(columnName),
                                value != null ? value : "N/A"));
                    }
                    result.append("\n");
                }
            }
        }

        return result.toString();
    }
}