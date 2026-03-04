package pitheguy.schemconvert.ui;

import pitheguy.schemconvert.converter.Converter;
import pitheguy.schemconvert.converter.formats.SchematicFormat;
import pitheguy.schemconvert.converter.formats.SchematicFormats;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class FormatSelectionDropdown extends JPanel {
    private final JComboBox<String> comboBox;

    public FormatSelectionDropdown() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        JLabel label = new JLabel("Convert To: ");
        label.setPreferredSize(new Dimension(70, 20));
        add(label);
        comboBox = new JComboBox<>(Converter.SCHEMATIC_EXTENSIONS.toArray(new String[0]));
        add(comboBox);
    }

    public SchematicFormat getSelectedFormat() {
        return SchematicFormats.formatFromExtension(comboBox.getSelectedItem().toString());
    }

    public void addActionListener(ActionListener l) {
        comboBox.addActionListener(l);
    }

    public void setSelectedFormat(String extension) {
        comboBox.setSelectedItem(extension);
    }
}
