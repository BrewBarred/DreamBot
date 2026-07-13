package main.actions;

import main.components.JParamTextField;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.menu.MenuHandler.createParameterPanel;

/**
 * Banking action. Opens the nearest bank if needed, then performs a mode:
 * <ul>
 *   <li><b>deposit-all</b> - deposit the entire inventory</li>
 *   <li><b>deposit-except</b> - deposit everything except the named items</li>
 *   <li><b>withdraw</b> - withdraw the named item (Amount, 0 = all)</li>
 * </ul>
 * The DreamBot {@code Bank} type is referenced by its fully-qualified name so this action class
 * can itself be called "Bank" (the registry key must match the class's simple name).
 */
public class Bank extends Action {

    private JParamTextField paramMode;
    private JParamTextField paramAmount;

    public Bank() {
        super();
        // Patch B.9: banking legitimately spans many polls (walk, open, PIN, then the actual
        // deposit/withdraw), so it gets a bigger try budget than the default - but still a FINITE
        // one, so a wrong PIN or an unreachable bank fails the task instead of hanging forever.
        maxAttempts = 40;
        paramMode = new JParamTextField("deposit-all");
        paramTarget = new JParamTextField("");           // item name(s) for except/withdraw
        paramAmount = new JParamTextField("0");
    }

    public Bank(Bank o) {
        this();
        paramMode.setParam(o.paramMode.getParam());
        paramTarget.setParam(o.paramTarget.getParam());
        paramAmount.setParam(o.paramAmount.getParam());
    }

    @Override
    public boolean execute() {
        // Make sure the bank is open first; opening spans a few loops, so retry until it is.
        if (!org.dreambot.api.methods.container.impl.bank.Bank.isOpen()) {

            // Patch B.9: the classic "stuck opening the bank" is the PIN screen - open() lands,
            // the PIN dialog blocks it, and we'd otherwise call open() forever. Deal with the PIN
            // first; while it's up we WAIT rather than spamming open().
            if (main.tools.BankPin.handle()) {
                noteAttempt();   // counts toward the try budget so a bad PIN can't hang the task
                return false;
            }

            org.dreambot.api.methods.container.impl.bank.Bank.open();
            noteAttempt();       // Patch B.9: a bank that never opens now gives up instead of hanging
            return false;
        }

        String mode = paramMode.getParam() == null ? "" : paramMode.getParam().trim().toLowerCase();
        String[] items = ActionUtil.names(paramTarget.getParam());
        int amount = ActionUtil.parseInt(paramAmount.getParam(), 0);

        switch (mode) {
            case "deposit-except":
            case "deposit-all-except":
                org.dreambot.api.methods.container.impl.bank.Bank.depositAllExcept(items);
                break;
            case "withdraw":
                if (items.length > 0)
                    org.dreambot.api.methods.container.impl.bank.Bank.withdraw(items[0], amount <= 0 ? Integer.MAX_VALUE : amount);
                break;
            case "deposit-all":
            default:
                org.dreambot.api.methods.container.impl.bank.Bank.depositAllItems();
                break;
        }
        return true;
    }

    @Override
    public JPanel createParamPanel() {
        JPanel mode = createParameterPanel("Mode:",
                "What to do at the bank.",
                paramMode, "  \"deposit-all\", \"deposit-except\", or \"withdraw\"");

        JPanel target = createParameterPanel("Item(s):",
                "Item name(s) for deposit-except / withdraw. Comma-separated.",
                paramTarget, "  e.g. \"Rune axe\"  or  \"Coins, Games necklace\"");

        JPanel amount = createParameterPanel("Amount:",
                "Withdraw amount (0 = all). Ignored for deposit modes.",
                paramAmount, "  e.g. \"28\"  or  \"0\"");

        return ActionUtil.stack(mode, target, amount);
    }

    @Override
    public String getParamTarget() {
        return paramTarget.getParam();
    }

    @Override
    public String toBuildString() {
        String mode = paramMode.getParam();
        String item = paramTarget.getParam();
        return "Bank → " + mode + (item == null || item.isEmpty() ? "" : " " + item);
    }

    @Override public String conflictGroup() { return "inventory"; }

    public Action copy() {
        return new Bank(this);
    }

    @Override
    public Map<String, String> serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Mode", paramMode.getParam());
        m.put("Target", paramTarget.getParam());
        m.put("Amount", paramAmount.getParam());
        return m;
    }

    @Override
    public void deserialize(Map<String, String> data) {
        if (data == null) return;
        if (data.get("Mode") != null) paramMode.setParam(data.get("Mode"));
        if (data.get("Target") != null) paramTarget.setParam(data.get("Target"));
        if (data.get("Amount") != null) paramAmount.setParam(data.get("Amount"));
    }
}
