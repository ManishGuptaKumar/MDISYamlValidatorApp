package org.nwg.mdis;

import org.yaml.snakeyaml.Yaml;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.logging.Logger;

public class FileService {
    private static final Logger log = MDISLogger.getLogger();

    public static File promptOpen(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("MDIS - Open YAML");

        FileNameExtensionFilter yamlFilter = new FileNameExtensionFilter("YAML Files (*.yaml, *.yml)", "yaml", "yml");
        chooser.setFileFilter(yamlFilter);
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            // Optional: Check file extension if user bypasses filter
            String name = file.getName().toLowerCase();
            if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
                JOptionPane.showMessageDialog(parent,
                        "Please select a file with .yaml or .yml extension.",
                        "Invalid File", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return file;
        }
        return null;
    }

    public static File promptSaveAs(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("MDIS - Save / Save As YAML");

        FileNameExtensionFilter yamlFilter = new FileNameExtensionFilter("YAML Files (*.yaml, *.yml)", "yaml", "yml");
        chooser.setFileFilter(yamlFilter);
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showSaveDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String name = file.getName().toLowerCase();
            if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
                file = new File(file.getAbsolutePath() + ".yaml");  // default to .yaml
            }
            return file;
        }

        return null;
    }

    public static String getTagValue(String fileContent, String tagName) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(fileContent);
            Object value = data.get(tagName);
            return value != null ? value.toString() : "Unknown";
        } catch (Exception e) {
            // Log the error or handle it as needed
            log.severe("Error reading YAML tag: ("+ tagName +") Error : " + e.getMessage());
            return "Unknown";
        }
    }
    public static String readFile(File f) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(f.toPath()));
    }

    public static void writeFile(File f, String content) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            bw.write(content);
        }
    }
}
