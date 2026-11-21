package pl.kolofortuny;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class KoloPlugin extends JavaPlugin implements Listener, TabExecutor {

    Wheel wheel;
    Pan pan;
    Portfel portfel;
    Shop shop;
    Panel panel;
    NamespacedKey bonKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bonKey = new NamespacedKey(this, "bon");
        wheel = new Wheel(this);
        pan = new Pan(this);
        portfel = new Portfel(this);
        shop = new Shop(this);
        panel = new Panel(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(panel, this);
        Bukkit.getPluginManager().registerEvents(shop, this);
        getCommand("kolo").setExecutor(this);
        getCommand("kolo").setTabCompleter(this);
        Bukkit.getScheduler().runTask(this, () -> { wheel.load(); pan.load(); });               // po załadowaniu światów
        Bukkit.getScheduler().runTaskTimer(this, () -> { wheel.ensure(); pan.ensure(); }, 200L, 100L);
    }

    @Override
    public void onDisable() {
        if (wheel != null) wheel.remove();
        if (pan != null) pan.remove();
    }

    public ItemStack createBon(int amount) {
        ItemStack it = Items.model("bon");
        it.setAmount(amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Items.c(getConfig().getString("teksty.bon-nazwa", "&6Bon")));
        m.lore(getConfig().getStringList("teksty.bon-opis").stream().map(Items::c).toList());
        m.getPersistentDataContainer().set(bonKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isBon(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(bonKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onUse(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (pan.isHitbox(e.getRightClicked())) {
            e.setCancelled(true);
            if (e.getPlayer().isSneaking()) panel.openDrops(e.getPlayer());
            else shop.open(e.getPlayer());
            return;
        }
        if (wheel.hitbox == null || !e.getRightClicked().getUniqueId().equals(wheel.hitbox.getUniqueId())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.isSneaking()) {
            panel.openDrops(p);
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isBon(hand)) {
            p.sendMessage(Items.c(getConfig().getString("teksty.brak-bonu", "&cPotrzebujesz bonu!")));
            return;
        }
        if (wheel.isSpinning()) {
            p.sendMessage(Items.c(getConfig().getString("teksty.kreci-sie", "&cKoło już się kręci!")));
            return;
        }
        if (wheel.spin(p)) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("kolo.admin")) {
            s.sendMessage(Items.c("&cBrak uprawnień."));
            return true;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("panel")) {
            if (s instanceof Player p) panel.openPanel(p);
            else s.sendMessage("Tej komendy możesz użyć tylko w grze.");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "edytor" -> {
                if (s instanceof Player p) panel.openEditor(p);
            }
            case "bon" -> {
                if (a.length >= 3 && a[1].equalsIgnoreCase("daj")) {
                    Player t = Bukkit.getPlayerExact(a[2]);
                    if (t == null) {
                        s.sendMessage(Items.c("&cGracz offline."));
                        return true;
                    }
                    int n = 1;
                    if (a.length > 3) {
                        try {
                            n = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
                        } catch (NumberFormatException ignored) {}
                    }
                    t.getInventory().addItem(createBon(n)).values()
                            .forEach(i -> t.getWorld().dropItemNaturally(t.getLocation(), i));
                    s.sendMessage(Items.c("&aDano " + n + " bon(ów) graczowi " + t.getName() + "."));
                } else {
                    s.sendMessage(Items.c("&eUżycie: /kolo bon daj <gracz> [ilość]"));
                }
            }
            case "ustaw" -> {
                if (s instanceof Player p) {
                    wheel.setBase(p.getLocation());
                    s.sendMessage(Items.c("&aKoło ustawione."));
                }
            }
            case "usun" -> {
                wheel.clear();
                s.sendMessage(Items.c("&cKoło usunięte."));
            }
            case "zakrec" -> {
                if (s instanceof Player p) wheel.spin(p);
            }
            case "pan" -> {
                if (!(s instanceof Player p)) return true;
                String sub = a.length > 1 ? a[1].toLowerCase() : "";
                switch (sub) {
                    case "ustaw" -> {
                        pan.setBase(p.getLocation());
                        s.sendMessage(Items.c("&aPan Szczęściarz ustawiony."));
                    }
                    case "usun" -> {
                        pan.clear();
                        s.sendMessage(Items.c("&cPan Szczęściarz usunięty."));
                    }
                    case "obroc" -> pan.rotate180();
                    case "rzut" -> pan.hatToss();
                    default -> s.sendMessage(Items.c("&eUżycie: /kolo pan [ustaw|usun|obroc|rzut]"));
                }
            }
            case "reload" -> {
                pan.load();
                wheel.load();
                s.sendMessage(Items.c("&aPrzeładowano."));
            }
            default -> s.sendMessage(Items.c("&eUżycie: /kolo [panel|edytor|bon daj <gracz> [ilość]|ustaw|usun|zakrec|pan|reload]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("kolo.admin")) return List.of();
        if (a.length == 1) return List.of("panel", "edytor", "bon", "ustaw", "usun", "zakrec", "pan", "reload").stream()
                .filter(x -> x.startsWith(a[0].toLowerCase())).toList();
        if (a.length == 2 && a[0].equalsIgnoreCase("bon")) return List.of("daj");
        if (a.length == 3 && a[0].equalsIgnoreCase("bon"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(a[2].toLowerCase())).toList();
        return List.of();
    }
}
