package com.blockworlds.utags.service;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Result;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Service interface for custom tag request operations.
 */
public interface RequestService {
    /**
     * Gets all custom tag requests.
     */
    Result<List<CustomTagRequest>> getCustomTagRequests();
    
    /**
     * Gets a custom tag request by player name.
     */
    Result<CustomTagRequest> getCustomTagRequestByPlayerName(String playerName);
    
    /**
     * Creates a new custom tag request.
     */
    Result<Boolean> createCustomTagRequest(Player player, String tagDisplay);
    
    /**
     * Accepts a custom tag request.
     */
    Result<Boolean> acceptCustomTagRequest(CustomTagRequest request);
    
    /**
     * Denies a custom tag request.
     */
    Result<Boolean> denyCustomTagRequest(CustomTagRequest request);
    
    /**
     * Checks if there are any pending tag requests.
     */
    Result<Boolean> hasPendingRequests();
    
    /**
     * Purges all tag requests.
     */
    Result<Boolean> purgeRequestsTable();
}
