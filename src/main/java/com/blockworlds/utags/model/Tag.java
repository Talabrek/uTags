package com.blockworlds.utags.model;

import org.bukkit.inventory.ItemStack;

/**
 * Represents a tag that can be displayed with a player's name in chat.
 */
public class Tag {
    private final String name;
    private final String display;
    private final TagType type;
    private final boolean isPublic;
    private final boolean color;
    private ItemStack material; // Not final to allow changes
    private int weight; // Not final to allow weight adjustments

    /**
     * Creates a new Tag with all properties.
     */
    public Tag(String name, String display, TagType type, boolean isPublic, 
               boolean color, ItemStack material, int weight) {
        this.name = name;
        this.display = display;
        this.type = type;
        this.isPublic = isPublic;
        this.color = color;
        this.material = material;
        this.weight = weight;
    }

    /** Returns the internal name of the tag */
    public String getName() { return name; }
    
    /** Returns the display text of the tag */
    public String getDisplay() { return display; }
    
    /** Returns the type of the tag (PREFIX, SUFFIX, BOTH) */
    public TagType getType() { return type; }
    
    /** Returns whether the tag is publicly available */
    public boolean isPublic() { return isPublic; }
    
    /** Returns whether the tag supports color */
    public boolean isColor() { return color; }
    
    /** Returns the material used to represent this tag in menus */
    public ItemStack getMaterial() { return material; }
    
    /** Sets the material used to represent this tag */
    public void setMaterial(ItemStack material) { this.material = material; }
    
    /** Returns the weight of the tag (higher = higher priority) */
    public int getWeight() { return weight; }
    
    /** Sets the weight of the tag */
    public void setWeight(int weight) { this.weight = weight; }
}
