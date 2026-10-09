package com.blockworlds.utags.bedrock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

/** {@link FormSender} on the Floodgate API. Only loaded when Floodgate is installed. */
final class FloodgateSender implements FormSender {

    private final Plugin plugin;

    FloodgateSender(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isBedrock(Player player) {
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }

    @Override
    public void send(Player player, BedrockMenu menu) {
        Form form;
        if (menu.modal()) {
            form = ModalForm.builder().title(menu.title()).content(menu.content())
                    .button1(menu.buttons().get(0)).button2(menu.buttons().get(1))
                    .validResultHandler(r -> onMain(player, menu, r.clickedFirst() ? 0 : 1))
                    .build();
        } else {
            SimpleForm.Builder b = SimpleForm.builder().title(menu.title()).content(menu.content());
            for (String text : menu.buttons()) b.button(text);
            form = b.validResultHandler(r -> onMain(player, menu, r.clickedButtonId())).build();
        }
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    /** Floodgate answers on a network thread; the services want the main thread. */
    private void onMain(Player player, BedrockMenu menu, int index) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) menu.select(index);
        });
    }
}
