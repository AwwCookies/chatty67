
package chatty.gui.components;

import chatty.User;
import chatty.gui.DockedDialogHelper;
import chatty.gui.DockedDialogManager;
import chatty.gui.MainGui;
import chatty.gui.components.menus.ContextMenu;
import chatty.gui.components.menus.ContextMenuAdapter;
import chatty.gui.components.menus.ContextMenuListener;
import chatty.util.DateTime;
import chatty.util.dnd.DockContent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Element;

/**
 * Dialog for displaying moderation actions (bans, timeouts, deleted messages)
 * from IRC chat events.
 * 
 * @author tduva
 */
public class ModerationActionDialog extends JDialog {
    
    private static final int MAX_NUMBER_LINES = 500;
    
    private final JTextArea log;
    private final JScrollPane scroll;
    private final DockedDialogHelper helper;
    private final MainGui main;
    
    private final List<String> entries = new ArrayList<>();
    private int displayedCount = 0;
    
    private final String title = "Moderation Log";
    private final String shortTitle = "Mod Log";

    public ModerationActionDialog(MainGui owner, DockedDialogManager dockedDialogs,
            ContextMenuListener contextMenuListener) {
        super(owner);
        this.main = owner;
        
        log = createLogArea();
        
        scroll = new JScrollPane(log);
        scroll.setPreferredSize(new Dimension(400, 300));
        add(scroll, BorderLayout.CENTER);
        
        DockContent content = dockedDialogs.createStyledContent(scroll, shortTitle, "-modactionlog-");
        helper = dockedDialogs.createHelper(new DockedDialogHelper.DockedDialog() {
            @Override
            public void setVisible(boolean visible) {
                ModerationActionDialog.super.setVisible(visible);
            }

            @Override
            public boolean isVisible() {
                return ModerationActionDialog.super.isVisible();
            }

            @Override
            public void addComponent(Component comp) {
                add(comp, BorderLayout.CENTER);
            }

            @Override
            public void removeComponent(Component comp) {
                remove(comp);
            }

            @Override
            public Window getWindow() {
                return ModerationActionDialog.this;
            }

            @Override
            public DockContent getContent() {
                return content;
            }
        });
        
        // Add context menu
        setupContextMenu();
        
        updateTitle();
        pack();
    }
    
    private void setupContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem clearItem = new JMenuItem("Clear Log");
        clearItem.addActionListener(e -> clear());
        menu.add(clearItem);
        
        log.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                openPopup(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                openPopup(e);
            }
            
            private void openPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        
        helper.installContextMenu(log);
    }
    
    private static JTextArea createLogArea() {
        // Caret to prevent scrolling
        DefaultCaret caret = new DefaultCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        JTextArea text = new JTextArea();
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setCaret(caret);
        text.setBorder(BorderFactory.createEmptyBorder(1, 2, 3, 2));
        return text;
    }
    
    @Override
    public void setVisible(boolean visible) {
        helper.setVisible(visible, true);
    }

    @Override
    public boolean isVisible() {
        return helper.isVisible();
    }
    
    private void updateTitle() {
        String displayTitle = displayedCount > 0 
            ? title + " (" + displayedCount + ")"
            : title;
        super.setTitle(displayTitle);
        if (helper != null) {
            helper.getContent().setLongTitle(displayTitle);
        }
    }
    
    /**
     * Add a ban or timeout entry.
     * 
     * @param user The affected user
     * @param duration The duration in seconds (-1 for permanent ban, -2 for deleted message)
     * @param reason The reason for the action (if available)
     * @param targetMsgId The message ID being targeted (for deleted messages)
     */
    public void addBan(User user, long duration, String reason, String targetMsgId) {
        String actionType;
        String durationStr = "";
        
        if (duration == -1) {
            actionType = "BAN";
        } else if (duration == -2) {
            actionType = "DELETED";
        } else if (duration == 0) {
            actionType = "BAN";
        } else {
            actionType = "TIMEOUT";
            durationStr = formatDuration(duration);
        }
        
        StringBuilder line = new StringBuilder();
        line.append("[").append(DateTime.currentTime()).append("] ");
        line.append("[").append(user.getChannel()).append("] ");
        line.append(actionType);
        
        if (!durationStr.isEmpty()) {
            line.append(" (").append(durationStr).append(")");
        }
        
        line.append(": ").append(user.getDisplayNick());
        
        if (reason != null && !reason.isEmpty()) {
            line.append(" - Reason: ").append(reason);
        }
        
        String entry = line.toString();
        entries.add(entry);
        
        // Trim if necessary
        if (entries.size() > MAX_NUMBER_LINES) {
            entries.remove(0);
        }
        
        displayedCount++;
        printLine(entry);
        updateTitle();
        helper.setNewMessage();
    }
    
    /**
     * Add a deleted message entry with the original message content.
     * 
     * @param user The affected user
     * @param targetMsgId The message ID being targeted
     * @param message The original message content
     */
    public void addDeletedMessage(User user, String targetMsgId, String message) {
        StringBuilder line = new StringBuilder();
        line.append("[").append(DateTime.currentTime()).append("] ");
        line.append("[").append(user.getChannel()).append("] ");
        line.append("DELETED: ").append(user.getDisplayNick());
        
        if (message != null && !message.isEmpty()) {
            // Truncate long messages
            String msgPreview = message.length() > 100 
                ? message.substring(0, 100) + "..." 
                : message;
            line.append(" - Message: \"").append(msgPreview).append("\"");
        }
        
        String entry = line.toString();
        entries.add(entry);
        
        // Trim if necessary
        if (entries.size() > MAX_NUMBER_LINES) {
            entries.remove(0);
        }
        
        displayedCount++;
        printLine(entry);
        updateTitle();
        helper.setNewMessage();
    }
    
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "h";
        } else {
            return (seconds / 86400) + "d";
        }
    }
    
    private void printLine(String line) {
        try {
            Document doc = log.getDocument();
            String linebreak = doc.getLength() > 0 ? "\n" : "";
            doc.insertString(doc.getLength(), linebreak + line, null);
            JScrollBar bar = scroll.getVerticalScrollBar();
            boolean scrollDown = bar.getValue() > bar.getMaximum() - bar.getVisibleAmount() - 4;
            if (scrollDown) {
                scrollDown();
            }
            clearSomeLines(doc);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void scrollDown() {
        scroll.validate();
        scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
        SwingUtilities.invokeLater(() -> {
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
        });
    }
    
    /**
     * Removes some lines from the given Document so it won't exceed the maximum
     * number of lines.
     */
    private void clearSomeLines(Document doc) {
        int count = doc.getDefaultRootElement().getElementCount();
        if (count > MAX_NUMBER_LINES) {
            removeFirstLines(doc, 10);
        }
    }
    
    /**
     * Removes the given number of lines from the given Document.
     */
    private void removeFirstLines(Document doc, int amount) {
        if (amount < 1) {
            amount = 1;
        }
        Element firstToRemove = doc.getDefaultRootElement().getElement(0);
        Element lastToRemove = doc.getDefaultRootElement().getElement(amount - 1);
        int startOffset = firstToRemove.getStartOffset();
        int endOffset = lastToRemove.getEndOffset();
        try {
            doc.remove(startOffset, endOffset);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Clears all log entries.
     */
    public void clear() {
        log.setText("");
        entries.clear();
        displayedCount = 0;
        updateTitle();
    }
    
    public void showDialog() {
        setVisible(true);
    }
    
    public int getDisplayedCount() {
        return displayedCount;
    }
}
