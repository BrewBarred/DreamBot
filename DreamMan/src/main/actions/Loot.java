package main.actions;

import main.components.JParamTextField;
import main.tools.LootTracker;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.wrappers.items.GroundItem;

import javax.swing.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Picks up ground items matching a name within a radius. Patch B.2 makes looting smart:
 *
 * <ul>
 *   <li><b>Own drops first</b> - items on/next to a tile where your latched combat target just
 *       died (recorded by Interact via {@link LootTracker}) are looted before anything else.</li>
 *   <li><b>Ironman-aware</b> - if a specific item repeatedly refuses to be taken (ironman/GIM
 *       accounts can't touch other players' drops), that exact item is blacklisted for a despawn
 *       cycle and skipped, instead of the bot spamming "Take" on it forever.</li>
 *   <li><b>Latched</b> - one item is worked at a time; when it vanishes (picked up) the next is
 *       chosen. Still opportunistic: nothing lootable in range = complete, never a stall.</li>
 * </ul>
 */
public class Loot extends Action {

    private JParamTextField paramRadius;

    /** Give one item this many idle re-tries / this long before deciding it isn't ours. */
    private static final int TAKE_TRIES = 3;
    private static final long TAKE_WINDOW_MS = 5000;

    private transient GroundItem latched;
    private transient int takeTries;
    private transient long firstTryAt;
    private transient long lastTryAt;

    public Loot() {
        super();
        paramTarget = new JParamTextField("Bones");
        paramRadius = new JParamTextField("8");
    }

    public Loot(Loot o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramRadius.setParam(o.paramRadius.getParam());
    }

    private void unlatch() {
        latched = null;
        takeTries = 0;
        firstTryAt = 0;
    }

    /** Best next candidate: not blacklisted, in radius, our-drops first, then nearest. */
    private GroundItem pick(String name, int radius) {
        List<GroundItem> all = GroundItems.all(g -> {
            if (g == null || g.getName() == null || !g.getName().equalsIgnoreCase(name)) return false;
            if (!ActionUtil.within(g.getTile(), radius)) return false;
            LootTracker.observeDrop(g);   // Patch B.4: tally items near our kills for the drop table
            return !LootTracker.isBlacklisted(g);
        });
        if (all == null || all.isEmpty()) return null;
        all.sort(Comparator
                .comparingInt(LootTracker::ownershipRank)
                .thenComparingDouble(g -> {
                    try { return g.distance(); } catch (Throwable t) { return Double.MAX_VALUE; }
                }));
        return all.get(0);
    }

    @Override
    public boolean execute() {
        LootTracker.prune();
        int radius = ActionUtil.parseInt(paramRadius.getParam(), 8);
        String name = paramTarget.getParam();

        // ── work the latched item ──
        if (latched != null) {
            boolean exists;
            try { exists = latched.exists(); } catch (Throwable t) { exists = false; }
            if (!exists) {
                unlatch();                       // picked up (by us, hopefully) - next one
            } else {
                long now = System.currentTimeMillis();
                boolean giveUp = takeTries >= TAKE_TRIES
                        || (firstTryAt > 0 && now - firstTryAt > TAKE_WINDOW_MS);
                if (giveUp) {
                    // Still on the ground after several takes: almost certainly not ours
                    // (ironman/GIM restriction) - block THIS item for a despawn cycle and move
                    // on. This is a soft skip, not a failed attempt: other loot may be fine.
                    LootTracker.blacklist(latched);
                    unlatch();
                } else if (ActionUtil.isIdle() && now - lastTryAt > 1200) {
                    lastTryAt = now;
                    if (firstTryAt == 0) firstTryAt = now;
                    takeTries++;
                    try { latched.interact("Take"); } catch (Throwable ignored) {}
                }
                return false;
            }
        }

        // ── acquire the next item ──
        latched = pick(name, radius);
        if (latched == null) {
            unlatch();
            return true;    // nothing lootable in range -> done (opportunistic by design)
        }
        return false;        // take it on the next poll (latched path above)
    }

    @Override
    public JPanel createParamPanel() {
        JPanel target = createParameterPanel("Item:",
                "The ground item to pick up (your own drops are looted first).",
                paramTarget, "  e.g. \"Bones\", \"Coins\", \"Cowhide\"");

        JPanel radius = createParameterPanel("Radius:",
                "Only pick up items within this many tiles. Items that refuse to be taken"
                        + " (another player's drop on an ironman) are skipped automatically.",
                paramRadius, "  e.g. \"8\"");

        return ActionUtil.stack(target, radius);
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    @Override
    public String toBuildString() {
        return "Loot → " + paramTarget.getParam();
    }

    @Override
    public Action copy() {
        return new Loot(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        m.put("Radius", paramRadius.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Radius") != null) paramRadius.setParam(data.get("Radius"));
    }
}
