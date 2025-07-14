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

public class GitLabUserDialog extends JDialog {
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
        log.info("Loading GitLab User Profile");
        setSize(500, 200);
        log.info("Loading GitLab User Profile Dialog Size");
        setLocationRelativeTo(parent);
        log.info("Loading GitLab User Profile Setting Screen Position");
        initComponents();
        log.info("Loading GitLab User Profile initialize Components");
        loadSettings();
        log.info("Loading GitLab User Profile Settings");

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
        GridBagConstraints gbc = new GridBagConstraints();
        tickIcon = loadScaledIcon("/icons/greenOK.png");
        crossIcon = loadScaledIcon("/icons/CrossRed.png");

            // Top message label
        messageLabel = new JLabel("Please Enter Project Code and Private Token");
        messageLabel.setForeground(Color.BLUE);
        messageLabel.setFont(new Font("Arial", Font.BOLD, 12));

        JLabel lblProjectCode = new JLabel("Project Code:");
        projectCodeField = new JTextField(25);
        statusProjectCode = new JLabel(); // For tick/cross

        JLabel lblToken = new JLabel("Access Token:");
        tokenField = new JTextField(25);
        statusToken = new JLabel();

        JLabel lblUserName = new JLabel("User Name:");
        userNameField = new JTextField(25);
        userNameField.setEditable(false);

        JLabel lblRacf = new JLabel("User Id:");
        racfField = new JTextField(25);
        racfField.setEditable(false);

        JLabel lblEmail = new JLabel("Email Address:");
        emailField = new JTextField(25);
        emailField.setEditable(false);

        JButton testConnection = new JButton("Test");
        saveButton = new JButton("Save");
        saveButton.setEnabled(false);
        JButton loadButton = new JButton("Load");
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
        loadButton.addActionListener(e -> {
            try {
                log.info("Getting User Information's from Gitlab Connection..");
                fetchGitLabUserInfo();
                log.info("Gitlab User Information Read Successful..");

            } catch (GitLabApiException ex) {
                log.severe("Unable to get User Information "  + ex.getMessage());
            }
        });
        closeButton.addActionListener(e -> dispose());

        gbc.insets = new Insets(2, 10, 2, 10);
        gbc.anchor = GridBagConstraints.WEST;
        int y = 0;

        // Row 0: Message label
        gbc.gridx = 0; gbc.gridy = y; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        add(messageLabel, gbc);
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

        // Row 6: Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(testConnection);
        buttonPanel.add(saveButton);
        buttonPanel.add(loadButton);

        gbc.gridx = 0; gbc.gridy = y; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.EAST;
        add(buttonPanel, gbc);
    }
    private void verifyConnection() throws GitLabApiException {
        fetchGitLabUserInfo();
    }

    private void fetchGitLabUserInfo() throws GitLabApiException {
        String projectCode = projectCodeField.getText().trim();
        String token = tokenField.getText().trim();
        this.gitLabApi = new GitLabApi(GitlabUrl, token);
        this.currentUser = gitLabApi.getUserApi().getCurrentUser();
        projectCodeField.setText(projectCode);
        userNameField.setText(currentUser.getName());
        racfField.setText(currentUser.getUsername());
        emailField.setText(currentUser.getEmail());
        saveButton.setEnabled(true);
        if (projectCode.isEmpty() || token.isEmpty()) {
            log.severe("Please provide both Project Code and Access Token to get User Info");
            JOptionPane.showMessageDialog(this, "Please provide both Project Code and Access Token.");
        }
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GitLabUserDialog(null).setVisible(true));
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
            log.info("Commiting Files to Pull Requests...");
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

                byte[] optBytes = Files.readAllBytes(path);
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
            log.info(currentUser.getName() + " Creating Pull Request");
            MergeRequestParams mergeParams = new MergeRequestParams()
                    .withSourceBranch(featureBranch)
                    .withTargetBranch(targetBranch)
                    .withTitle(pullRequestTitle)
                    .withDescription(htmlDescription)
                    .withAssigneeId(userId)
                    .withRemoveSourceBranch(true);

            MergeRequest MR= gitLabApi.getMergeRequestApi().createMergeRequest(project.getId(), mergeParams);
            log.info(currentUser.getName() + " Pull Request " + MR.getWebUrl()+ " Created");

            JOptionPane.showMessageDialog(null, "Merge request created successfully! " + MR.getWebUrl(), "Success", JOptionPane.INFORMATION_MESSAGE);

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

    public CommitAction.Action checkIfFileExists(GitLabApi api, Long projectId,String filePath, String branch)
    {
        CommitAction.Action newAction = CommitAction.Action.CREATE;
        try {
            log.info("Checking File Existence For "+ filePath);
            api.getRepositoryFileApi().getFile(projectId, filePath, branch);
            newAction = CommitAction.Action.UPDATE;
            log.info("File Exists, Updating");
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() != 404)
            {
                log.info("File Does not Exists, Creating");
                newAction = CommitAction.Action.CREATE;
            }
        }
        return newAction;
    }
    public void MyProfile() {
        log.info("Loading Your Profile...");
        SwingUtilities.invokeLater(() -> new GitLabUserDialog(null).setVisible(true));
        log.info("Profile Loaded...");
    }
}
