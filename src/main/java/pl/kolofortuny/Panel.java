package pl.kolofortuny;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class Panel implements Listener {

    static class H implements InventoryHolder {
        final String type;
        H(String type) { this.type = type; }
        @Override public Inventory getInventory() { return null; }
    }

    private final KoloPlugin pl;
    private final Wheel w;
    private final Map<UUID, Integer> pending = new HashMap<>();

    public Panel(KoloPlugin pl) {
        this.pl = pl;
        this.w = pl.wheel;
    }

    private FileConfiguration cfg() { return pl.getConfig(); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static ItemStack glass() { return Items.button(Material.BLACK_STAINED_GLASS_PANE, " "); }

    private static String onoff(boolean b) { return b ? "&aWŁĄCZONE" : "&cWYŁĄCZONE"; }

    // ================= PANEL GŁÓWNY =================

    public void openPanel(Player p) {
        Inventory inv = Bukkit.createInventory(new H("panel"), 45, Items.c("&6&lKoło Fortuny &8- Panel"));
        fillPanel(inv);
        p.openInventory(inv);
    }

    private void fillPanel(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass());
        String hint = "&8LPM +  |  PPM -  |  Shift x5";
        inv.setItem(10, Items.button(Material.CLOCK, "&eCzas animacji",
                "&7Wartość: &f" + cfg().getInt("ustawienia.czas-animacji") + " s", hint));
        inv.setItem(11, Items.button(Material.FIREWORK_STAR, "&eMinimalna liczba obrotów",
                "&7Wartość: &f" + cfg().getInt("ustawienia.obroty"), hint));
        inv.setItem(12, Items.button(Material.ENDER_EYE, "&eRozmiar koła",
                "&7Wartość: &f" + cfg().getDouble("ustawienia.rozmiar") + " bloków", "&8LPM +0.25  |  PPM -0.25"));
        inv.setItem(13, Items.button(Material.COMPARATOR, "&eTryb bezczynności (gdy nikt nie kręci)",
                "&7Wartość: &f" + cfg().getString("ustawienia.idle"),
                "&8GLOW = miga podświetlenie przedmiotu przy wskaźniku",
                "&8OBROT = koło powoli się obraca", "&8BRAK = stoi bez efektu", "&8Kliknij, aby zmienić"));
        inv.setItem(14, Items.button(Material.REPEATER, "&eBezczynność: okres obrotu",
                "&7Wartość: &f" + cfg().getInt("ustawienia.idle-okres") + " s", "&8Dotyczy trybu OBROT", "&8LPM +5  |  PPM -5"));
        inv.setItem(15, Items.button(Material.LANTERN, "&eBezczynność: miganie",
                "&7Wartość: &f" + cfg().getInt("ustawienia.idle-miganie") + " ticków", "&8Dotyczy trybu GLOW", "&8LPM +5  |  PPM -5"));
        inv.setItem(16, Items.button(Material.NOTE_BLOCK, "&eDźwięk tikania",
                "&7Stan: " + onoff(cfg().getBoolean("ustawienia.dzwiek")), "&8Kliknij, aby przełączyć"));
        inv.setItem(19, Items.button(Material.OAK_SIGN, "&eOgłoszenie wygranej na czacie",
                "&7Stan: " + onoff(cfg().getBoolean("ustawienia.ogloszenie")), "&8Kliknij, aby przełączyć"));
        inv.setItem(21, Items.button(Material.CHEST, "&a&lEdytor slotów",
                "&7Przedmioty, szanse i komendy 12 slotów koła"));
        inv.setItem(22, Items.button(Material.PAPER, "&6Daj sobie bon", "&8LPM: 1 szt.  |  Shift: 16 szt."));
        inv.setItem(23, Items.button(Material.LIME_DYE, "&aUstaw koło tutaj",
                "&7Dół koła pojawi się w miejscu, w którym stoisz", "&7Koło ustawia się wzdłuż kierunku, w który patrzysz"));
        inv.setItem(24, Items.button(Material.RED_DYE, "&cUsuń koło"));
        inv.setItem(25, Items.button(Material.BLAZE_POWDER, "&dTestowy obrót", "&7Kręci kołem bez bonu"));
        inv.setItem(31, Items.button(Material.ANVIL, "&7Przeładuj config"));
        inv.setItem(37, Items.button(Material.ARMOR_STAND, "&a&lUstaw Pana Szczęściarza tutaj",
                "&7Stoi w miejscu, w którym jesteś,", "&7twarzą w stronę, w którą patrzysz"));
        inv.setItem(38, Items.button(Material.RED_DYE, "&cUsuń Pana Szczęściarza"));
        inv.setItem(39, Items.button(Material.SPYGLASS, "&eRozmiar Pana Szczęściarza",
                "&7Wartość: &f" + cfg().getDouble("pan.rozmiar", 1.15), "&8LPM +0.1  |  PPM -0.1"));
        inv.setItem(40, Items.button(Material.COMPASS, "&eObróć Pana o 180°",
                "&7Jeśli stoi tyłem - kliknij", "&7Obecnie: &f" + cfg().getInt("pan.obrot", 0) + "°"));
        inv.setItem(41, Items.button(Material.FIREWORK_ROCKET, "&dTest: rzut cylindrem"));
        inv.setItem(42, Items.button(Material.GOLD_INGOT, "&6Cena bonu w sklepie Pana",
                "&7Wartość: &f" + String.format(Locale.US, "%.2f", cfg().getDouble("bon.cena", 10.0)) + " VPLN",
                "&8LPM +1  |  PPM -1  |  Shift x10"));
    }

    private void panelClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int d = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 5 : 1);
        FileConfiguration c = cfg();
        switch (e.getSlot()) {
            case 10 -> c.set("ustawienia.czas-animacji", clamp(c.getInt("ustawienia.czas-animacji") + d, 2, 60));
            case 11 -> c.set("ustawienia.obroty", clamp(c.getInt("ustawienia.obroty") + d, 1, 20));
            case 12 -> {
                double v = c.getDouble("ustawienia.rozmiar") + (e.isLeftClick() ? 0.25 : -0.25);
                c.set("ustawienia.rozmiar", Math.max(1.0, Math.min(10.0, v)));
                pl.saveConfig();
                w.build();
            }
            case 13 -> {
                String cur = c.getString("ustawienia.idle", "GLOW").toUpperCase();
                c.set("ustawienia.idle", cur.equals("GLOW") ? "OBROT" : cur.equals("OBROT") ? "BRAK" : "GLOW");
            }
            case 14 -> c.set("ustawienia.idle-okres", clamp(c.getInt("ustawienia.idle-okres") + (e.isLeftClick() ? 5 : -5), 5, 300));
            case 15 -> c.set("ustawienia.idle-miganie", clamp(c.getInt("ustawienia.idle-miganie") + (e.isLeftClick() ? 5 : -5), 10, 200));
            case 16 -> c.set("ustawienia.dzwiek", !c.getBoolean("ustawienia.dzwiek"));
            case 19 -> c.set("ustawienia.ogloszenie", !c.getBoolean("ustawienia.ogloszenie"));
            case 21 -> {
                openEditor(p);
                return;
            }
            case 22 -> p.getInventory().addItem(pl.createBon(e.isShiftClick() ? 16 : 1));
            case 23 -> {
                w.setBase(p.getLocation());
                p.sendMessage(Items.c("&aKoło ustawione w tym miejscu."));
            }
            case 24 -> {
                w.clear();
                p.sendMessage(Items.c("&cKoło usunięte."));
            }
            case 25 -> {
                p.closeInventory();
                w.spin(p);
                return;
            }
            case 31 -> {
                w.load();
                pl.pan.load();
                p.sendMessage(Items.c("&aPrzeładowano."));
            }
            case 37 -> {
                pl.pan.setBase(p.getLocation());
                p.sendMessage(Items.c("&aPan Szczęściarz ustawiony."));
            }
            case 38 -> {
                pl.pan.clear();
                p.sendMessage(Items.c("&cPan Szczęściarz usunięty."));
            }
            case 39 -> {
                double v = c.getDouble("pan.rozmiar", 1.15) + (e.isLeftClick() ? 0.1 : -0.1);
                c.set("pan.rozmiar", Math.max(0.4, Math.min(2.5, Math.round(v * 10) / 10.0)));
                pl.saveConfig();
                pl.pan.build();
            }
            case 40 -> pl.pan.rotate180();
            case 42 -> {
                double v = c.getDouble("bon.cena", 10.0) + (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
                c.set("bon.cena", Math.max(0.0, Math.round(v * 100) / 100.0));
            }
            case 41 -> {
                p.closeInventory();
                pl.pan.hatToss();
                return;
            }
            default -> {
                return;
            }
        }
        pl.saveConfig();
        fillPanel(e.getInventory());
    }

    // ================= EDYTOR SLOTÓW =================

    private static int itemSlot(int i) { return (i / 6) * 27 + 1 + (i % 6); }

    /** kind: 0 = slot na przedmiot, 9 = przycisk szansy, 18 = przycisk komend. Zwraca indeks slotu koła lub -1. */
    private static int find(int slot, int kind) {
        for (int i = 0; i < Wheel.N; i++) if (itemSlot(i) + kind == slot) return i;
        return -1;
    }

    public void openEditor(Player p) {
        Inventory inv = Bukkit.createInventory(new H("editor"), 54, Items.c("&6&lKoło Fortuny &8- Edytor slotów"));
        for (int i = 0; i < 54; i++) inv.setItem(i, glass());
        for (int i = 0; i < Wheel.N; i++) {
            ItemStack it = w.slots.get(i).item;
            inv.setItem(itemSlot(i), it == null ? null : it.clone());
            inv.setItem(itemSlot(i) + 9, chanceBtn(i));
            inv.setItem(itemSlot(i) + 18, cmdBtn(i));
        }
        p.openInventory(inv);
    }

    private ItemStack chanceBtn(int i) {
        int total = 0;
        for (Wheel.Slot s : w.slots) total += Math.max(0, s.weight);
        int wt = w.slots.get(i).weight;
        String pct = total > 0 ? String.format(Locale.US, "%.1f", wt * 100.0 / total) : "0.0";
        return Items.button(Material.GOLD_NUGGET, "&eSzansa slotu #" + (i + 1),
                "&7Waga: &f" + wt, "&7Szansa: &f" + pct + "%", "&8LPM +1  |  PPM -1  |  Shift x10");
    }

    private ItemStack cmdBtn(int i) {
        List<String> lore = new ArrayList<>();
        List<String> cmds = w.slots.get(i).commands;
        if (cmds.isEmpty()) lore.add("&7(brak komend)");
        for (int k = 0; k < Math.min(6, cmds.size()); k++) lore.add("&7- &f" + cmds.get(k));
        if (cmds.size() > 6) lore.add("&7... i " + (cmds.size() - 6) + " więcej");
        lore.add("&8LPM: dodaj komendę  |  PPM: wyczyść");
        lore.add("&8%player% = nick zwycięzcy");
        return Items.button(Material.COMMAND_BLOCK, "&bKomendy slotu #" + (i + 1), lore.toArray(new String[0]));
    }

    private void syncItems(Inventory inv) {
        for (int i = 0; i < Wheel.N; i++) {
            ItemStack it = inv.getItem(itemSlot(i));
            w.slots.get(i).item = (it == null || it.getType().isAir()) ? null : it.clone();
        }
        w.saveSlots();
        w.refreshIcons();
    }

    private void editorClick(InventoryClickEvent e, Player p) {
        Inventory top = e.getView().getTopInventory();
        boolean bad = e.isShiftClick() || e.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        if (e.getClickedInventory() != top) {
            if (bad) e.setCancelled(true);
            return;
        }
        int s = e.getSlot();
        if (find(s, 0) >= 0) {                       // slot na przedmiot - zwykła obsługa
            if (bad) e.setCancelled(true);
            else Bukkit.getScheduler().runTask(pl, () -> syncItems(top));
            return;
        }
        e.setCancelled(true);
        int i = find(s, 9);
        if (i >= 0) {
            int d = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
            Wheel.Slot sl = w.slots.get(i);
            sl.weight = Math.max(0, sl.weight + d);
            w.saveSlots();
            for (int k = 0; k < Wheel.N; k++) top.setItem(itemSlot(k) + 9, chanceBtn(k));
            return;
        }
        i = find(s, 18);
        if (i >= 0) {
            if (e.isLeftClick()) {
                pending.put(p.getUniqueId(), i);
                p.closeInventory();
                p.sendMessage(Items.c("&eWpisz na czacie komendę dla slotu #" + (i + 1)
                        + " &7(bez /, &f%player%&7 = nick zwycięzcy). Wpisz &fanuluj&7, aby przerwać."));
            } else {
                w.slots.get(i).commands.clear();
                w.saveSlots();
                top.setItem(s, cmdBtn(i));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Integer i = pending.remove(e.getPlayer().getUniqueId());
        if (i == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        final String cmd = msg.startsWith("/") ? msg.substring(1) : msg;
        Bukkit.getScheduler().runTask(pl, () -> {
            Player p = e.getPlayer();
            if (!cmd.equalsIgnoreCase("anuluj") && !cmd.isEmpty()) {
                w.slots.get(i).commands.add(cmd);
                w.saveSlots();
                p.sendMessage(Items.c("&aDodano komendę do slotu #" + (i + 1) + "."));
            }
            openEditor(p);
        });
    }

    // ================= PODGLĄD DROPÓW (dla graczy) =================

    public void openDrops(Player p) {
        Inventory inv = Bukkit.createInventory(new H("drops"), 27, Items.c("&6&lKoło Fortuny &8- Dropy"));
        int total = 0;
        for (Wheel.Slot s : w.slots) total += Math.max(0, s.weight);
        for (Wheel.Slot s : w.slots) {
            if (s.weight <= 0 || total <= 0) continue;
            ItemStack it = s.item != null ? s.item.clone() : Items.button(Material.NETHER_STAR, "&dNiespodzianka");
            ItemMeta m = it.getItemMeta();
            List<Component> lore = m.hasLore() ? new ArrayList<>(m.lore()) : new ArrayList<>();
            lore.add(Items.c("&7Szansa: &e" + String.format(Locale.US, "%.1f", s.weight * 100.0 / total) + "%"));
            m.lore(lore);
            it.setItemMeta(m);
            inv.addItem(it);
        }
        p.openInventory(inv);
    }

    // ================= ZDARZENIA =================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof H h)) return;
        Player p = (Player) e.getWhoClicked();
        switch (h.type) {
            case "panel" -> panelClick(e, p);
            case "editor" -> editorClick(e, p);
            default -> e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof H) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof H h && h.type.equals("editor")) syncItems(e.getInventory());
    }
}
