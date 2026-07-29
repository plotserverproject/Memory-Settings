package org.wildeprojekt.modpacksettings.client;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.wildeprojekt.modpacksettings.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

/**
 * Builds and displays Swing dialogs used by the client before launch.
 */
public class PanelDialog {

    /**
     * Shows a modal warning when the JVM has less memory allocated than the modpack requires.
     */
    public static void showAllocatedRamDialog() {
        show(new Spec()
                .windowTitle(Messages.get("ram_dialog_title"))
                .heading(Messages.get("ram_dialog_heading"))
                .bodyHtml(html(Messages.get("ram_dialog_body_1"), Messages.get("ram_dialog_body_2")))
                .link(Messages.get("ram_dialog_link"), Messages.get("ram_dialog_link_url"))
                .footerHtml(html(Messages.get("ram_dialog_footer_1"), Messages.get("ram_dialog_footer_2")))
                .confirmText(Messages.get("ram_dialog_confirm"))
                .quitLabel(Messages.get("button_quit")));
    }

    /**
     * Shows a modal dialog matching the provided specification.
     *
     * @param spec dialog content and button configuration
     */
    public static void show(Spec spec) {

        Objects.requireNonNull(spec, "spec");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        JFrame taskbarFrame = new JFrame();
        taskbarFrame.setUndecorated(true);
        taskbarFrame.setType(Window.Type.NORMAL);
        taskbarFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        taskbarFrame.setIconImage(Toolkit.getDefaultToolkit().getImage(
                PanelDialog.class.getResource("/assets/modpacksettings/icon.png")));
        taskbarFrame.setSize(0, 0);
        taskbarFrame.setLocation(-10000, -10000); // move it offscreen
        taskbarFrame.setVisible(true); // must be visible for taskbar entry

        JDialog dialog = new JDialog(taskbarFrame, spec.windowTitle, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        URL url = PanelDialog.class.getResource("/assets/modpacksettings/icon.png");
        ImageIcon icon = null;

        if (url != null) {
            Image rawImage = new ImageIcon(url).getImage();
            Image scaledImage = getScaledImage(rawImage, 128);
            icon = new ImageIcon(scaledImage);
        }

        if (icon != null) {
            JLabel imageLabel = new JLabel(icon);
            panel.add(imageLabel, BorderLayout.WEST);
        }

        JPanel messagePanel = new JPanel();

        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(spec.heading);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        messagePanel.add(title);
        messagePanel.add(Box.createVerticalStrut(5));

        JLabel text = new JLabel(spec.bodyHtml);
        messagePanel.add(text);

        if (spec.linkText != null && spec.linkUrl != null) {
            JLabel link = new JLabel("<html><a style='font-size:11px;' href=''>" + escapeHtml(spec.linkText) + "</a></html>");
            link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            link.addMouseListener(new MouseAdapter() {
                /**
                 * Opens the configured help link in the system browser.
                 *
                 * @param e mouse click event from the guide link
                 */
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(new URI(spec.linkUrl));
                    } catch (Exception ignored) {
                    }
                }
            });
            messagePanel.add(link);
            messagePanel.add(Box.createVerticalStrut(10));
        }

        if (spec.footerHtml != null) {
            JLabel warning = new JLabel(spec.footerHtml);
            messagePanel.add(warning);
            messagePanel.add(Box.createVerticalStrut(10));
        }

        JCheckBox confirmBox = null;
        if (spec.confirmText != null) {
            confirmBox = new JCheckBox(spec.confirmText);
            confirmBox.setFont(confirmBox.getFont().deriveFont(Font.PLAIN, 13f));
            messagePanel.add(confirmBox);
        }

        panel.add(messagePanel, BorderLayout.CENTER);

        dialog.add(panel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton continueButton = new JButton(spec.continueLabel);
        continueButton.setEnabled(confirmBox == null);

        if (spec.quitLabel != null) {
            JButton quitButton = new JButton(spec.quitLabel);
            quitButton.addActionListener(e -> {
                dialog.dispose();
                System.exit(0);
            });
            buttons.add(quitButton);
        }
        buttons.add(continueButton);
        dialog.add(buttons, BorderLayout.SOUTH);

        continueButton.addActionListener(e -> dialog.dispose());

        if (confirmBox != null) {
            JCheckBox finalConfirmBox = confirmBox;
            confirmBox.addItemListener(e -> continueButton.setEnabled(finalConfirmBox.isSelected()));
        }

        dialog.addWindowListener(new WindowAdapter() {
            /**
             * Disposes the hidden taskbar owner frame after the warning dialog closes.
             *
             * @param e window event emitted by the warning dialog
             */
            @Override
            public void windowClosed(WindowEvent e) {
                taskbarFrame.dispose();
            }
        });

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        dialog.setVisible(true);

    }

    /**
     * Wraps message lines in the small HTML body used by Swing labels.
     *
     * @param lines message lines to join with HTML line breaks
     * @return HTML label text
     */
    private static String html(String... lines) {
        return "<html><body style='font-size:11px;'>" + String.join("<br>", lines) + "</body></html>";
    }

    /**
     * Scales an image to the requested width while preserving its aspect ratio.
     *
     * @param srcImg      source image to scale
     * @param targetWidth desired output width in pixels
     * @return scaled image with the computed proportional height
     */
    @SuppressWarnings("SameParameterValue")
    private static Image getScaledImage(Image srcImg, int targetWidth) {

        int originalWidth = srcImg.getWidth(null);
        int originalHeight = srcImg.getHeight(null);

        int targetHeight = (int) ((double) originalHeight / originalWidth * targetWidth);

        if (originalWidth > targetWidth * 2) {
            int w = originalWidth;
            int h = originalHeight;

            BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(srcImg, 0, 0, w, h, null);

            while (w > targetWidth || h > targetHeight) {
                w = Math.max(targetWidth, w / 2);
                h = Math.max(targetHeight, h / 2);

                BufferedImage tmp2 = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                g2 = tmp2.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(tmp, 0, 0, w, h, null);
                tmp = tmp2;
            }

            g2.dispose();
            return tmp;
        }

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(srcImg, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();

        return resized;
    }

    /**
     * Escapes plain text for use inside simple Swing HTML labels.
     *
     * @param value plain text value to escape
     * @return HTML-safe text
     */
    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Shows a modal warning when the resource pack updater fails.
     *
     * @param reason failure detail to display in the dialog
     */
    public static void showResourcePackFailureDialog(String reason) {
        String safeReason = escapeHtml(Objects.requireNonNullElse(reason, Messages.get("error_unknown")));
        show(new Spec()
                .windowTitle(Messages.get("error_pack_dialog_title"))
                .heading(Messages.get("error_pack_dialog_heading"))
                .bodyHtml(html(Messages.get("error_pack_dialog_body_1"), Messages.get("error_pack_dialog_body_2")))
                .footerHtml(html("<b>" + Messages.get("error_pack_dialog_details") + "</b> " + safeReason)));
    }

    /**
     * Dialog configuration used by the reusable modal renderer.
     */
    @Setter
    @Accessors(fluent = true, chain = true)
    public static final class Spec {

        /** Label used for the Continue button. */
        private final String continueLabel = Messages.get("button_continue");

        /** Dialog title shown in the window title bar. */
        private String windowTitle;

        /** Large bold heading shown at the top of the message area. */
        private String heading;

        /** Main HTML message body shown below the heading. */
        private String bodyHtml;

        /** Optional hyperlink text shown below the body. */
        private String linkText;

        /** Optional hyperlink target opened in the system browser. */
        private String linkUrl;

        /** Optional HTML footer message shown below the link. */
        private String footerHtml;

        /** Optional confirmation checkbox label that gates the Continue button. */
        private String confirmText;

        /** Optional label used for the Quit button. */
        private String quitLabel;

        /**
         * Sets an optional hyperlink row.
         *
         * @param linkText hyperlink text to display
         * @param linkUrl  URL opened when the hyperlink is clicked
         * @return this spec for chaining
         */
        public Spec link(String linkText, String linkUrl) {
            this.linkText = linkText;
            this.linkUrl = linkUrl;
            return this;
        }
    }
}
