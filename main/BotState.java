package main;

public enum BotState {
    IDLE("Idle"),
    RUNNING("Running"),
    PAUSED("Paused"),
    BANKING("Banking"),
    WALKING("Walking"),
    COMBAT("Combat"),
    GATHERING("Gathering"),
    TRAINING("Training"),
    THINKING("Thinking");

    private final String name;

    BotState(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
