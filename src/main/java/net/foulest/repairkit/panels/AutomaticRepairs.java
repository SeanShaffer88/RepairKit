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
import net.foulest.repairkit.model.CancellationToken;
import net.foulest.repairkit.model.RepairOption;
import net.foulest.repairkit.model.RepairReport;
import net.foulest.repairkit.model.RepairTask;
import net.foulest.repairkit.model.RiskLevel;
import net.foulest.repairkit.model.StepStatus;
import net.foulest.repairkit.model.TaskResult;
import net.foulest.repairkit.service.RepairOperations;
import net.foulest.repairkit.service.RepairService;
import net.foulest.repairkit.util.ConstantUtil;
import net.foulest.repairkit.util.CommandResult;
import net.foulest.repairkit.util.CommandUtil;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.SoundUtil;
import net.foulest.repairkit.util.SwingUtil;
import net.foulest.repairkit.util.WindowsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Automatic Repairs panel.
 * <p>
 * Options are separated from progress: checkboxes are a configuration, and the
 * results table reports what actually happened. The run button, progress bar,
 * and cancel button all operate on the event dispatch thread, while the repair
 * work runs sequentially on a background thread.
 *
 * @author Foulest
 */
public final class AutomaticRepairs extends JPanel {

    private final Map<String, JCheckBox> optionBoxes = new LinkedHashMap<>();
    private final Map<String, Integer> rowByTask = new LinkedHashMap<>();

    private final JButton runButton;
    private final JButton cancelButton;
    private final JButton viewLogButton;
    private final JButton openBackupsButton;
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel(Messages.get("footer.ready"));
    private final DefaultTableModel resultsModel;
    private final JTable resultsTable;

    private final boolean safeMode;
    private CancellationToken token = new CancellationToken();
    private volatile Boolean bitLockerProtected;

    /**
     * Creates the panel.
     *
     * @param safeMode whether Windows is running in Safe Mode
     */
    public AutomaticRepairs(boolean safeMode) {
        super(new BorderLayout(0, 12));
        this.safeMode = safeMode;
        setBorder(new EmptyBorder(16, 18, 12, 18));

        add(createHeader(), BorderLayout.PAGE_START);

        @NotNull JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(new EmptyBorder(4, 0, 4, 0));

        boolean advancedHeading = false;

        for (RepairOption option : RepairOperations.options()) {
            if (option.risk() == RiskLevel.HIGH && !advancedHeading) {
                options.add(createAdvancedHeading());
                advancedHeading = true;
            }

            options.add(createOptionRow(option));
        }

        @NotNull JScrollPane optionsScroll = SwingUtil.scroll(options);
        optionsScroll.setBorder(BorderFactory.createTitledBorder(Messages.get("repairs.options")));
        optionsScroll.setPreferredSize(new Dimension(700, 300));

        resultsModel = new DefaultTableModel(
                new Object[]{Messages.get("repairs.column.task"),
                        Messages.get("repairs.column.status"),
                        Messages.get("repairs.column.details")}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultsTable = new JTable(resultsModel);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.setRowHeight(Math.max(22, resultsTable.getRowHeight()));
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(320);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(460);
        resultsTable.getAccessibleContext().setAccessibleName(
                Messages.get("repairs.results"));

        @NotNull JScrollPane resultsScroll = new JScrollPane(resultsTable);
        resultsScroll.setBorder(BorderFactory.createTitledBorder(
                Messages.get("repairs.results")));

        @NotNull JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                optionsScroll, resultsScroll);
        split.setResizeWeight(0.55);
        split.setContinuousLayout(true);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        runButton = SwingUtil.primaryButton(Messages.get("repairs.run"),
                Messages.get("repairs.run"), this::startRun);
        cancelButton = SwingUtil.button(Messages.get("repairs.cancel"),
                Messages.get("repairs.cancel"), this::requestCancel);
        cancelButton.setEnabled(false);

        viewLogButton = SwingUtil.button(Messages.get("repairs.openLog"),
                Messages.get("repairs.openLog"), this::viewLog);
        openBackupsButton = SwingUtil.button(Messages.get("repairs.openBackups"),
                Messages.get("repairs.openBackups"), this::openBackups);
        openBackupsButton.setEnabled(false);

        add(createControls(), BorderLayout.PAGE_END);

        // BitLocker queries are slow; run the check in the background so the
        // confirmation dialog can mention it without blocking the interface.
        Thread bitLockerCheck = new Thread(() -> {
            bitLockerProtected = WindowsUtil.isSystemDriveBitLockerProtected();
        }, "repairkit-bitlocker");
        bitLockerCheck.setDaemon(true);
        bitLockerCheck.start();
    }

    private @NotNull JPanel createAdvancedHeading() {
        @NotNull JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(new EmptyBorder(14, 0, 4, 0));

        @NotNull JLabel heading = SwingUtil.heading(Messages.get("repairs.advanced"));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);

        @NotNull JLabel note = new JLabel("<html><body style='width:640px'>"
                + SwingUtil.escapeHtml(Messages.get("repairs.advanced.description"))
                + "</body></html>");
        note.setFont(SwingUtil.font(Font.PLAIN, 12));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(note);
        return panel;
    }

    private @NotNull JPanel createHeader() {
        @NotNull JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        @NotNull JLabel title = SwingUtil.title(Messages.get("repairs.title"));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);

        @NotNull JLabel description = SwingUtil.description(
                Messages.get("repairs.description"), 760);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(description);

        if (safeMode) {
            @NotNull JLabel notice = new JLabel(Messages.get("startup.safemode.message"));
            notice.setFont(SwingUtil.font(Font.BOLD, 12));
            notice.setBorder(new EmptyBorder(8, 0, 0, 0));
            notice.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(notice);
        }

        return header;
    }

    private @NotNull JPanel createOptionRow(@NotNull RepairOption option) {
        @NotNull JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(new EmptyBorder(2, 0, 6, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        @NotNull JCheckBox box = new JCheckBox(option.title(), option.defaultEnabled());
        box.setFont(SwingUtil.font(Font.BOLD, 13));
        box.setToolTipText(option.description());
        box.getAccessibleContext().setAccessibleDescription(option.description());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (safeMode && option.id().equals("bloatware")) {
            box.setEnabled(false);
            box.setSelected(false);
            box.setToolTipText(Messages.get("startup.safemode.message"));
        }

        optionBoxes.put(option.id(), box);
        row.add(box);

        @NotNull JLabel description = new JLabel("<html><body style='width:640px'>"
                + SwingUtil.escapeHtml(option.description()) + "</body></html>");
        description.setFont(SwingUtil.font(Font.PLAIN, 12));
        description.setBorder(new EmptyBorder(0, 24, 0, 0));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(description);

        @NotNull JLabel risk = new JLabel(Messages.get("repairs.risk", option.risk().displayName()));
        risk.setFont(SwingUtil.font(Font.PLAIN, 11));
        risk.setForeground(UIManager.getColor("Label.disabledForeground"));
        risk.setBorder(new EmptyBorder(0, 24, 0, 0));
        risk.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(risk);

        return row;
    }

    private @NotNull JPanel createControls() {
        @NotNull JPanel controls = new JPanel(new BorderLayout(0, 6));

        @NotNull JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        buttons.add(SwingUtil.button(Messages.get("repairs.recommended"),
                Messages.get("repairs.recommended"), this::selectRecommended));
        buttons.add(SwingUtil.button(Messages.get("repairs.clear"),
                Messages.get("repairs.clear"), this::clearSelection));
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(runButton);
        buttons.add(cancelButton);
        controls.add(buttons, BorderLayout.PAGE_START);

        @NotNull JPanel progress = new JPanel(new BorderLayout(10, 0));
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.getAccessibleContext().setAccessibleName(
                Messages.get("repairs.progressName"));
        statusLabel.getAccessibleContext().setAccessibleName(
                Messages.get("repairs.statusName"));
        progress.add(progressBar, BorderLayout.CENTER);

        @NotNull JPanel status = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        status.add(statusLabel);
        status.add(viewLogButton);
        status.add(openBackupsButton);
        progress.add(status, BorderLayout.LINE_END);
        controls.add(progress, BorderLayout.CENTER);

        return controls;
    }

    private void viewLog() {
        @NotNull Path log = DebugUtil.getLogFile();
        @NotNull StringBuilder content = new StringBuilder();

        try {
            @NotNull List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - 1000);

            for (String line : lines.subList(start, lines.size())) {
                content.append(line).append('\n');
            }
        } catch (IOException ex) {
            DebugUtil.warn("Failed to read the log file", ex);
            SwingUtil.warning(this, Messages.get("error.log.title"),
                    Messages.get("repairs.logUnavailable"));
            return;
        }

        @NotNull JTextArea area = new JTextArea(content.toString(), 26, 100);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(area.getDocument().getLength());
        area.getAccessibleContext().setAccessibleName(Messages.get("repairs.log.title"));

        @NotNull JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(880, 520));
        scroll.getAccessibleContext().setAccessibleName(Messages.get("repairs.log.title"));

        JOptionPane.showMessageDialog(this, scroll, Messages.get("repairs.log.title"),
                JOptionPane.PLAIN_MESSAGE);
    }

    private void openBackups() {
        if (!WindowsUtil.openDirectory(WindowsUtil.backupDirectory())) {
            SwingUtil.warning(this, Messages.get("error.log.title"),
                    Messages.get("repairs.backupUnavailable"));
        }
    }

    private void selectRecommended() {
        for (RepairOption option : RepairOperations.options()) {
            @NotNull JCheckBox box = optionBoxes.get(option.id());

            if (box != null && box.isEnabled()) {
                box.setSelected(option.defaultEnabled());
            }
        }
    }

    private void clearSelection() {
        optionBoxes.values().forEach(box -> box.setSelected(false));
    }

    private @NotNull Set<String> selectedIds() {
        @NotNull Set<String> selected = new LinkedHashSet<>();
        optionBoxes.forEach((id, box) -> {
            if (box.isSelected()) {
                selected.add(id);
            }
        });
        return selected;
    }

    private void startRun() {
        if (RepairService.isRunning()) {
            return;
        }

        @NotNull Set<String> selected = selectedIds();

        if (selected.isEmpty()) {
            SoundUtil.playSound(ConstantUtil.ERROR_SOUND);
            JOptionPane.showMessageDialog(this, Messages.get("repairs.selectOne.message"),
                    Messages.get("repairs.selectOne.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!confirmRun(selected)) {
            return;
        }

        token = new CancellationToken();
        openBackupsButton.setEnabled(false);
        resultsModel.setRowCount(0);
        rowByTask.clear();
        progressBar.setIndeterminate(true);
        progressBar.setValue(0);
        progressBar.setString("");
        statusLabel.setText(Messages.get("repairs.running", 0, 0));
        setOptionsEnabled(false);
        runButton.setEnabled(false);
        cancelButton.setEnabled(true);

        DebugUtil.debug("User started a repair run with: "
                + RepairOperations.describeSelection(selected));

        Thread worker = new Thread(() -> {
            try {
                @NotNull RepairReport report = RepairService.execute(selected, token,
                        new UiListener());
                SwingUtilities.invokeLater(() -> finishRun(report));
            } catch (RuntimeException ex) {
                DebugUtil.warn("Repair run failed", ex);
                SwingUtilities.invokeLater(() -> {
                    resetControls();
                    SoundUtil.playSound(ConstantUtil.ERROR_SOUND);
                    JOptionPane.showMessageDialog(this,
                            Messages.get("error.unexpected.message", ex.getMessage()),
                            Messages.get("error.unexpected.title"),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "repairkit-repairs");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean confirmRun(@NotNull Set<String> selected) {
        @NotNull List<RepairOption> options = RepairOperations.selectedOptions(selected);
        @NotNull StringBuilder summary = new StringBuilder("<html><body style='width:560px'>");
        summary.append("<b>").append(SwingUtil.escapeHtml(
                Messages.get("repairs.confirm.header"))).append("</b><ul>");

        boolean highRisk = false;

        for (RepairOption option : options) {
            summary.append("<li>").append(SwingUtil.escapeHtml(option.title()))
                    .append(" &mdash; ").append(SwingUtil.escapeHtml(
                            Messages.get("repairs.risk", option.risk().displayName())))
                    .append("</li>");

            if (option.risk() == RiskLevel.HIGH) {
                highRisk = true;
            }
        }

        summary.append("</ul>");

        if (!selected.contains("restorePoint")) {
            summary.append("<p><b>").append(SwingUtil.escapeHtml(
                    Messages.get("repairs.confirm.noRestore"))).append("</b></p>");
        }

        summary.append("<p>").append(SwingUtil.escapeHtml(
                Messages.get("repairs.confirm.backup"))).append("</p>");

        if (highRisk) {
            summary.append("<p><b>")
                    .append(SwingUtil.escapeHtml(Messages.get("repairs.confirm.highRisk")))
                    .append("</b></p>");
        }

        if (Boolean.TRUE.equals(bitLockerProtected)) {
            summary.append("<p><b>").append(SwingUtil.escapeHtml(
                    Messages.get("repairs.bitlocker"))).append("</b></p>");
        }

        if (WindowsUtil.isDomainJoined()) {
            summary.append("<p>").append(SwingUtil.escapeHtml(
                    Messages.get("repairs.domain"))).append("</p>");
        }

        summary.append("</body></html>");

        int choice = JOptionPane.showConfirmDialog(this, summary.toString(),
                Messages.get("repairs.confirm.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

        return choice == JOptionPane.OK_OPTION;
    }

    private void finishRun(@NotNull RepairReport report) {
        resetControls();

        if (report.backupFile() != null) {
            openBackupsButton.setEnabled(true);
        }

        progressBar.setIndeterminate(false);
        progressBar.setMaximum(100);
        progressBar.setValue(100);
        progressBar.setString("");

        @NotNull String summary = report.summary();
        statusLabel.setText(summary);
        SoundUtil.playSound(report.isFullySuccessful()
                ? ConstantUtil.EXCLAMATION_SOUND
                : ConstantUtil.WARNING_SOUND);

        @NotNull String details = summary + "\n\n"
                + (report.isFullySuccessful()
                ? Messages.get("repairs.finished.allOk")
                : Messages.get("repairs.finished.someFailed"))
                + "\n\nLog: " + report.logFile();

        if (report.backupFile() != null) {
            details += "\nRegistry backup: " + report.backupFile();
        }

        JOptionPane.showMessageDialog(this, details,
                Messages.get("repairs.finished.title"),
                report.isFullySuccessful()
                        ? JOptionPane.INFORMATION_MESSAGE
                        : JOptionPane.WARNING_MESSAGE);

        if (report.requiresRestart()) {
            promptForRestart();
        }
    }

    private void promptForRestart() {
        int choice = JOptionPane.showConfirmDialog(this,
                Messages.get("repairs.restart.message"),
                Messages.get("repairs.restart.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        @NotNull CommandResult result = CommandUtil.run(
                WindowsUtil.system32("shutdown.exe"), Duration.ofSeconds(30),
                "/r", "/t", "0");

        if (!result.succeeded()) {
            SwingUtil.warning(this, Messages.get("repairs.restart.title"),
                    Messages.get("repairs.restart.failed", result.tail(2)));
        }
    }

    private void resetControls() {
        progressBar.setIndeterminate(false);
        statusLabel.setText(Messages.get("footer.ready"));
        setOptionsEnabled(true);
        runButton.setEnabled(true);
        cancelButton.setEnabled(false);
        viewLogButton.setEnabled(true);
    }

    private void setOptionsEnabled(boolean enabled) {
        optionBoxes.forEach((id, box) -> box.setEnabled(enabled
                && !(safeMode && id.equals("bloatware"))));
    }

    /**
     * Requests cancellation of the current run.
     */
    public void requestCancel() {
        if (!RepairService.isRunning()) {
            return;
        }

        token.cancel();
        statusLabel.setText(Messages.get("repairs.cancelling"));
        cancelButton.setEnabled(false);
    }

    /**
     * Updates a results row, adding it when the task was not started yet.
     *
     * @param result the result
     */
    private void showResult(@NotNull TaskResult result) {
        @Nullable Integer row = rowByTask.get(result.task().id());
        @NotNull Object[] values = {
                result.title(),
                result.status().displayName(),
                result.message()
        };

        if (row == null) {
            rowByTask.put(result.task().id(), resultsModel.getRowCount());
            resultsModel.addRow(values);
        } else {
            resultsModel.removeRow(row);
            resultsModel.insertRow(row, values);
        }
    }

    private final class UiListener implements RepairService.Listener {

        @Override
        public void onTaskStarted(@NotNull RepairTask task, int index, int total) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setMaximum(total);
                progressBar.setValue(index);
                progressBar.setString((index + 1) + " / " + total);
                statusLabel.setText(Messages.get("repairs.running", index + 1, total));

                if (!rowByTask.containsKey(task.id())) {
                    rowByTask.put(task.id(), resultsModel.getRowCount());
                    resultsModel.addRow(new Object[]{task.title(),
                            StepStatus.RUNNING.displayName(), ""});
                }
            });
        }

        @Override
        public void onTaskFinished(@NotNull TaskResult result, int index, int total) {
            SwingUtilities.invokeLater(() -> {
                showResult(result);
                progressBar.setIndeterminate(false);
                progressBar.setMaximum(total);
                progressBar.setValue(index + 1);
                progressBar.setString((index + 1) + " / " + total);
            });
        }
    }
}
