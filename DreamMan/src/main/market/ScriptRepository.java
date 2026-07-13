package main.market;

import java.util.List;

/**
 * Where market scripts live (Patch B.11). Two implementations ship:
 *
 * <ul>
 *   <li>{@link FolderRepository} - a directory. Point it at a Dropbox/Drive/network folder and a
 *       group of friends has a working shared market today, with no server at all.</li>
 *   <li>{@link HttpRepository} - a REST server, for when there's one to point at. The endpoint
 *       contract is documented on that class.</li>
 * </ul>
 *
 * The UI talks only to this interface, so switching between them changes nothing else.
 */
public interface ScriptRepository {

    /** Human-readable name of where we're connected (shown in the UI). */
    String describe();

    /** Everything published, newest first. Never throws - returns empty on failure. */
    List<ScriptListing> list();

    /** Publishes (or replaces, by id) a listing. @throws Exception with a readable reason. */
    void publish(ScriptListing listing) throws Exception;

    /** Records this install's rating (1-5) for a script. */
    void rate(String scriptId, int stars) throws Exception;

    /** Notes that this install downloaded a script (drives the download count). */
    void noteDownload(String scriptId);

    /** Removes a listing - only meaningful where the caller is allowed to. */
    void remove(String scriptId) throws Exception;
}
