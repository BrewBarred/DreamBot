package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Walks to the nearest bank the PLAYER CAN ACTUALLY USE and opens it (Patch B.2). Resolution
 * uses DreamBot's own {@code BankLocation.getNearest()}, which accounts for membership/F2P and
 * entry requirements - exactly the "smart detect" the framework needs.
 *
 * <p><b>Build-safety note:</b> BankLocation is called through reflection because this patch was
 * produced without live-javadoc access to verify its exact signatures. If the running client
 * exposes it (standard clients do), you get full smart routing; if not, the action falls back to
 * a plain {@code Bank.open()} attempt and says so in the status, and your build is never at risk
 * from an API-name mismatch either way.
 */
public class FindBank extends Action {

    private transient Tile bankTile;
    private transient long lastStepAt;
    private transient boolean reflectionFailed;

    public FindBank() {
        super();
        paramTarget = new JParamTextField("nearest");
    }

    public FindBank(FindBank o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
    }

    /** BankLocation.getNearest() -> its Tile, via reflection. Null when unavailable. */
    private Tile resolveNearestBankTile() {
        try {
            Class<?> cls = Class.forName("org.dreambot.api.methods.container.impl.bank.BankLocation");
            Object loc = null;
            for (String m : new String[]{"getNearest", "getNearestBank"}) {
                try {
                    Method mm = cls.getMethod(m);
                    loc = mm.invoke(null);
                    if (loc != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (loc == null) return null;
            for (String m : new String[]{"getTile", "getCenter"}) {
                try {
                    Object t = loc.getClass().getMethod(m).invoke(loc);
                    if (t instanceof Tile) return (Tile) t;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable t) {
            if (!reflectionFailed)
                Logger.log(Logger.LogType.WARN,
                        "[FindBank] BankLocation unavailable in this client - falling back to Bank.open(): " + t);
            reflectionFailed = true;
        }
        return null;
    }

    @Override
    public boolean execute() {
        boolean open;
        try { open = Bank.isOpen(); } catch (Throwable t) { open = false; }
        if (open) {
            bankTile = null;
            resetAttempts();
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastStepAt < 1800)
            return false;
        lastStepAt = now;

        if (bankTile == null)
            bankTile = resolveNearestBankTile();

        try {
            if (bankTile != null) {
                double dist = org.dreambot.api.methods.interactive.Players.getLocal()
                        .getTile().distance(bankTile);
                if (dist > 6) {
                    if (!Walking.walk(bankTile))
                        noteAttempt();
                    return false;
                }
            }
            // close enough (or no smart location available): try to open
            if (!Bank.open())
                noteAttempt();
        } catch (Throwable t) {
            noteAttempt();
        }
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        return createParameterPanel("Bank:",
                "Walks to the nearest bank your account can use (F2P/requirements respected via"
                        + " the client's own bank data) and opens it. Completes when the bank is open.",
                paramTarget, "  leave as \"nearest\"");
    }

    @Override
    public String getParamTarget() { return "nearest usable"; }

    @Override
    public String toBuildString() { return "FindBank → nearest usable"; }

    @Override
    public Action copy() { return new FindBank(this); }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
    }
}
