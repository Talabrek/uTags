package com.blockworlds.utags;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Tag {

    private String name;
    private String display;
    private TagType type;
    private boolean isPublic;
    private boolean color;

    private ItemStack material;

    public Tag(String name, String display, TagType type, boolean isPublic, boolean color, ItemStack material) {
        this.name = name;
        this.display = display;
        this.type = type;
        this.isPublic = isPublic;
        this.color = color;
        this.material = material;
    }

    public String getName() {
        return name;
    }

    public String getDisplay() {
        return display;
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
}