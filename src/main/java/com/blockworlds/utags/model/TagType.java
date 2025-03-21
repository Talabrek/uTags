package com.blockworlds.utags.model;

/**
 * Enum representing the possible types of a tag.
 */
public enum TagType {
    /** Tag appears before player name */
    PREFIX,
    
    /** Tag appears after player name */
    SUFFIX,
    
    /** Tag can be used as either prefix or suffix */
    BOTH
}
