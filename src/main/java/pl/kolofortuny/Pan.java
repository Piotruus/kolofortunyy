package pl.kolofortuny;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * Pan Szczęściarz - animowany ludzik złożony z 8 Display Entities (tułów, głowa, cylinder, 2 ręce, 2 nogi, moneta).
 * Wszystkie wymiary w "px" (16 px = 1 blok przy rozmiarze 1.0). Początek układu = środek stóp.
 */
public class Pan {
    private static final double NECK_Y = 22;      // oś głowy
    private static final double HAT_Y = 31;       // podstawa cylindra

    private final KoloPlugin pl;
    private final Random rnd = new Random();
    private Location base;
    private ItemDisplay torso, head, hat, armL, armR, coin;
    private Interaction hitbox;
    private BukkitTask task;
    private long t, tossStart = -1, tipStart = -1;
    private boolean cheering;
    private double yawOff, pitchOff;

    public Pan(KoloPlugin pl) { this.pl = pl; }

    private FileConfiguration cfg() { return pl.getConfig(); }
    private double S() { return cfg().getDouble("pan.rozmiar", 1.15); }
    private int flip() { return cfg().getInt("pan.obrot", 0) == 180 ? 180 : 0; }
    public boolean alive() { return torso != null && torso.isValid(); }
    public boolean isHitbox(Entity e) { return hitbox != null && hitbox.getUniqueId().equals(e.getUniqueId()); }

    // ---------- konfiguracja ----------

    public void load() {
        base = null;
        String wn = cfg().getString("pan.swiat");
        if (wn != null && Bukkit.getWorld(wn) != null) {
            base = new Location(Bukkit.getWorld(wn), cfg().getDouble("pan.x"), cfg().getDouble("pan.y"),
                    cfg().getDouble("pan.z"), (float) cfg().getDouble("pan.yaw"), 0f);
        }
        build();
    }

    public void setBase(Location l) {
        base = new Location(l.getWorld(), l.getBlockX() + 0.5, l.getY(), l.getBlockZ() + 0.5,
                Math.round(l.getYaw() / 45f) * 45f, 0f);
        cfg().set("pan.swiat", base.getWorld().getName());
        cfg().set("pan.x", base.getX());
        cfg().set("pan.y", base.getY());
        cfg().set("pan.z", base.getZ());
        cfg().set("pan.yaw", (double) base.getYaw());
        pl.saveConfig();
        build();
    }

    public void clear() {
        remove();
        base = null;
        for (String k : new String[]{"swiat", "x", "y", "z", "yaw"}) cfg().set("pan." + k, null);
        pl.saveConfig();
    }

    public void rotate180() {
        cfg().set("pan.obrot", flip() == 180 ? 0 : 180);
        pl.saveConfig();
        build();
    }

    // ---------- budowa ----------

    private ItemDisplay mk(World w, Location l, String model) {
        return w.spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(Items.model(model));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
            d.setTeleportDuration(3);
        });
    }

    public void build() {
        remove();
        if (base == null || base.getWorld() == null) return;
        World w = base.getWorld();
        double u = S() / 16.0;
        Location feet = base.clone();
        feet.setYaw(base.getYaw() + flip());
        feet.setPitch(0);
        Location neck = feet.clone().add(0, NECK_Y * u, 0);

        torso = mk(w, feet, "pan/tulow");
        armL = mk(w, feet, "pan/reka");
        armR = mk(w, feet, "pan/reka");
        coin = mk(w, feet, "pan/moneta");
        head = mk(w, neck, "pan/glowa");
        hat = mk(w, neck, "pan/cylinder");
        final float sz = (float) S();
        hitbox = w.spawn(base, Interaction.class, d -> {
            d.setInteractionWidth(sz * 1.0f);
            d.setInteractionHeight(sz * 2.9f);
            d.setPersistent(false);
        });
        yawOff = 0;
        pitchOff = 0;
        tick();
        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(pl, 5L, 2L);
    }

    public void remove() {
        if (task != null) task.cancel();
        task = null;
        for (Entity e : new Entity[]{torso, head, hat, armL, armR, coin, hitbox}) {
            if (e != null) e.remove();
        }
        torso = head = hat = armL = armR = coin = null;
        hitbox = null;
    }

    public void ensure() {
        if (base == null || alive() || base.getWorld() == null) return;
        if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;
        build();
    }

    // ---------- reakcje ----------

    public void cheer(boolean on) {
        cheering = on;
        if (on && alive()) {
            base.getWorld().playSound(wpos(0, 20), Sound.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1f, 1f);
        }
    }

    public void hatToss() {
        if (!alive()) return;
        tossStart = t;
        World w = base.getWorld();
        Location hp = wpos(0, 40);
        w.spawnParticle(Particle.FIREWORK, hp, 70, 0.6, 0.9, 0.6, 0.15);
        w.playSound(hp, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.NEUTRAL, 1f, 1.1f);
        w.playSound(hp, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.NEUTRAL, 1f, 1f);
    }

    /** Pan wita gracza: uchyla cylinder i mówi "dzień dobry". */
    public void greet() {
        if (!alive()) return;
        tipStart = t;
        base.getWorld().playSound(wpos(0, 30), Sound.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1f, 1.2f);
    }

    public void say(Player p) {
        List<String> q = cfg().getStringList("pan.teksty");
        if (q.isEmpty()) q = List.of("Szczęście sprzyja odważnym!");
        p.sendMessage(Items.c("&5&lPan Szczęściarz&8: &d" + q.get(rnd.nextInt(q.size()))));
        if (alive()) {
            tipStart = t;
            base.getWorld().playSound(wpos(0, 30), Sound.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1f, 1.2f);
        }
    }

    // ---------- animacja ----------

    /** Pozycja w świecie dla punktu (lx, ly) w px w układzie Pana (x+ = jego lewa strona). */
    private Location wpos(double lx, double ly) {
        double yaw = Math.toRadians(base.getYaw() + flip());
        double u = S() / 16.0;
        return base.clone().add(lx * u * Math.cos(yaw), ly * u, lx * u * Math.sin(yaw));
    }

    private static Quaternionf rz(double a) { return new Quaternionf().rotationZ((float) a); }

    private static double wrap(double a) { return ((a + 180) % 360 + 360) % 360 - 180; }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private void tr(ItemDisplay d, double x, double y, double z, Quaternionf q, double sc, int dur) {
        if (d == null || !d.isValid()) return;
        float u = (float) (S() / 16.0);
        float s = (float) (S() * sc);
        d.setInterpolationDuration(dur);
        d.setInterpolationDelay(0);
        d.setTransformation(new Transformation(new Vector3f((float) (x * u), (float) (y * u), (float) (z * u)),
                q, new Vector3f(s, s, s), new Quaternionf()));
    }

    private void tick() {
        if (!alive()) return;
        t += 2;
        World w = base.getWorld();

        // --- wzrok: głowa śledzi najbliższego gracza ---
        Player near = null;
        double bd = 64;
        for (Player p : w.getPlayers()) {
            double d = p.getLocation().distanceSquared(base);
            if (d < bd) { bd = d; near = p; }
        }
        double ty, tp;
        if (near != null) {
            Location from = head.getLocation();
            Vector dir = near.getEyeLocation().toVector().subtract(from.toVector());
            Location tmp = from.clone().setDirection(dir);
            ty = clamp(wrap(tmp.getYaw() - base.getYaw()), -70, 70);
            tp = clamp(tmp.getPitch(), -25, 25);
        } else {
            ty = 14 * Math.sin(t * 0.025);
            tp = 2 * Math.sin(t * 0.04);
        }
        yawOff += (ty - yawOff) * 0.35;
        pitchOff += (tp - pitchOff) * 0.35;
        int fl = flip();
        Location hl = head.getLocation();
        hl.setYaw((float) (base.getYaw() + yawOff + fl));
        hl.setPitch((float) (fl == 180 ? -pitchOff : pitchOff));
        head.teleport(hl);
        hat.teleport(hl);

        // --- stan ---
        boolean tossing = tossStart >= 0 && t - tossStart < 46;
        if (!tossing) tossStart = -1;
        boolean cheer = cheering || tossing;
        if (!cheer && tipStart < 0 && t % 300 < 2) tipStart = t;
        boolean tipping = tipStart >= 0 && t - tipStart < 36;
        if (!tipping) tipStart = -1;
        boolean waving = !cheer && near != null && bd < 36;

        // Podskakuje tylko głowa i cylinder - korpus z nogami stoi w miejscu, więc nogi nigdzie nie uciekają.
        double hop = cheer ? Math.abs(Math.sin(t * 0.3)) * 3 : 0;
        double up = hop + Math.sin(t * 0.12) * 0.35;

        // --- korpus + nogi (jeden element, zawsze w tym samym miejscu) ---
        tr(torso, 0, 16, 0, new Quaternionf(), 1, 3);

        // --- ręka lewa (x+): macha do graczy / cieszy się ---
        double aL = cheer ? 150 + 12 * Math.sin(t * 0.5)
                : waving ? 135 + 18 * Math.sin(t * 0.55)
                : 7 + 3 * Math.sin(t * 0.09);
        tr(armL, 5.5, 21, 0, rz(Math.toRadians(aL)), 1, 3);

        // --- ręka prawa (x-): podrzuca monetę ---
        double ph = (t % 36) / 36.0;
        double aR = cheer ? 150 + 12 * Math.sin(t * 0.5 + Math.PI)
                : 40 + 25 * Math.sin(Math.PI * Math.min(1.0, ph / 0.22));
        tr(armR, -5.5, 21, 0, rz(-Math.toRadians(aR)), 1, 3);

        // --- głowa i cylinder ---
        tr(head, 0, up, 0, rz(Math.toRadians(2 * Math.sin(t * 0.05))), 1, 3);
        double lift = 0, tilt = 0;
        Quaternionf hq;
        if (tossing) {
            double u = (t - tossStart) / 46.0;
            lift = 44 * 4 * u * (1 - u);
            hq = new Quaternionf().rotationZ((float) (Math.PI * 4 * u)).rotateY((float) (Math.PI * 2 * u));
        } else {
            if (tipping) {
                double u = (t - tipStart) / 36.0;
                lift = 4 * Math.sin(Math.PI * u);
                tilt = Math.toRadians(22) * Math.sin(Math.PI * u);
            }
            hq = rz(tilt);
        }
        tr(hat, 0, (HAT_Y - NECK_Y) + up + lift, 0, hq, 1, 3);

        // --- moneta ---
        double rest = Math.toRadians(40);
        double hx = -5.5 - 11 * Math.sin(rest) - 2.5;
        double hy = 21 - 11 * Math.cos(rest);
        if (cheer) {
            tr(coin, 0, 52 + up + 2 * Math.sin(t * 0.4), 0, new Quaternionf().rotationY((float) (t * 0.45)), 7.0 / 16, 3);
            if (t % 8 < 2) w.spawnParticle(Particle.HAPPY_VILLAGER, wpos(0, 50 + up), 5, 0.4, 0.3, 0.4, 0);
        } else if (ph < 0.14) {
            double cx = -5.5 - 11 * Math.sin(Math.toRadians(aR)) - 2.5;
            double cy = 21 - 11 * Math.cos(Math.toRadians(aR)) + 2.5;
            tr(coin, cx, cy, 0, new Quaternionf(), 5.0 / 16, 3);
        } else {
            double f = (ph - 0.14) / 0.86;
            tr(coin, hx, hy + 2.5 + 26 * 4 * f * (1 - f), 0,
                    new Quaternionf().rotationX((float) (Math.PI * 6 * f)), 5.0 / 16, 3);
        }
        if (!cheer && t > 40 && t % 36 < 2) {                      // złapanie monety
            Location hp = wpos(hx, hy + 3);
            w.spawnParticle(Particle.END_ROD, hp, 6, 0.1, 0.1, 0.1, 0.02);
            w.playSound(hp, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 0.7f, 0.9f + rnd.nextFloat() * 0.6f);
        }
    }
}
