package main.actions;

import org.dreambot.api.methods.map.Tile;

import javax.swing.*;

public abstract class Action {
    String target;

    public Action() {}

    public Action(String target) {
        this.target = target;
    }

    // Every subclass must implement these:
    public abstract boolean execute();
    public abstract String getType();
    public abstract String getTarget();
    public abstract Action copy();
    /**
     * Returns a JPanel containing all the UI controls for this action's parameters.
     * The panel is "live" — reading its fields at any time gives the current values.
     * Return null if this action has no configurable parameters beyond target.
     */
    public abstract JPanel getParameterControls();

    /**
     * Builds and returns a fully-configured copy of this action
     * using the current state of its parameter controls.
     * Called by JActionSelector when "Add to builder" is clicked.
     */
    public abstract Action buildFromControls();

    @Override
    public String toString() {
        return getType() + " → " + getTarget();
    }

    public static Tile parseStringIntoTile(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // Remove any characters that aren't digits, commas, or spaces
        // This allows for formats like "(3222, 3218)" or "3222 3218 0"
        String cleaned = input.replaceAll("[^0-9, ]", "").trim();

        // Split by comma or space
        String[] parts = cleaned.split("[, ]+");

        try {
            if (parts.length == 2) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (isValid(x, y, 0)) {
                    return new Tile(x, y, 0);
                }

            } else if (parts.length == 3) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                if (isValid(x, y, z)) {
                    return new Tile(x, y, z);
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    }

    private static boolean isValid(int x, int y, int z) {
        return x >= 0 && x <= 16383 &&
                y >= 0 && y <= 16383 &&
                z >= 0 && z <= 3;
    }
}
//package main.actions;
//
//import main.tools.Rand;
//import org.dreambot.api.methods.map.Tile;
//import org.dreambot.api.utilities.Logger;
//import org.dreambot.api.utilities.Sleep;
//
//import javax.swing.*;
//import java.util.Collections;
//import java.util.Set;
//
//public abstract class Action {
//    private int delay = -1;
//
//    protected Action() {}
//
//    public Action(int delay) {
//        this.delay = delay;
//    }
//
//    public abstract JPanel getParameterControls();
//    abstract boolean performAction();// Add to Action.java
//    public abstract boolean isComplete();
//    public abstract Action copy();
//    public abstract String getType();
//    public abstract String getTarget();
//
//    // Every action must have these
//    public int getDelay() {
//        return delay == -1 ? Rand.actionDelay() : delay;
//    }
//
//    public void setDelay(int d) {
//        delay = d;
//    }
//
//    public void resetDelay() {
//        delay = -1;
//    }
//
//    // Default logic if you want
//    public boolean execute() {
//        try {
//            Logger.log(Logger.LogType.ERROR, "Executing action...");
//
//            if (performAction())
//                Sleep.sleep(getDelay());
//
//            return true;
//        } catch (Exception e) {
//            Logger.log(Logger.LogType.ERROR, "Action failed: " + e.getMessage());
//            e.printStackTrace();
//            return false;
//        }
//    }


//
//    /**
//     * Define how "nearby targets" populates itself with items relevant to this class. Returns an empty set from the
//     * {@link Collections} util by default.
//     * <p>
//     * It is recommended to override this function in child classes and provide logic to define nearby items where
//     * possible.
//     *
//     * @return A set containing nearby items relevant to this class.
//     */
//        public static Set<String> scanTargets() {
//            ///  Define how "nearby targets" finds nearby targets for this class
//            return Collections.emptySet();
//        }
//
//    protected static Tile parseTile(String s) {
//        try {
//            String[] p = s.split(",");
//            return new Tile(
//                    Integer.parseInt(p[0].trim()),
//                    Integer.parseInt(p[1].trim()),
//                    p.length >= 3 ? Integer.parseInt(p[2].trim()) : 0);
//
//        } catch (Exception e) {
//            return null;
//        }
//    }
//}
////package main.actions;
////
////import com.google.gson.annotations.SerializedName;
////import org.dreambot.api.methods.Calculations;
////import org.dreambot.api.methods.container.impl.Inventory;
////import org.dreambot.api.methods.container.impl.bank.Bank;
////import org.dreambot.api.methods.container.impl.equipment.Equipment;
////import org.dreambot.api.methods.dialogues.Dialogues;
////import org.dreambot.api.methods.item.GroundItems;
////import org.dreambot.api.methods.interactive.GameObjects;
////import org.dreambot.api.methods.interactive.NPCs;
////import org.dreambot.api.methods.interactive.Players;
////import org.dreambot.api.methods.map.Tile;
////import org.dreambot.api.methods.skills.Skill;
////import org.dreambot.api.methods.skills.Skills;
////import org.dreambot.api.methods.walking.impl.Walking;
////import org.dreambot.api.methods.widget.Widgets;
////import org.dreambot.api.utilities.Logger;
////import org.dreambot.api.utilities.Sleep;
////import org.dreambot.api.wrappers.items.GroundItem;
////import org.dreambot.api.wrappers.interactive.GameObject;
////import org.dreambot.api.wrappers.interactive.NPC;
////import org.dreambot.api.wrappers.items.Item;
////
////import java.util.Arrays;
////
/////**
//// * Represents a single executable bot action.
//// */
////public class Action {
////
////    public enum ActionType {
////        ATTACK,
////        LOOT,
////        CHOP,
////        MINE,
////        FISH,
////        COOK,
////        USE_INV,
////        USE_ON,            // Added for your specific request
////        BANK,
////        BANK_DEPOSIT_ALL,
////        BANK_DEPOSIT,
////        BANK_WITHDRAW,
////        BANK_CLOSE,
////        DROP,
////        DROP_ALL_EXCEPT,
////        EQUIP,
////        UNEQUIP,
////        EAT,
////        DRINK,
////        TALK_TO,
////        TRADE,
////        PICKPOCKET,
////        OPEN,
////        CLOSE,
////        CLIMB,
////        SEARCH,
////        OPERATE,
////        SMELT,
////        SMITH,
////        CRAFT,
////        FLETCH,
////        PRAYER_ALTAR,
////        WALK,               // Added for your specific request
////        WALK_TO,
////        WALK_TO_NPC,
////        WALK_TO_OBJECT,
////        WAIT_MS,
////        WAIT_LEVEL,
////        WAIT_ITEM,
////        WAIT_NPC_NEARBY,
////        WAIT_ANIM_DONE,
////        WAIT_HP_PERCENT,
////        DIALOGUE_CONTINUE,
////        DIALOGUE_OPTION,
////        DIALOGUE_CHAIN,
////        BURY,
////        EXAMINE,
////        PAY_TOLL,
////        SPIN,
////        FILL_VIAL,
////        CAST_SPELL,
////        INTERACT,           // Added for consistency
////        ANIMATION_WAIT      // Added for consistency
////    }
////
////    public enum WaitCondition {
////        NONE,
////        ANIMATING,
////        NOT_ANIMATING,
////        IN_COMBAT,
////        NOT_IN_COMBAT,
////        DIALOGUE_OPEN,
////        BANK_OPEN,
////        INVENTORY_FULL,
////        INVENTORY_CHANGED,
////    }
////
////    @SerializedName("type")
////    public ActionType type;
////
////    public String target;
////    public String secondaryTarget;
////    public int[] dialogueOptions;
////    public int quantity = -1;
////    public int waitMs = 0;
////    public WaitCondition waitCondition = WaitCondition.NONE;
////
////    // -----------------------------------------------------------------------
////    // Constructors
////    // -----------------------------------------------------------------------
////
////    public Action(ActionType type, String target) {
////        this.type = type;
////        this.target = target;
////    }
////
////    public Action(ActionType type, String target, String secondaryTarget) {
////        this.type = type;
////        this.target = target;
////        this.secondaryTarget = secondaryTarget;
////    }
////
////    public Action(ActionType type, String target, int[] dialogueOptions) {
////        this.type = type;
////        this.target = target;
////        this.dialogueOptions = dialogueOptions;
////    }
////
////    public Action(ActionType type, String target, int quantity) {
////        this.type = type;
////        this.target = target;
////        this.quantity = quantity;
////    }
////
////    public Action(Action other) {
////        this.type = other.type;
////        this.target = other.target;
////        this.secondaryTarget = other.secondaryTarget;
////        this.dialogueOptions = other.dialogueOptions != null
////                ? Arrays.copyOf(other.dialogueOptions, other.dialogueOptions.length) : null;
////        this.quantity = other.quantity;
////        this.waitMs = other.waitMs;
////        this.waitCondition = other.waitCondition;
////    }
////
////    // -----------------------------------------------------------------------
////    // Execute logic
////    // -----------------------------------------------------------------------
////
////    public boolean execute() {
////        boolean result = performAction();
////        if (waitMs > 0) Sleep.sleep(waitMs);
////        if (waitCondition != null) applyWaitCondition(waitCondition);
////        return result;
////    }
////
////    private boolean performAction() {
////        switch (type) {
////            case WALK: // Handle the WALK request literally
////            case WALK_TO: {
////                Tile tile = parseTile(target);
////                if (tile == null) {
////                    Logger.log("[WALK] Invalid tile format: " + target);
////                    return false;
////                }
////                Walking.walk(tile);
////                Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(tile) < 4, 10000);
////                return true;
////            }
////
////            case USE_ON: // Handle the USE_ON request literally
////            case USE_INV: {
////                Item item = Inventory.get(target);
////                if (item == null) {
////                    Logger.log("[USE_INV] Item not in inventory: " + target);
////                    return false;
////                }
////                if (secondaryTarget == null) {
////                    Logger.log("[USE_INV] secondaryTarget is null.");
////                    return false;
////                }
////                Item invItem = Inventory.get(secondaryTarget);
////                if (invItem != null) {
////                    item.useOn(invItem);
////                    Sleep.sleep(600, 900);
////                    return true;
////                }
////                GameObject obj = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(secondaryTarget));
////                if (obj != null) {
////                    item.useOn(obj);
////                    Sleep.sleepUntil(() -> Dialogues.canContinue() || Players.getLocal().isAnimating(), 4000);
////                    return true;
////                }
////                NPC npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(secondaryTarget));
////                if (npc != null) {
////                    item.useOn(npc);
////                    Sleep.sleep(600, 900);
////                    return true;
////                }
////                Logger.log("[USE_INV] Cannot find secondary target: " + secondaryTarget);
////                return false;
////            }
////
////            // ... (Rest of your original switch cases remain identical)
////            case ATTACK: {
////                if (Players.getLocal().isInCombat()) { Sleep.sleep(400, 700); return true; }
////                NPC npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(target) && n.hasAction("Attack") && !n.isInCombat());
////                if (npc == null) return false;
////                if (npc.interact("Attack")) Sleep.sleepUntil(() -> Players.getLocal().isInCombat(), 4000);
////                return true;
////            }
////            case LOOT: {
////                GroundItem item = GroundItems.closest(target);
////                if (item == null) return false;
////                if (item.interact("Take")) Sleep.sleepUntil(() -> Inventory.contains(target), 3000);
////                return true;
////            }
////            case CHOP: {
////                if (Inventory.isFull() || Players.getLocal().isAnimating()) return true;
////                GameObject tree = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(target) && g.hasAction("Chop down"));
////                if (tree == null) return false;
////                if (tree.interact("Chop down")) Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 5000);
////                return true;
////            }
////            case MINE: {
////                if (Inventory.isFull() || Players.getLocal().isAnimating()) return true;
////                GameObject rock = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(target) && g.hasAction("Mine"));
////                if (rock == null) return false;
////                if (rock.interact("Mine")) Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 5000);
////                return true;
////            }
////            case FISH: {
////                if (Inventory.isFull() || Players.getLocal().isAnimating()) return true;
////                NPC spot = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(target));
////                if (spot == null) return false;
////                String fishAction = Arrays.stream(new String[]{"Bait", "Lure", "Net", "Cage", "Harpoon", "Use-rod", "Small net"}).filter(spot::hasAction).findFirst().orElse(null);
////                if (fishAction == null) return false;
////                if (spot.interact(fishAction)) Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 5000);
////                return true;
////            }
////            case COOK: {
////                if (Players.getLocal().isAnimating()) return true;
////                String fireTarget = secondaryTarget != null ? secondaryTarget : "Fire";
////                Item food = Inventory.get(target);
////                if (food == null) return false;
////                GameObject obj = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(fireTarget));
////                if (obj != null) { food.useOn(obj); Sleep.sleepUntil(() -> Dialogues.canContinue() || Players.getLocal().isAnimating(), 4000); if (Dialogues.canContinue()) Dialogues.spaceToContinue(); return true; }
////                return false;
////            }
////            case BANK: { if (Bank.isOpen()) return true; if (Bank.open()) Sleep.sleepUntil(Bank::isOpen, 5000); return Bank.isOpen(); }
////            case BANK_DEPOSIT_ALL: { if (!Bank.isOpen()) return false; Bank.depositAllItems(); return true; }
////            case BANK_DEPOSIT: { if (!Bank.isOpen()) return false; Bank.deposit(target, quantity == -1 ? Integer.MAX_VALUE : quantity); return true; }
////            case BANK_WITHDRAW: { if (!Bank.isOpen()) return false; if (quantity == -1) Bank.withdrawAll(target); else Bank.withdraw(target, quantity); return true; }
////            case BANK_CLOSE: { if (Bank.isOpen()) Bank.close(); return true; }
////            case DROP: { Inventory.dropAll(target); return true; }
////            case DROP_ALL_EXCEPT: { String[] keep = target.split(","); Inventory.dropAllExcept(i -> i != null && i.getName() != null && Arrays.stream(keep).anyMatch(k -> i.getName().equalsIgnoreCase(k.trim()))); return true; }
////            case EQUIP: { Item item = Inventory.get(target); if (item != null) item.interact(Arrays.stream(new String[]{"Wear", "Wield", "Equip"}).filter(item::hasAction).findFirst().orElse("Wear")); return true; }
////            case UNEQUIP: { Item item = Equipment.get(target); if (item != null) item.interact("Remove"); return true; }
////            case EAT: { Item food = Inventory.get(target); if (food != null) food.interact("Eat"); return true; }
////            case DRINK: { Item potion = Inventory.get(i -> i != null && i.getName() != null && i.getName().toLowerCase().contains(target.toLowerCase())); if (potion != null) potion.interact("Drink"); return true; }
////            case TALK_TO: { NPC npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(target) && n.hasAction("Talk-to")); if (npc != null && npc.interact("Talk-to")) { Sleep.sleepUntil(Dialogues::canContinue, 4000); if (dialogueOptions != null) followDialogueChain(dialogueOptions); } return true; }
////            case TRADE: { NPC npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(target) && n.hasAction("Trade")); if (npc != null) npc.interact("Trade"); return true; }
////            case PICKPOCKET: { NPC npc = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase(target) && n.hasAction("Pickpocket")); if (npc != null) npc.interact("Pickpocket"); return true; }
////            case OPEN: { GameObject obj = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(target) && g.hasAction("Open")); if (obj != null) obj.interact("Open"); return true; }
////            case CLOSE: { GameObject obj = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(target) && g.hasAction("Close")); if (obj != null) obj.interact("Close"); return true; }
////            case CLIMB: { GameObject obj = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(target)); if (obj != null) obj.interact(Arrays.stream(new String[]{"Climb-up", "Climb-down", "Climb-over", "Climb"}).filter(obj::hasAction).findFirst().orElse("Climb")); return true; }
////            case SEARCH: { GameObject obj = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(target) && g.hasAction("Search")); if (obj != null) obj.interact("Search"); return true; }
////            case OPERATE: { GameObject obj = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(target)); if (obj != null) obj.interact(Arrays.stream(new String[]{"Operate", "Pull", "Push", "Press", "Activate"}).filter(obj::hasAction).findFirst().orElse("Operate")); return true; }
////            case SMELT: { if (Players.getLocal().isAnimating()) return true; GameObject furnace = GameObjects.closest(g -> g != null && g.getName() != null && g.getName().equalsIgnoreCase(secondaryTarget != null ? secondaryTarget : "Furnace")); if (furnace != null && furnace.interact("Smelt")) { Sleep.sleepUntil(Dialogues::canContinue, 4000); if (Dialogues.canContinue()) Dialogues.spaceToContinue(); } return true; }
////            case SMITH: { if (Players.getLocal().isAnimating()) return true; GameObject anvil = GameObjects.closest("Anvil"); if (anvil != null && anvil.interact("Smith")) Sleep.sleepUntil(Dialogues::canContinue, 4000); return true; }
////            case FLETCH: { if (Players.getLocal().isAnimating()) return true; Item tool = Inventory.get(secondaryTarget != null ? secondaryTarget : "Knife"); Item logs = Inventory.get(target); if (tool != null && logs != null && tool.useOn(logs)) { Sleep.sleepUntil(Dialogues::canContinue, 3000); if (Dialogues.canContinue()) Dialogues.spaceToContinue(); } return true; }
////            case PRAYER_ALTAR: { GameObject altar = GameObjects.closest(target); if (altar != null) altar.interact(altar.hasAction("Pray-at") ? "Pray-at" : "Pray"); return true; }
////            case WALK_TO_NPC: { NPC npc = NPCs.closest(target); if (npc != null) Walking.walk(npc.getTile()); return true; }
////            case WALK_TO_OBJECT: { GameObject obj = GameObjects.closest(target); if (obj != null) Walking.walk(obj.getTile()); return true; }
////            case WAIT_MS: { Sleep.sleep(waitMs > 0 ? waitMs : (quantity > 0 ? quantity : 1000)); return true; }
////            case WAIT_LEVEL: { Skill s = parseSkill(target); if (s != null) Sleep.sleepUntil(() -> Skills.getRealLevel(s) >= quantity, 3600000); return true; }
////            case WAIT_ITEM: { Sleep.sleepUntil(() -> Inventory.count(target) >= quantity, 30000); return true; }
////            case WAIT_NPC_NEARBY: { Sleep.sleepUntil(() -> NPCs.closest(target) != null, 30000); return true; }
////            case WAIT_ANIM_DONE: { Sleep.sleepUntil(() -> !Players.getLocal().isAnimating(), 10000); return true; }
////            case WAIT_HP_PERCENT: { Sleep.sleepUntil(() -> (Skills.getBoostedLevel(Skill.HITPOINTS) * 100 / Skills.getRealLevel(Skill.HITPOINTS)) >= quantity, 30000); return true; }
////            case DIALOGUE_CONTINUE: { if (Dialogues.canContinue()) Dialogues.spaceToContinue(); return true; }
////            case DIALOGUE_OPTION: { if (Dialogues.canContinue()) Dialogues.chooseOption(quantity); return true; }
////            case DIALOGUE_CHAIN: { if (dialogueOptions != null) followDialogueChain(dialogueOptions); return true; }
////            case BURY: { Item b = Inventory.get(target); if (b != null) b.interact("Bury"); return true; }
////            case EXAMINE: { GameObject o = GameObjects.closest(target); if (o != null) o.interact("Examine"); return true; }
////            case SPIN: { if (Players.getLocal().isAnimating()) return true; GameObject w = GameObjects.closest(secondaryTarget != null ? secondaryTarget : "Spinning wheel"); if (w != null && w.interact("Spin")) { Sleep.sleepUntil(Dialogues::canContinue, 4000); if (Dialogues.canContinue()) Dialogues.spaceToContinue(); } return true; }
////            case FILL_VIAL: { Item v = Inventory.get(target); GameObject s = GameObjects.closest(secondaryTarget != null ? secondaryTarget : "Water source"); if (v != null && s != null) v.useOn(s); return true; }
////            case CAST_SPELL: { String[] p = target.split(":"); Widgets.get(Integer.parseInt(p[0]), Integer.parseInt(p[1])).interact("Cast"); return true; }
////
////            default:
////                Logger.log("[Action] Unimplemented action type: " + type);
////                return false;
////        }
////    }
////
////    // -----------------------------------------------------------------------
////    // Helpers
////    // -----------------------------------------------------------------------
////
////    private void followDialogueChain(int[] options) {
////        for (int option : options) {
////            Sleep.sleepUntil(Dialogues::canContinue, 5000);
////            if (!Dialogues.canContinue()) return;
////            if (option == 0) Dialogues.spaceToContinue();
////            else Dialogues.chooseOption(option);
////            Sleep.sleep(Calculations.random(350, 750));
////        }
////    }
////
////    private void applyWaitCondition(WaitCondition condition) {
////        switch (condition) {
////            case ANIMATING: Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 5000); break;
////            case NOT_ANIMATING: Sleep.sleepUntil(() -> !Players.getLocal().isAnimating(), 10000); break;
////            case IN_COMBAT: Sleep.sleepUntil(() -> Players.getLocal().isInCombat(), 6000); break;
////            case NOT_IN_COMBAT: Sleep.sleepUntil(() -> !Players.getLocal().isInCombat(), 30000); break;
////            case DIALOGUE_OPEN: Sleep.sleepUntil(Dialogues::canContinue, 5000); break;
////            case BANK_OPEN: Sleep.sleepUntil(Bank::isOpen, 5000); break;
////            case INVENTORY_FULL: Sleep.sleepUntil(Inventory::isFull, 60000); break;
////            case INVENTORY_CHANGED:
////                int before = Inventory.fullSlotCount();
////                Sleep.sleepUntil(() -> Inventory.fullSlotCount() != before, 10000);
////                break;
////            default: break;
////        }
////    }
////
////
////    private Skill parseSkill(String name) {
////        try { return Skill.valueOf(name.toUpperCase()); } catch (Exception e) { return null; }
////    }
////
////    @Override
////    public String toString() {
////        StringBuilder sb = new StringBuilder();
////        sb.append(type.name()).append(" → ").append(target);
////        if (secondaryTarget != null && !secondaryTarget.isEmpty()) sb.append(" | on: ").append(secondaryTarget);
////        if (quantity != -1) sb.append(" | qty: ").append(quantity);
////        if (waitMs > 0) sb.append(" | +").append(waitMs).append("ms");
////        if (waitCondition != WaitCondition.NONE) sb.append(" | wait: ").append(waitCondition);
////        return sb.toString();
////    }
////}