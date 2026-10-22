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
package net.foulest.repairkit.util;

import net.foulest.repairkit.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * Shared Swing helpers.
 * <p>
 * The UI uses the platform look and feel and the system font rather than a
 * hard-coded Arial and absolute pixel positions. Components keep their default
 * focus painting, expose accessible names, and are reachable with the keyboard.
 *
 * @author Foulest
 */
public final class SwingUtil {

    /** Brand blue; chosen so white text meets WCAG AA contrast. */
    private static final Color BRAND = new Color(0, 90, 158);
    private static final Color BRAND_HOVER = new Color(0, 62, 110);
    private static final Color LINK = new Color(0, 90, 158);

    private SwingUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Applies the system look and feel.
     */
    public static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            DebugUtil.warn("Failed to set the system look and feel", ex);
        }

        // Keep animations off for users who disable them in Windows.
        UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);
    }

    /**
     * Gets the base UI font at a given size.
     *
     * @param style the font style
     * @param size  the font size, or {@code 0} to use the platform default
     * @return the font
     */
    public static @NotNull Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");

        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }

        float actualSize = size > 0 ? size : base.getSize2D();
        return base.deriveFont(style, actualSize);
    }

    /**
     * Creates a large title label.
     *
     * @param text the text
     * @return the label
     */
    public static @NotNull JLabel title(@NotNull String text) {
        @NotNull JLabel label = new JLabel(text);
        label.setFont(font(Font.BOLD, 20));
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
    }

    /**
     * Creates a section heading.
     *
     * @param text the text
     * @return the label
     */
    public static @NotNull JLabel heading(@NotNull String text) {
        @NotNull JLabel label = new JLabel(text);
        label.setFont(font(Font.BOLD, 15));
        return label;
    }

    /**
     * Creates a wrapped description label.
     *
     * @param text  the text
     * @param width the wrap width in pixels
     * @return the label
     */
    public static @NotNull JLabel description(@NotNull String text, int width) {
        @NotNull JLabel label = new JLabel("<html><body style='width:" + width + "px'>"
                + escapeHtml(text) + "</body></html>");
        label.setFont(font(Font.PLAIN, 13));
        return label;
    }

    /**
     * Creates a standard button with an accessible name and tooltip.
     *
     * @param text    the button text
     * @param tooltip the tooltip, may be empty
     * @param action  the action to run on click
     * @return the button
     */
    public static @NotNull JButton button(@NotNull String text, @NotNull String tooltip,
                                          @NotNull Runnable action) {
        @NotNull JButton button = new JButton(text);
        configureButton(button, tooltip, action);
        return button;
    }

    /**
     * Creates a primary action button.
     *
     * @param text    the button text
     * @param tooltip the tooltip
     * @param action  the action
     * @return the button
     */
    public static @NotNull JButton primaryButton(@NotNull String text, @NotNull String tooltip,
                                                 @NotNull Runnable action) {
        @NotNull JButton button = button(text, tooltip, action);
        button.setFont(font(Font.BOLD, 13));
        return button;
    }

    /**
     * Creates a toggle button used for the banner navigation.
     *
     * @param text    the button text
     * @param tooltip the tooltip
     * @param action  the action
     * @return the toggle button
     */
    public static @NotNull JToggleButton navigationButton(@NotNull String text,
                                                          @NotNull String tooltip,
                                                          @NotNull Runnable action) {
        @NotNull JToggleButton button = new JToggleButton(text);
        button.setToolTipText(tooltip);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(true);
        button.setForeground(Color.WHITE);
        button.setBackground(BRAND);
        button.setFont(font(Font.BOLD, 14));
        applyMnemonic(button);
        button.getAccessibleContext().setAccessibleName(text);
        button.getAccessibleContext().setAccessibleDescription(tooltip);
        button.addActionListener(event -> action.run());
        button.getModel().addChangeListener(event -> {
            if (button.getModel().isRollover()) {
                button.setBackground(BRAND_HOVER);
            } else if (button.isSelected()) {
                button.setBackground(BRAND_HOVER);
            } else {
                button.setBackground(BRAND);
            }
        });
        return button;
    }

    private static void configureButton(@NotNull JButton button, @NotNull String tooltip,
                                        @NotNull Runnable action) {
        if (!tooltip.isBlank()) {
            button.setToolTipText(tooltip);
            button.getAccessibleContext().setAccessibleDescription(tooltip);
        }

        applyMnemonic(button);
        button.getAccessibleContext().setAccessibleName(button.getText());
        button.addActionListener(event -> action.run());
    }

    /**
     * Assigns an Alt mnemonic based on the first letter of the button text.
     *
     * @param button the button
     */
    private static void applyMnemonic(@NotNull AbstractButton button) {
        @NotNull String text = button.getText();

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (Character.isLetterOrDigit(character)) {
                button.setMnemonic(KeyEvent.getExtendedKeyCodeForChar(character));
                button.setDisplayedMnemonicIndex(index);
                return;
            }
        }
    }

    /**
     * Creates a link-styled button that is keyboard accessible.
     *
     * @param text   the link text
     * @param action the action
     * @return the button
     */
    public static @NotNull JButton linkButton(@NotNull String text, @NotNull Runnable action) {
        @NotNull JButton button = new JButton(text);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(LINK);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(font(Font.PLAIN, 13));
        button.setFocusPainted(true);
        applyMnemonic(button);
        button.getAccessibleContext().setAccessibleName(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    /**
     * Creates a white link for use on the blue banner.
     *
     * @param text   the link text
     * @param action the action
     * @return the button
     */
    public static @NotNull JButton bannerLink(@NotNull String text, @NotNull Runnable action) {
        @NotNull JButton button = new JButton(text);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(Color.WHITE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(font(Font.PLAIN, 13));
        button.setFocusPainted(true);
        applyMnemonic(button);
        button.getAccessibleContext().setAccessibleName(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    /**
     * Opens a URI with the Windows shell, reporting failures to the user.
     *
     * @param parent the parent component, may be {@code null}
     * @param uri    the URI
     */
    public static void openUri(@Nullable Component parent, @NotNull String uri) {
        if (!WindowsUtil.openUri(uri)) {
            error(parent, Messages.get("common.error"),
                    Messages.get("common.openFailed", uri));
        }
    }

    /**
     * Shows an error dialog on the event dispatch thread.
     *
     * @param parent  the parent component, may be {@code null}
     * @param title   the dialog title
     * @param message the message
     */
    public static void error(@Nullable Component parent, @NotNull String title,
                             @NotNull String message) {
        runOnEdt(() -> {
            SoundUtil.playSound(ConstantUtil.ERROR_SOUND);
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
        });
    }

    /**
     * Shows a warning dialog on the event dispatch thread.
     *
     * @param parent  the parent component
     * @param title   the dialog title
     * @param message the message
     */
    public static void warning(@Nullable Component parent, @NotNull String title,
                               @NotNull String message) {
        runOnEdt(() -> JOptionPane.showMessageDialog(parent, message, title,
                JOptionPane.WARNING_MESSAGE));
    }

    /**
     * Shows an informational dialog on the event dispatch thread.
     *
     * @param parent  the parent component
     * @param title   the dialog title
     * @param message the message
     */
    public static void info(@Nullable Component parent, @NotNull String title,
                            @NotNull String message) {
        runOnEdt(() -> JOptionPane.showMessageDialog(parent, message, title,
                JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * Runs code on the event dispatch thread, immediately when already there.
     *
     * @param runnable the code
     */
    public static void runOnEdt(@NotNull Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    /**
     * Wraps a component in a scroll pane with sensible scrolling.
     *
     * @param component the component
     * @return the scroll pane
     */
    public static @NotNull JScrollPane scroll(@NotNull Component component) {
        @NotNull JScrollPane pane = new JScrollPane(component);
        pane.setBorder(null);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }

    /**
     * Gets a theme-appropriate border color.
     *
     * @return the border color
     */
    public static @NotNull Color borderColor() {
        @Nullable Color color = UIManager.getColor("Component.borderColor");

        if (color == null) {
            color = UIManager.getColor("controlShadow");
        }

        return color == null ? Color.GRAY : color;
    }

    /**
     * Scales an icon to a square size.
     *
     * @param icon the icon
     * @param size the target size in pixels
     * @return the scaled icon
     */
    public static @NotNull ImageIcon scale(@NotNull ImageIcon icon, int size) {
        Image image = icon.getImage();

        if (image == null) {
            return icon;
        }

        @NotNull BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();

        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }

        return new ImageIcon(scaled);
    }

    /**
     * Escapes text for use in a Swing HTML label.
     *
     * @param text the text
     * @return the escaped text
     */
    public static @NotNull String escapeHtml(@NotNull String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
