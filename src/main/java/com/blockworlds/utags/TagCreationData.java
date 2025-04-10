package com.blockworlds.utags;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

// Simple class to hold data during the tag creation wizard process
public class TagCreationData {
    private String name;
    private String display;
    private TagType type = TagType.PREFIX; // Default type
    private int weight = 10; // Default weight
    private ItemStack material = new ItemStack(Material.NAME_TAG); // Default icon
    private boolean isPublic = true; // Default public
    private boolean color = true; // Default color (though unused currently)

    // Getters
    public String getName() { return name; }
    public String getDisplay() { return display; }
    public TagType getType() { return type; }
    public int getWeight() { return weight; }
    public ItemStack getMaterial() { return material; }
    public boolean isPublic() { return isPublic; }
    public boolean isColor() { return color; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setDisplay(String display) { this.display = display; }
    public void setType(TagType type) { this.type = type; }
    public void setWeight(int weight) { this.weight = weight; }
    public void setMaterial(ItemStack material) { this.material = (material != null && material.getType() != Material.AIR) ? material : new ItemStack(Material.NAME_TAG); }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public void setColor(boolean color) { this.color = color; }

    // Check if essential data is set for final confirmation
    public boolean isComplete() {
        // Weight is now an int with a default, so no null check needed
        return name != null && !name.isEmpty() &&
               display != null && !display.isEmpty() &&
               type != null;
        // Material defaults, flags default
    }
}