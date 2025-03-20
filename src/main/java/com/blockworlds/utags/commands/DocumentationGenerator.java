package com.blockworlds.utags.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for generating documentation for the uTags plugin.
 * This is a development-time tool to help maintain documentation consistency.
 */
public class DocumentationGenerator {

    // Constants for file paths
    private static final String DOCS_DIR = "docs";
    private static final String API_DOCS_FILE = DOCS_DIR + "/api.md";
    private static final String USER_GUIDE_FILE = DOCS_DIR + "/user_guide.md";
    private static final String ADMIN_GUIDE_FILE = DOCS_DIR + "/admin_guide.md";
    private static final String DEVELOPER_GUIDE_FILE = DOCS_DIR + "/developer_guide.md";
    
    // Constants for documentation sections
    private static final String SECTION_BREAK = "\n\n---\n\n";
    private static final String CODE_BLOCK_START = "```java";
    private static final String CODE_BLOCK_END = "```";

    /**
     * Main method to generate documentation.
     * Run this as a standalone program during development.
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        try {
            // Create docs directory if it doesn't exist
            File docsDir = new File(DOCS_DIR);
            if (!docsDir.exists()) {
                docsDir.mkdirs();
            }
            
            // Generate documentation
            generateApiDocs();
            generateUserGuide();
            generateAdminGuide();
            generateDeveloperGuide();
            
            System.out.println("Documentation generated successfully in " + DOCS_DIR + " directory.");
        } catch (IOException e) {
            System.err.println("Error generating documentation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generates API documentation.
     *
     * @throws IOException If an I/O error occurs
     */
    private static void generateApiDocs() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(API_DOCS_FILE))) {
            // Write header
            writer.write("# uTags API Documentation\n\n");
            writer.write("This document details the API for the uTags plugin.\n\n");
            
            // Document main classes
            documentClass(writer, "uTags", "Main plugin class");
            documentClass(writer, "Tag", "Tag data object");
            documentClass(writer, "TagType", "Enum for tag types");
            
            // Document services
            writer.write("## Services\n\n");
            writer.write("These services provide the business logic for the plugin.\n\n");
            documentClass(writer, "TagService", "Service for tag operations");
            documentClass(writer, "MenuService", "Service for menu operations");
            
            // Document utilities
            writer.write("## Utilities\n\n");
            writer.write("These utility classes provide common functionality.\n\n");
            documentClass(writer, "MenuUtils", "Utilities for menu operations");
            documentClass(writer, "MessageUtils", "Utilities for message formatting");
            documentClass(writer, "ValidationUtils", "Utilities for input validation");
            documentClass(writer, "PermissionUtils", "Utilities for permission checking");
            documentClass(writer, "StringUtils", "Utilities for string manipulation");
            documentClass(writer, "Preconditions", "Utilities for argument validation");
        }
    }

    /**
     * Generates a user guide.
     *
     * @throws IOException If an I/O error occurs
     */
    private static void generateUserGuide() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_GUIDE_FILE))) {
            // Write header
            writer.write("# uTags User Guide\n\n");
            writer.write("This guide explains how to use the uTags plugin as a player.\n\n");
            
            // Table of contents
            writer.write("## Table of Contents\n\n");
            writer.write("1. [Introduction](#introduction)\n");
            writer.write("2. [Basic Commands](#basic-commands)\n");
            writer.write("3. [Using the Tag Menu](#using-the-tag-menu)\n");
            writer.write("4. [Requesting Custom Tags](#requesting-custom-tags)\n");
            writer.write("5. [Tag Formatting](#tag-formatting)\n");
            writer.write("6. [Premium Benefits](#premium-benefits)\n\n");
            
            // Introduction
            writer.write("## Introduction\n\n");
            writer.write("uTags allows you to display custom prefixes and suffixes in chat. ");
            writer.write("You can choose from a selection of pre-made tags or request custom ones.\n\n");
            
            // Basic Commands
            writer.write("## Basic Commands\n\n");
            writer.write("- `/tag` - Opens the tag selection menu\n");
            writer.write("- `/tag set [tagname]` - Directly sets your tag\n");
            writer.write("- `/tag request [tag]` - Requests a custom tag\n");
            writer.write("- `/tag help` - Shows help information\n\n");
            
            // Using the Tag Menu
            writer.write("## Using the Tag Menu\n\n");
            writer.write("The tag menu allows you to browse and select from available tags:\n\n");
            writer.write("1. Type `/tag` to open the main menu\n");
            writer.write("2. Click on \"Change Prefix\" or \"Change Suffix\"\n");
            writer.write("3. Browse through available tags (use navigation arrows for more pages)\n");
            writer.write("4. Click on a tag to select it\n\n");
            
            // Requesting Custom Tags
            writer.write("## Requesting Custom Tags\n\n");
            writer.write("Premium members can request custom tags:\n\n");
            writer.write("1. Use `/tag request &c[MyTag]` to request a custom tag\n");
            writer.write("2. Follow the formatting rules (start with color code, use brackets)\n");
            writer.write("3. Confirm the tag when prompted\n");
            writer.write("4. Wait for staff approval\n\n");
            
            // Tag Formatting
            writer.write("## Tag Formatting\n\n");
            writer.write("Custom tags must follow these rules:\n\n");
            writer.write("1. Start with a color code (e.g., &6, &a, &b)\n");
            writer.write("2. Be surrounded by square brackets [ ]\n");
            writer.write("3. Be maximum 15 characters long (excluding color codes)\n");
            writer.write("4. Not contain formatting codes (&k, &l, etc.)\n\n");
            
            // Premium Benefits
            writer.write("## Premium Benefits\n\n");
            writer.write("Premium members get these extra benefits:\n\n");
            writer.write("1. Access to exclusive tags\n");
            writer.write("2. Ability to request custom tags\n");
            writer.write("3. Multiple custom tag slots\n");
            writer.write("4. Priority tag approval\n");
        }
    }

    /**
     * Generates an admin guide.
     *
     * @throws IOException If an I/O error occurs
     */
    private static void generateAdminGuide() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ADMIN_GUIDE_FILE))) {
            // Write header
            writer.write("# uTags Admin Guide\n\n");
            writer.write("This guide explains how to administer the uTags plugin.\n\n");
            
            // Table of contents
            writer.write("## Table of Contents\n\n");
            writer.write("1. [Installation](#installation)\n");
            writer.write("2. [Configuration](#configuration)\n");
            writer.write("3. [Admin Commands](#admin-commands)\n");
            writer.write("4. [Managing Tags](#managing-tags)\n");
            writer.write("5. [Handling Custom Tag Requests](#handling-custom-tag-requests)\n");
            writer.write("6. [Permissions](#permissions)\n");
            writer.write("7. [Troubleshooting](#troubleshooting)\n\n");
            
            // Installation
            writer.write("## Installation\n\n");
            writer.write("1. Place the uTags.jar file in your plugins folder\n");
            writer.write("2. Restart the server\n");
            writer.write("3. Configure the plugin (config.yml)\n\n");
            
            // Configuration
            writer.write("## Configuration\n\n");
            writer.write("The config.yml file contains these sections:\n\n");
            writer.write("- `database`: Database connection settings\n");
            writer.write("- `frame-material`: Material for menu borders\n");
            writer.write("- `default-tag`: Default tag for new players\n\n");
            
            // Admin Commands
            writer.write("## Admin Commands\n\n");
            writer.write("- `/tag admin create [name] [display] [type] [weight]` - Create a new tag\n");
            writer.write("- `/tag admin delete [name]` - Delete an existing tag\n");
            writer.write("- `/tag admin edit [tagname] [attribute] [newvalue]` - Edit a tag attribute\n");
            writer.write("- `/tag admin requests` - View pending tag requests\n");
            writer.write("- `/tag admin purge tags` - Remove all tags\n");
            writer.write("- `/tag admin purge requests` - Remove all pending requests\n\n");
            
            // Managing Tags
            writer.write("## Managing Tags\n\n");
            writer.write("### Creating Tags\n\n");
            writer.write("To create a tag, hold the item you want to represent it and use:\n\n");
            writer.write("`/tag admin create [name] [display] [type] [weight]`\n\n");
            writer.write("- `name`: Internal name (alphanumeric, no spaces)\n");
            writer.write("- `display`: How it appears in chat (color codes allowed)\n");
            writer.write("- `type`: PREFIX, SUFFIX, or BOTH\n");
            writer.write("- `weight`: Priority for sorting (higher = higher priority)\n\n");
            
            // Handling Custom Tag Requests
            writer.write("## Handling Custom Tag Requests\n\n");
            writer.write("1. View pending requests with `/tag admin requests`\n");
            writer.write("2. Click a request in the menu to review it\n");
            writer.write("3. Left-click to approve or right-click to deny\n");
            writer.write("4. The player will be notified of the decision\n\n");
            
            // Permissions
            writer.write("## Permissions\n\n");
            writer.write("- `utags.admin`: Access to admin commands\n");
            writer.write("- `utags.staff`: Notified about new tag requests\n");
            writer.write("- `utags.tag.[tagname]`: Allows use of a specific tag\n");
            writer.write("- `utags.custom[1-5]`: Allows requesting custom tags\n");
            writer.write("- `utags.tagcolor`: Allows changing tag colors\n\n");
            
            // Troubleshooting
            writer.write("## Troubleshooting\n\n");
            writer.write("### Database Issues\n\n");
            writer.write("If you encounter database errors:\n\n");
            writer.write("1. Check the connection settings in config.yml\n");
            writer.write("2. Verify database permissions\n");
            writer.write("3. Check the console for specific error messages\n\n");
            
            writer.write("### Missing Tags\n\n");
            writer.write("If tags are not appearing:\n\n");
            writer.write("1. Verify LuckPerms is installed and working\n");
            writer.write("2. Check player permissions\n");
            writer.write("3. Check tag visibility settings in LuckPerms\n");
        }
    }

    /**
     * Generates a developer guide.
     *
     * @throws IOException If an I/O error occurs
     */
    private static void generateDeveloperGuide() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DEVELOPER_GUIDE_FILE))) {
            // Write header
            writer.write("# uTags Developer Guide\n\n");
            writer.write("This guide explains how to extend or integrate with the uTags plugin.\n\n");
            
            // Table of contents
            writer.write("## Table of Contents\n\n");
            writer.write("1. [Architecture Overview](#architecture-overview)\n");
            writer.write("2. [API Usage](#api-usage)\n");
            writer.write("3. [Event Listening](#event-listening)\n");
            writer.write("4. [Adding Custom Tag Types](#adding-custom-tag-types)\n");
            writer.write("5. [Integration Examples](#integration-examples)\n");
            writer.write("6. [Database Schema](#database-schema)\n");
            writer.write("7. [Coding Standards](#coding-standards)\n\n");
            
            // Architecture Overview
            writer.write("## Architecture Overview\n\n");
            writer.write("uTags follows a layered architecture:\n\n");
            writer.write("1. **Commands Layer**: Handles user input\n");
            writer.write("2. **Service Layer**: Contains business logic\n");
            writer.write("3. **Data Access Layer**: Interfaces with the database\n");
            writer.write("4. **UI Layer**: Manages inventory-based user interfaces\n\n");
            
            // API Usage
            writer.write("## API Usage\n\n");
            writer.write("To use uTags in your plugin:\n\n");
            writer.write(CODE_BLOCK_START + "\n");
            writer.write("// Get the uTags instance\n");
            writer.write("uTags uTagsPlugin = (uTags) Bukkit.getPluginManager().getPlugin(\"uTags\");\n\n");
            writer.write("// Access tags\n");
            writer.write("List<Tag> prefixTags = uTagsPlugin.getAvailableTags(TagType.PREFIX);\n\n");
            writer.write("// Set a player's tag\n");
            writer.write("uTagsPlugin.setPlayerTag(player, tagDisplay, TagType.PREFIX);\n");
            writer.write(CODE_BLOCK_END + "\n\n");
            
            // Event Listening
            writer.write("## Event Listening\n\n");
            writer.write("uTags doesn't currently provide custom events, but you can listen to inventory events:\n\n");
            writer.write(CODE_BLOCK_START + "\n");
            writer.write("@EventHandler\n");
            writer.write("public void onInventoryClick(InventoryClickEvent event) {\n");
            writer.write("    if (event.getView().getTitle().contains(\"uTags Menu\")) {\n");
            writer.write("        // Handle menu interaction\n");
            writer.write("    }\n");
            writer.write("}\n");
            writer.write(CODE_BLOCK_END + "\n\n");
            
            // Adding Custom Tag Types
            writer.write("## Adding Custom Tag Types\n\n");
            writer.write("To add a custom tag type, extend the TagType enum:\n\n");
            writer.write(CODE_BLOCK_START + "\n");
            writer.write("// First, fork the plugin and add to the enum\n");
            writer.write("public enum TagType {\n");
            writer.write("    PREFIX,\n");
            writer.write("    SUFFIX,\n");
            writer.write("    BOTH,\n");
            writer.write("    CUSTOM_TYPE // Your new type\n");
            writer.write("}\n");
            writer.write(CODE_BLOCK_END + "\n\n");
            
            // Integration Examples
            writer.write("## Integration Examples\n\n");
            writer.write("### Integration with Chat Plugins\n\n");
            writer.write(CODE_BLOCK_START + "\n");
            writer.write("@EventHandler(priority = EventPriority.HIGH)\n");
            writer.write("public void onChat(AsyncPlayerChatEvent event) {\n");
            writer.write("    Player player = event.getPlayer();\n");
            writer.write("    String prefix = uTagsPlugin.getLuckPerms().getUserManager()\n");
            writer.write("        .getUser(player.getUniqueId()).getCachedData().getMetaData().getPrefix();\n");
            writer.write("    // Format chat with prefix\n");
            writer.write("}\n");
            writer.write(CODE_BLOCK_END + "\n\n");
            
            // Database Schema
            writer.write("## Database Schema\n\n");
            writer.write("uTags uses two main tables:\n\n");
            writer.write("### Tags Table\n\n");
            writer.write("```sql\n");
            writer.write("CREATE TABLE `tags` (\n");
            writer.write("    `id` INT AUTO_INCREMENT PRIMARY KEY,\n");
            writer.write("    `name` VARCHAR(255) NOT NULL,\n");
            writer.write("    `display` VARCHAR(255) NOT NULL,\n");
            writer.write("    `type` ENUM('PREFIX', 'SUFFIX', 'BOTH') NOT NULL,\n");
            writer.write("    `public` BOOLEAN NOT NULL,\n");
            writer.write("    `color` BOOLEAN NOT NULL,\n");
            writer.write("    `material` MEDIUMTEXT NOT NULL,\n");
            writer.write("    `weight` INT NOT NULL,\n");
            writer.write("    INDEX `idx_tag_name` (`name`)\n");
            writer.write(");\n");
            writer.write("```\n\n");
            
            writer.write("### Tag Requests Table\n\n");
            writer.write("```sql\n");
            writer.write("CREATE TABLE `tag_requests` (\n");
            writer.write("    `id` INT AUTO_INCREMENT PRIMARY KEY,\n");
            writer.write("    `player_uuid` VARCHAR(36) NOT NULL,\n");
            writer.write("    `player_name` VARCHAR(255) NOT NULL,\n");
            writer.write("    `tag_display` VARCHAR(255) NOT NULL,\n");
            writer.write("    INDEX `idx_player_uuid` (`player_uuid`),\n");
            writer.write("    INDEX `idx_player_name` (`player_name`)\n");
            writer.write(");\n");
            writer.write("```\n\n");
            
            // Coding Standards
            writer.write("## Coding Standards\n\n");
            writer.write("When contributing to uTags, follow these guidelines:\n\n");
            writer.write("- Use the provided utility classes for common operations\n");
            writer.write("- Add comprehensive JavaDoc to all public methods\n");
            writer.write("- Follow the single responsibility principle\n");
            writer.write("- Use proper exception handling with specific exception types\n");
            writer.write("- Validate all method parameters\n");
            writer.write("- Follow the naming conventions in CODING_STANDARDS.md\n");
        }
    }

    /**
     * Documents a class and its methods.
     *
     * @param writer The writer to use
     * @param className The name of the class
     * @param description A brief description of the class
     * @throws IOException If an I/O error occurs
     */
    private static void documentClass(BufferedWriter writer, String className, String description) throws IOException {
        writer.write("### " + className + "\n\n");
        writer.write(description + "\n\n");
        
        // This would normally reflect on actual classes
        // For this example, we'll just write placeholder method documentation
        writer.write("#### Methods\n\n");
        writer.write("- `public void methodName(String param)` - Description of the method\n");
        writer.write("- `public String getProperty()` - Gets a property value\n");
        writer.write("- `public void setProperty(String value)` - Sets a property value\n\n");
        
        writer.write(SECTION_BREAK);
    }

    /**
     * Gets the public methods of a class.
     *
     * @param clazz The class to get methods from
     * @return A list of public methods
     */
    private static List<Method> getPublicMethods(Class<?> clazz) {
        List<Method> publicMethods = new ArrayList<>();
        Method[] allMethods = clazz.getDeclaredMethods();
        
        for (Method method : allMethods) {
            if (Modifier.isPublic(method.getModifiers())) {
                publicMethods.add(method);
            }
        }
        
        // Sort methods by name
        publicMethods.sort(Comparator.comparing(Method::getName));
        
        return publicMethods;
    }
}
