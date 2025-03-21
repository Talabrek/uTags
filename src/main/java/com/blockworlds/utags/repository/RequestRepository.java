package com.blockworlds.utags.repository;

import com.blockworlds.utags.model.CustomTagRequest;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for CustomTagRequest data access operations.
 * Follows the Repository pattern to abstract the data access layer.
 */
public interface RequestRepository {
    /**
     * Gets all custom tag requests.
     * 
     * @return A list of custom tag requests
     */
    List<CustomTagRequest> getAllRequests();

    /**
     * Gets a custom tag request by player name.
     * 
     * @param playerName The name of the player
     * @return The custom tag request, or null if not found
     */
    CustomTagRequest getRequestByPlayerName(String playerName);

    /**
     * Creates a custom tag request.
     * 
     * @param playerUuid The UUID of the player
     * @param playerName The name of the player
     * @param tagDisplay The display text for the tag
     * @return True if successful, false otherwise
     */
    boolean createRequest(UUID playerUuid, String playerName, String tagDisplay);

    /**
     * Removes a custom tag request.
     * 
     * @param requestId The ID of the request to remove
     * @return True if successful, false otherwise
     */
    boolean removeRequest(int requestId);

    /**
     * Checks if there are any pending tag requests.
     * 
     * @return True if there are pending requests, false otherwise
     */
    boolean hasPendingRequests();

    /**
     * Purges all tag requests from the database.
     * 
     * @return True if successful, false otherwise
     */
    boolean purgeRequestsTable();
}
