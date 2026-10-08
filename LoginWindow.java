import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;

public class LoginWindow extends JFrame {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final BorrowEaseDatabase db = new BorrowEaseDatabase(this::showMessage);

    private final JTextField loginIdField = new JTextField(14);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JTextField fullNameField = new JTextField(14);
    private final JTextField studentNoField = new JTextField(14);
    private final JPasswordField newPasswordField = new JPasswordField(14);
    private final JPasswordField confirmPasswordField = new JPasswordField(14);
    private final JLabel messageLabel = new JLabel(" ");
    private final JToggleButton studentModeButton = new JToggleButton("Student", true);
    private final JToggleButton staffModeButton = new JToggleButton("Staff");
    private final JLabel loginIdLabel = new JLabel("Student no.:");
    private final JButton loginButton = new JButton("Log in");
    private final JButton createAccountButton = new JButton("Create account");
    private final JButton signUpButton = new JButton("Create account");
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private boolean staffMode;

    public LoginWindow() {
        super("BorrowEase - Log in");
        db.initDatabase();
        buildUi();
    }

    private void buildUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("BorrowEase — Equipment and Room Borrowing System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(title, BorderLayout.NORTH);

        cardPanel.add(buildLoginForm(), "login");
        cardPanel.add(buildSignUpForm(), "signup");
        add(cardPanel, BorderLayout.CENTER);

        messageLabel.setForeground(Color.RED);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(messageLabel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(loginButton);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private JPanel buildLoginForm() {
        JPanel form = formPanel();
        ButtonGroup modes = new ButtonGroup();
        modes.add(studentModeButton);
        modes.add(staffModeButton);
        studentModeButton.addActionListener(e -> setStaffMode(false));
        staffModeButton.addActionListener(e -> setStaffMode(true));
        addButtons(form, 0, studentModeButton, staffModeButton);
        addRow(form, 1, loginIdLabel, loginIdField);
        addRow(form, 2, new JLabel("Password:"), passwordField);

        loginButton.addActionListener(this::onLogin);
        createAccountButton.addActionListener(e -> showForm("signup", signUpButton));
        addButtons(form, 3, loginButton, createAccountButton);
        return form;
    }

    private JPanel buildSignUpForm() {
        JPanel form = formPanel();
        addRow(form, 0, new JLabel("Full name:"), fullNameField);
        addRow(form, 1, new JLabel("Student no.:"), studentNoField);
        addRow(form, 2, new JLabel("Password:"), newPasswordField);
        addRow(form, 3, new JLabel("Confirm password:"), confirmPasswordField);

        JButton backButton = new JButton("Back");
        signUpButton.addActionListener(this::onSignUp);
        backButton.addActionListener(e -> showForm("login", loginButton));
        addButtons(form, 4, signUpButton, backButton);
        return form;
    }

    private JPanel formPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 16, 4, 16));
        return form;
    }

    private void addRow(JPanel form, int row, JLabel label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.LINE_END;
        form.add(label, c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        form.add(field, c);
    }

    private void addButtons(JPanel form, int row, AbstractButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        for (AbstractButton button : buttons) panel.add(button);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.insets = new Insets(8, 4, 0, 4);
        form.add(panel, c);
    }

    private void showForm(String name, JButton defaultButton) {
        showMessage(" ");
        passwordField.setText("");
        newPasswordField.setText("");
        confirmPasswordField.setText("");
        cards.show(cardPanel, name);
        getRootPane().setDefaultButton(defaultButton);
    }

    private void setStaffMode(boolean staff) {
        if (staff == staffMode) return;
        staffMode = staff;
        loginIdLabel.setText(staff ? "Staff ID:" : "Student no.:");
        createAccountButton.setVisible(!staff);
        passwordField.setText("");
        showMessage(" ");
    }

    private void onLogin(ActionEvent e) {
        String loginId = loginIdField.getText().trim();
        char[] password = passwordField.getPassword();
        String idName = staffMode ? "staff ID" : "student number";
        try {
            if (loginId.isEmpty() || password.length == 0) {
                showMessage("Enter your " + idName + " and password.");
                return;
            }
            User user = db.getUser(loginId);
            if (user == null || isStaff(user) != staffMode
                    || !PasswordHasher.matches(password, user.passwordSalt, user.passwordHash)) {
                passwordField.setText("");
                showMessage("Wrong " + idName + " or password.");
                return;
            }
            openMainWindow(user);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void onSignUp(ActionEvent e) {
        String fullName = fullNameField.getText().trim();
        String studentNo = studentNoField.getText().trim();
        char[] password = newPasswordField.getPassword();
        char[] confirm = confirmPasswordField.getPassword();
        try {
            String problem = checkSignUp(fullName, studentNo, password, confirm);
            if (problem != null) {
                showMessage(problem);
                return;
            }
            User user = new User(studentNo, fullName, "STUDENT");
            user.passwordSalt = PasswordHasher.newSalt();
            user.passwordHash = PasswordHasher.hash(password, user.passwordSalt);
            if (db.insertUser(user)) {
                openMainWindow(user);
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private String checkSignUp(String fullName, String studentNo, char[] password, char[] confirm) {
        if (fullName.isEmpty()) return "Enter your full name.";
        if (studentNo.isEmpty()) return "Enter your student number.";
        if (studentNo.matches(".*\\s.*")) return "Student number can't contain spaces.";
        if (password.length < MIN_PASSWORD_LENGTH) return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        if (!Arrays.equals(password, confirm)) return "Passwords don't match.";
        if (db.getUser(studentNo) != null) return "That student number is already registered.";
        return null;
    }

    private boolean isStaff(User user) {
        return !user.role.equals("STUDENT");
    }

    private void openMainWindow(User user) {
        dispose();
        new BorrowEaseGUI(user).setVisible(true);
    }

    private void showMessage(String message) {
        messageLabel.setText(message);
    }
}
