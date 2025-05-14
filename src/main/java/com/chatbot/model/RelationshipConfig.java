package com.chatbot.model;

/**
 * Configuration class for defining relationships between database tables.
 * Supports different types of joins and relationship metadata.
 */
public class RelationshipConfig {
    private String sourceTable;
    private String targetTable;
    private String sourceColumn;
    private String targetColumn;
    private String joinType;
    private String relationshipType; // ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
    private String description;

    public RelationshipConfig(String sourceTable, String targetTable,
            String sourceColumn, String targetColumn) {
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
        this.sourceColumn = sourceColumn;
        this.targetColumn = targetColumn;
        this.joinType = "INNER"; // Default join type
        this.relationshipType = "MANY_TO_ONE"; // Default relationship type
    }

    // Getters and setters
    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getSourceColumn() {
        return sourceColumn;
    }

    public void setSourceColumn(String sourceColumn) {
        this.sourceColumn = sourceColumn;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public void setTargetColumn(String targetColumn) {
        this.targetColumn = targetColumn;
    }

    public String getJoinType() {
        return joinType;
    }

    public void setJoinType(String joinType) {
        this.joinType = joinType.toUpperCase();
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Validates if this is a valid relationship configuration
     */
    public boolean isValid() {
        return sourceTable != null && !sourceTable.isEmpty() &&
                targetTable != null && !targetTable.isEmpty() &&
                sourceColumn != null && !sourceColumn.isEmpty() &&
                targetColumn != null && !targetColumn.isEmpty();
    }

    /**
     * Creates a human-readable description of the relationship
     */
    public String getReadableDescription() {
        if (description != null && !description.isEmpty()) {
            return description;
        }
        return String.format("%s %s joined with %s through %s = %s",
                sourceTable, joinType, targetTable, sourceColumn, targetColumn);
    }
}