//package main.unsorted;
//
//// FILTER THIS CLASS AND STEAL USEFUL ATTRIBUTES AND COMMENTS
//
//import locations.Spot;
//import locations.clueLocations.LocationFinder;
//// Requirement is removed as it is an OSBot-specific domain requirement
//import org.dreambot.api.methods.map.Area; // OSBot Area -> DreamBot Area
//import org.dreambot.api.methods.map.Tile; // Tile -> Tile
//import utils.Toon;
//
///**
// * Represents a clue step that the bot can solve.
// * <p>
// * A {@code Clue} is a specialized type of {@link Spot} that has all the standard location properties
// * (name, {@link Area}, and travel methods), but also includes additional attributes that are unique to clue steps,
// * such as a textual hint and an associated map ID.
// * <p>
// * Subclasses of {@code Clue} can be created to model different types of
// * clue scroll challenges (e.g., Hot-and-Cold, Charlie the Tramp, map clues).
// * This allows script logic to treat all clues uniformly while still enabling
// * task-specific customization where required.
// * <p>
// *
// * Supervised training usage:
// * <pre>{@code
// * Clue clue = new HotAndColdClue("Varrock Mine", new Area(...),
// * "Dig near the iron rocks.", 101);
// * clue.walkTo(bot);               // inherited from Location
// * system.out.println(clue.getHint());  // clue-specific
// * }</pre>
// * <p>
// *
// * Semi-supervised training usage:
// * <b>COMING SOON!</b> This feature will essentially copy the supervised training step, except it will automatically
// * generate an unknown clue scroll on completion, or creates a new potential task to do based on the players current
// * location and status after receiving something the bot perceives as a "reward", such as a casket, money, or xp.
// */
//public abstract class ClueSpot extends Spot implements LocationFinder {
//    ///
//    ///     ~ Clue interface ~
//    ///
//
//    /**
//     * The widget id of the map associated with this clue location.
//     */
//    int mapId;
//    /**
//     * The hint associated with this clue location.
//     */
//    String hint;
//    /**
//     * The task required to complete this scroll (later upgrading this to be a Task object for easier completion)
//     */
//    String task;
//    /**
//     *
//     */
//    Tile digTile; // Tile -> Tile
//    /**
//     * The NPC you would usually speak with to complete a clue step
//     */
//    Toon npc;
//    /**
//     * An optional list of basic requirements for this clue location.
//     * <p>
//     * Althought this function may save you from killing dragons at level 3, this logic is not yet complex enough to
//     * determine sub-requirements, such as whether you have the skill level to create the resources you need to produce
//     * an item required for a given clue step.
//     */
//    Object[] requirements; // Requirement[] replaced with generic Object[] until DreamBot equivalent is defined
//}