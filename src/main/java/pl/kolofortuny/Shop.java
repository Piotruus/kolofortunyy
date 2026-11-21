package pl.kolofortuny;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/** GUI Pana Szczęściarza: kupno bonu za VPLN z pluginu 777-Portfel. */
public class Shop implements Listener {

    static class SH implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final int BON_SLOT = 13;
    private static final List<String> DEFAULT_LORE = List.of(
            "",
            "&8» &7BILET NA JEDEN OBRÓT &6KOŁA FORTUNY",
            "&8» &7KLIKNIJ &f&lPPM &7NA KOŁO, TRZYMAJĄC BON W DŁONI",
            "",
            "&8» &7CENA: &e{CENA} VPLN",
            "&8» &7TWOJE SALDO: &a{SALDO} VPLN",
            "",
            "&a▶ &aKLIKNIJ, ABY KUPIĆ BON");

    private final KoloPlugin pl;
    private final Set<UUID> buying = new HashSet<>();

    public Shop(KoloPlugin pl) { this.pl = pl; }

    private FileConfiguration cfg() { return pl.getConfig(); }
    private double price() { return cfg().getDouble("bon.cena", 10.0); }
    private static String money(double v) { return String.format(Locale.US, "%.2f", v); }
    private void sync(Runnable r) { Bukkit.getScheduler().runTask(pl, r); }

    private ItemStack bonIcon(String saldo) {
        ItemStack it = pl.createBon(1);
        ItemMeta m = it.getItemMeta();
        m.displayName(Items.c(cfg().getString("sklep.nazwa", "&e&lBON DO KOŁA FORTUNY")));
        List<String> raw = cfg().getStringList("sklep.opis");
        if (raw.isEmpty()) raw = DEFAULT_LORE;
        m.lore(raw.stream()
                .map(s -> s.replace("{CENA}", money(price())).replace("{SALDO}", saldo))
                .map(Items::c).collect(Collectors.toList()));
        it.setItemMeta(m);
        return it;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new SH(), 27, Items.c(cfg().getString("sklep.tytul", "&5&lPan Szczęściarz &8- &6Bon")));
        ItemStack dark = Items.button(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack violet = Items.button(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) ? violet : dark);
        inv.setItem(11, Items.button(Material.EMERALD, "&a&l☘ SZCZĘŚCIE", "&7Kup bon i zakręć kołem fortuny!"));
        inv.setItem(15, Items.button(Material.GOLD_INGOT, "&6&l$ NAGRODY", "&7Shift + PPM na kole", "&7pokazuje listę nagród."));
        inv.setItem(BON_SLOT, bonIcon("..."));
        p.openInventory(inv);
        if (pl.pan != null) pl.pan.greet();
        refresh(p, inv);
    }

    /** Wczytuje saldo z 777-Portfel i odświeża opis bonu. */
    private void refresh(Player p, Inventory inv) {
        if (!pl.portfel.available()) {
            inv.setItem(BON_SLOT, bonIcon("&cBRAK PLUGINU"));
            return;
        }
        pl.portfel.balance(p.getUniqueId()).whenComplete((b, err) -> sync(() -> {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof SH)) return;
            inv.setItem(BON_SLOT, bonIcon(err != null || b == null ? "?" : money(b)));
        }));
    }

    private void msg(Player p, String path, String def, String extra) {
        p.sendMessage(Items.c(cfg().getString(path, def)
                .replace("{cena}", money(price())).replace("{ilosc}", "1").replace("{saldo}", extra)));
    }

    private void buy(Player p) {
        if (!pl.portfel.available()) {
            msg(p, "sklep.brak-portfela", "&8» &cPlugin 777-Portfel nie jest zainstalowany!", "");
            return;
        }
        if (!buying.add(p.getUniqueId())) return;
        final double price = price();
        pl.portfel.has(p, price).whenComplete((ok, err) -> sync(() -> {
            if (err != null || ok == null || !ok) {
                buying.remove(p.getUniqueId());
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                msg(p, "sklep.brak-srodkow", "&8» &cNie masz wystarczająco VPLN! Cena bonu: &4{cena} VPLN&c.", "");
                return;
            }
            pl.portfel.take(p, price).whenComplete((done, err2) -> sync(() -> {
                buying.remove(p.getUniqueId());
                if (err2 != null || done == null || !done) {
                    msg(p, "sklep.blad", "&8» &cNie udało się pobrać VPLN. Spróbuj ponownie.", "");
                    return;
                }
                pl.portfel.addSpent(p.getUniqueId(), price);
                p.getInventory().addItem(pl.createBon(1)).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                msg(p, "sklep.zakup-ok", "&8» &aKupiłeś &eBON DO KOŁA FORTUNY &aza &2{cena} ᴠᴘʟɴ&a!", "");
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof SH) refresh(p, p.getOpenInventory().getTopInventory());
            }));
        }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof SH)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getSlot() == BON_SLOT && e.getWhoClicked() instanceof Player p) buy(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof SH) e.setCancelled(true);
    }
}
