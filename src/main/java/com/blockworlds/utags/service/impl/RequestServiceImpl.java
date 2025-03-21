package com.blockworlds.utags.service.impl;

import com.blockworlds.utags.model.CustomTagRequest;
import com.blockworlds.utags.model.Result;
import com.blockworlds.utags.model.Tag;
import com.blockworlds.utags.model.TagType;
import com.blockworlds.utags.repository.RequestRepository;
import com.blockworlds.utags.service.RequestService;
import com.blockworlds.utags.service.TagService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of RequestService interface.
 */
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final TagService tagService;
    private final LuckPerms luckPerms;
    private final JavaPlugin plugin;
    private final Logger logger;

    /**
     * Creates a new RequestServiceImpl.
     */
    public RequestServiceImpl(RequestRepository requestRepository, TagService tagService, 
                             LuckPerms luckPerms, JavaPlugin plugin, Logger logger) {
        this.requestRepository = requestRepository;
        this.tagService = tagService;
        this.luckPerms = luckPerms;
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public Result<List<CustomTagRequest>> getCustomTagRequests() {
        try {
            return requestRepository.getAllRequests();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving custom tag requests", e);
            return Result.error("Failed to retrieve tag requests: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<CustomTagRequest> getCustomTagRequestByPlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return Result.failure("Player name cannot be empty");
        }
        
        try {
            return requestRepository.getRequestByPlayerName(playerName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving custom tag request by player name", e);
            return Result.error("Failed to retrieve tag request: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> createCustomTagRequest(Player player, String tagDisplay) {
        if (player == null) {
            return Result.failure("Player cannot be null");
        }
        
        if (tagDisplay == null || tagDisplay.isEmpty()) {
            return Result.failure("Tag display cannot be empty");
        }
        
        // Check custom tag limit
        Result<Integer> countResult = tagService.countCustomTags(player.getName());
        if (!countResult.isSuccess()) {
            return Result.failure(countResult.getMessage());
        }
        
        int customTagCount = countResult.getValue();
        String requiredPermission = "utags.custom" + (customTagCount + 1);
        
        if (!player.hasPermission(requiredPermission)) {
            return Result.failure("You don't have permission to request more custom tags");
        }
        
        try {
            boolean success = requestRepository.createRequest(
                player.getUniqueId(), player.getName(), tagDisplay).getValue();
            
            if (success) {
                // Notify staff about the new request
                notifyStaffAboutRequest(player);
            }
            
            return Result.success(success);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating custom tag request", e);
            return Result.error("Failed to create tag request: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> acceptCustomTagRequest(CustomTagRequest request) {
        if (request == null) {
            return Result.failure("Request cannot be null");
        }
        
        try {
            Result<Integer> countResult = tagService.countCustomTags(request.getPlayerName());
            if (!countResult.isSuccess()) {
                return Result.failure(countResult.getMessage());
            }
            
            int customTagCount = countResult.getValue();
            String permission = "utags.tag." + request.getPlayerName() + (customTagCount + 1);
            
            // Add the new tag to the tags table
            Tag newTag = new Tag(
                request.getPlayerName() + (customTagCount + 1),
                request.getTagDisplay(),
                TagType.PREFIX,
                false,
                false,
                new ItemStack(Material.PLAYER_HEAD),
                1
            );
            
            Result<Boolean> addResult = tagService.addTag(newTag);
            if (!addResult.isSuccess()) {
                return Result.failure("Failed to add new tag: " + addResult.getMessage());
            }
            
            // Remove the request
            Result<Boolean> removeResult = requestRepository.removeRequest(request.getId());
            if (!removeResult.isSuccess()) {
                logger.warning("Failed to remove custom tag request with ID: " + request.getId());
            }
            
            // Add the permission to the player
            luckPerms.getUserManager().loadUser(request.getPlayerUuid()).thenAcceptAsync(user -> {
                user.data().add(Node.builder(permission).build());
                luckPerms.getUserManager().saveUser(user);
                
                // Execute the configured command to notify the player
                String command = plugin.getConfig().getString(
                    "accept-command", 
                    "mail send %player% Your custom tag request has been accepted!"
                );
                command = command.replace("%player%", request.getPlayerName());
                String finalCommand = command;
                
                Bukkit.getScheduler().runTask(plugin, () -> 
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
                );
            });
            
            return Result.success(true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error accepting custom tag request", e);
            return Result.error("Failed to accept tag request: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> denyCustomTagRequest(CustomTagRequest request) {
        if (request == null) {
            return Result.failure("Request cannot be null");
        }
        
        try {
            Result<Boolean> removeResult = requestRepository.removeRequest(request.getId());
            if (!removeResult.isSuccess()) {
                return Result.failure("Failed to remove request: " + removeResult.getMessage());
            }
            
            // Execute the configured command to notify the player
            String command = plugin.getConfig().getString(
                "deny-command", 
                "mail send %player% Your custom tag request has been denied."
            );
            command = command.replace("%player%", request.getPlayerName());
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return Result.success(true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error denying custom tag request", e);
            return Result.error("Failed to deny tag request: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> hasPendingRequests() {
        try {
            return requestRepository.hasPendingRequests();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error checking for pending requests", e);
            return Result.error("Failed to check pending requests: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<Boolean> purgeRequestsTable() {
        try {
            logger.warning("Purging all tag requests from database");
            return requestRepository.purgeRequestsTable();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error purging requests table", e);
            return Result.error("Failed to purge requests: " + e.getMessage(), e);
        }
    }
    
    /**
     * Notifies online staff members about a new tag request.
     */
    private void notifyStaffAboutRequest(Player requester) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("utags.staff")) {
                    player.sendMessage("§b[uTags] §fNew tag request from " + 
                        requester.getName() + "! Use /tag admin requests to review.");
                }
            }
        });
    }
}
