package to.wildeprojekt.modpacksettings.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;

public class PanelDialog {
    public static void showAllocatedRamDialog(){
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }catch (Exception ignored) {}

        JFrame taskbarFrame = new JFrame();
        taskbarFrame.setUndecorated(true);
        taskbarFrame.setType(Window.Type.NORMAL);
        taskbarFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        taskbarFrame.setIconImage(Toolkit.getDefaultToolkit().getImage(
                PanelDialog.class.getResource("/assets/modpacksettings/icon.png")));
        taskbarFrame.setSize(0, 0);
        taskbarFrame.setLocation(-10000, -10000); // move it offscreen
        taskbarFrame.setVisible(true); // must be visible for taskbar entry

        JDialog dialog = new JDialog(taskbarFrame, "Performance Warning", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(15,15));
        dialog.setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));


        URL url = PanelDialog.class.getResource("/assets/ardasettings/icon.png");
        ImageIcon icon = null;

        if (url != null){
            Image rawImage = new ImageIcon(url).getImage();
            Image scaledImage = getScaledImage(rawImage, 128);
            icon = new ImageIcon(scaledImage);
        }

        if (icon != null){
            JLabel imageLabel = new JLabel(icon);
            panel.add(imageLabel, BorderLayout.WEST);
        }


        JPanel messagePanel = new JPanel();

        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("⚠ Performance Warning");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        messagePanel.add(title);
        messagePanel.add(Box.createVerticalStrut(5));

        JLabel text = new JLabel("<html><body style='font-size:11px;'>You have less then <b> 8192 MB</b> of memory allocated.<br>"
                + "Wildeprojekt <b>needs</b> at least <b>8192 MB</b> to function properly!</body></html>");
        messagePanel.add(text);

        JLabel link = new JLabel("<html><a style='font-size:11px;' href=''>View the guide on how to increase allocated memory here.</a></html>");
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://www.ardacraft.me/resources/guide-to-allocating-more-memory"));
                } catch (Exception ignored) {}
            }
        });
        messagePanel.add(link);
        messagePanel.add(Box.createVerticalStrut(10));

        JLabel warning = new JLabel("<html> <body style='font-size:11px;'>If you continue without allocating at least 8192 MB of memory you <b>will </b>run into issues.<br>" +
                "We cannot provide support for you if you have not allocated the correct amount of memory.</body></html>");
        messagePanel.add(warning);
        messagePanel.add(Box.createVerticalStrut(10));

        JCheckBox confirmBox = new JCheckBox("I understand the risk and want to continue anyway.");
        confirmBox.setFont(confirmBox.getFont().deriveFont(Font.PLAIN, 13f));
        messagePanel.add(confirmBox);

        panel.add(messagePanel, BorderLayout.CENTER);

        dialog.add(panel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton quitButton = new JButton("Quit");
        JButton continueButton = new JButton("Continue");
        continueButton.setEnabled(false);

        buttons.add(quitButton);
        buttons.add(continueButton);
        dialog.add(buttons, BorderLayout.SOUTH);

        quitButton.addActionListener(e -> {
            dialog.dispose();
            System.exit(0);
        });

        continueButton.addActionListener(e -> {
            dialog.dispose();
        });

        confirmBox.addItemListener(e -> continueButton.setEnabled(confirmBox.isSelected()));


        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        dialog.setVisible(true);
        dialog.toFront();
        dialog.requestFocus();

        dialog.setType(Window.Type.NORMAL);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setAlwaysOnTop(true);

        dialog.setFocusableWindowState(true);
        dialog.setFocusable(true);


        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                taskbarFrame.dispose();
            }
        });

    }


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
}






