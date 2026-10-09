package com.blockworlds.utags.module;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.spi.CommandExtension;
import com.blockworlds.utags.spi.DisplayDecorator;
import com.blockworlds.utags.spi.MenuExtension;
import com.blockworlds.utags.spi.MenuOpenerOverride;
import com.blockworlds.utags.spi.MenuRequest;
import com.blockworlds.utags.spi.PackContributor;
import com.blockworlds.utags.spi.StorageExtension;
import com.blockworlds.utags.storage.Migrations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** What modules registered through the {@code spi} interfaces. */
public final class Extensions {

    private final List<DisplayDecorator> decorators = new CopyOnWriteArrayList<>();
    private final List<MenuExtension> menus = new CopyOnWriteArrayList<>();
    private final List<MenuOpenerOverride> openers = new CopyOnWriteArrayList<>();
    private final List<StorageExtension> storage = new CopyOnWriteArrayList<>();
    private final List<PackContributor> packs = new CopyOnWriteArrayList<>();
    private final List<CommandExtension> commands = new CopyOnWriteArrayList<>();
    private final List<com.blockworlds.utags.spi.OutputTransformer> outputs = new CopyOnWriteArrayList<>();

    public void add(DisplayDecorator d) { decorators.add(d); }

    public void add(MenuExtension m) { menus.add(m); }

    public void add(MenuOpenerOverride o) { openers.add(o); }

    public void add(StorageExtension s) { storage.add(s); }

    public void add(PackContributor p) { packs.add(p); }

    public void add(CommandExtension c) { commands.add(c); }

    public void add(com.blockworlds.utags.spi.OutputTransformer o) { outputs.add(o); }

    /** Runs the output transformers in order; a failing one is skipped. */
    public String transformOutput(java.util.UUID player, Tag tag, boolean prefix, String text) {
        String result = text;
        for (com.blockworlds.utags.spi.OutputTransformer o : outputs) {
            try {
                String next = o.transform(player, tag, prefix, result);
                if (next != null) result = next;
            } catch (RuntimeException e) {
                // a broken transformer must not stop the tag from being written
            }
        }
        return result;
    }

    public List<CommandExtension> commands() { return List.copyOf(commands); }

    public List<MenuExtension> menus() { return List.copyOf(menus); }

    public List<PackContributor> packs() { return List.copyOf(packs); }

    /** Offers a player menu to the registered overrides; true when one opened it (the caller skips the chest menu). */
    public boolean openOverridden(Player player, MenuRequest request) {
        for (MenuOpenerOverride o : openers) {
            if (o.open(player, request)) return true;
        }
        return false;
    }

    /** Runs the decorators in priority order. */
    public Component decorate(Component tag, Tag def, Player viewer, DisplayDecorator.Context context) {
        return decorate(tag, def, null, viewer, context);
    }

    /** Runs every decorator in priority order; {@code wearer} is whose tag it is (null when unknown). */
    public Component decorate(Component tag, Tag def, java.util.UUID wearer, Player viewer, DisplayDecorator.Context context) {
        Component result = tag;
        for (DisplayDecorator d : decorators.stream().sorted(Comparator.comparingInt(DisplayDecorator::priority)).toList()) {
            result = d.decorate(result, def, wearer, viewer, context);
        }
        return result;
    }

    /** The core migrations plus every registered extension one; an extension number below 100 is rejected. */
    public List<Migrations.Migration> migrations() {
        List<Migrations.Migration> all = new ArrayList<>(Migrations.core());
        for (StorageExtension s : storage) {
            for (Migrations.Migration m : s.migrations()) {
                if (m.version() < StorageExtension.FIRST_EXTENSION_VERSION) {
                    throw new IllegalStateException("Storage extension migration " + m.version() + " (" + m.name()
                            + ") is below " + StorageExtension.FIRST_EXTENSION_VERSION);
                }
                all.add(m);
            }
        }
        return all;
    }
}
