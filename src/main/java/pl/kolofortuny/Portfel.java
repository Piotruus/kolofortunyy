package pl.kolofortuny;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Połączenie z pluginem 777-Portfel (waluta VPLN). Używa refleksji, więc nie trzeba mieć jego jara przy budowaniu.
 * Korzysta z tych samych metod, których używa sklep 777-Portfel: hasBalance, removeBalanceWithoutTransaction, addToTotalSpent, getBalance.
 */
public class Portfel {
    private final KoloPlugin pl;
    private Object wm;
    private Method has, take, spent, bal;

    public Portfel(KoloPlugin pl) { this.pl = pl; }

    public boolean available() {
        if (wm != null) return true;
        Plugin p = Bukkit.getPluginManager().getPlugin("777-Portfel");
        if (p == null || !p.isEnabled()) return false;
        try {
            wm = p.getClass().getMethod("getWalletManager").invoke(p);
            Class<?> c = wm.getClass();
            has = c.getMethod("hasBalance", Player.class, double.class);
            take = c.getMethod("removeBalanceWithoutTransaction", Player.class, double.class);
            spent = c.getMethod("addToTotalSpent", UUID.class, double.class);
            bal = c.getMethod("getBalance", UUID.class);
            return true;
        } catch (Exception e) {
            pl.getLogger().warning("Nie udało się połączyć z 777-Portfel: " + e);
            wm = null;
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Object> call(Method m, Object... args) {
        try {
            return (CompletableFuture<Object>) m.invoke(wm, args);
        } catch (Exception e) {
            CompletableFuture<Object> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
    }

    /** Czy gracz ma co najmniej tyle VPLN. */
    public CompletableFuture<Boolean> has(Player p, double amount) {
        return call(has, p, amount).thenApply(o -> o instanceof Boolean b && b);
    }

    /** Zabiera VPLN (tak samo jak zakup w sklepie 777-Portfel). Zwraca false, jeśli się nie udało. */
    public CompletableFuture<Boolean> take(Player p, double amount) {
        return call(take, p, amount).thenApply(o -> !(o instanceof Boolean b) || b);
    }

    public void addSpent(UUID id, double amount) {
        call(spent, id, amount);
    }

    public CompletableFuture<Double> balance(UUID id) {
        return call(bal, id).thenApply(o -> o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o)));
    }
}
