package org.nwg.mdis;

import javax.swing.*;
import java.awt.*;

public class FontChooserDialog extends JDialog {
    private Font selectedFont;

    public FontChooserDialog(JFrame parent, Font current) {
        super(parent, "Choose Font", true);
        // simplified: using only size dropdown for demo
        String[] sizes = { "10","12","14","16","18","20","24","28","32" };
        JComboBox<String> sizeBox = new JComboBox<>(sizes);
        sizeBox.setSelectedItem(String.valueOf(current.getSize()));
        JLabel preview = new JLabel("Preview Text");
        preview.setFont(current);
        JButton ok = new JButton("OK");

        ok.addActionListener(e -> {
            int newSize = Integer.parseInt((String) sizeBox.getSelectedItem());
            selectedFont = new Font(current.getFamily(), current.getStyle(), newSize);
            dispose();
        });

        JPanel p = new JPanel();
        p.add(new JLabel("Size:"));
        p.add(sizeBox);
        p.add(ok);
        setLayout(new BorderLayout());
        add(p, BorderLayout.NORTH);
        add(preview, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    public Font getSelectedFont() {
        return selectedFont;
    }
}
