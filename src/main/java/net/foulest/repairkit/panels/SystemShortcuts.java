/*
 * RepairKit - an all-in-one Java-based Windows repair and maintenance toolkit.
 * Copyright (C) 2026 Foulest (https://github.com/Foulest)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.foulest.repairkit.panels;

import net.foulest.repairkit.Messages;
import net.foulest.repairkit.model.Shortcut;
import net.foulest.repairkit.service.ShortcutCatalog;
import net.foulest.repairkit.service.ToolLauncher;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.FileUtil;
import net.foulest.repairkit.util.SwingUtil;
import net.foulest.repairkit.util.WindowsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * The System Shortcuts panel.
 * <p>
 * Shortcuts are defined in {@link ShortcutCatalog}. Settings pages open through
 * the Windows shell rather than {@code cmd /c start}, and tools that do not work
 * in Safe Mode are disabled with an explanation.
 *
 * @author Foulest
 */
public final class SystemShortcuts extends JPanel {

    private static final int CARD_WIDTH = 280;
    private static final int CARD_HEIGHT = 150;

    /**
     * Creates the panel.
     */
    public SystemShortcuts() {
        super(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 18, 12, 18));

        @NotNull JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        @NotNull JLabel title = SwingUtil.title(Messages.get("shortcuts.title"));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);

        @NotNull JLabel description = SwingUtil.description(
                Messages.get("shortcuts.description"), 720);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(description);

        add(header, BorderLayout.PAGE_START);

        @NotNull JPanel grid = new JPanel();
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));

        boolean safeMode = WindowsUtil.isSafeMode();
        @NotNull List<Shortcut> shortcuts = ShortcutCatalog.shortcuts();

        for (int index = 0; index < shortcuts.size(); index += 3) {
            @NotNull JPanel row = new JPanel(new GridLayout(1, 3, 14, 14));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(0, 0, 14, 0));

            for (int column = 0; column < 3; column++) {
                int shortcutIndex = index + column;

                if (shortcutIndex < shortcuts.size()) {
                    row.add(createCard(shortcuts.get(shortcutIndex), safeMode));
                } else {
                    @NotNull JPanel filler = new JPanel();
                    filler.setOpaque(false);
                    row.add(filler);
                }
            }

            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT + 14));
            grid.add(row);
        }

        @NotNull JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);
    }

    private @NotNull JComponent createCard(@NotNull Shortcut shortcut, boolean safeMode) {
        @NotNull JPanel card = new JPanel(new BorderLayout(10, 8));
        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingUtil.borderColor()),
                new EmptyBorder(10, 10, 10, 10)));

        @NotNull JLabel icon = new JLabel(SwingUtil.scale(
                FileUtil.getImageIcon(shortcut.icon()), 40));
        card.add(icon, BorderLayout.LINE_START);

        @NotNull JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        @NotNull JLabel name = new JLabel(shortcut.name());
        name.setFont(SwingUtil.font(Font.BOLD, 14));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);

        @NotNull JLabel description = new JLabel("<html><body style='width:170px'>"
                + SwingUtil.escapeHtml(shortcut.description()) + "</body></html>");
        description.setFont(SwingUtil.font(Font.PLAIN, 11));
        description.setBorder(new EmptyBorder(4, 0, 0, 0));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(description);

        card.add(text, BorderLayout.CENTER);

        @NotNull JButton button = SwingUtil.button(Messages.get("shortcuts.open"),
                shortcut.description(), () -> open(shortcut));

        if (safeMode && shortcut.blockedInSafeMode()) {
            button.setEnabled(false);
            button.setToolTipText(Messages.get("shortcuts.safeMode.message"));
            button.getAccessibleContext().setAccessibleDescription(
                    Messages.get("shortcuts.safeMode.message"));
        }

        card.add(button, BorderLayout.PAGE_END);
        return card;
    }

    private void open(@NotNull Shortcut shortcut) {
        Thread worker = new Thread(() -> {
            @NotNull ToolLauncher.LaunchResult result = ToolLauncher.launch(shortcut);
            DebugUtil.debug(result.message());

            if (!result.success()) {
                SwingUtil.error(this, Messages.get("shortcuts.failed.title"),
                        Messages.get("shortcuts.failed.message", shortcut.name()));
            }
        }, "repairkit-shortcut-" + shortcut.id());
        worker.setDaemon(true);
        worker.start();
    }
}
