package com.blockworlds.utags.bedrock;

import com.blockworlds.utags.module.UTagsContext;
import com.blockworlds.utags.module.UTagsModule;
import org.bukkit.Bukkit;

/** Shows the player menus as Floodgate forms to Bedrock players. Off unless Floodgate is installed and {@code bedrock.forms} is true. */
public final class BedrockModule implements UTagsModule {

    @Override
    public String id() {
        return "bedrock";
    }

    @Override
    public void enable(UTagsContext ctx) {
        if (!ctx.plugin().getConfig().getBoolean("bedrock.forms", true)) {
            ctx.logger().info("Bedrock forms are off (bedrock.forms: false).");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            ctx.logger().info("Floodgate not found: Bedrock players get the chest menus.");
            return;
        }
        ctx.extensions().add(new Forms(new PluginFormServices(ctx.plugin()), new FloodgateSender(ctx.plugin())));
        ctx.logger().info("Bedrock forms are on: Floodgate players get forms instead of chest menus.");
    }
}
