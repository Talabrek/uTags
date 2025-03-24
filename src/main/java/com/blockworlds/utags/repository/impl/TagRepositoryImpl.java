@Override
public Result<Boolean> editTagAttribute(String tagName, String attribute, String newValue) {
    if (tagName == null || tagName.isEmpty()) {
        return Result.failure("Tag name cannot be empty");
    }
    
    if (attribute == null || attribute.isEmpty()) {
        return Result.failure("Attribute name cannot be empty");
    }
    
    try (Connection conn = connector.getConnection()) {
        PreparedStatement stmt;
        
        // Use different prepared statements based on attribute to avoid SQL injection
        switch (attribute) {
            case "name":
                stmt = conn.prepareStatement("UPDATE tags SET name = ? WHERE name = ?");
                break;
            case "display":
                stmt = conn.prepareStatement("UPDATE tags SET display = ? WHERE name = ?");
                break;
            case "type":
                stmt = conn.prepareStatement("UPDATE tags SET type = ? WHERE name = ?");
                break;
            case "public":
                boolean publicValue = Boolean.parseBoolean(newValue);
                try (PreparedStatement boolStmt = conn.prepareStatement("UPDATE tags SET public = ? WHERE name = ?")) {
                    boolStmt.setBoolean(1, publicValue);
                    boolStmt.setString(2, tagName);
                    int affected = boolStmt.executeUpdate();
                    return Result.success(affected > 0);
                }
            case "color":
                boolean colorValue = Boolean.parseBoolean(newValue);
                try (PreparedStatement boolStmt = conn.prepareStatement("UPDATE tags SET color = ? WHERE name = ?")) {
                    boolStmt.setBoolean(1, colorValue);
                    boolStmt.setString(2, tagName);
                    int affected = boolStmt.executeUpdate();
                    return Result.success(affected > 0);
                }
            case "material":
                stmt = conn.prepareStatement("UPDATE tags SET material = ? WHERE name = ?");
                break;
            case "weight":
                try {
                    int weightValue = Integer.parseInt(newValue);
                    try (PreparedStatement intStmt = conn.prepareStatement("UPDATE tags SET weight = ? WHERE name = ?")) {
                        intStmt.setInt(1, weightValue);
                        intStmt.setString(2, tagName);
                        int affected = intStmt.executeUpdate();
                        return Result.success(affected > 0);
                    }
                } catch (NumberFormatException e) {
                    return Result.failure("Weight must be a valid integer");
                }
            default:
                return Result.failure("Invalid attribute name: " + attribute);
        }
        
        // For string attributes
        try (stmt) {
            stmt.setString(1, newValue);
            stmt.setString(2, tagName);
            
            int affected = stmt.executeUpdate();
            return Result.success(affected > 0);
        }
    } catch (SQLException e) {
        logger.warning("Error updating tag attribute: " + e.getMessage());
        return Result.error("Failed to update tag attribute", e);
    }
}
