package org.nwg.mdis;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.nwg.mdis.FileService.makeTooltip;

public class GitLabUserDialog extends JDialog {
    private JProgressBar progressBar;
    private final Color customColor = Color.decode("#5A287D");
    private GitLabApi gitLabApi;
    private User currentUser;
    private JTextField projectCodeField, tokenField;
    private JTextField userNameField, racfField, emailField;
    private final String GitlabUrl = "https://gitlab.com/";
    JButton saveButton;
    private JLabel statusProjectCode;
    private JLabel statusToken;
    private JLabel messageLabel;
    private ImageIcon tickIcon;
    private ImageIcon crossIcon;
    private static final Logger log = MDISLogger.getLogger();


    public GitLabUserDialog(Frame parent) {
        super(parent, "GitLab User Profile", true);
        setBackground(customColor);
        log.info("Loading GitLab User Profile");

        setMinimumSize(new Dimension(600, 300));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        initComponents();  // 🔁 FIRST load UI
        log.info("Loading GitLab User Profile initialize Components");

        loadSettings();
        log.info("Loading GitLab User Profile Settings");

        pack();                    // 🔁 THEN pack after components added
        setLocationRelativeTo(parent); // 🔁 Then center
        setAlwaysOnTop(true);      // 🔁 Then set always on top
        log.info("GitLab User Profile Dialog prepared");
    }


    private ImageIcon loadScaledIcon(String path) {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(path)));
        Image image = icon.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }

    private void showStatus(boolean success, String message) {
        messageLabel.setText(message);
        messageLabel.setForeground(success ? new Color(0, 128, 0) : Color.RED);
        statusProjectCode.setIcon(success ? tickIcon : crossIcon);
        statusToken.setIcon(success ? tickIcon : crossIcon);
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        getContentPane().setBackground(customColor);
        GridBagConstraints gbc = new GridBagConstraints();
        tickIcon = loadScaledIcon("/icons/greenOK.png");
        crossIcon = loadScaledIcon("/icons/CrossRed.png");

        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BorderLayout(10, 0)); // gap between icon and text
        messagePanel.setBackground(customColor); // your custom background

// 1. Icon label (left)
        //ImageIcon infoIcon = new ImageIcon(getClass().getResource());
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/gitlab-logo-500.png")));
        Image scaledImage = icon.getImage().getScaledInstance(70, 70, Image.SCALE_SMOOTH);
        ImageIcon scaledIcon = new ImageIcon(scaledImage);
        JLabel iconLabel = new JLabel(scaledIcon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP); // align top if text is multiline

// 2. Multiline message label (right)
        String message = "Please enter your Project Code and Private Token. "
                + "Ensure the token has read access to the GitLab repository.";

// Wrap message using HTML
        messageLabel = new JLabel("<html><body style='width:300px;'>" + message + "</body></html>");
        messageLabel.setForeground(Color.WHITE);
        messageLabel.setFont(new Font("Arial", Font.BOLD, 12));
        messageLabel.setOpaque(false);

// Add to messagePanel
        messagePanel.add(iconLabel, BorderLayout.WEST);
        messagePanel.add(messageLabel, BorderLayout.CENTER);



        JLabel lblProjectCode = new JLabel("Project Code:");
        lblProjectCode.setForeground(Color.WHITE);
        projectCodeField = new JTextField(10);
        statusProjectCode = new JLabel(); // For tick/cross
        projectCodeField.setToolTipText(makeTooltip(
                "/icons/gitlab.png",
                "GitLab Project ID",
                "<html><body width='300'>" +
                        "<b>Enter the numeric ID of your GitLab project.</b><br><br>" +
                        "To find it:<br>" +
                        "• Open your GitLab project in the browser.<br>" +
                        "• The Project ID is visible near the project title or under <b>Settings > General</b>.<br>" +
                        "</body></html>"
        ));
        JLabel lblToken = new JLabel("Access Token:");
        lblToken.setForeground(Color.WHITE);
        tokenField = new JPasswordField(20);
        tokenField.setToolTipText(makeTooltip(
                "/tooltips/token.png",
                "GitLab Access Token",
                "<html><body width='300'>" +
                        "<b>Enter your GitLab Personal Access Token.</b><br><br>" +
                        "To generate one:<br>" +
                        "• Go to <b>User Settings > Access Tokens</b> in GitLab.<br>" +
                        "• Name it (e.g., 'Pipeline PR Token').<br>" +
                        "• Select the following scopes:<br>" +
                        "&nbsp;&nbsp;✅ <b>api</b><br>" +
                        "&nbsp;&nbsp;✅ <b>read_repository</b><br>" +
                        "&nbsp;&nbsp;✅ <b>write_repository</b><br><br>" +
                        "Copy the token shown and paste it here. Keep it secure." +
                        "</body></html>"
        ));
        statusToken = new JLabel();

        JLabel lblUserName = new JLabel("User Name:");
        lblUserName.setForeground(Color.WHITE);
        userNameField = new JTextField(20);
        userNameField.setEditable(false);
        userNameField.setToolTipText(makeTooltip(
                "/tooltips/user-name.png",
                "GitLab User Name",
                "This is your full name as registered in your GitLab profile.\n" +
                        "It will be auto-filled after a successful connection test."
        ));

        JLabel lblRacf = new JLabel("User Id:");
        lblRacf.setForeground(Color.WHITE);
        racfField = new JTextField(20);
        racfField.setEditable(false);
        racfField.setToolTipText(makeTooltip(
                "/tooltips/id-card.png",
                "GitLab User ID",
                "This is your GitLab user ID.\n" +
                        "It will be auto-populated after testing your GitLab connection."
        ));

        JLabel lblEmail = new JLabel("Email Address:");
        lblEmail.setForeground(Color.WHITE);
        emailField = new JTextField(25);
        emailField.setEditable(false);
        emailField.setToolTipText(makeTooltip(
                "/tooltips/email.png",
                "GitLab Email Address",
                "This is the email address associated with your GitLab account.\n" +
                        "It will be fetched after verifying your access token."
        ));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true); // Shows a spinning bar
        progressBar.setVisible(false);      // Hidden by default
        progressBar.setBackground(customColor);
        progressBar.setForeground(Color.WHITE);

        JButton testConnection = new JButton("Test");
        testConnection.setToolTipText(makeTooltip(
                "/tooltips/testConnection.png",
                "Test GitLab Connection",
                "<html><body width='300'>" +
                        "Click to verify the GitLab connection using the <b>Project ID</b> and <b>Access Token</b> provided.<br><br>" +
                        "If successful, your user profile (name, ID, email) will be auto-filled from GitLab." +
                        "</body></html>"
        ));
        saveButton = new JButton("Save");
        saveButton.setToolTipText(makeTooltip(
                "/tooltips/save-user.png",
                "Save GitLab Configuration",
                "<html><body width='300'>" +
                        "Click to save the <b>Project ID</b> and <b>Access Token</b> securely using Preferences.<br><br>" +
                        "These settings will be auto-loaded next time you launch the app.<br>" +
                        "<b>Note:</b> Saved locally, not encrypted." +
                        "</body></html>"
        ));
        saveButton.setEnabled(false);
        //JButton loadButton = new JButton("Load");
        JButton closeButton = new JButton("Close");

        // Action Listeners
        testConnection.addActionListener(e -> {
            log.info("Verifying Gitlab Connection..");
            try {
                verifyConnection();
                log.info("Gitlab Connection Successful..");
                showStatus(true, "Connection Successful");

            } catch (GitLabApiException ex) {
                log.severe("Unable to connect with Gitlab " + ex.getMessage());
                showStatus(false, "Connection failed: " + ex.getMessage());
            }
        });
        saveButton.addActionListener(e -> saveSettings());
   /*     loadButton.addActionListener(e -> {
            log.info("Getting User Information's from Gitlab Connection..");
            fetchGitLabUserInfo();
            log.info("Gitlab User Information Read Successful..");

        });*/
        closeButton.addActionListener(e -> dispose());

        gbc.insets = new Insets(2, 10, 2, 10);
        gbc.anchor = GridBagConstraints.WEST;
        int y = 0;


        // Row 0: Message label
        gbc.gridx = 0; gbc.gridy = y; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        add(messagePanel, gbc);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        y++;

        // Row 1: Project Code + status icon
        gbc.gridx = 0; gbc.gridy = y; add(lblProjectCode, gbc);
        gbc.gridx = 1; gbc.gridy = y; add(projectCodeField, gbc);
        gbc.gridx = 2; gbc.gridy = y++; add(statusProjectCode, gbc);

        // Row 2: Token + status icon
        gbc.gridx = 0; gbc.gridy = y; add(lblToken, gbc);
        gbc.gridx = 1; gbc.gridy = y; add(tokenField, gbc);
        gbc.gridx = 2; gbc.gridy = y++; add(statusToken, gbc);

        // Row 3: User Name
        gbc.gridx = 0; gbc.gridy = y; add(lblUserName, gbc);
        gbc.gridx = 1; gbc.gridy = y++; gbc.gridwidth = 2; add(userNameField, gbc);
        gbc.gridwidth = 1;

        // Row 4: RACF
        gbc.gridx = 0; gbc.gridy = y; add(lblRacf, gbc);
        gbc.gridx = 1; gbc.gridy = y++; gbc.gridwidth = 2; add(racfField, gbc);
        gbc.gridwidth = 1;

        // Row 5: Email
        gbc.gridx = 0; gbc.gridy = y; add(lblEmail, gbc);
        gbc.gridx = 1; gbc.gridy = y++; gbc.gridwidth = 2; add(emailField, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = y++;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        add(progressBar, gbc);

        // Row 6: Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(testConnection);
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);
        gbc.gridx = 0; gbc.gridy = y; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.EAST;
        buttonPanel.setBackground(customColor);
        add(buttonPanel, gbc);
    }
    private void verifyConnection() throws GitLabApiException {
        fetchGitLabUserInfo();
    }


    private void fetchGitLabUserInfo() {
        String projectCode = projectCodeField.getText().trim();
        String token = tokenField.getText().trim();

        progressBar.setVisible(true); // Show progress bar
        saveButton.setEnabled(false); // Disable save during loading
        SwingWorker<User, Void> worker = new SwingWorker<User, Void>() {

            @Override
            protected User doInBackground() throws Exception {
                gitLabApi = new GitLabApi(GitlabUrl, token);
                return gitLabApi.getUserApi().getCurrentUser();
            }

            @Override
            protected void done() {
                try {
                    currentUser = (User) get();
                    projectCodeField.setText(projectCode);
                    userNameField.setText(currentUser.getName());
                    racfField.setText(currentUser.getUsername());
                    emailField.setText(currentUser.getEmail());
                    saveButton.setEnabled(true);
                    showStatus(true, "Connection Successful");
                    log.info("Gitlab User Information Read Successful..");
                    if (projectCode.isEmpty() || token.isEmpty()) {
                        log.severe("Please provide both Project Code and Access Token to get User Info");
                        JOptionPane.showMessageDialog(null, "Please provide both Project Code and Access Token.");
                    }
                } catch (Exception ex) {
                    log.severe("Unable to get User Information: " + ex.getMessage());
                    showStatus(false, "Connection failed: " + ex.getMessage());
                } finally {
                    progressBar.setVisible(false); // Hide when done
                }
            }
        };

        worker.execute(); // Start background task
    }

    private void saveSettings() {
        Preferences prefs = Preferences.userNodeForPackage(GitLabUserDialog.class);
        try {
            // Reset (clear) preferences if they exist
            if (prefs.keys().length > 0) {
                prefs.clear();
            }
            prefs.put("projectCode", projectCodeField.getText());
            prefs.put("token", tokenField.getText());
            prefs.put("gitlabUrl", GitlabUrl);
            JOptionPane.showMessageDialog(this, "User Setting Saved.");
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

        public void loadSettings() {
        Preferences prefs = Preferences.userNodeForPackage(GitLabUserDialog.class);
        projectCodeField.setText(prefs.get("projectCode", ""));
        tokenField.setText(prefs.get("token", ""));
    }

    public void createPullRequest(String baseFilePath,
                                              String optionalFilePath,
                                              List<String> additionalFiles,
                                              String targetBranch,
                                              String featureBranch,
                                              String commitMessage,
                                              String pullRequestTitle,
                                              String htmlDescription)
    {
        loadSettings();
        Preferences prefs = Preferences.userNodeForPackage(GitLabUserDialog.class);
        String projectToken = prefs.get("token", "");
        String projectId = prefs.get("projectCode", "");

        try (GitLabApi gitLabApi = new GitLabApi(GitlabUrl, projectToken)) {
            Project project = gitLabApi.getProjectApi().getProject(projectId);
            log.info("Connected To Gitlab Project " + project.getName());
            log.info("Creating feature Branch " + featureBranch);
            Branch featureBranchNm = gitLabApi.getRepositoryApi().createBranch(project.getId(), featureBranch, targetBranch);
            log.info("Feature Branch Created Successfully");
            List<CommitAction> commitActions = new ArrayList<>();
            log.info("Commiting Files to Merge Requests...");
            Path path = Paths.get(baseFilePath);
            byte[] bytes = Files.readAllBytes(path);
            String content = new String(bytes, StandardCharsets.UTF_8);
            String pipelinePrefix = "src/metadata/pipelines"+ "/" + path.getFileName();
            CommitAction.Action pipelineAction= checkIfFileExists(gitLabApi,project.getId(),pipelinePrefix,featureBranchNm.getName());

            commitActions.add(new CommitAction()
                    .withAction(pipelineAction)
                    .withFilePath(pipelinePrefix)
                    .withContent(content));
            log.info("Pipeline Config Commited from Path " + path.getFileName() );
            // Optional file

            if (optionalFilePath != null && !optionalFilePath.trim().isEmpty()) {
                log.info("Adding Manifest Config from Path " + optionalFilePath);
                Path optPath = Paths.get(optionalFilePath);
                String optPrefix = "src/metadata/manifest"+ "/" + optPath.getFileName();
                CommitAction.Action optAction= checkIfFileExists(gitLabApi,project.getId(),optPrefix,featureBranchNm.getName());

                byte[] optBytes = Files.readAllBytes(optPath);
                String optContent = new String(optBytes, StandardCharsets.UTF_8);

                commitActions.add(new CommitAction()
                        .withAction(optAction)
                        .withFilePath(optPrefix)
                        .withContent(optContent));
                log.info("Manifest Config Added from Path " + optionalFilePath);
            }
            else
            {
                log.info("Manifest Config Not Provided ...Skipping");
            }
            log.info("Adding SQL Files (if Exists)");
            for (String file : additionalFiles) {
                Path listPath = Paths.get(file);
                log.info("Adding SQL Config from Path " + listPath);
                String sqlPrefix = "src/metadata/sql"+ "/" + listPath.getFileName();
                CommitAction.Action sqlAction= checkIfFileExists(gitLabApi,project.getId(),sqlPrefix,featureBranchNm.getName());

                byte[] optBytes = Files.readAllBytes(listPath);
                String listContent = new String(optBytes, StandardCharsets.UTF_8);

                commitActions.add(new CommitAction()
                        .withAction(sqlAction)
                        .withFilePath(sqlPrefix)
                        .withContent(listContent));
                log.info("SQL Config from Path " + listPath + "Added");
            }

            // 3. Commit all files
            gitLabApi.getCommitsApi().createCommit(
                    project.getId(),
                    featureBranch,
                    commitMessage,
                    null, null, null,
                    commitActions
            );
            log.info("All Files Added to Commit");
            log.info("Creating Merge Request...");
            // 4. Create merge request with HTML description
            this.gitLabApi = new GitLabApi(GitlabUrl, projectToken);
            this.currentUser = gitLabApi.getUserApi().getCurrentUser();
            Long userId = currentUser.getId();
            log.info("Creating Merge Request......");
            MergeRequestParams mergeParams = new MergeRequestParams()
                    .withSourceBranch(featureBranch)
                    .withTargetBranch(targetBranch)
                    .withTitle(pullRequestTitle)
                    .withDescription(htmlDescription)
                    .withAssigneeId(userId)
                    .withRemoveSourceBranch(true);

            MergeRequest MR= gitLabApi.getMergeRequestApi().createMergeRequest(project.getId(), mergeParams);
            String Message = String.format("%s Created Merge Request %s and URL %s",
                    currentUser.getName(),MR.getIid(),MR.getWebUrl());
            log.info(Message);

            JOptionPane.showMessageDialog(null, Message, MR.getId() + " Created", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (GitLabApiException e) {
            log.severe("Gitlab Exception Occurred " + e.getMessage());
            JOptionPane.showMessageDialog(null, "GitLab API Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ioEx) {
            log.severe("IO Exception Occurred " +ioEx.getMessage());
            JOptionPane.showMessageDialog(null, "File read error: " + ioEx.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            log.severe("Generic Exception Occurred " +ex.getMessage());
            JOptionPane.showMessageDialog(null, "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public CommitAction.Action checkIfFileExists(GitLabApi api, Long projectId,String filePath, String branch) throws GitLabApiException {
        CommitAction.Action newAction = CommitAction.Action.CREATE;
        try {
            log.info("Checking File Existence For "+ filePath);
            api.getRepositoryFileApi().getFile(projectId, filePath, branch);
            newAction = CommitAction.Action.UPDATE;
            log.info("File Exists, Updating");
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404) {
                log.info("File does not exist, will CREATE.");
            } else {
                log.severe("Unexpected GitLab error when checking file: " + e.getMessage());
                throw e;
            }
        }
        return newAction;
    }
}
