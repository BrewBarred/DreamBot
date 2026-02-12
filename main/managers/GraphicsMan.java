package main.managers;

import main.BotMan;
import org.dreambot.api.methods.map.Tile; // Use Tile instead of Tile for DreamBot

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * GraphicsMan, responsible for drawing informative/decorative on-screen graphics.
 */
public class GraphicsMan {
    private final int START_X = 15;
    private final int START_Y = 25;
    private final int DEFAULT_PADDING = 20;
    private final int DEFAULT_LINE_SPACING = 10;
    private final Font DEFAULT_FONT_TITLE = new Font("Arial", Font.BOLD, 16);
    private final Font DEFAULT_FONT_NORMAL = new Font("Arial", Font.PLAIN, 14);
    private final Color DEFAULT_TEXT_COLOR = Color.WHITE;

    public final BotMan bot;

    public int currentX;
    public int currentY;
    private List<String> linesMain = new ArrayList<>();
    private int linesTR;
    private int linesBL;
    private int boxWidthMain;


    public GraphicsMan(BotMan bot) {
        this.bot = bot;
    }

    /**
     * Main function used for drawing overlay objects over the client screen.
     *
     * @param g The graphics object used for drawing.
     */
    public void draw(Graphics g) { // DreamBot's onPaint passes standard Graphics
        if (bot == null || !bot.isDrawing())
            return;

        Graphics2D g2d = (Graphics2D) g; // Cast to Graphics2D to maintain your hard work

        // always reset layout each frame to prevent drawing off-screen
        currentX = START_X + DEFAULT_PADDING;
        currentY = START_Y + DEFAULT_PADDING;

        // enable smoother graphics using antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // set title text properties
        g2d.setColor(this.DEFAULT_TEXT_COLOR);
        g2d.setFont(this.DEFAULT_FONT_TITLE);

        // draw bot script name and version as overlay title
        drawMainMenuText(g2d, bot.getManifest().name() + " v" + bot.getManifest().version()); // Modified to getManifest()

        // set text properties back to normal font
        g2d.setFont(this.DEFAULT_FONT_NORMAL);

        // draw current task
        drawMainMenuText(g2d,"Player status: " + bot.getPlayerStatus());
        drawMainMenuText(g2d, ("  Progress: " + bot.getTaskProgress() + "%"));
        // draw the bots status
        drawMainMenuText(g2d, "Bot status: " + bot.getBotStatus());

        // draw current position if player is not null
        if (bot.getPlayer().getTile() != null) {
            Tile pos = bot.getPlayer().getTile();
            drawMainMenuText(g2d, "Tile: x = " + pos.getX()
                    + ", y = " + pos.getY()
                    + ", z = " + pos.getZ());
        } else {
            bot.log("player is null!");
        }

        ///
        /// Script specific overlay are drawn here
        ///
        //TODO add feature to send status to BotMenu

        // any other custom overlay bits go here using drawText(...) or drawBox(...) etc.
        // e.g:
        // draw progress circle
        // drawProgressCircle(g, 20, 250, 35, progress / 100); // turn this into a completion bar?

        // update item tracker
        // Tracker.draw(g);

        ///
        /// Generic overlay (present for all bots)
        ///

        // draw translucent background around everything we've drawn (black with 50% opacity)
        g2d.setColor(new Color(0, 0, 0, 128));
        g2d.fillRoundRect(START_X, START_Y, 650, currentY, 30, 30);
    }

    public final void drawMainMenuText(Graphics2D g, String text) {
        // draw the passed string to the client screen
        g.drawString(text, currentX + DEFAULT_PADDING, currentY + DEFAULT_PADDING);
        // add this line to the top-left lines list (TL)
        linesMain.add(text);
        // move y down for next text drawing
        currentY += g.getFont().getSize() + DEFAULT_LINE_SPACING;
    }
}