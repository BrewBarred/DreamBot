package main.managers;

import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.Player;

//TODO consider deleting this class after inspection dreambot API, this api looks so good it might not be needed lol.
public class TileMan {
    public Player getPlayer() {
        return Players.getLocal();
    }

    public Tile getTile() {
        return getPlayer().getTile();
    }

    public int getX() {
        return  getPlayer().getX();
    }

    public int getY() {
        return getPlayer().getY();
    }

    public String getPos() {
        return getX() + ", " + getY();
    }
}
