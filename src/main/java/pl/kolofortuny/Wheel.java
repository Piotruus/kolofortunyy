package pl.kolofortuny;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Wheel {

    public static class Slot {
        public ItemStack item;                       // przedmiot (może być null)
        public int weight = 10;                      // waga szansy
        public List<String> commands = new ArrayList<>();
    }

    public static final int N = 12;
    static final double TAU = Math.PI * 2, SEG = TAU / N;
    static final double PED = 0.27;                  // wysokość podstawki jako ułamek średnicy koła

    private final KoloPlugin plugin;
    private final Random rnd = new Random();
    public final List<Slot> slots = new ArrayList<>();
    public Location base;                            // dół koła (przy ziemi)
    public Interaction hitbox;
    private ItemDisplay body;
    private final ItemDisplay[] icons = new ItemDisplay[N];
    private final List<Entity> extras = new ArrayList<>();
    private final ItemDisplay[] gems = new ItemDisplay[2];
    private BukkitTask decoTask;
    private double theta;                            // aktualny obrót (rad)
    private boolean spinning;
    private BukkitTask idleTask, spinTask;

    public Wheel(KoloPlugin plugin) { this.plugin = plugin; }

    private FileConfiguration cfg() { return plugin.getConfig(); }
    public double size() { return cfg().getDouble("ustawienia.rozmiar", 3.0); }
    public boolean isSpinning() { return spinning; }
    public boolean alive() { return body != null && body.isValid(); }
    private Location center() { return base.clone().add(0, size() * PED + size() / 2 - size() * 0.03, 0); }
    private int top() { return Math.floorMod((int) Math.round(theta / SEG), N); }

    // ---------- konfiguracja ----------

    public void load() {
        plugin.reloadConfig();
        slots.clear();
        for (int i = 0; i < N; i++) {
            Slot s = new Slot();
            String p = "sloty." + i;
            s.item = cfg().getItemStack(p + ".item");
            s.weight = cfg().getInt(p + ".szansa", 10);
            s.commands = new ArrayList<>(cfg().getStringList(p + ".komendy"));
            slots.add(s);
        }
        base = null;
        String wn = cfg().getString("kolo.swiat");
        if (wn != null && Bukkit.getWorld(wn) != null) {
            base = new Location(Bukkit.getWorld(wn), cfg().getDouble("kolo.x"), cfg().getDouble("kolo.y"),
                    cfg().getDouble("kolo.z"), (float) cfg().getDouble("kolo.yaw"), 0f);
        }
        build();
    }

    public void saveSlots() {
        for (int i = 0; i < N; i++) {
            Slot s = slots.get(i);
            String p = "sloty." + i;
            cfg().set(p + ".item", s.item);
            cfg().set(p + ".szansa", s.weight);
            cfg().set(p + ".komendy", s.commands);
        }
        plugin.saveConfig();
    }

    public void setBase(Location l) {
        base = new Location(l.getWorld(), l.getBlockX() + 0.5, l.getY(), l.getBlockZ() + 0.5,
                Math.round(l.getYaw() / 90f) * 90f, 0f);
        cfg().set("kolo.swiat", base.getWorld().getName());
        cfg().set("kolo.x", base.getX());
        cfg().set("kolo.y", base.getY());
        cfg().set("kolo.z", base.getZ());
        cfg().set("kolo.yaw", (double) base.getYaw());
        plugin.saveConfig();
        theta = 0;
        build();
    }

    public void clear() {
        remove();
        base = null;
        cfg().set("kolo", null);
        plugin.saveConfig();
    }

    // ---------- budowa ----------

    private static void setup(ItemDisplay d, ItemStack stack) {
        d.setItemStack(stack);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setPersistent(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(1.5f);
    }

    private static void setT(ItemDisplay d, Vector3f tr, Quaternionf rot, float scale, int dur) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDuration(dur);
        d.setInterpolationDelay(0);
        d.setTransformation(new Transformation(tr, rot, new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private ItemStack iconStack(int i) {
        ItemStack it = slots.get(i).item;
        if (it == null || it.getType().isAir()) return null;
        ItemStack c = it.clone();
        c.setAmount(1);
        return c;
    }

    public void build() {
        remove();
        if (base == null || base.getWorld() == null) return;
        World w = base.getWorld();
        Location c = center();
        float size = (float) size();

        body = w.spawn(c, ItemDisplay.class, d -> setup(d, Items.model("kolo")));
        for (int i = 0; i < N; i++) {
            final int k = i;
            icons[i] = w.spawn(c, ItemDisplay.class, d -> {
                setup(d, iconStack(k));
                d.setGlowColorOverride(Color.fromRGB(255, 215, 0));
            });
        }
        for (int sgn : new int[]{1, -1}) {              // wskaźnik widoczny z obu stron
            ItemDisplay pd = w.spawn(c, ItemDisplay.class, d -> setup(d, Items.model("wskaznik")));
            setT(pd, new Vector3f(0, size * 0.47f, 0.012f * sgn), new Quaternionf(), size * 0.17f, 0);
            extras.add(pd);
        }
        ItemDisplay ped = w.spawn(base, ItemDisplay.class, d -> setup(d, Items.model("podstawka")));
        float sy = size * (float) PED;
        setTv(ped, new Vector3f(0, sy * 0.5f, 0), new Quaternionf(), new Vector3f(size * 0.5f, sy, size * 0.26f), 0);
        extras.add(ped);
        for (int i = 0; i < 2; i++) {
            gems[i] = w.spawn(c, ItemDisplay.class, d -> setup(d, Items.model("krysztal")));
            extras.add(gems[i]);
        }
        TextDisplay td = w.spawn(base.clone().add(0, size * (1 + PED) + 0.35, 0), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.text(Items.c(cfg().getString("teksty.nad-kolem", "")));
            d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
        });
        extras.add(td);
        hitbox = w.spawn(base, Interaction.class, d -> {
            d.setInteractionWidth(size);
            d.setInteractionHeight((float) (size * (1 + PED)));
            d.setPersistent(false);
        });
        apply(0);
        startIdle();
        startDeco();
    }

    public void refreshIcons() {
        for (int i = 0; i < N; i++) {
            if (icons[i] != null && icons[i].isValid()) icons[i].setItemStack(iconStack(i));
        }
    }

    public void remove() {
        stopIdle();
        stopDeco();
        if (spinTask != null) spinTask.cancel();
        spinTask = null;
        spinning = false;
        if (body != null) body.remove();
        body = null;
        for (int i = 0; i < N; i++) {
            if (icons[i] != null) icons[i].remove();
            icons[i] = null;
        }
        for (Entity e : extras) e.remove();
        extras.clear();
        gems[0] = gems[1] = null;
        if (hitbox != null) hitbox.remove();
        hitbox = null;
    }

    /** Wywoływane cyklicznie - odbudowuje koło, jeśli chunk został wyładowany i załadowany ponownie. */
    public void ensure() {
        if (base == null || alive() || base.getWorld() == null) return;
        if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;
        build();
    }

    private static void setTv(ItemDisplay d, Vector3f tr, Quaternionf rot, Vector3f sc, int dur) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDuration(dur);
        d.setInterpolationDelay(0);
        d.setTransformation(new Transformation(tr, rot, sc, new Quaternionf()));
    }

    /** Dekoracje: dwa kryształy krążą i obracają się cały czas - niezależnie od tego, czy ktoś kręci kołem. */
    private void startDeco() {
        stopDeco();
        decoTask = new BukkitRunnable() {
            long n = 0;

            @Override
            public void run() {
                if (!alive()) return;
                n += 2;
                float size = (float) size();
                double yaw = Math.toRadians(base.getYaw());
                for (int i = 0; i < 2; i++) {
                    double sgn = i == 0 ? 1 : -1;
                    double bob = Math.sin(n * 0.07 + i * 1.9) * 0.05 * size;
                    double ox = sgn * 0.7 * size, oy = 0.22 * size + bob;
                    setT(gems[i], new Vector3f((float) ox, (float) oy, 0f),
                            new Quaternionf().rotationY((float) (n * 0.1 * sgn)), size * 0.2f, 3);
                    if (n % 30 == 0) {
                        Location gp = center().add(ox * Math.cos(yaw), oy, ox * Math.sin(yaw));
                        base.getWorld().spawnParticle(Particle.END_ROD, gp, 2, 0.15, 0.15, 0.15, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 6, 2);
    }

    private void stopDeco() {
        if (decoTask != null) decoTask.cancel();
        decoTask = null;
    }

    // ---------- animacja ----------

    /** Ustawia pozycje: tarcza się obraca, przedmioty krążą po okręgu, ale pozostają w pionie. */
    private void apply(int dur) {
        if (!alive()) return;
        float size = (float) size();
        setT(body, new Vector3f(), new Quaternionf().rotationZ((float) theta), size, dur);
        double r = 0.3037 * size;
        float is = size * 0.12f;
        double cos = Math.cos(theta), sin = Math.sin(theta);
        for (int i = 0; i < N; i++) {
            double a = i * SEG;
            double x0 = r * Math.sin(a), y0 = r * Math.cos(a);
            double x = x0 * cos - y0 * sin;
            double y = x0 * sin + y0 * cos;
            setT(icons[i], new Vector3f((float) x, (float) y, 0f), new Quaternionf(), is, dur);
        }
    }

    private void setGlowTop(boolean on) {
        int t = top();
        for (int i = 0; i < N; i++) {
            ItemDisplay ic = icons[i];
            if (ic == null || !ic.isValid()) continue;
            boolean g = on && i == t;
            if (ic.isGlowing() != g) ic.setGlowing(g);
        }
    }

    private void stopIdle() {
        if (idleTask != null) idleTask.cancel();
        idleTask = null;
    }

    private void startIdle() {
        stopIdle();
        idleTask = new BukkitRunnable() {
            int acc = 0;
            boolean on = true;

            @Override
            public void run() {
                if (!alive() || spinning) return;
                String mode = cfg().getString("ustawienia.idle", "GLOW").toUpperCase();
                if (mode.equals("OBROT")) {
                    double ticks = Math.max(5, cfg().getInt("ustawienia.idle-okres", 40)) * 20.0;
                    theta = (theta + TAU / ticks * 2) % TAU;
                    apply(3);
                    setGlowTop(true);
                } else if (mode.equals("GLOW")) {
                    acc += 2;
                    if (acc >= Math.max(4, cfg().getInt("ustawienia.idle-miganie", 30))) {
                        acc = 0;
                        on = !on;
                    }
                    setGlowTop(on);
                } else {
                    setGlowTop(false);
                }
            }
        }.runTaskTimer(plugin, 10, 2);
    }

    private int pick() {
        int total = 0;
        for (Slot s : slots) total += Math.max(0, s.weight);
        if (total <= 0) return -1;
        int r = rnd.nextInt(total);
        for (int i = 0; i < N; i++) {
            r -= Math.max(0, slots.get(i).weight);
            if (r < 0) return i;
        }
        return -1;
    }

    private void tickSound(double u) {
        if (!cfg().getBoolean("ustawienia.dzwiek", true)) return;
        base.getWorld().playSound(center(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.BLOCKS, 1.5f, (float) (0.8 + u * 0.9));
    }

    public boolean spin(Player p) {
        if (!alive() || spinning) return false;
        int win = pick();
        if (win < 0) {
            p.sendMessage(Items.c("&cKoło nie ma żadnego slotu z szansą większą od 0."));
            return false;
        }
        spinning = true;
        if (plugin.pan != null) plugin.pan.cheer(true);
        stopIdle();
        setGlowTop(false);
        final double target = win * SEG + (rnd.nextDouble() - 0.5) * SEG * 0.6;
        final double start = theta;
        final double total = (((target - start) % TAU) + TAU) % TAU
                + TAU * (cfg().getInt("ustawienia.obroty", 3) + rnd.nextInt(2));
        final int ticks = Math.max(40, cfg().getInt("ustawienia.czas-animacji", 8) * 20);
        spinTask = new BukkitRunnable() {
            int t = 0;
            int last = (int) Math.floor((start + SEG / 2) / SEG);

            @Override
            public void run() {
                if (!alive()) {
                    spinning = false;
                    cancel();
                    return;
                }
                t++;
                double u = Math.min(1.0, (double) t / ticks);
                theta = start + total * (1 - Math.pow(1 - u, 3));   // zwalnianie (ease-out)
                apply(2);
                int s = (int) Math.floor((theta + SEG / 2) / SEG);
                if (s != last) {
                    last = s;
                    tickSound(u);
                }
                if (t >= ticks) {
                    cancel();
                    finish(p, win);
                }
            }
        }.runTaskTimer(plugin, 1, 1);
        return true;
    }

    private String prizeName(Slot s) {
        if (s.item == null) return "niespodziankę";
        ItemMeta m = s.item.getItemMeta();
        String n = (m != null && m.hasDisplayName())
                ? PlainTextComponentSerializer.plainText().serialize(m.displayName())
                : s.item.getType().name().toLowerCase().replace('_', ' ');
        return (s.item.getAmount() > 1 ? s.item.getAmount() + "x " : "") + n;
    }

    private void finish(Player p, int win) {
        theta = theta % TAU;
        spinning = false;
        if (plugin.pan != null) { plugin.pan.cheer(false); plugin.pan.hatToss(); }
        Slot s = slots.get(win);
        World w = base.getWorld();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, center().add(0, size() / 2, 0), 80, 0.5, 0.3, 0.3, 0.4);
        w.playSound(center(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 2f, 1f);

        if (s.item != null) {
            ItemStack it = s.item.clone();
            if (p.isOnline()) {
                p.getInventory().addItem(it).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            } else {
                w.dropItemNaturally(base.clone().add(0, 1, 0), it);
            }
        }
        for (String cmd : s.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        if (cfg().getBoolean("ustawienia.ogloszenie", true)) {
            String msg = cfg().getString("teksty.wygrana", "&6{gracz} &7wygrał &e{nagroda}&7!")
                    .replace("{gracz}", p.getName()).replace("{nagroda}", prizeName(s));
            Bukkit.broadcast(Items.c(msg));
        }
        startIdle();
    }
}
