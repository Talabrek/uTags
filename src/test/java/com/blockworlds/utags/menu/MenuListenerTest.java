package com.blockworlds.utags.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One listener for every menu: it cancels all clicks and drags, and routes clicks on the menu to its holder. */
class MenuListenerTest {

    /** A menu that records the clicks it gets. */
    private static final class RecordingMenu extends MenuHolder {
        final List<Integer> slots = new ArrayList<>();
        final Inventory inventory = mock(Inventory.class);

        RecordingMenu() {
            when(inventory.getHolder()).thenReturn(this);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void onClick(MenuClick click) {
            slots.add(click.slot());
        }
    }

    private final MenuListener listener = new MenuListener();
    private final Player player = mock(Player.class);

    /** A click event (mocked: the real event classes need the server registries) in a window with this top inventory. */
    private InventoryClickEvent click(Inventory top, Inventory clicked, int rawSlot, ItemStack item) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(clicked);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getCurrentItem()).thenReturn(item);
        return event;
    }

    private ItemStack item() {
        ItemStack item = mock(ItemStack.class); // a real ItemStack needs the registries too
        when(item.isEmpty()).thenReturn(false);
        return item;
    }

    @Test
    void aClickOnAnItemOfTheMenuIsCancelledAndRoutedToItsHolder() {
        RecordingMenu menu = new RecordingMenu();
        InventoryClickEvent event = click(menu.inventory, menu.inventory, 5, item());
        listener.onClick(event);
        verify(event).setCancelled(true);
        assertEquals(List.of(5), menu.slots);
    }

    @Test
    void shiftClickOnThePlayerInventoryIsCancelledButNotRouted() {
        RecordingMenu menu = new RecordingMenu();
        InventoryClickEvent event = click(menu.inventory, mock(Inventory.class), 60, item());
        listener.onClick(event);
        verify(event).setCancelled(true); // a shift-click from the player inventory would move an item into the menu
        assertTrue(menu.slots.isEmpty());
    }

    @Test
    void aClickOnAnEmptySlotIsCancelledButNotRouted() {
        RecordingMenu menu = new RecordingMenu();
        ItemStack empty = mock(ItemStack.class);
        when(empty.isEmpty()).thenReturn(true);
        InventoryClickEvent event = click(menu.inventory, menu.inventory, 5, empty);
        listener.onClick(event);
        verify(event).setCancelled(true);
        assertTrue(menu.slots.isEmpty());
    }

    @Test
    void aClickOutsideTheWindowIsCancelledButNotRouted() {
        RecordingMenu menu = new RecordingMenu();
        InventoryClickEvent event = click(menu.inventory, null, -999, null);
        listener.onClick(event);
        verify(event).setCancelled(true);
        assertTrue(menu.slots.isEmpty());
    }

    @Test
    void dragsAcrossAMenuAreCancelled() {
        RecordingMenu menu = new RecordingMenu();
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(menu.inventory);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        listener.onDrag(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    void otherInventoriesAreLeftAlone() {
        Inventory chest = mock(Inventory.class);
        when(chest.getHolder()).thenReturn(mock(InventoryHolder.class));
        InventoryClickEvent event = click(chest, chest, 5, item());
        listener.onClick(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void everyMenuHolderClassAnswersItsOwnClicks() throws Exception {
        List<Class<?>> holders = List.of(
                TagOptionsMenu.class, TagMenuManager.SelectionHolder.class, TagMenuManager.RequestConfirmHolder.class,
                TagColorMenuManager.ColorHolder.class, NameColorMenuManager.NameColorHolder.class,
                AdminMenuManager.MainHolder.class, AdminMenuManager.TagListHolder.class, AdminMenuManager.TagEditorHolder.class,
                AdminMenuManager.PurgeTypeHolder.class, AdminMenuManager.PurgeConfirmHolder.class,
                AdminMenuManager.DeleteConfirmHolder.class, AdminMenuManager.WizardHolder.class,
                RequestMenus.ListHolder.class, RequestMenus.ReviewHolder.class);
        for (Class<?> holder : holders) {
            assertTrue(MenuHolder.class.isAssignableFrom(holder), holder + " is a MenuHolder");
            assertFalse(Modifier.isAbstract(holder.getModifiers()), holder + " is concrete");
            assertEquals(holder, holder.getMethod("onClick", MenuClick.class).getDeclaringClass(), holder + " handles its own clicks");
        }
        // every class in the menu package that extends MenuHolder is in the list above
        long found = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/blockworlds/utags/menu"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                found += source.split("extends (MenuHolder|AdminHolder)", -1).length - 1;
            }
        }
        assertEquals(holders.size(), found - 1, "holder classes in the sources (minus the abstract AdminHolder base)");
    }

    @Test
    void noCodeFindsAMenuByItsTitleOrBuildsAnInventoryOutsideTheHolder() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains(".getTitle()"), file + " reads an inventory title");
                if (!file.endsWith("MenuHolder.java")) {
                    assertFalse(source.contains("Bukkit.createInventory"), file + " builds an inventory without a MenuHolder");
                }
            }
        }
    }
}
