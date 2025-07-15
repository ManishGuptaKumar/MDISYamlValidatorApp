package org.nwg.mdis;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.nwg.mdis.FileService.makeTooltip;

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
    private final YamlValidationService validator;
    private final RSyntaxTextArea editorTextArea;
    private final YamlEditorApp parent;

    public PullRequestDialog(YamlEditorApp parent, YamlValidationService validator, RSyntaxTextArea editorTextArea) {
        super(parent, "Create Merge Request", true);
        this.parent = parent;
        this.validator = validator;
        this.editorTextArea = editorTextArea;
        getContentPane().setBackground(customColor);
        initUI();
        setupListeners();
        pack();
        setLocationRelativeTo(parent);
        prefillPipelinePath();
    }

    private void prefillPipelinePath() {
        File currentFile = parent.getCurrentFile();
        if (currentFile != null) {
            pipelineFileField.setText(currentFile.getAbsolutePath());
        }
    }
    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // === MESSAGE PANEL ===
        JPanel messagePanel = new JPanel(new BorderLayout(10, 0));
        messagePanel.setBackground(customColor);

        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/gitlab-logo-500.png")));
        Image scaledImage = icon.getImage().getScaledInstance(40, 40, Image.SCALE_SMOOTH);
        JLabel iconLabel = new JLabel(new ImageIcon(scaledImage));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel messageLabel = new JLabel("<html><body style='width:500px;'>"
                + "Please provide the necessary files (Yaml and SQL) to create a GitLab Merge Request.<br>"
                + "MCR is required for production deployments only."
                + "</body></html>");
        messageLabel.setFont(new Font("Arial", Font.BOLD, 12));
        messageLabel.setForeground(Color.WHITE);

        messagePanel.add(iconLabel, BorderLayout.WEST);
        messagePanel.add(messageLabel, BorderLayout.CENTER);

        // === COMMON FIELD DIMENSIONS ===
        Dimension fieldSize = new Dimension(350, 25);

        // === FORM PANEL ===
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(customColor);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // === Environment and MCR on same row ===
        JLabel envLabel = new JLabel("Environment:");
        envLabel.setForeground(Color.WHITE);
        envCombo = new JComboBox<>(new String[]{"Dev", "Test", "AppDev", "AppTest", "Prod"});
        envCombo.setPreferredSize(new Dimension(160, 25));
        envCombo.setToolTipText(makeTooltip(
                "/tooltips/eniv.png",
                "Environment",
                "Select the environment where this Merge request will be merged (e.g., Dev, Test, Prod)." +
                        "<p> each environment will be associated with one repository branch"
        ));

        JLabel mcrLabel = new JLabel("MCR / TCR Number:");
        mcrLabel.setForeground(Color.WHITE);
        mcrField = new JTextField();
        mcrField.setText("None");
        mcrField.setEnabled(false);
        mcrField.setBackground(Color.GRAY);
        mcrField.setPreferredSize(new Dimension(160, 25));
        mcrField.setToolTipText(makeTooltip(
                "/tooltips/mcr.png",
                "MCR / TCR Number",
                "Enter a valid MCR (for production changes) or TCR number for auditing purposes."
        ));
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(envLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(envCombo, gbc);

        gbc.gridx = 2;
        formPanel.add(mcrLabel, gbc);
        gbc.gridx = 3;
        formPanel.add(mcrField, gbc);
        row++;

        // === Pipeline YAML ===
        JLabel pipelineLabel = new JLabel("Pipeline Config File:");
        pipelineLabel.setForeground(Color.WHITE);
        pipelineFileField = new JTextField();
        pipelineFileField.setEditable(false);
        pipelineFileField.setPreferredSize(fieldSize);
        pipelineFileField.setToolTipText(makeTooltip(
                "/tooltips/yaml.png",
                "Pipeline Configuration File",
                "Select a pipeline configuration file in YAML format. The file must follow the expected schema and structure."

        ));

        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(pipelineLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        formPanel.add(pipelineFileField, gbc);
        gbc.gridwidth = 1;
        row++;

        // === Settings YAML ===
        JLabel settingsLabel = new JLabel("Manifest Config File");
        settingsLabel.setForeground(Color.WHITE);
        settingsFileField = new JTextField();
        settingsFileField.setEditable(false);
        settingsFileField.setPreferredSize(fieldSize);
        attachSettingsBtn = new JButton("Select ...");
        attachSettingsBtn.setPreferredSize(new Dimension(25, 25));
        settingsFileField.setToolTipText(makeTooltip(
                "/tooltips/manifest.png",
                "Manifest Configuration File (Optional)",
                "Provide a YAML file containing key-value configuration settings. This file helps customize the pipeline execution context."
        ));

        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(settingsLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        formPanel.add(settingsFileField, gbc);
        gbc.gridx = 3; gbc.gridwidth = 1;
        formPanel.add(attachSettingsBtn, gbc);
        row++;

        // === SQL PANEL ===
        TitledBorder whiteBorder = BorderFactory.createTitledBorder("SQL Files (optional)");
        whiteBorder.setTitleColor(Color.WHITE);
        whiteBorder.setBorder(BorderFactory.createLineBorder(Color.WHITE));

        JPanel sqlPanel = new JPanel(new BorderLayout(5, 5));
        sqlPanel.setBorder(whiteBorder);
        sqlPanel.setBackground(customColor);

        sqlFileList = new JList<>(sqlFileListModel);
        sqlFileList.setVisibleRowCount(5);
        sqlFileList.setToolTipText(makeTooltip(
                "/tooltips/sqlfile.png",
                "SQL File(s)",
                "Select one or more SQL files to include in the Merge Request. This is optional and only needed if your pipeline YAML references SQL scripts."
        ));
        JScrollPane scrollPane = new JScrollPane(sqlFileList);


        JPanel sqlBtnPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        addSqlBtn = new JButton("Add SQL File");
        addSqlBtn.setToolTipText(makeTooltip(
                "/tooltips/addFile.png",
                "Add SQL File",
                "Browse and select one or more SQL files to include in the Merge Request. These are optional and only required if referenced in your pipeline."
        ));

        removeSqlBtn = new JButton("Remove Selected");
        removeSqlBtn.setToolTipText(makeTooltip(
                "/tooltips/deleteFile.png",
                "Remove Selected File",
                "Remove the selected SQL file(s) from the list. This does not delete files from disk — only from the Merge request."
        ));
        removeSqlBtn.setEnabled(false);
        sqlBtnPanel.add(addSqlBtn);
        sqlBtnPanel.add(removeSqlBtn);
        sqlBtnPanel.setBackground(customColor);

        sqlPanel.add(scrollPane, BorderLayout.CENTER);
        sqlPanel.add(sqlBtnPanel, BorderLayout.SOUTH);

        // === BUTTON PANEL ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        createPrBtn = new JButton("Create Merge Request");
        createPrBtn.setToolTipText(makeTooltip(
                "/icons/PullReq.png",
                "Create Merge Request",
                "Directly submit a GitLab Merge Request using the selected environment, pipeline, settings, and SQL files. Ensure all fields are valid."
        ));

        cancelBtn = new JButton("Cancel");
        buttonPanel.add(createPrBtn);
        buttonPanel.add(cancelBtn);
        buttonPanel.setBackground(customColor);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 10, 20));

        // === MAIN PANEL STACK ===
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(customColor);

        mainPanel.add(messagePanel);
        mainPanel.add(Box.createVerticalStrut(12));
        mainPanel.add(formPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(sqlPanel);

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        sqlFileList.setBackground(Color.WHITE);  // List readability
    }

    private void setupListeners() {
        envCombo.addActionListener(e -> {
            boolean isProd = "Prod".equals(envCombo.getSelectedItem());
            mcrField.setEnabled(isProd);
            mcrField.setBackground(Color.WHITE);
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
        if(isProd){ mcrField.setEnabled(true);mcrField.setBackground(Color.WHITE);}
        else { mcrField.setEnabled(false);mcrField.setBackground(Color.GRAY);}
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

    // Simple listener utility
    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update();
        default void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
        default void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
        default void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
    }
}
