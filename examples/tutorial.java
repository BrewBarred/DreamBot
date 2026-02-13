//import org.dreambot.api.methods.Calculations;
//import org.dreambot.api.methods.container.impl.Inventory;
//import org.dreambot.api.methods.dialogues.Dialogues;
//import org.dreambot.api.methods.interactive.GameObjects;
//import org.dreambot.api.methods.interactive.NPCs;
//import org.dreambot.api.methods.interactive.Players;
//import org.dreambot.api.methods.map.Area;
//import org.dreambot.api.methods.settings.PlayerSettings;
//import org.dreambot.api.methods.tabs.Tab;
//import org.dreambot.api.methods.tabs.Tabs;
//import org.dreambot.api.methods.walking.impl.Walking;
//import org.dreambot.api.methods.widget.Widgets;
//import org.dreambot.api.script.AbstractScript;
//import org.dreambot.api.script.Category;
//import org.dreambot.api.script.ScriptManifest;
//import org.dreambot.api.wrappers.interactive.GameObject;
//import org.dreambot.api.wrappers.interactive.NPC;
//import org.dreambot.api.wrappers.widgets.WidgetChild;
//
//@ScriptManifest(category = Category.MISC, name = "Ultra-Robust Tutorial Island", author = "Gemini", version = 5.0)
//public class RobustTutIsland extends AbstractScript {
//
//    private static final int PROGRESS_VARP = 406;
//
//    // Areas for robust walking if the bot gets lost
//    private final Area SURVIVAL_AREA = new Area(3098, 3105, 3105, 3091);
//    private final Area KITCHEN_AREA = new Area(3073, 3083, 3078, 3086);
//
//    @Override
//    public int onLoop() {
//        // 1. Handle Overlays (Username, Appearance)
//        if (handleInitialOverlays()) return Calculations.random(1000, 2000);
//
//        // 2. Handle Blocking Dialogues
//        if (Dialogues.canContinue()) {
//            Dialogues.continueDialogue();
//            return Calculations.random(600, 1100);
//        }
//
//        int progress = PlayerSettings.getConfig(PROGRESS_VARP);
//
//        // 3. Main State Machine
//        switch (progress) {
//            case 0: case 1: case 2:
//                robustTalk("Gielinor Guide");
//                break;
//            case 3:
//                Tabs.open(Tab.OPTIONS);
//                break;
//            case 7: case 10:
//                walkAndTalk("Survival Expert", SURVIVAL_AREA);
//                break;
//            case 20:
//                handleFishing();
//                break;
//            case 130: case 140:
//                walkAndTalk("Master Chef", KITCHEN_AREA);
//                break;
//            case 160:
//                cookBread();
//                break;
//            case 1000:
//                log("Script Complete. Welcome to Lumbridge.");
//                stop();
//                break;
//            default:
//                // If progress is between markers, the bot will default to
//                // finding the nearest NPC or clicking through dialogues.
//                if (!Dialogues.inDialogue()) {
//                    log("Stuck? Progress: " + progress + ". Seeking nearest instructor.");
//                    Dialogues.continueDialogue();
//                }
//                break;
//        }
//
//        return Calculations.random(600, 1200);
//    }
//
//    private boolean handleInitialOverlays() {
//        // Handle Display Name
//        if (Widgets.getWidget(558) != null && Widgets.getWidget(558).isVisible()) {
//            String name = "User" + Calculations.random(1000, 9999) + "X";
//            getKeyboard().type(name, true);
//            sleep(2000);
//            WidgetChild setName = Widgets.getWidgetChild(558, 18);
//            if (setName != null) setName.interact();
//            return true;
//        }
//        // Handle Appearance
//        if (Widgets.getWidget(679) != null && Widgets.getWidget(679).isVisible()) {
//            Widgets.getWidgetChild(679, 68).interact();
//            return true;
//        }
//        return false;
//    }
//
//    private void walkAndTalk(String name, Area area) {
//        if (!area.contains(getLocalPlayer())) {
//            Walking.walk(area.getCenter());
//        } else {
//            robustTalk(name);
//        }
//    }
//
//    private void robustTalk(String name) {
//        NPC npc = NPCs.closest(name);
//        if (npc != null) {
//            if (!npc.isOnScreen()) {
//                Walking.walk(npc.getTile());
//            } else {
//                npc.interact("Talk-to");
//                sleepUntil(Dialogues::inDialogue, 4000);
//            }
//        }
//    }
//
//    private void handleFishing() {
//        if (!Inventory.contains("Raw shrimps")) {
//            NPC spot = NPCs.closest("Fishing spot");
//            if (spot != null && spot.interact("Net")) {
//                sleepUntil(() -> Inventory.contains("Raw shrimps"), 10000);
//            }
//        }
//    }
//
//    private void cookBread() {
//        GameObject range = GameObjects.closest("Range");
//        if (Inventory.contains("Bread dough") && range != null) {
//            Inventory.get("Bread dough").useOn(range);
//            sleepUntil(() -> !Inventory.contains("Bread dough"), 5000);
//        }
//    }
//}