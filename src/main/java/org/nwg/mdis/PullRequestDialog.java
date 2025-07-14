package org.nwg.mdis;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PullRequestDialog extends JDialog {

    private JComboBox<String> envCombo;
    private JTextField mcrField;
    private JTextField pipelineFileField;
    private JTextField settingsFileField;
    private JButton attachSettingsBtn;
    private DefaultListModel<File> sqlFileListModel = new DefaultListModel<>();
    private JList<File> sqlFileList;
    private JButton addSqlBtn, removeSqlBtn;
    private JButton createPrBtn, cancelBtn;
    private final Color customColor = Color.decode("#5A287D");
    private boolean confirmed = false;
//#3C1053
    private final YamlValidationService validator;
    private final RSyntaxTextArea editorTextArea;
    private final YamlEditorApp parent;

    public PullRequestDialog(YamlEditorApp parent, YamlValidationService validator, RSyntaxTextArea editorTextArea) {
        super(parent, "Create Pull Request", true);
        this.parent = parent;
        this.validator = validator;
        this.editorTextArea = editorTextArea;
        getContentPane().setBackground(customColor);
        initUI();
        setupListeners();
        pack();
        setLocationRelativeTo(parent);
        prefillPipelinePath();
        //setVisible(true);
    }

    private void prefillPipelinePath() {
        File currentFile = parent.getCurrentFile();
        if (currentFile != null) {
            pipelineFileField.setText(currentFile.getAbsolutePath());
        }
    }
    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        // === ENV & MCR PANEL ===
        JPanel envPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2,2,2,2);
        gbc.anchor = GridBagConstraints.WEST;  // Ensures left alignment
        gbc.fill = GridBagConstraints.HORIZONTAL; // Makes components expand horizontally
        gbc.weightx = 1.0;


        JLabel envLabel = new JLabel("Environment:");
        envCombo = new JComboBox<>(new String[]{"Dev", "Test", "AppDev", "AppTest", "Prod"});
        JLabel mcrLabel = new JLabel("MCR / TCR Number:");
        mcrField = new JTextField(20);
        mcrField.setEnabled(false);
// Environment row
        gbc.gridx = 0;
        gbc.gridy = 0;
        envPanel.add(envLabel, gbc);

        gbc.gridx = 1;
        envPanel.add(envCombo, gbc);

// MCR row
        gbc.gridx = 0;
        gbc.gridy = 1;
        envPanel.add(mcrLabel, gbc);

        gbc.gridx = 1;
        envPanel.add(mcrField, gbc);

        // === PIPELINE PANEL ===
        JPanel pipelinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel pipelineLabel = new JLabel("Pipeline YAML:");
        pipelineFileField = new JTextField(35);
        pipelineFileField.setEditable(false);
        pipelinePanel.add(pipelineLabel);
        pipelinePanel.add(pipelineFileField);

        // === SETTINGS PANEL ===
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel settingsLabel = new JLabel("Settings YAML:");
        settingsFileField = new JTextField(32);
        settingsFileField.setEditable(false);
        attachSettingsBtn = new JButton("...");
        attachSettingsBtn.setPreferredSize(new Dimension(25, 18));

        settingsPanel.add(settingsLabel);
        settingsPanel.add(settingsFileField);
        settingsPanel.add(attachSettingsBtn);

        // === SQL PANEL ===
        TitledBorder whiteBorder = BorderFactory.createTitledBorder("SQL Files (optional)");
        whiteBorder.setTitleColor(Color.WHITE);                      // Title text color
        whiteBorder.setBorder(BorderFactory.createLineBorder(Color.WHITE));  // Border line color
        JPanel sqlPanel = new JPanel(new BorderLayout(5, 5));
        sqlPanel.setBorder(whiteBorder);

        sqlFileList = new JList<>(sqlFileListModel);
        sqlFileList.setVisibleRowCount(5);
        JScrollPane scrollPane = new JScrollPane(sqlFileList);

        JPanel sqlBtnPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        addSqlBtn = new JButton("Add SQL File");
        removeSqlBtn = new JButton("Remove Selected");
        removeSqlBtn.setEnabled(false);
        sqlBtnPanel.add(addSqlBtn);
        sqlBtnPanel.add(removeSqlBtn);

        sqlPanel.add(scrollPane, BorderLayout.CENTER);
        sqlPanel.add(sqlBtnPanel, BorderLayout.SOUTH);

        // === BUTTON PANEL ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        createPrBtn = new JButton("Create PR");
        createPrBtn.setEnabled(true);
        cancelBtn = new JButton("Cancel");
        buttonPanel.add(createPrBtn);
        buttonPanel.add(cancelBtn);

        // === MAIN PANEL STACKED VERTICALLY ===
        JPanel mainPanel = new JPanel();


        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(40, 20, 20, 20));

        mainPanel.add(pipelinePanel);
        mainPanel.add(Box.createVerticalStrut(2));

        mainPanel.add(settingsPanel);
        mainPanel.add(Box.createVerticalStrut(2));

        mainPanel.add(sqlPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        mainPanel.add(envPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Apply dark background to panels
        mainPanel.setBackground(customColor);
        pipelinePanel.setBackground(customColor);
        settingsPanel.setBackground(customColor);
        envPanel.setBackground(customColor);
        sqlPanel.setBackground(customColor);
        sqlBtnPanel.setBackground(customColor);
        buttonPanel.setBackground(customColor);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 5, 20));

// Optionally change text color to white
        Color fg = Color.WHITE;
        pipelineLabel.setForeground(fg);
        settingsLabel.setForeground(fg);
        envLabel.setForeground(fg);
        mcrLabel.setForeground(fg);
        sqlPanel.setForeground(fg);
        sqlFileList.setBackground(Color.WHITE);  // Keep list readable

    }

    private void setupListeners() {
        envCombo.addActionListener(e -> {
            boolean isProd = "Prod".equals(envCombo.getSelectedItem());
            mcrField.setEnabled(isProd);
            validateForm();
        });

        mcrField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            public void update() { validateForm(); }
        });


        attachSettingsBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("YAML Files", "yaml", "yml"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                settingsFileField.setText(file.getAbsolutePath());
            }
        });

        addSqlBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("SQL Files", "sql"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                for (File f : chooser.getSelectedFiles()) {
                    if (!sqlFileListModel.contains(f)) {
                        sqlFileListModel.addElement(f);
                    }
                }
            }
            validateForm();
        });

        removeSqlBtn.addActionListener(e -> {
            List<File> selected = sqlFileList.getSelectedValuesList();
            selected.forEach(sqlFileListModel::removeElement);
            validateForm();
        });

        sqlFileList.addListSelectionListener(e -> {
            removeSqlBtn.setEnabled(!sqlFileList.isSelectionEmpty());
        });

        editorTextArea.getDocument().addDocumentListener(new SimpleDocumentListener() {
            public void update() { validateForm(); }
        });

        createPrBtn.addActionListener(e -> {
            confirmed = true;
            setVisible(false);
        });

        cancelBtn.addActionListener(e -> {
            confirmed = false;
            setVisible(false);
        });
    }

    private void validateForm() {
        String yamlText = editorTextArea.getText();
        String validationResult = validator.validate(yamlText);

        // boolean isValidYaml = validationResult != null && validationResult.trim().isEmpty();
        assert validationResult != null;
        boolean isValidYaml = validationResult.replace(" ", "").equalsIgnoreCase("YAMLisvalid!");

        String env = (String) envCombo.getSelectedItem();
        boolean isProd = "Prod".equals(env);
        boolean mcrValid = !isProd || (mcrField.getText() != null && !mcrField.getText().trim().isEmpty());

        createPrBtn.setEnabled(isValidYaml && mcrValid);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getSelectedEnv() {
        return (String) envCombo.getSelectedItem();
    }

    public String getMcrNumber() {
        return mcrField.getText().trim();
    }
    public File getPipelineFile() {
        String path = pipelineFileField.getText().trim();
        return path.isEmpty() ? null : new File(path);
    }

    public File getSettingsFile() {
        String path = settingsFileField.getText().trim();
        return path.isEmpty() ? null : new File(path);
    }

    public List<File> getSqlFiles() {
        List<File> list = new ArrayList<>();
        for (int i = 0; i < sqlFileListModel.size(); i++) {
            list.add(sqlFileListModel.get(i));
        }
        return list;
    }
/*    private void applyTheme(Component comp) {
        comp.setBackground(customColor);
        if (comp instanceof JLabel) {
            ((JLabel) comp).setForeground(Color.WHITE);
        }
        if (comp instanceof JPanel) {
            for (Component child : ((JPanel) comp).getComponents()) {
                applyTheme(child);
            }
        }
    }*/
    // Simple listener utility
    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update();
        default void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
        default void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
        default void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
    }
}
