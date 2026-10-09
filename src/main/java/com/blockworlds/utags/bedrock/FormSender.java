package com.blockworlds.utags.bedrock;

import org.bukkit.entity.Player;

/** Where forms go: Floodgate in the server, a recorder in tests. */
public interface FormSender {

    boolean isBedrock(Player player);

    /** Shows the form; a click calls {@link BedrockMenu#select} on the main thread. */
    void send(Player player, BedrockMenu menu);
}
