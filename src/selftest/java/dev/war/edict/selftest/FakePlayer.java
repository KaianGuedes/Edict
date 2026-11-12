package dev.war.edict.selftest;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A Player stand-in (a dynamic proxy) that records what the plugin does to it. There is no real client in a headless run. */
final class FakePlayer implements InvocationHandler {

    final UUID id = UUID.randomUUID();
    final String name;
    String locale = "en_us";
    boolean admin = true;
    Location location;
    Vector velocity = new Vector(0, -3, 0);
    float fallDistance = 99f;
    final List<String> messages = new ArrayList<>();
    final List<Location> teleports = new ArrayList<>();
    final List<Inventory> opened = new ArrayList<>();
    final List<Vector> velocitySets = new ArrayList<>();
    int closes;
    final Player player;

    FakePlayer(String name, Location location) {
        this.name = name;
        this.location = location;
        this.player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, this);
    }

    Inventory lastInventory() {
        return opened.isEmpty() ? null : opened.get(opened.size() - 1);
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] a) {
        switch (m.getName()) {
            case "equals":
                return proxy == a[0];
            case "hashCode":
                return id.hashCode();
            case "toString":
                return "FakePlayer[" + name + "]";
            case "getName":
            case "getDisplayName":
                return name;
            case "getUniqueId":
                return id;
            case "getWorld":
                return location.getWorld();
            case "getLocation":
                return location.clone();
            case "getLocale":
                return locale;
            case "hasPermission":
            case "isOp":
                return admin;
            case "isOnline":
            case "isValid":
                return true;
            case "isOnGround":
                return false;
            case "getVelocity":
                return velocity.clone();
            case "setVelocity":
                velocitySets.add(((Vector) a[0]).clone());
                velocity = ((Vector) a[0]).clone();
                return null;
            case "setFallDistance":
                fallDistance = (Float) a[0];
                return null;
            case "getFallDistance":
                return fallDistance;
            case "teleport":
                teleports.add(((Location) a[0]).clone());
                location = ((Location) a[0]).clone();
                return true;
            case "teleportAsync":
                teleports.add(((Location) a[0]).clone());
                location = ((Location) a[0]).clone();
                return CompletableFuture.completedFuture(true);
            case "openInventory":
                if (a[0] instanceof Inventory inv) opened.add(inv);
                return null;
            case "closeInventory":
                closes++;
                return null;
            case "sendMessage":
                if (a != null && a.length > 0) {
                    if (a[0] instanceof String s) messages.add(s);
                    else if (a[0] instanceof String[] arr) for (String s : arr) messages.add(s);
                    else if (a.length > 1 && a[1] instanceof String s) messages.add(s);
                }
                return null;
            default:
                break;
        }
        Class<?> r = m.getReturnType();
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == float.class) return 0f;
        if (r == double.class) return 0d;
        if (r == short.class) return (short) 0;
        if (r == byte.class) return (byte) 0;
        if (r == char.class) return (char) 0;
        return null;
    }

    World world() {
        return location.getWorld();
    }
}
