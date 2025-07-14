package org.nwg.mdis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.nwg.mdis.FileService.getTagValue;

public class YamlEditorApp extends JFrame {
    private final RSyntaxTextArea textArea = new RSyntaxTextArea(20, 60);
    private final JTextArea outputArea = new JTextArea(6, 60);
    private final YamlValidationService validator = new YamlValidationService();
    private static final Logger log = MDISLogger.getLogger();

    private final GitLabUserDialog gitService = new GitLabUserDialog(this);
    private File currentFile;
    Color customColor = Color.decode("#3C1053");

    public YamlEditorApp() {
        log.info("Loading MDIS - Yaml Editor Application");
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
        textArea.setCodeFoldingEnabled(true);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 14));

        setTitle("MDIS- YAML Editor + Validator");
        setLayout(new BorderLayout());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/yaml.png")));
        setIconImage(icon.getImage());
        // Header banner
        JLabel banner = new JLabel(loadBanner("Natwest"));
        banner.setHorizontalAlignment(JLabel.CENTER);
        add(banner, BorderLayout.NORTH);

        // Editor area
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
        textArea.setCodeFoldingEnabled(true);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 14));

        JScrollPane editorScroll = new RTextScrollPane(textArea);
        add(editorScroll, BorderLayout.CENTER);

        // Output pane
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane outScroll = new JScrollPane(outputArea);
        outScroll.setBorder(BorderFactory.createTitledBorder("Validation Output"));
        add(outScroll, BorderLayout.SOUTH);

        // Toolbar and Menu
        add(createToolBar(), BorderLayout.WEST);
        setJMenuBar(createMenuBar());

        pack();
        getContentPane().setBackground(customColor);

        setLocationRelativeTo(null);
        setVisible(true);
        log.info("MDIS GUI - Yaml Editor Application Loaded...");
    }
    private ImageIcon loadIcon(String name) {
        URL url = getClass().getResource("/icons/" + name + ".png");
        if (url != null) {
            ImageIcon icon = new ImageIcon(url);
            Image scaledImage = icon.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH);
            return new ImageIcon(scaledImage);
        }
        return new ImageIcon(); // fallback empty icon
    }

    private ImageIcon loadBanner(String name) {
        URL url = getClass().getResource("/icons/" + name + ".png");
        return (url != null) ? new ImageIcon(url) : new ImageIcon();
    }

    private JToolBar createToolBar() {
        log.info("Loading Application Toolbars");
        JToolBar tb = new JToolBar(JToolBar.VERTICAL);
        tb.setFloatable(false);
        tb.setBackground(Color.WHITE);

        JButton openBtn = new JButton(loadIcon("open"));
        openBtn.setToolTipText("Open YAML file");
        openBtn.addActionListener(e -> openFile());

        JButton saveBtn = new JButton(loadIcon("save"));
        saveBtn.setToolTipText("Save current file");
        saveBtn.addActionListener(e -> saveFile());

        JButton saveAsBtn = new JButton(loadIcon("saveas"));
        saveAsBtn.setToolTipText("Save As");
        saveAsBtn.addActionListener(e -> saveAsFile());

        JButton beautifyBtn = new JButton(loadIcon("beautify"));
        beautifyBtn.setToolTipText("Beautify YAML");
        beautifyBtn.addActionListener(e -> beautifyYaml());

        JButton validateBtn = new JButton(loadIcon("validate"));
        validateBtn.setToolTipText("Validate YAML");
        validateBtn.addActionListener(e -> validateYaml());

        JButton clearBtn = new JButton(loadIcon("clear"));
        clearBtn.setToolTipText("Clear editor");
        clearBtn.addActionListener(e -> textArea.setText(""));

        JButton fontBtn = new JButton(loadIcon("font"));
        fontBtn.setToolTipText("Change font");
        fontBtn.addActionListener(e -> {
            FontChooserDialog dlg = new FontChooserDialog(this, textArea.getFont());
            Font f = dlg.getSelectedFont();
            if (f != null) textArea.setFont(f);
        });

        JButton createPR = new JButton(loadIcon("PullReq"));
        createPR.setToolTipText("Create Pull Request");
        createPR.addActionListener(e -> {
            try {
                validateAndOpenPullRequestDialog();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        JButton userProfile = new JButton(loadIcon("User"));
        userProfile.setToolTipText("Manage Your Profile");
        userProfile.addActionListener(e -> gitService.MyProfile());
        tb.setOpaque(true);
        tb.add(openBtn);
        tb.add(saveBtn);
        tb.add(saveAsBtn);
        tb.addSeparator();
        tb.add(beautifyBtn);
        tb.add(validateBtn);
        tb.add(clearBtn);
        tb.addSeparator();
        tb.add(fontBtn);
        tb.addSeparator();
        tb.add(createPR);
        tb.add(userProfile);
        log.info("Application Toolbars Loaded");
        return tb;
    }


    private void validateAndOpenPullRequestDialog() throws IOException {
        log.info("Creation of Pull Request Validation Initiated");
        String yamlText = textArea.getText();
        if (yamlText == null || yamlText.trim().isEmpty()) {
            log.warning("YAML content is empty, aborting validation and Pull Request");
            JOptionPane.showMessageDialog(this,
                    "YAML content is empty. Please enter or open a YAML file before proceeding.",
                    "Empty YAML",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (validateYaml()) {
        //if (true) {
            log.info("YAML Validated Successfully..Preparing Pull Request Dialog");
            PullRequestDialog prDialog = new PullRequestDialog(this, validator, textArea);
            prDialog.setVisible(true);
            if (prDialog.isConfirmed()) {
                String baseFilePath = prDialog.getPipelineFile().getAbsolutePath();
                byte[] bytes = Files.readAllBytes(Paths.get(baseFilePath));
                String fileContent = new String(bytes, StandardCharsets.UTF_8);
                log.info("Pipeline Config : " + baseFilePath);
                File settingsFile = prDialog.getSettingsFile();
                String optionalFilePath = (settingsFile != null) ? settingsFile.getAbsolutePath() : null;
                log.info("Manifest Config : " + settingsFile);
                java.util.List<File>files = prDialog.getSqlFiles();
                List<String> additionalFiles = files.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
                log.info("SQL Files : " + additionalFiles);
                String targetBranch=prDialog.getSelectedEnv();

                String Environment = targetBranch;
                String PipelineName = getTagValue(fileContent,"pipeline_id");
                String TemplateType = getTagValue(fileContent,"template_id");
                String SpokeName = getTagValue(fileContent,"spoke_name");
                String CreatedBy = System.getProperty("user.name");
                String featureBranch=String.format("PipelineCreateOrUpdate/%s_%s_%s_%s",
                SpokeName,PipelineName,CreatedBy,System.currentTimeMillis());
                String commitMessage=String.format("MDIS %s Pipeline Configuration Create/Update",PipelineName);
                String pullRequestTitle=PipelineName + " Pipeline";
                String MCRNumber = prDialog.getMcrNumber();
                String MCR = (MCRNumber != null && !MCRNumber.isEmpty()) ? MCRNumber : "None";
                log.info("Target Branch  : " + targetBranch);
                log.info("Environment    : " + Environment);
                log.info("Pipeline Name  : " + PipelineName);
                log.info("TemplateType   : " + TemplateType);
                log.info("Spoke Name     : " + SpokeName);
                log.info("Created By     : " + CreatedBy);
                log.info("Feature Branch : " + featureBranch);
                log.info("PR Title       : " + pullRequestTitle);
                log.info("MCR Number     : " + MCRNumber);


                switch (targetBranch.toLowerCase()) {
                    case "dev":
                        targetBranch = SpokeName + "-dev";
                        break;
                    case "test":
                        targetBranch = SpokeName + "-test";
                        break;
                    case "appdev":
                        targetBranch = SpokeName + "-appdev";
                        break;
                    case "apptest":
                        targetBranch = SpokeName + "-apptest";
                        break;
                    case "prod":
                        targetBranch = SpokeName + "-prod";
                        break;
                    default:
                        targetBranch = "dev";
                        break;
                }
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String CreatedOn = now.format(formatter);

                String htmlDescription = String.format(
                        "<table style=\"border-collapse: collapse; width: 60%%; margin: 30px auto; font-family: Arial, sans-serif; box-shadow: 0 0 10px rgba(0,0,0,0.1);\">" +
                                "<tr>" +
                                "<td colspan=\"2\" style=\"background-color: #333; color: #fff; font-size: 1.2em; text-align: center; font-weight: bold;\">Pipeline Config</td>" +
                                "</tr>" +
                                "<tr>" +
                                "<th style=\"border: 1px solid #ddd; padding: 12px 16px; text-align: left;\">Key</th>" +
                                "<th style=\"border: 1px solid #ddd; padding: 12px 16px; text-align: left;\">Value</th>" +
                                "</tr>" +
                                "<tr style=\"background-color: #f9f9f9;\">" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">Pipeline Name</td>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">%s</td>" +
                                "</tr>" +
                                "<tr>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">Template Type</td>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">%s</td>" +
                                "</tr>" +
                                "<tr style=\"background-color: #f9f9f9;\">" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">Target Branch</td>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">%s</td>" +
                                "</tr>" +
                                "<tr>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">MCR</td>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">%s</td>" +
                                "</tr>" +
                                "<tr style=\"background-color: #f9f9f9;\">" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">Environment</td>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">%s</td>" +
                                "</tr>" +
                                "<tr>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">Created By</td>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">%s</td>" +
                                "</tr>" +
                                "<tr style=\"background-color: #f9f9f9;\">" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">Created On</td>" +
                                "<td style=\"border: 1px solid #ddd; padding: 12px 16px;\">%s</td>" +
                                "</tr>" +
                                "</table>",
                        PipelineName,        // %s
                        TemplateType,        // %s
                        targetBranch,        // %s
                        MCR,               // %s
                        Environment,         // %s
                        CreatedBy,           // %s
                        CreatedOn            // %s
                );
                log.info("Preparing to Create Pull Request");
                gitService.createPullRequest(baseFilePath,optionalFilePath,additionalFiles,targetBranch,featureBranch,
                        commitMessage,pullRequestTitle,htmlDescription);
            }
        } else {
            log.severe("YAML validation failed. Please fix errors before proceeding.");
            JOptionPane.showMessageDialog(this,
                    "YAML validation failed. Please fix errors before proceeding.",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private JMenuBar createMenuBar() {
        log.info("Setting up Menu Bars");
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(menuItem("New", loadIcon("Add"),e -> textArea.setText("")));
        file.add(menuItem("Open",loadIcon("open"), e -> openFile()));

        file.addSeparator();
        file.add(menuItem("Save", loadIcon("save"), e -> saveFile()));
        file.add(menuItem("Save As",loadIcon("saveas"), e -> saveAsFile()));
        file.addSeparator();
        file.add(menuItem("Close",loadIcon("fileclose"), e -> textArea.setText("")));
        file.add(menuItem("Exit",loadIcon("exitApp"), e -> this.dispose()));

        JMenu format = new JMenu("Format");
        format.add(menuItem("Beautify YAML", loadIcon("beautify"),e -> beautifyYaml()));
        format.add(menuItem("Validate YAML", loadIcon("validate"),e -> validateYaml()));
        format.addSeparator();
        format.add(menuItem("Font...", loadIcon("font"),e -> {
            FontChooserDialog dlg = new FontChooserDialog(this, textArea.getFont());
            Font f = dlg.getSelectedFont();
            if (f != null) textArea.setFont(f);
        }));

        JMenu git = new JMenu("GitLab");
        JMenuItem UserProfile = new JMenuItem("My Gitlab Profile",loadIcon("User") );
        UserProfile.addActionListener(e -> gitService.MyProfile());
        git.add(UserProfile);

        JMenuItem PullRequest = new JMenuItem("Create Pull Request",loadIcon("gitlab") );
        PullRequest.addActionListener(e -> {
            try {
                validateAndOpenPullRequestDialog();
            } catch (IOException ex) {
                log.severe("Issue While Calling validateAndOpenPullRequestDialog " + ex.getMessage());
                throw new RuntimeException(ex);
            }
        });
        git.add(PullRequest);

        JMenu help = new JMenu("Help");
        help.add(menuItem("About",loadIcon("clear"), e ->
                JOptionPane.showMessageDialog(this,
                        "YAML Editor + Validator\nVersion 1.0",
                        "About", JOptionPane.INFORMATION_MESSAGE)));

        mb.add(file);
        mb.add(format);
        mb.add(git);
        mb.add(help);
        return mb;
    }


    private JMenuItem menuItem(String title, Icon icon, ActionListener act) {
        JMenuItem it = new JMenuItem(title, icon);
        it.addActionListener(act);
        return it;
    }

    private void openFile() {
        File f = FileService.promptOpen(this);
        if (f != null) {
            try {
                currentFile = f;
                textArea.setText(FileService.readFile(f));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Open Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile() {
        if (currentFile != null) {
            try {
                FileService.writeFile(currentFile, textArea.getText());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Save Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            saveAsFile();
        }
    }

    private void saveAsFile() {
        File f = FileService.promptSaveAs(this);
        if (f != null) {
            currentFile = f;
            saveFile();
        }
    }

    private void beautifyYaml() {
        try {
            ObjectMapper m = new ObjectMapper(new YAMLFactory());
            JsonNode node = m.readTree(textArea.getText());
            String pretty = m.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            textArea.setText(pretty);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Beautify Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean validateYaml() {
        String validationResult = validator.validate(textArea.getText());
        assert validationResult != null;
        boolean response = validationResult.replace(" ", "").equalsIgnoreCase("YAMLisvalid!");
        outputArea.setText(validationResult);
        return response;
    }

    public File getCurrentFile() { return currentFile; }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(YamlEditorApp::new);
    }
}
