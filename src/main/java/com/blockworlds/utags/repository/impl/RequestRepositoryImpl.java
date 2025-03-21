package com.blockworlds.utags.repository.impl;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.repository.RequestRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Implementation of RequestRepository using SQL database.
 */
public class RequestRepositoryImpl implements RequestRepository {
    // SQL query constants
    private static final String SELECT_ALL_REQUESTS = "SELECT * FROM tag_requests";
    private static final String SELECT_REQUEST_BY_PLAYER = "SELECT * FROM tag_requests WHERE player_name = ?";
    private static final String INSERT_REQUEST = "INSERT INTO tag_requests (player_uuid, player_name, tag_display) VALUES (?, ?, ?)";
    private static final String UPDATE_REQUEST = "UPDATE tag_requests SET player_name = ?, tag_display = ? WHERE player_uuid = ?";
    private static final String CHECK_REQUEST_EXISTS = "SELECT COUNT(*) FROM tag_requests WHERE player_uuid = ?";
    private static final String DELETE_REQUEST = "DELETE FROM tag_requests WHERE id = ?";
    private static final String COUNT_REQUESTS = "SELECT COUNT(*) FROM tag_requests";
    private static final String PURGE_REQUESTS = "TRUNCATE TABLE tag_requests";
    
    private final DatabaseConnector connector;
    private final Logger logger;
    
    /**
     * Creates a new RequestRepositoryImpl.
     */
    public RequestRepositoryImpl(DatabaseConnector connector, Logger logger) {
        this.connector = connector;
        this.logger = logger;
    }
    
    @Override
    public Result<List<CustomTagRequest>> getAllRequests() {
        List<CustomTagRequest> requests = new ArrayList<>();
        
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_REQUESTS);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                requests.add(createRequestFromResultSet(rs));
            }
            return Result.success(requests);
        } catch (SQLException e) {
            logger.warning("Error retrieving tag requests: " + e.getMessage());
            return Result.error("Failed to retrieve tag requests", e);
        }
    }
    
    @Override
    public Result<CustomTagRequest> getRequestByPlayerName(String playerName) {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_REQUEST_BY_PLAYER)) {
            
            stmt.setString(1, playerName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Result.success(createRequestFromResultSet(rs));
                } else {
                    return Result.failure("No request found for player: " + playerName);
                }
            }
        } catch (SQLException e) {
            logger.warning("Error getting request by player name: " + e.getMessage());
            return Result.error("Failed to get request", e);
        }
    }
    
    @Override
    public Result<Boolean> createRequest(UUID playerUuid, String playerName, String tagDisplay) {
        try (Connection conn = connector.getConnection()) {
            // Check if request already exists for this player
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(CHECK_REQUEST_EXISTS)) {
                checkStmt.setString(1, playerUuid.toString());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        exists = rs.getInt(1) > 0;
                    }
                }
            }
            
            // Update existing or insert new request
            PreparedStatement stmt;
            if (exists) {
                stmt = conn.prepareStatement(UPDATE_REQUEST);
                stmt.setString(1, playerName);
                stmt.setString(2, tagDisplay);
                stmt.setString(3, playerUuid.toString());
            } else {
                stmt = conn.prepareStatement(INSERT_REQUEST);
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, playerName);
                stmt.setString(3, tagDisplay);
            }
            
            try {
                int affected = stmt.executeUpdate();
                stmt.close();
                return Result.success(affected > 0);
            } finally {
                try { stmt.close(); } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            logger.warning("Error creating tag request: " + e.getMessage());
            return Result.error("Failed to create tag request", e);
        }
    }
    
    @Override
    public Result<Boolean> removeRequest(int requestId) {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_REQUEST)) {
            
            stmt.setInt(1, requestId);
            
            int affected = stmt.executeUpdate();
            return Result.success(affected > 0);
        } catch (SQLException e) {
            logger.warning("Error removing tag request: " + e.getMessage());
            return Result.error("Failed to remove tag request", e);
        }
    }
    
    @Override
    public Result<Boolean> hasPendingRequests() {
        try (Connection conn = connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(COUNT_REQUESTS);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return Result.success(rs.getInt(1) > 0);
            } else {
                return Result.success(false);
            }
        } catch (SQLException e) {
            logger.warning("Error checking pending requests: " + e.getMessage());
            return Result.error("Failed to check pending requests", e);
        }
    }
    
    @Override
    public Result<Boolean> purgeRequestsTable() {
        try (Connection conn = connector.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate(PURGE_REQUESTS);
            return Result.success(true);
        } catch (SQLException e) {
            logger.warning("Error purging requests table: " + e.getMessage());
            return Result.error("Failed to purge requests table", e);
        }
    }
    
    /**
     * Creates a CustomTagRequest object from a ResultSet row.
     */
    private CustomTagRequest createRequestFromResultSet(ResultSet rs) throws SQLException {
        return new CustomTagRequest(
            rs.getInt("id"),
            UUID.fromString(rs.getString("player_uuid")),
            rs.getString("player_name"),
            rs.getString("tag_display")
        );
    }
}
