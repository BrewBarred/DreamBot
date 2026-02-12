package main.managers;

import main.BotMan;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * The Logger class handles logging messages for all classes including general status updates, error-handling and debug
 * information.
 */
public class LogMan {
    ///  Public variables

    ///  Private static variables

    /**
     * Buffer limit for the {@link BotMenu}'s log console.
     * <p>
     * This value represents the maximum number of lines to be displayed in the console at once.
     */
    private static final int LOG_BUFFER = 214700;
    private String multiLineHeader = "=> ";
    private String multiLinePrefix = "\n\t\t\t";

    ///  Private variables

    protected final BotMan bot;
    /**
     * Enum to define the different types of log messages
     */
    public enum LogSource { ALL, PLAYER, BOT, GET, PATCH, POST, DEBUG }
    /**
     * List to store all logging messages for printing to the {@link BotMenu} console.
     */
    private final List<LogEntry> logList = new CopyOnWriteArrayList<>();

    ///  BotMenu settings

    /**
     * True if the {@link BotMenu} is currently set to pause printing to the log console, else false if printing.
     */
    private boolean logPaused = false;

    ///  Menu components

    /**
     * The combo-box used to filter by the {@link LogSource Logsource enum} values.
     */
    private JComboBox<LogSource> cbSource;
    /**
     * The text-box used to filter the logging console
     */
    private JTextField tbSearch;
    /**
     * The check-box used to enable/disable case-sensitive filtering.
     */
    private JCheckBox chkCaseSensitive;
    /**
     * The button used to toggle whether the logging console should track in-game logs or not.
     */
    private JToggleButton btnToggleExecution;
    /**
     * The {@link JTextPane pane} used to display the {@link StyledDocument styled logging document}.
     */
    private JTextPane logPane;
    /**
     * The {@link StyledDocument styled logging document} used to display all log messages to the
     * {@link BotMenu log console}.
     */
    private StyledDocument logDoc;

    /// Nested class: Log entry class defines what can be logged to the bot menu's log console and holds necessary data.

    /**
     * A static class which contains all the data required to print output to the {@link BotMenu}'s log console.
     */
    public static class LogEntry {
        /**
         * The timestamp at which this entry was created.
         */
        private final long timeMillis;
        /**
         * The source of this logging message, used to filter the log console between different out types. Possible
         * values include {@link LogSource#PLAYER}, {@link LogSource#BOT} and {@link LogSource#DEBUG}.
         */
        private final LogSource source;
        /**
         * A brief description of the reason for this entry being logged.
         */
        private final String message;

        /**
         * Constructs a new log entry using the passed log source and logging message.
         * @param source The source type of this output message i.e., {@link LogSource#PLAYER}, {@link LogSource#BOT}
         * {@link LogSource#DEBUG}.
         * @param message The output message being printed to the {@link BotMenu}'s log console.
         */
        LogEntry(LogSource source, String message) {
            if (source == null || message == null)
                throw new RuntimeException("Error creating log entry due to null parameters! Source: " + source + ", message: " + message);

            this.timeMillis = System.currentTimeMillis();
            this.source = source;
            this.message = message;
        }

        @Override
        public String toString() {
            String caller = "";
            if (source == LogSource.DEBUG) {
                caller = BotMan.GetCaller();
                if (caller == null)
                    caller = "";

                if (!caller.isEmpty())
                    caller += " ";
            }

            return "[" + source + "]\t" + caller + message + "\n";
        }

        /**
         * @return A formatted time string denoting the time at which this output message was printed to the console.
         */
        private String getTime() {
            return "[" + formatTime(timeMillis) + "] ";
        }

        /**
         * Formats the passed {@link Long} value into the "HH:mm:ss" time format using the
         * {@link SimpleDateFormat} format function.
         *
         * @param millis The time to format.
         * @return The formatted time string in "HH:mm:SS" format.
         */
        private String formatTime(long millis) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return sdf.format(new java.util.Date(millis));
        }

        /**
         * @return This log entries source as a {@link String} value.
         */
        private String getSource() {
            return source.toString();
        }
    }

    /**
     * Constructs a {@link LogMan Log Manager} object which can be used to print, catch or handle error logs both to the client and
     * BotMenu console to centralize debugging and output formatting.
     *
     * @param bot A reference to the associated {@link BotMan} class, logging errors.
     */
    public LogMan(BotMan bot) {
        this.bot = bot;
    }

    public static LogSource getSource(String method) {
        try {
            if (method == null)
                return LogSource.DEBUG;

            // throws illegal argument exception if value doesn't exist. Expects: GET, POST or PATCH
            return LogSource.valueOf(method.trim().toUpperCase());

        } catch (IllegalArgumentException e) {
            return LogSource.DEBUG;
        }
    }

    public static LogSource getSource(DataMan.REQUEST_METHOD method) {
        return getSource(method.toString());
    }

    /**
     * Builds the "Logs" dashboard submenu UI.
     * <n>
     * Provides controls to:
     * - Filter by log source (ALL / STATUS / BOT_STATUS)
     * - Search the logs (with case sensitivity toggle)
     * - Pause/resume live logging (drops logs while paused)
     * - Clear logs
     * - Save logs to a text file (may be blocked by OSBot SecurityManager)
     *
     * @return A fully-functional {@link JComponent} which can be loaded as a card to display the log console in the
     * {@link BotMenu}.
     */
    public JComponent buildTabLogs() {
        /// TOP BAR CONTROLS (FILTERS + ACTION BUTTONS)

        // create a combo-box to filter logging messages by their source type (i.e., All, Status, Bot Status, Debug)
        cbSource = new JComboBox<>(LogSource.values());
        // set default selection value
        cbSource.setSelectedItem(LogSource.ALL);

        // add a text field used to search/filter logs by substring match
        tbSearch = new JTextField();
        // give the search field a consistent width to avoid layout jitter.
        tbSearch.setPreferredSize(new Dimension(220, 24));

        // add a checkbox to toggle whether the search is case-sensitive or not.
        chkCaseSensitive = new JCheckBox("Case sensitive");
        chkCaseSensitive.setSelected(false);

        // creates a button to copy logs to the users console
        JButton btnCopy = new JButton("Copy");
        // add toggle button to pause/resume logging.
        btnToggleExecution = new JToggleButton("Pause");
        // clears the stored log buffer and the visible log document
        JButton btnClear = new JButton("Clear");

        /// Header: create the left side (i.e., filter, search bar, case sensitivity)

        // creates a "log panel" used to filter the log console output
        JPanel logPanelFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        logPanelFilters.add(new JLabel("Filter by:"));
        logPanelFilters.add(cbSource);
        logPanelFilters.add(new JLabel("Search:"));
        logPanelFilters.add(tbSearch);
        logPanelFilters.add(chkCaseSensitive);

        /// Header: create the right side (i.e., copy, clear and pause/resume

        JPanel logPanelControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        logPanelControls.add(btnCopy);
        logPanelControls.add(btnToggleExecution);
        logPanelControls.add(btnClear);

        /// Header: join the filter/controls together to create the header

        // BorderLayout keeps filters left and buttons right.
        JPanel logPanelHeader = new JPanel(new BorderLayout(8, 8));
        logPanelHeader.add(logPanelFilters, BorderLayout.WEST);
        logPanelHeader.add(logPanelControls, BorderLayout.EAST);

        /// Log console: create a styled, scrollable document to output formatted messages

        // JTextPane is used instead of JTextArea so we can apply styles/colors.
        logPane = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                // return false to enable horizontal scrolling
                return false;
            }
        };
        // make the log console read-only.
        logPane.setEditable(false);
        // Use a monospace font to mimic a real console.
        logPane.setFont(new Font("Consolas", Font.PLAIN, 13));
        // Add padding so text is not flush against the edges.
        logPane.setMargin(new Insets(8, 8, 8, 8));
        // Cache the StyledDocument backing the JTextPane. All styled log output is written to this document.
        logDoc = logPane.getStyledDocument();

        // Register named styles (TIME, STATUS, BOT_STATUS, NORMAL).
        defineLogStyles(logDoc);
        // Wrap the log pane in a scroll pane for overflow.
        JScrollPane scroll = new JScrollPane(logPane);

        /// Log panel: (final step) join the header and body together to create the logPanel

        JPanel logPanel = new JPanel(new BorderLayout(12, 12));
        // add outer padding around the entire log panel.
        logPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        // mount the top bar and log console
        logPanel.add(logPanelHeader, BorderLayout.NORTH);
        logPanel.add(scroll, BorderLayout.CENTER);

        ///
        /// LISTENERS (HOOK UI CONTROLS INTO LOG VIEW)
        ///

        /// Refresh the log view when the source filter changes.
        cbSource.addActionListener(e -> callRefresh());

        ///  add listeners to search bar
        addSearchBarListeners();

        /// Refresh the view when case sensitivity is toggled.
        chkCaseSensitive.addActionListener(e -> callRefresh());

        /// Add a listen to pause/resume logging
        btnToggleExecution.addActionListener(e -> {
            logPaused = btnToggleExecution.isSelected();
            btnToggleExecution.setText(logPaused ? "Resume" : "Pause");
        });

        /// Add a listener to clear all output currently being displayed on
        btnClear.addActionListener(e -> bot.safeRun(this::clear));

        /// Add a listener to the copy button to call the function which copies the log console to the clipboard
        btnCopy.addActionListener(e -> bot.safeRun(this::copyLogs));

        // refresh on EDT to ensure proper initialization
        bot.safeRun(this::refresh);
        /// Return the fully assembled log panel.
        return logPanel;
    }

    /**
     * Add listener to search bar to reach to live changes in the search field. Any keystroke triggers a refresh of the
     * filtered log view.
     */
    private void addSearchBarListeners() {
        // create a listener that forces a refresh after each insert, remove or change to the search phrase1
        DocumentListener dl = new DocumentListener() {
            // add direct refreshes instead of waiting for swing EDT to prevent delays between keystrokes and results
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        };

        // attach the document listener to the search field.
        tbSearch.getDocument().addDocumentListener(dl);
    }

    /**
     * Defines and registers the different styles for the passed {@link StyledDocument} object.
     *
     * @param doc The {@link StyledDocument} to load the log console styles into.
     */
    private void defineLogStyles(StyledDocument doc) {
        Style def = StyleContext.getDefaultStyleContext()
                .getStyle(StyleContext.DEFAULT_STYLE);

        // define the style for the time-stamps of each entry
        Style time = doc.addStyle("TIME", def);
        StyleConstants.setForeground(time, new Color(120, 120, 120));

        // define the normal style for the log console
        Style normal = doc.addStyle("NORMAL", def);
        StyleConstants.setForeground(normal, Color.DARK_GRAY);

        // define the style for player status output logs
        Style status = doc.addStyle(LogSource.PLAYER.toString(), def);
        StyleConstants.setForeground(status, new Color(30, 90, 200));

        // define the style for bot status output logs
        Style botStatus = doc.addStyle(LogSource.BOT.toString(), def);
        StyleConstants.setForeground(botStatus, new Color(0, 120, 70));

        // define the style for post http request status updates
        Style postStatus = doc.addStyle(LogSource.POST.toString(), def);
        StyleConstants.setForeground(postStatus, new Color(95, 17, 108));

        // define the style for patch http request status updates
        Style patchStatus = doc.addStyle(LogSource.PATCH.toString(), def);
        StyleConstants.setForeground(patchStatus, new Color(140, 29, 30));

        // define the style for get http request status updates
        Style getStatus = doc.addStyle(LogSource.GET.toString(), def);
        StyleConstants.setForeground(getStatus, new Color(90, 80, 0));

        // define the style for the debug output logs (any default OsBot logs or log() output)
        Style debug = doc.addStyle(LogSource.DEBUG.toString(), def);
        StyleConstants.setForeground(debug, new Color(50, 100, 120));
    }

    /**
     * Return true if the passed {@link LogEntry entry} should be displayed in the {@link BotMenu}'s log console or not.
     *
     * @param entry The log entry being validated.
     * @return True if the passed entries message matches the filtered source type or search phrase.
     */
    private boolean matchesFilter(LogEntry entry) {
        // get the selected source in the menus filter combo-box
        LogSource selectedSourceFilter = (LogSource) cbSource.getSelectedItem();
        // get the users search phrase
        String search = tbSearch.getText();
        boolean caseSensitive = chkCaseSensitive.isSelected();

        // return early if filter is not set to ALL or this entries source doesn't match the filter
        if (selectedSourceFilter != LogSource.ALL && entry.source != selectedSourceFilter)
            return false;

        // return early if no search phrase has been entered
        if (search == null || search.isEmpty())
            return true;

        // validate the entry message
        String msg = entry.toString();

        // apply the case sensitivity filter
        if (!caseSensitive) {
            msg = msg.toLowerCase();
            search = search.toLowerCase();
        }

        return msg.contains(search);
    }

    ///  Refresh tasks: used to dynamically update data as it comes through.

    /**
     * Dynamically refresh all the log managers display components using the
     * {@link SwingUtilities#invokeLater(Runnable)} function to ensure thread-safe execution.
     */
    public final void callRefresh() {
        bot.safeRun(this::refresh);
    }

    /**
     * Refreshes the {@link LogMan log manager} to keep it dynamically updated. This function automatically handles any
     * filtering before logging anything to the console.
     */
    private void refresh() {
        // return early if there is no log console
        if (logDoc == null)
            return;

        try {
            // clear any old data
            logDoc.remove(0, logDoc.getLength());

            // for each log entry in the log list
            for (LogEntry e : logList) {
                // don't print if the user has filtered this text type out
                if (!matchesFilter(e))
                    continue;

                // insert the time prefix to this log statement
                log(e.getTime(), logDoc.getStyle("TIME"));
                // format the output with its associated style before outputting to bot menu log console
                log(e.toString(), getSelectedStyle(e));
            }

            // reset cursor to the bottom
            if (logPane != null && logDoc != null)
                logPane.setCaretPosition(logDoc.getLength());

        } catch (BadLocationException e) {
            bot.checkAttempts();
        }
    }

    /**
     * Helper function to print a {@link String} message to the console, followed by a new-line character.
     *
     * @param string The {@link String} being printed to the {@link BotMenu}'s log console.
     * @param style The {@link Style} to use in the styled document while printing to the log console.
     */
    private void log(String string, Style style) throws BadLocationException {
        logDoc.insertString(logDoc.getLength(), string, style);
    }

    /**
     * Helper function to select the style for each different type of log console output
     *
     * @param entry The {@link LogEntry} to select a style for.
     * @return The style associate with the passed {@link LogEntry} type.
     */
    private Style getSelectedStyle(LogEntry entry) {
        // try fetch the style based on log entry type
        Style style = logDoc.getStyle(entry.getSource());
        // return log entry style if any is loaded into Style, else return default style
        return style != null ? style : logDoc.getStyle("NORMAL");
    }

    /**
     * Formats the passed {@link Long} value into the "HH:mm:ss" time format using the{@link SimpleDateFormat} format
     * function.
     *
     * @param millis The millisecond value to convert into a time string.
     * @return A {@link String} representing the passed value in the equivalent "HH:mm:ss" time format.
     */
    private String formatTime(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        return sdf.format(new java.util.Date(millis));
    }

    public void log(LogSource source, String... lines) {
        // validate lines array at least up to the first value
        if (lines == null || lines.length < 1)
            return;

        // if there is only the 1 item we validated, return that, or try format the other lines
        String message = formatMultiLog(lines);
        // use the passed source and formatted message to create a log entry
        LogEntry e =  new LogEntry(source, message);
        // log this entry
        log(e);
    }

    private String formatMultiLog(String[] lines) {
        // lines should never be null, empty or single values - code/input bug
        if (lines == null || lines.length < 1)
            throw new IllegalArgumentException();

        // if single value, validate before returning
        if (lines.length == 1 && lines[0] != null)
            return lines[0];

        // otherwise, init a string builder to format multi-line logs
        StringBuilder sb = new StringBuilder(multiLineHeader);
        // apply custom formatting for first line e.g., drop to next line etc.
        sb.append(lines[0]).append(multiLinePrefix);

        // for every additional line provided in the String... array, insert prefixes
        for (int i = 1; i < lines.length; i++) {
            if (lines[i] == null)
                continue;

            // don't append prefix to the last item in the lines list
            sb.append(lines[i]).append(i == lines.length - 1 ? "" : multiLinePrefix);
        }

        return sb.toString();
    }



    /**
     * Adds the passed {@link LogEntry} to the {@link LogMan#logList} for display in the {@link BotMenu}'s log console.
     * <n>
     * Note: This {@link LogEntry} is not appended to the {@link LogMan#logList} if {@link LogMan#logPaused} is true.
     *
     * @param entry The item currently being added to the log list for tracking.
     */
    private void log(LogEntry entry) {
        // if the log is paused we are not tracking the current logs, return early
        if (logPaused)
            return;

        // handle console logging (this does not infinitely recurse since it gets formatted with "[" start char)
        bot.log(entry.toString());

        // handle bot menu logging (if a menu exists)
        if (bot.getBotMenu() != null) {
            // log entries not only to track them but also to limit the total log entries (buffer)
            bot.safeRun(() -> logList.add(entry));

            // limit output size by log buffer value
            if (logList.size() > LOG_BUFFER) {
                int overflow = logList.size() - LOG_BUFFER;
                // drop oldest messages
                bot.safeRun(() -> logList.subList(0, overflow).clear());
            }

            // update view (respects current filter/search)
            callRefresh();
        }
    }

    ///  MENU CONTROL FUNCTIONS (e.g., button-click events etc.)

    /**
     * Copies the log console contents to the users clipboard for pasting elsewhere.
     */
    private void copyLogs() {
        // create a string builder for simple & efficient concatenations
        StringBuilder sb = new StringBuilder(logList.size() * 48);

        // for each log in the log list
        for (LogEntry e : logList) {
            // use string builder to group time, source and message contents
            sb.append('[').append(formatTime(e.timeMillis)).append("] ")
                    .append(e.source).append(' ')
                    .append(e.message).append('\n');
        }

        // use string builder to select and copy console text into a variable
        StringSelection selection = new StringSelection(sb.toString());
        // use Toolkit to copy selection to the users clipboard
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    /**
     * Removes all contents from the log document, ready for updating.
     */
    private void clear() {
        try {
            logList.clear();
            logDoc.remove(0, logDoc.getLength());
        } catch (BadLocationException ignored) {}
    }
}