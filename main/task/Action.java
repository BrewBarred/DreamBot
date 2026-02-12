package main.task;

import main.actions.Dig;
import main.actions.Fetch;
import main.managers.Wait;
import org.dreambot.api.methods.container.impl.bank.BankLocation;
import org.dreambot.api.methods.map.Area; // Area -> DreamBot Area
import org.dreambot.api.methods.map.Tile; // Tile -> Tile
import org.dreambot.api.wrappers.items.Item;

import java.util.function.Function;

/**
 * Defines all the {@link Action}s this bot can execute. These can be joined together to create a {@link Task} which can
 * then be submitted to {@link main.managers.TaskMan} for execution.
 * <n>
 * An action is the simplest section that can be extracted from a {@link Task}. Actions can be executed for testing via
 * the BotMenu. Actions only ever take 0 or 1 parameter and automatically called the correct function using that type.
 * <n>
 * Each enum constant knows:
 * <pre>
 * 1. What kind of target object it expects (NPC, RS2Object, Area, or none, etc.).
 * 2. How to construct the matching Task based on the action being called.
 *
 * </pre>
 */
public enum Action {
    //    ACTION(Function.class, target -> new Action((Function<main.BotMan, Boolean>) target)), // placeholder
//    MINE(RS2Object.class, target -> new Mine((RS2Object) target)),
//    WALK_TO(Area.class, target -> new WalkTo((Area) target)),
//    ATTACK(NPC.class, target -> new Attack((NPC) target)),
//    TALK_TO(NPC.class, target -> new TalkTo((NPC) target)),
    WAIT(Void.class, target -> new Wait()),
    WAIT_AT(Tile.class, target -> new Wait().at((Tile) target)), // Tile -> Tile
    WAIT_AROUND(Area.class, target -> new Wait().around((Area) target)),
    //WAIT_AROUND(Area.class, target -> new Wait((Area) target)),
    // WAIT_FOR // wait a certain number of ticks, or for an npc to be in range
    DIG(Void.class, target -> new Dig()),
    DIG_AT(Tile.class, target ->
            new Dig().at((Tile) target)
    ), // Tile -> Tile
    DIG_AROUND(Area.class, target -> new Dig().around((Area) target)),FETCH(Object[].class, target -> {
        Object[] args = (Object[]) target;
        return new Fetch((Item) args[0]).from((BankLocation) args[1]);
    });
    //SOLVE(String.class, target -> new Solve((String) target));

    ////    //    MINE(), FISH(), SOL1VE(), KILL(), CRY(), CRAFT(), WALK(), CAST(), PERFORM(), COMPLETE(), READ(), WRITE(), WAIT(),
    ////    //    TALK_TO(), SAY(), PUNISH(), REWARD(), INTERACT(), PICKPOCKET(), BUY(), SELL(), TRADE(), CUT(), FLETCH(), SOW(),
    ////    //    HIGH_ALCH(), PICKUP(), DROP(), LOOK_AT(), RUN(), RUN_FROM(), RUN_TO(), DANCE(), SPIN(), RASPBERRY(), GOBLIN_SALUTE(),
    ////    //    BURN(), USE(), SELECT(), OPEN(), CLOSE(), CHANGE_TAB(), SET(), PUSH(), PULL(), SLASH(), CHOP(), INSPECT(), PROSPECT(),
    ////    //    ASSESS(), THINK(), TAKE_NOTES(), GATHER(), COLLECT(), INTERPRET();// target

    // other ideas: EQUIP, UNQUIP, EMOTE,

    private final Class<?> targetClass;
    private final Function<Object, Task> task;

    Action(Class<?> targetClass, Function<Object, Task> task) {
        this.targetClass = targetClass;
        this.task = task;
    }

    /**
     * Factory method to create a task using the passed object.
     */
    public Task create(Object target) {
        if (target == null && targetClass == Void.class) {
            return task.apply(null);
        }
        if (!targetClass.isInstance(target)) {
            throw new IllegalArgumentException(
                    "Invalid target type for " + this + ": expected " + targetClass.getSimpleName()
            );
        }
        return task.apply(target);
    }

    public Task create() {
        if (targetClass != Void.class)
            throw new IllegalStateException(this + " requires target " + targetClass.getSimpleName());
        return task.apply(null);
    }

    ///
    ///  Getters/setters //TODO double check usage of these getters, delete if not needed
    ///
    public Class<?> getTargetClass() {
        return targetClass;
    }

    public boolean requiresTarget() {
        return targetClass != Void.class;
    }

    public boolean isTargetTypeTile() {
        return targetClass == Tile.class; // Tile -> Tile
    }

    public boolean isTargetTypeArea() {
        return targetClass == Area.class;
    }



//    // --- Sugar methods live here ---
//    public Task anyNearby(main.BotMan bot, Object... params) {
//        switch (this) {
//            case MINE:
//                RS2Object rock = bot.getObjects().closest(o -> o.hasAction("Mine"));
//                //if (rock != null) return create(rock, params);
//                throw new IllegalStateException("No minable rocks nearby.");
//
//            case ATTACK:
//                NPC npc = bot.getNpcs().closest(n -> n.hasAction("Attack"));
//                //if (npc != null) return create(npc, params);
//                throw new IllegalStateException("No attackable NPCs nearby.");
//
//            default:
//                throw new UnsupportedOperationException(this + " doesn’t support anyNearby()");
//        }
//    }


//    // --- Perform overloads ---
//    public boolean perform(main.BotMan bot, Toon toon) {
//        switch (this) {
//            case ATTACK:
//                NPC npc = toon.getNpc();
//                return npc != null && npc.interact("Attack");
//            case TALK_TO:
//                NPC talkNpc = bot.getNpcs().closest(toon.getName());
//                return talkNpc != null && bot.talkTo(toon, toon.getFastDialogue());
//            default:
//                return false;
//        }
//    }
//
//    public boolean perform(main.BotMan bot, NPC npc) {
//        return perform(bot, new Toon(npc));
//    }
//
//    public boolean perform(main.BotMan bot, Rock rock) {
//        switch (this) {
//            case MINE:
//                return bot.getObjects().closest(rock.getName()).interact("Mine");
//            default:
//                return false;
//        }
//    }
//
//    public boolean perform(main.BotMan bot, Area area) {
//    public boolean perform(main.BotMan bot, Area area) {
//        switch (this) {
//            case WALK:
//                return bot.walking.webWalk(area);
//            default:
//                return false;
//        }
//    }
//
//    public boolean perform(main.BotMan bot, String target) {
//        switch (this) {
//            case DIG:
//                return bot.getInventory().interact("Spade", "Dig");
//            case EMOTE:
//                return EmoteMan.performEmote(bot, EmoteMan.valueOf(target));
//            case EQUIP:
//                return bot.getInventory().interact(target, "Wear");
//            default:
//                return false;
//        }
//    }
}