package com.chatbot.model;

import java.util.*;

/**
 * Configuration class for database table metadata and query settings.
 * Provides enhanced support for multi-table operations and relationships.
 */
public class TableConfig {
    private String displayName;
    private List<String> searchableColumns;
    private List<String> countableColumns;
    private Map<String, String> columnTypes;
    private Map<String, RelationshipConfig> relationships;
    private List<String> primaryKeys;
    private Map<String, String> columnDescriptions;
    private Map<String, String> columnDisplayNames;
    private boolean isViewable;
    private boolean isCountable;
    private boolean isSearchable;
    private int maxResults;

    public TableConfig() {
        this.searchableColumns = new ArrayList<>();
        this.countableColumns = new ArrayList<>();
        this.columnTypes = new HashMap<>();
        this.relationships = new HashMap<>();
        this.primaryKeys = new ArrayList<>();
        this.columnDescriptions = new HashMap<>();
        this.columnDisplayNames = new HashMap<>();
        this.isViewable = true;
        this.isCountable = true;
        this.isSearchable = true;
        this.maxResults = 100;
    }

    // Getters and setters
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getSearchableColumns() {
        return searchableColumns;
    }

    public void setSearchableColumns(List<String> searchableColumns) {
        this.searchableColumns = searchableColumns;
    }

    public List<String> getCountableColumns() {
        return countableColumns;
    }

    public void setCountableColumns(List<String> countableColumns) {
        this.countableColumns = countableColumns;
    }

    public Map<String, String> getColumnTypes() {
        return columnTypes;
    }

    public void setColumnTypes(Map<String, String> columnTypes) {
        this.columnTypes = columnTypes;
    }

    public Map<String, RelationshipConfig> getRelationships() {
        return relationships;
    }

    public void setRelationships(Map<String, RelationshipConfig> relationships) {
        this.relationships = relationships;
    }

    public void addRelationship(String tableName, RelationshipConfig relationship) {
        this.relationships.put(tableName, relationship);
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public void setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
    }

    public Map<String, String> getColumnDescriptions() {
        return columnDescriptions;
    }

    public void setColumnDescriptions(Map<String, String> columnDescriptions) {
        this.columnDescriptions = columnDescriptions;
    }

    public Map<String, String> getColumnDisplayNames() {
        return columnDisplayNames;
    }

    public void setColumnDisplayNames(Map<String, String> columnDisplayNames) {
        this.columnDisplayNames = columnDisplayNames;
    }

    public boolean isViewable() {
        return isViewable;
    }

    public void setViewable(boolean viewable) {
        isViewable = viewable;
    }

    public boolean isCountable() {
        return isCountable;
    }

    public void setCountable(boolean countable) {
        isCountable = countable;
    }

    public boolean isSearchable() {
        return isSearchable;
    }

    public void setSearchable(boolean searchable) {
        isSearchable = searchable;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    /**
     * Validates if a column exists and is searchable
     */
    public boolean isColumnSearchable(String columnName) {
        return searchableColumns.contains(columnName);
    }

    /**
     * Validates if a column exists and is countable
     */
    public boolean isColumnCountable(String columnName) {
        return countableColumns.contains(columnName);
    }

    /**
     * Gets the display name for a column
     */
    public String getColumnDisplayName(String columnName) {
        return columnDisplayNames.getOrDefault(columnName, columnName);
    }

    /**
     * Gets the description for a column
     */
    public String getColumnDescription(String columnName) {
        return columnDescriptions.getOrDefault(columnName, "");
    }

    /**
     * Gets the data type for a column
     */
    public String getColumnType(String columnName) {
        return columnTypes.getOrDefault(columnName, "");
    }

    /**
     * Checks if this table has a relationship with another table
     */
    public boolean hasRelationshipWith(String tableName) {
        return relationships.containsKey(tableName);
    }

    /**
     * Gets the relationship configuration with another table
     */
    public Optional<RelationshipConfig> getRelationshipWith(String tableName) {
        return Optional.ofNullable(relationships.get(tableName));
    }

    /**
     * Gets all tables that this table has relationships with
     */
    public Set<String> getRelatedTables() {
        return relationships.keySet();
    }

    /**
     * Builds a JOIN clause for a related table
     */
    public String buildJoinClause(String targetTable) {
        RelationshipConfig relationship = relationships.get(targetTable);
        if (relationship == null) {
            return "";
        }

        return String.format(" %s JOIN %s ON %s.%s = %s.%s",
                relationship.getJoinType(),
                targetTable,
                relationship.getSourceTable(),
                relationship.getSourceColumn(),
                relationship.getTargetTable(),
                relationship.getTargetColumn());
    }
}