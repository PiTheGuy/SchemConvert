package pitheguy.schemconvert.ui;

import pitheguy.schemconvert.converter.*;
import pitheguy.schemconvert.converter.formats.SchematicFormat;
import pitheguy.schemconvert.nbt.NbtException;
import pitheguy.schemconvert.util.Util;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class Gui extends JFrame {
    private JTextField inputPathField;
    private JTextField outputPathField;
    private File[] selectedFiles;
    private FormatSelectionDropdown formatDropdown;
    private JButton convertButton;
    private String lastPath = null;
    private JLabel outputLabel;

    public Gui() {
        super("SchemConvert");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createInputPathPanel());
        panel.add(createOutputPathPanel());
        formatDropdown = new FormatSelectionDropdown();
        formatDropdown.addActionListener(e -> {
            if (!outputPathField.getText().isEmpty()) {
                String outputPath = Util.stripExtension(outputPathField.getText())
                        + formatDropdown.getSelectedFormat().getExtension();
                outputPathField.setText(outputPath);
            }
        });
        panel.add(formatDropdown);
        panel.add(createButtonPanel());
        add(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createInputPathPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        JLabel label = new JLabel("Input File:");
        label.setPreferredSize(new Dimension(70, 20));
        panel.add(label);
        inputPathField = new JTextField(20);
        inputPathField.setEditable(false);
        panel.add(inputPathField);
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = createFileChooser();
            chooser.setMultiSelectionEnabled(true);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = chooser.getSelectedFiles();
                inputPathField.setText(
                        selectedFiles.length == 1 ? selectedFiles[0].getAbsolutePath() : "Multiple files selected");
                if (outputPathField.getText().isEmpty() || selectedFiles.length > 1) {
                    String outputPath = selectedFiles.length == 1
                            ? Util.stripExtension(selectedFiles[0].getAbsolutePath())
                                    + formatDropdown.getSelectedFormat().getExtension()
                            : selectedFiles[0].getParentFile().getAbsolutePath();
                    outputPathField.setText(outputPath);
                }
                this.selectedFiles = selectedFiles;
                lastPath = selectedFiles[0].getParentFile().getAbsolutePath();
                outputLabel.setText(selectedFiles.length > 1 ? "Output:" : "Output File:");
            }
            updateButtonState();
        });
        panel.add(browseButton);
        return panel;
    }

    private JPanel createOutputPathPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        outputLabel = new JLabel("Output File:");
        outputLabel.setPreferredSize(new Dimension(70, 20));
        panel.add(outputLabel);
        outputPathField = new JTextField(20);
        outputPathField.setEditable(false);
        panel.add(outputPathField);
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = createFileChooser();
            if (selectedFiles != null && selectedFiles.length > 1)
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!inputPathField.getText().isEmpty())
                chooser.setCurrentDirectory(selectedFiles[0].getParentFile());
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
                if (selectedFiles != null && selectedFiles.length > 1) {
                    String extension = Util.getExtension(outputPathField.getText());
                    if (Converter.SCHEMATIC_EXTENSIONS.contains(extension))
                        formatDropdown.setSelectedFormat(extension);
                }
            }
            updateButtonState();
        });
        panel.add(browseButton);
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        convertButton = new JButton("Convert");
        convertButton.setEnabled(false);
        convertButton.addActionListener(e -> convert());
        panel.add(convertButton);
        return panel;
    }

    private void updateButtonState() {
        convertButton.setEnabled(!inputPathField.getText().isEmpty() && !outputPathField.getText().isEmpty());
    }

    private void convert() {
        if (selectedFiles.length > 1) {
            convertMultiple();
            return;
        }
        try {
            File output = new File(outputPathField.getText());
            SchematicFormat format = formatDropdown.getSelectedFormat();
            new Converter().convert(selectedFiles[0], output, format);
            outputPathField.setText("");
            JOptionPane.showMessageDialog(this, "Schematic successfully converted!", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "An error occurred while reading the input file or writing the output file to disk.", "Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (NbtException | SchematicParseException e) {
            JOptionPane.showMessageDialog(this,
                    "An error occurred while parsing the schematic. If you're sure this is a valid schematic, please report this!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } catch (ConversionException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "An unexpected error occurred.", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void convertMultiple() {
        File output = new File(outputPathField.getText());
        SchematicFormat format = formatDropdown.getSelectedFormat();
        try {
            List<File> failedFiles = new Converter().convert(selectedFiles, output, format);
            outputPathField.setText("");
            if (failedFiles.isEmpty())
                JOptionPane.showMessageDialog(this, "Schematics successfully converted!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            else {
                StringBuilder sb = new StringBuilder();
                sb.append("The following files failed to convert:\n");
                for (File file : failedFiles)
                    sb.append("- ").append(file.getName()).append("\n");

                sb.append("\n\n").append(selectedFiles.length).append(" other files converted successfully!");
                JOptionPane.showMessageDialog(this, sb.toString(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "An unexpected error occurred", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JFileChooser createFileChooser() {
        JFileChooser chooser = new JFileChooser(lastPath);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || Converter.SCHEMATIC_EXTENSIONS.stream()
                        .anyMatch(ext -> f.getName().toLowerCase().endsWith(ext));
            }

            @Override
            public String getDescription() {
                StringBuilder sb = new StringBuilder();
                sb.append("Schematic files (");
                sb.append(Converter.SCHEMATIC_EXTENSIONS.stream().map(ext -> "*" + ext)
                        .collect(Collectors.joining("; ")));
                sb.append(")");
                return sb.toString();
            }
        });
        return chooser;
    }

}
