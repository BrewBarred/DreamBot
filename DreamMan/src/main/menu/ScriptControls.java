package main.menu;

/**
 * Bridge the Swing menu uses to drive the running script (Patch A2): login, logout, and
 * stop-with-logout. Implemented by the script (DreamBotMan) and handed to the menu via
 * {@link DreamBotMenu#setScriptControls(ScriptControls)}, so the control-bar buttons can act
 * without the menu needing a direct dependency on the script class.
 */
public interface ScriptControls {
    void requestLogin();
    void requestLogout();
    void requestStop(boolean logout);
}
