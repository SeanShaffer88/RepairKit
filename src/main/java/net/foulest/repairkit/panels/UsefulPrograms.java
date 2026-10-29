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
import net.foulest.repairkit.model.Tool;
import net.foulest.repairkit.service.ToolCatalog;
import net.foulest.repairkit.service.ToolLauncher;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.FileUtil;
import net.foulest.repairkit.util.SwingUtil;
import net.foulest.repairkit.util.WindowsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * The Useful Programs panel.
 * <p>
 * Entries come from {@link ToolCatalog}; this class only builds the cards and
 * handles paging. All components are created on the event dispatch thread, and
 * launching a tool never blocks the interface.
 *
 * @author Foulest
 */
public final class UsefulPrograms extends JPanel {

    private static final int CARD_WIDTH = 280;
    private static final int CARD_HEIGHT = 150;

    private final List<List<Tool>> pages = ToolCatalog.pages();
    private final JPanel grid = new JPanel();
    private final JLabel pageLabel = new JLabel();
    private final JButton previousButton;
    private final JButton nextButton;

    private int page;

    /**
     * Creates the panel.
     */
    public UsefulPrograms() {
        super(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 18, 12, 18));

        previousButton = SwingUtil.button("\u25C0", Messages.get("programs.previous"),
                () -> showPage(page - 1));
        nextButton = SwingUtil.button("\u25B6", Messages.get("programs.next"),
                () -> showPage(page + 1));

        add(createHeader(), BorderLayout.PAGE_START);

        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setBorder(new EmptyBorder(2, 0, 2, 0));

        @NotNull JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);

        showPage(0);
    }

    private @NotNull JPanel createHeader() {
        @NotNull JPanel header = new JPanel(new BorderLayout(8, 0));

        @NotNull JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        @NotNull JLabel title = SwingUtil.title(Messages.get("programs.title"));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);

        @NotNull JLabel description = SwingUtil.description(
                Messages.get("programs.description"), 720);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(description);

        header.add(text, BorderLayout.CENTER);

        @NotNull JPanel navigation = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        navigation.add(previousButton);
        pageLabel.setFont(SwingUtil.font(Font.BOLD, 12));
        navigation.add(pageLabel);
        navigation.add(nextButton);
        header.add(navigation, BorderLayout.LINE_END);

        return header;
    }

    private void showPage(int requested) {
        int clamped = Math.max(0, Math.min(pages.size() - 1, requested));
        page = clamped;

        pageLabel.setText(Messages.get("programs.page", clamped + 1, pages.size()));
        previousButton.setEnabled(clamped > 0);
        nextButton.setEnabled(clamped < pages.size() - 1);

        grid.removeAll();
        @NotNull List<Tool> tools = pages.get(clamped);

        for (int index = 0; index < tools.size(); index += 3) {
            @NotNull JPanel row = new JPanel(new GridLayout(1, 3, 14, 14));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(0, 0, 14, 0));

            for (int column = 0; column < 3; column++) {
                int toolIndex = index + column;

                if (toolIndex < tools.size()) {
                    row.add(createCard(tools.get(toolIndex)));
                } else {
                    @NotNull JPanel filler = new JPanel();
                    filler.setOpaque(false);
                    row.add(filler);
                }
            }

            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT + 14));
            grid.add(row);
        }

        grid.revalidate();
        grid.repaint();
    }

    private @NotNull JComponent createCard(@NotNull Tool tool) {
        @NotNull JPanel card = new JPanel(new BorderLayout(10, 8));
        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SwingUtil.borderColor()),
                new EmptyBorder(10, 10, 10, 10)));

        @NotNull JLabel icon = new JLabel(SwingUtil.scale(
                FileUtil.getImageIcon(tool.icon()), 40));
        card.add(icon, BorderLayout.LINE_START);

        @NotNull JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        @NotNull JLabel name = new JLabel(tool.name());
        name.setFont(SwingUtil.font(Font.BOLD, 14));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);

        @NotNull JLabel subtitle = new JLabel(tool.subtitle());
        subtitle.setFont(SwingUtil.font(Font.PLAIN, 11));
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(subtitle);

        @NotNull JLabel description = new JLabel("<html><body style='width:170px'>"
                + SwingUtil.escapeHtml(tool.description()) + "</body></html>");
        description.setFont(SwingUtil.font(Font.PLAIN, 11));
        description.setBorder(new EmptyBorder(4, 0, 0, 0));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(description);

        card.add(text, BorderLayout.CENTER);
        card.add(createAction(tool), BorderLayout.PAGE_END);
        return card;
    }

    private @NotNull JButton createAction(@NotNull Tool tool) {
        if (!tool.isBundled()) {
            @NotNull String url = tool.url() == null ? "" : tool.url();
            return SwingUtil.button(Messages.get("programs.visit"), tool.description(), () -> {
                if (!ToolLauncher.visit(url)) {
                    SwingUtil.error(this, Messages.get("programs.failed.title"),
                            Messages.get("programs.failed.message", tool.name()));
                }
            });
        }

        // The holder lets the action disable the button while the tool extracts.
        @NotNull JButton[] holder = new JButton[1];
        @NotNull JButton button = SwingUtil.button(Messages.get("programs.launch"),
                tool.description(), () -> launch(tool, holder[0]));
        holder[0] = button;
        return button;
    }

    private void launch(@NotNull Tool tool, @Nullable JButton button) {
        if (tool.windows10Only()) {
            WindowsUtil.OsInfo info = WindowsUtil.osInfo();

            if (info.build() > 0 && info.build() < 10240) {
                SwingUtil.warning(this, Messages.get("programs.outdated.title"),
                        Messages.get("programs.outdated.message"));
                return;
            }
        }

        @Nullable String original = button == null ? null : button.getText();

        if (button != null) {
            button.setEnabled(false);
            button.setText(Messages.get("programs.launching", tool.name()));
        }

        Thread worker = new Thread(() -> {
            @NotNull ToolLauncher.LaunchResult result = ToolLauncher.launch(tool);
            DebugUtil.debug(result.message());

            SwingUtilities.invokeLater(() -> {
                if (button != null) {
                    button.setEnabled(true);
                    button.setText(original);
                }

                if (!result.success()) {
                    SwingUtil.error(this, Messages.get("programs.failed.title"),
                            Messages.get("programs.failed.message", tool.name()));
                }
            });
        }, "repairkit-tool-" + tool.id());
        worker.setDaemon(true);
        worker.start();
    }
}
