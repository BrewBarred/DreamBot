package main.actions;

import main.components.JParamTextField;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.utilities.Logger;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Buys or sells items in an open shop (Patch B.2). Open the shop first with the building blocks
 * (Interact "Trade" on the shopkeeper, or Interact "Talk-to" + a Chat step for dialogue-driven
 * shops like tanners), then this step trades. Buying checks for Coins in the inventory first -
 * if there are none it fails fast with a clear message ("withdraw gold first" - chain a Bank
 * withdraw step before it) instead of clicking uselessly.
 *
 * <p><b>Build-safety note:</b> the client's Shop API is called through reflection (same reason
 * as FindBank - no live-javadoc access this round). Standard clients resolve it; if a method is
 * missing, the step reports it and fails cleanly rather than ever breaking your build.
 */
public class Shop extends Action {

    private JParamTextField paramMode;
    private JParamTextField paramAmount;
    private transient long lastActAt;
    private transient int done;

    public Shop() {
        super();
        paramTarget = new JParamTextField("Leather");
        paramMode = new JParamTextField("buy");
        paramAmount = new JParamTextField("1");
    }

    public Shop(Shop o) {
        this();
        paramTarget.setParam(o.paramTarget.getParam());
        paramMode.setParam(o.paramMode.getParam());
        paramAmount.setParam(o.paramAmount.getParam());
    }

    private static Object shopCall(String method, Object... args) throws Exception {
        Class<?> cls = Class.forName("org.dreambot.api.methods.container.impl.Shop");
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
            try { return m.invoke(null, args); } catch (IllegalArgumentException ignored) {}
        }
        throw new NoSuchMethodException("Shop." + method + "/" + args.length);
    }

    @Override
    public boolean execute() {
        String item = paramTarget.getParam();
        boolean buying = paramMode.getParam() == null
                || !paramMode.getParam().trim().toLowerCase().startsWith("s");
        int want = Math.max(1, ActionUtil.parseInt(paramAmount.getParam(), 1));

        if (done >= want) {
            done = 0;
            resetAttempts();
            return true;
        }

        if (buying && !Inventory.contains("Coins")) {
            // The "check for gold" rule: no coins -> say why and fail fast so the task can be
            // rebuilt with a Bank withdraw step in front, rather than spam-clicking the shop.
            Logger.log(Logger.LogType.WARN,
                    "[Shop] No Coins in inventory - withdraw gold before this step.");
            noteAttempt();
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastActAt < 1200)
            return false;
        lastActAt = now;

        try {
            Object open = shopCall("isOpen");
            if (!(open instanceof Boolean) || !(Boolean) open) {
                noteAttempt();   // shop not open: pair this with Interact "Trade" first
                return false;
            }
            Object ok = shopCall(buying ? "purchase" : "sell", item, 1);
            if (ok instanceof Boolean && (Boolean) ok) done++;
            else noteAttempt();
        } catch (Throwable t) {
            Logger.log(Logger.LogType.WARN, "[Shop] Shop API unavailable in this client: " + t);
            noteAttempt();
        }
        return false;
    }

    @Override
    public JPanel createParamPanel() {
        JPanel item = createParameterPanel("Item:",
                "The shop item to trade (shop must already be open - use Interact \"Trade\","
                        + " or Talk-to + Chat for dialogue shops like tanners).",
                paramTarget, "  e.g. \"Leather\", \"Iron ore\"");

        JPanel mode = createParameterPanel("Mode:",
                "buy or sell. Buying checks you actually have Coins first.",
                paramMode, "  e.g. \"buy\" or \"sell\"");

        JPanel amount = createParameterPanel("Amount:",
                "How many to trade, one per action tick.",
                paramAmount, "  e.g. \"5\"");

        return ActionUtil.stack(item, mode, amount);
    }

    @Override
    public String getParamTarget() { return paramTarget.getParam(); }

    @Override
    public String toBuildString() {
        return "Shop → " + paramMode.getParam() + " " + paramAmount.getParam()
                + "x " + paramTarget.getParam();
    }

    @Override
    public Action copy() { return new Shop(this); }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Target", paramTarget.getParam());
        m.put("Mode", paramMode.getParam());
        m.put("Amount", paramAmount.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Mode") != null) paramMode.setParam(data.get("Mode"));
        if (data.get("Amount") != null) paramAmount.setParam(data.get("Amount"));
    }
}
