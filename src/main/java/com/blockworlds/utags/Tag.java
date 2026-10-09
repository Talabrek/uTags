package com.blockworlds.utags;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Tag {

    private String name;
    private String display;
    private TagType type;
    private boolean isPublic;
    private boolean color; // Existing field for GUI item color?
    private boolean colorable; // New field: Can player change this tag's color?
    private int weight;

    private ItemStack material;

    // Custom tags only (see CustomTags): who owns it and which slot it fills. Null for normal tags.
    private UUID ownerUuid;
    private Integer slot;

    public Tag(String name, String display, TagType type, boolean isPublic, boolean color, boolean colorable, ItemStack material, int weight) {
        this.name = name;
        this.display = display;
        this.type = type;
        this.isPublic = isPublic;
        this.color = color;
        this.colorable = colorable; // Assign the new field
        this.material = material;
        this.weight = weight;
    }

    public Tag(String name, String display, TagType type, boolean isPublic, boolean color, boolean colorable, ItemStack material, int weight,
               UUID ownerUuid, Integer slot) {
        this(name, display, type, isPublic, color, colorable, material, weight);
        this.ownerUuid = ownerUuid;
        this.slot = slot;
    }

    public String getName() {
        return name;
    }

    public String getDisplay() {
        return display;
    }

    public boolean isColorable() {
        return colorable;
    }

    public TagType getType() {
        return type;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public boolean isColor() {
        return color;
    }

    public ItemStack getMaterial() {
        return material;
    }

    public void setMaterial(ItemStack material) {
        this.material = material;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Integer getSlot() {
        return slot;
    }
}
