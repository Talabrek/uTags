package com.blockworlds.utags.repository;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Result;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for CustomTagRequest data access operations.
 */
public interface RequestRepository {
    /** Gets all custom tag requests */
    Result<List<CustomTagRequest>> getAllRequests();

    /** Gets a custom tag request by player name */
    Result<CustomTagRequest> getRequestByPlayerName(String playerName);

    /** Creates a custom tag request */
    Result<Boolean> createRequest(UUID playerUuid, String playerName, String tagDisplay);

    /** Removes a custom tag request */
    Result<Boolean> removeRequest(int requestId);

    /** Checks if there are any pending tag requests */
    Result<Boolean> hasPendingRequests();

    /** Purges all tag requests from the database */
    Result<Boolean> purgeRequestsTable();
}
