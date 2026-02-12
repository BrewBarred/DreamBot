package main.managers;

import main.BotMan;

import java.io.IOException;

/**
 * The settings manager is designed to help scripters easily adjust in-game, bot-menu and script-specific settings.
 * <n>
 * All available settings for any {@link BotMan} or {@link BotMenu} instance can be found here, keeping all setting
 * centralized for easier manipulation and management.
 */
public class SettingsMan {
    private SettingsMan settingsMan;
    ///  link to the calling bot instance
    private BotMan bot;
    private String settings;

    ///  Menu settings


    ///  Script settings


    ///  Client settings
    private boolean isDrawingOverlays = false;

    ///  Game settings (in-game settings add urgent tasks and will not be changed on script pause)
    private boolean hideRoofs = false;

    ///  Developer settings

    public SettingsMan(BotMan bot) throws IOException {
        //TODO check this singleton implementation works, just to prevent players loading multiple instances of settings
        // and confusing them
        if (settingsMan == null) {
            this.settingsMan = this;
            this.bot = bot;
            this.settings = loadSettings();
            bot.setBotStatus("Settings:\n\n\n" + settings);
        }
    }

    public String getSettingsJSON() {
        //TODO replace this with a proper JSON builder
        return settings;
    }

    public boolean isDrawingOverlays() {
        return isDrawingOverlays;
    }

    public void setDrawingOverlays(boolean draw) {
        this.isDrawingOverlays = draw;
        bot.setBotStatus("Overlays: " + (isDrawingOverlays ? "Enabled" : "Disabled"));
    }
//
//    public boolean isHideRoofs() {
//        return hideRoofs;
//    }

//    public void setHideRoofs(boolean hide) {
//        this.hideRoofs = hide;
//        // Apply immediately to the DreamBot client options
//        bot.getOptions().setHideRoofs(hide);
//        bot.setBotStatus("Hide roofs: " + (hide ? "Enabled" : "Disabled"));
//    }

//    public void saveSettings() throws IOException {
//        // TODO: convert settings into a JSON string, and then save it to the server.
//        // settings should contain the bot manifest details, the bot settings, the menu settings, and maybe some other key/value pairs later down the
//        // track.
//
//        // use player name as primary key for database storage so users don't need to create database accounts
//        String player = bot.GetPlayerName();
//        bot.log("Saving settings for: " + bot.getBot() + player);
//        String exampleSettings = "{\"attack_style\":\"aggressive\",\"food\":\"shark\"}";
//
//        bot.putServerSetting()/
//        bot.log("Saved settings for: " + player); // {"attack_style":"aggressive","food":"shark"}
//        bot.setBotStatus("Successfully saved settings!");
//    }

    /**
     * // TODO: setup local hosting later for better server control, tailored multi-user access and to remove 3rd party
     * // reliance to mitigate storage space access etc.
     *
     * Load the preferred settings for this player by fetching any existing settings from the ETA Bot server.
     */
    public String loadSettings() throws IOException {
        // fetch the settings for this player from the server
        bot.log("Fetching settings for: " + bot.getManifest().name());
        settings = bot.downloadSettings();
        settings = settings == null ? GetDefault() : settings;
        // output fetched settings as a debug log entry
        bot.log("Loaded settings:\n" + settings);
        bot.setBotStatus("Successfully loaded settings!");
        return settings;
    }

    private String GetDefault() {
        return "{Settings: Default}";
    }
}