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
package net.foulest.repairkit;

import net.foulest.repairkit.panels.AutomaticRepairs;
import net.foulest.repairkit.panels.SystemShortcuts;
import net.foulest.repairkit.panels.UsefulPrograms;
import net.foulest.repairkit.service.RepairOperations;
import net.foulest.repairkit.service.RepairService;
import net.foulest.repairkit.util.CommandUtil;
import net.foulest.repairkit.util.ConstantUtil;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.FileUtil;
import net.foulest.repairkit.util.ProcessUtil;
import net.foulest.repairkit.util.SingleInstanceLock;
import net.foulest.repairkit.util.SoundUtil;
import net.foulest.repairkit.util.SwingUtil;
import net.foulest.repairkit.util.UpdateUtil;
import net.foulest.repairkit.util.WindowsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The RepairKit application window.
 * <p>
 * Startup validation happens before any UI work, and the frame is built
 * entirely on the event dispatch thread with layout managers. The window is
 * resizable, uses the system font, and exposes keyboard shortcuts for page
 * navigation.
 *
 * @author Foulest
 */
public final class RepairKit extends JFrame {

    private static final String PANEL_REPAIRS = "repairs";
    private static final String PANEL_PROGRAMS = "programs";
    private static final String PANEL_SHORTCUTS = "shortcuts";

    private static @Nullable SingleInstanceLock instanceLock;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final Map<String, JToggleButton> navigation = new LinkedHashMap<>();
    private final AutomaticRepairs repairsPanel;

    /**
     * Starts RepairKit.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SwingUtil.installLookAndFeel();

        if (!WindowsUtil.isWindows() || GraphicsEnvironment.isHeadless()) {
            // Nothing below this point is safe off Windows: registry and shell
            // access would fail with a linkage error rather than a message.
            @NotNull String message = Messages.get("startup.os.message",
                    System.getProperty("os.name", "unknown"));

            if (GraphicsEnvironment.isHeadless()) {
                System.err.println(message);
            } else {
                JOptionPane.showMessageDialog(null, message,
                        Messages.get("startup.os.title"), JOptionPane.ERROR_MESSAGE);
            }

            System.exit(1);
        }

        @Nullable SingleInstanceLock lock = SingleInstanceLock.acquire();

        if (lock == null) {
            SoundUtil.playSound(ConstantUtil.ERROR_SOUND);
            JOptionPane.showMessageDialog(null,
                    Messages.get("startup.instance.message"),
                    Messages.get("startup.instance.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        instanceLock = lock;
        DebugUtil.logEnvironment(args);

        if (!WindowsUtil.is64Bit()) {
            SoundUtil.playSound(ConstantUtil.ERROR_SOUND);
            JOptionPane.showMessageDialog(null,
                    Messages.get("startup.arch.message"),
                    Messages.get("startup.arch.title"), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        if (!WindowsUtil.isAdministrator()) {
            SoundUtil.playSound(ConstantUtil.ERROR_SOUND);
            JOptionPane.showMessageDialog(null,
                    Messages.get("startup.admin.message"),
                    Messages.get("startup.admin.title"), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        WindowsUtil.OsInfo osInfo = WindowsUtil.osInfo();

        if (!osInfo.supported()) {
            SoundUtil.playSound(ConstantUtil.ERROR_SOUND);
            JOptionPane.showMessageDialog(null,
                    Messages.get("startup.os.message", osInfo.describe()),
                    Messages.get("startup.os.title"), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        boolean safeMode = WindowsUtil.isSafeMode();

        // Left-over tool directories are removed before a new session starts.
        FileUtil.cleanupToolDirectories();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CommandUtil.terminateAll();
            DebugUtil.close();

            if (instanceLock != null) {
                instanceLock.close();
            }
        }, "repairkit-shutdown"));

        SwingUtilities.invokeLater(() -> {
            RepairKit frame = new RepairKit(safeMode);
            frame.setVisible(true);

            if (osInfo.windows10EndOfLife()) {
                SwingUtil.warning(frame, Messages.get("startup.os.eol.title"),
                        Messages.get("startup.os.eol.message"));
            }

            if (safeMode) {
                SwingUtil.warning(frame, Messages.get("startup.safemode.title"),
                        Messages.get("startup.safemode.message"));
            }

            @NotNull List<String> invalidConfig = RepairOperations.invalidConfigFiles();

            if (!invalidConfig.isEmpty()) {
                SwingUtil.warning(frame, Messages.get("error.config.title"),
                        Messages.get("error.config.message", String.join(", ", invalidConfig)));
            }

            if (ProcessUtil.isProcessRunning("medal.exe")) {
                SwingUtil.warning(frame, Messages.get("startup.medal.title"),
                        Messages.get("startup.medal.message"));
            }

            UpdateUtil.checkForUpdates();
        });
    }

    private RepairKit(boolean safeMode) {
        super(Messages.get("app.title"));

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(880, 620));
        setPreferredSize(new Dimension(1040, 740));

        @NotNull Image icon = FileUtil.getImageIcon("icons/RepairKit.png").getImage();
        setIconImage(icon);

        repairsPanel = new AutomaticRepairs(safeMode);
        @NotNull UsefulPrograms programsPanel = new UsefulPrograms();
        @NotNull SystemShortcuts shortcutsPanel = new SystemShortcuts();

        cards.add(repairsPanel, PANEL_REPAIRS);
        cards.add(programsPanel, PANEL_PROGRAMS);
        cards.add(shortcutsPanel, PANEL_SHORTCUTS);

        setLayout(new BorderLayout());
        add(createBanner(), BorderLayout.PAGE_START);
        add(cards, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.PAGE_END);

        installKeyboardShortcuts();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                attemptExit();
            }
        });

        pack();
        setLocationRelativeTo(null);
        showPanel(PANEL_REPAIRS);
    }

    private @NotNull JPanel createBanner() {
        @NotNull JPanel banner = new JPanel(new BorderLayout(16, 0));
        banner.setBackground(new Color(0, 90, 158));
        banner.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        @NotNull JPanel identity = new JPanel(new BorderLayout(10, 0));
        identity.setOpaque(false);

        @NotNull JLabel logo = new JLabel(SwingUtil.scale(
                FileUtil.getImageIcon("icons/RepairKit.png"), 40));
        identity.add(logo, BorderLayout.LINE_START);

        @NotNull JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        @NotNull JLabel title = new JLabel(Messages.get("app.title"));
        title.setForeground(Color.WHITE);
        title.setFont(SwingUtil.font(Font.BOLD, 21));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(title);

        @NotNull JButton byline = SwingUtil.bannerLink(Messages.get("app.byline"),
                () -> WindowsUtil.openUri("https://github.com/Foulest"));
        byline.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(byline);

        identity.add(titles, BorderLayout.CENTER);
        banner.add(identity, BorderLayout.LINE_START);

        @NotNull JPanel navigationPanel = new JPanel(new GridBagLayout());
        navigationPanel.setOpaque(false);
        @NotNull GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 4, 0, 4);

        @NotNull ButtonGroup group = new ButtonGroup();

        addNavigationButton(navigationPanel, group, constraints, PANEL_REPAIRS,
                Messages.get("nav.automatic"));
        addNavigationButton(navigationPanel, group, constraints, PANEL_PROGRAMS,
                Messages.get("nav.programs"));
        addNavigationButton(navigationPanel, group, constraints, PANEL_SHORTCUTS,
                Messages.get("nav.shortcuts"));

        banner.add(navigationPanel, BorderLayout.CENTER);

        @NotNull JPanel versionPanel = new JPanel();
        versionPanel.setOpaque(false);
        versionPanel.setLayout(new BoxLayout(versionPanel, BoxLayout.Y_AXIS));

        @NotNull JLabel version = new JLabel(Messages.get("app.version",
                UpdateUtil.versionString()));
        version.setForeground(Color.WHITE);
        version.setFont(SwingUtil.font(Font.PLAIN, 12));
        version.setAlignmentX(Component.RIGHT_ALIGNMENT);
        versionPanel.add(version);

        @NotNull JButton checkForUpdates = SwingUtil.bannerLink(
                Messages.get("app.version.tooltip"),
                () -> WindowsUtil.openUri("https://github.com/Foulest/RepairKit/releases"));
        checkForUpdates.setAlignmentX(Component.RIGHT_ALIGNMENT);
        versionPanel.add(checkForUpdates);

        banner.add(versionPanel, BorderLayout.LINE_END);
        return banner;
    }

    private void addNavigationButton(@NotNull JPanel panel, @NotNull ButtonGroup group,
                                     @NotNull GridBagConstraints constraints,
                                     @NotNull String key, @NotNull String text) {
        @NotNull JToggleButton button = SwingUtil.navigationButton(text, text,
                () -> showPanel(key));
        group.add(button);
        navigation.put(key, button);
        panel.add(button, constraints);
    }

    private @NotNull JPanel createFooter() {
        @NotNull JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(4, 14, 6, 14));

        @NotNull JLabel logLabel = new JLabel(Messages.get("footer.logLocation",
                DebugUtil.getLogFile().getFileName().toString()));
        logLabel.setFont(SwingUtil.font(Font.PLAIN, 11));
        footer.add(logLabel, BorderLayout.LINE_START);

        @NotNull JPanel links = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        links.setOpaque(false);
        links.add(SwingUtil.linkButton(Messages.get("footer.openLog"),
                () -> FileUtil.open(DebugUtil.getLogFile())));
        links.add(SwingUtil.linkButton(Messages.get("footer.openBackups"),
                () -> WindowsUtil.openDirectory(WindowsUtil.backupDirectory())));
        footer.add(links, BorderLayout.LINE_END);

        return footer;
    }

    private void installKeyboardShortcuts() {
        @NotNull InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        @NotNull ActionMap actionMap = getRootPane().getActionMap();

        bindPanelShortcut(inputMap, actionMap, KeyEvent.VK_1, PANEL_REPAIRS);
        bindPanelShortcut(inputMap, actionMap, KeyEvent.VK_2, PANEL_PROGRAMS);
        bindPanelShortcut(inputMap, actionMap, KeyEvent.VK_3, PANEL_SHORTCUTS);
    }

    private void bindPanelShortcut(@NotNull InputMap inputMap, @NotNull ActionMap actionMap,
                                   int keyCode, @NotNull String panel) {
        @NotNull String name = "show-" + panel;
        inputMap.put(KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_DOWN_MASK), name);
        actionMap.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showPanel(panel);
            }
        });
    }

    private void showPanel(@NotNull String key) {
        cardLayout.show(cards, key);
        @Nullable JToggleButton button = navigation.get(key);

        if (button != null) {
            button.setSelected(true);
        }
    }

    private void attemptExit() {
        if (RepairService.isRunning()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    Messages.get("frame.close.message"),
                    Messages.get("frame.close.title"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (choice != JOptionPane.YES_OPTION) {
                return;
            }

            repairsPanel.requestCancel();
            CommandUtil.cancelActiveProcesses();
        }

        dispose();
        DebugUtil.close();

        if (instanceLock != null) {
            instanceLock.close();
        }

        System.exit(0);
    }
}
