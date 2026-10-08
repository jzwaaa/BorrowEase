import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class BorrowEaseGUI extends JFrame {

    private static final int PENALTY_PER_DAY = 50;
    private static final String PESO = "\u20B1";
    private static final Color SUCCESS_COLOR = new Color(0, 128, 0);
    private static final Color WARNING_COLOR = new Color(204, 102, 0);
    private static final Color PROBLEM_COLOR = Color.RED;

    private final BorrowEaseDatabase db = new BorrowEaseDatabase(this::showProblem);

    private final User user;

    private int currentDay;

    private final DefaultTableModel catalogModel = tableModel(new String[]{"ID", "Item", "Available", "Total"},
            String.class, String.class, Integer.class, Integer.class);
    private final DefaultTableModel myRequestsModel = tableModel(new String[]{"Request", "Item", "Qty", "Status", "Due day", "Charges"},
            String.class, String.class, Integer.class, String.class, Integer.class, String.class);
    private final DefaultTableModel pendingModel = tableModel(new String[]{"Request", "Student", "Item", "Qty", "Due day"},
            String.class, String.class, String.class, Integer.class, Integer.class);
    private final DefaultTableModel approvedModel = tableModel(new String[]{"Request", "Student", "Item", "Qty", "Due day", "Days late"},
            String.class, String.class, String.class, Integer.class, Integer.class, Integer.class);
    private final DefaultTableModel unpaidModel = tableModel(
            new String[]{"Request", "Student", "Item", "Penalty (" + PESO + ")", "Damage fee (" + PESO + ")", "Total (" + PESO + ")"},
            String.class, String.class, String.class, Integer.class, Integer.class, Integer.class);
    private final DefaultTableModel inventoryModel = tableModel(
            new String[]{"ID", "Item", "Total", "Available", "In use", "Under maintenance"},
            String.class, String.class, Integer.class, Integer.class, Integer.class, Integer.class);

    private final JTable catalogTable = table(catalogModel, 60, 250, 80, 70);
    private final JTable myRequestsTable = table(myRequestsModel, 55, 140, 40, 200, 55, 285);
    private final JTable pendingTable = table(pendingModel, 55, 110, 125, 35, 55);
    private final JTable approvedTable = table(approvedModel, 55, 105, 115, 35, 55, 60);
    private final JTable unpaidTable = table(unpaidModel, 60, 150, 170, 90, 100, 80);
    private final JTable inventoryTable = table(inventoryModel, 60, 250, 70, 80, 70, 130);
    private final List<JTable> searchedTables = new ArrayList<>();

    private final JTextField borrowDaysField = new JTextField("3", 3);
    private final JTextField borrowQuantityField = new JTextField("1", 3);
    private final JTextField newItemIdField = new JTextField(6);
    private final JTextField newItemNameField = new JTextField(12);
    private final JTextField newItemQuantityField = new JTextField("1", 3);
    private final JTextField searchField = new JTextField(12);
    private final JTextArea statusArea = new JTextArea(2, 20);
    private final JLabel currentDayLabel = new JLabel();
    private JDialog reportWindow;

    public BorrowEaseGUI(User user) {
        super("BorrowEase - Simple Prototype");
        this.user = user;
        currentDay = db.loadCurrentDay();
        buildUi();
        refreshAll();
        showLoginReminders();
    }

    private void buildUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("BorrowEase — Equipment and Room Borrowing System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        currentDayLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton advanceDayButton = new JButton("Advance Day ▶");
        advanceDayButton.setToolTipText("Simulate time passing, so a due date can be missed for the demo.");
        advanceDayButton.addActionListener(this::onAdvanceDay);
        JLabel userLabel = new JLabel(user.fullName + " (" + roleTitle() + ")");
        JButton logoutButton = new JButton("Log out");
        logoutButton.addActionListener(this::onLogout);
        JPanel dayBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        dayBar.add(currentDayLabel);
        dayBar.add(advanceDayButton);
        dayBar.add(userLabel);
        dayBar.add(logoutButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(title, BorderLayout.CENTER);
        north.add(dayBar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(roleTitle(), buildRoleTab());
        add(tabs, BorderLayout.CENTER);

        statusArea.setEditable(false);
        statusArea.setFocusable(false);
        statusArea.setOpaque(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 10, 8, 10)));
        add(statusArea, BorderLayout.SOUTH);
    }

    private String roleTitle() {
        if (user.role.equals("CUSTODIAN")) return "Custodian";
        if (user.role.equals("ADMINISTRATOR")) return "Administrator";
        return "Student";
    }

    private JPanel buildRoleTab() {
        if (user.role.equals("CUSTODIAN")) return buildCustodianTab();
        if (user.role.equals("ADMINISTRATOR")) return buildAdminTab();
        return buildStudentTab();
    }

    private JPanel buildStudentTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel daysPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        daysPanel.add(new JLabel("Borrow days:"));
        daysPanel.add(borrowDaysField);
        daysPanel.add(new JLabel("How many:"));
        daysPanel.add(borrowQuantityField);
        JPanel north = new JPanel(new BorderLayout());
        north.add(daysPanel, BorderLayout.WEST);
        north.add(searchPanel(catalogTable, myRequestsTable), BorderLayout.EAST);
        panel.add(north, BorderLayout.NORTH);

        JPanel lists = new JPanel(new GridLayout(2, 1, 0, 10));
        lists.add(titledScroll("Catalog (Browse Equipment & Rooms)", catalogTable));
        lists.add(titledScroll("My Requests (View Status)", myRequestsTable));
        panel.add(lists, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton submitButton = new JButton("Submit Borrow Request");
        JButton cancelButton = new JButton("Cancel Selected Request");
        submitButton.addActionListener(this::onSubmitRequest);
        cancelButton.addActionListener(this::onCancelRequest);
        buttons.add(submitButton);
        buttons.add(cancelButton);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildCustodianTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(searchPanel(pendingTable, approvedTable, unpaidTable), BorderLayout.NORTH);

        JPanel lists = new JPanel(new GridLayout(1, 2, 10, 0));
        lists.add(titledScroll("Incoming Requests (Pending)", pendingTable));
        lists.add(titledScroll("Approved (Borrowed) Items", approvedTable));
        JScrollPane unpaidScroll = titledScroll("Unpaid Charges", unpaidTable);
        unpaidScroll.setPreferredSize(new Dimension(0, 120));
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(lists, BorderLayout.CENTER);
        center.add(unpaidScroll, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton approveButton = new JButton("Approve Selected");
        JButton rejectButton = new JButton("Reject Selected");
        JButton returnOkButton = new JButton("Returned OK");
        JButton returnDamagedButton = new JButton("Returned Damaged");
        JButton paidButton = new JButton("Mark Paid");
        approveButton.addActionListener(e -> onDecideRequest(true));
        rejectButton.addActionListener(e -> onDecideRequest(false));
        returnOkButton.addActionListener(e -> onMarkReturned(false));
        returnDamagedButton.addActionListener(e -> onMarkReturned(true));
        paidButton.addActionListener(this::onMarkPaid);
        buttons.add(approveButton);
        buttons.add(rejectButton);
        buttons.add(returnOkButton);
        buttons.add(returnDamagedButton);
        buttons.add(paidButton);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildAdminTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(titledScroll("Inventory (Manage Inventory)", inventoryTable), BorderLayout.CENTER);

        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addPanel.add(new JLabel("New item ID:"));
        addPanel.add(newItemIdField);
        addPanel.add(new JLabel("Name:"));
        addPanel.add(newItemNameField);
        addPanel.add(new JLabel("Qty:"));
        addPanel.add(newItemQuantityField);
        JButton addButton = new JButton("Add Item");
        addButton.addActionListener(this::onAddItem);
        addPanel.add(addButton);
        JPanel north = new JPanel(new BorderLayout());
        north.add(addPanel, BorderLayout.WEST);
        north.add(searchPanel(inventoryTable), BorderLayout.EAST);
        panel.add(north, BorderLayout.NORTH);

        JButton reportButton = new JButton("Generate Utilization Report");
        reportButton.addActionListener(this::onGenerateReport);
        JButton repairedButton = new JButton("Mark Repaired");
        repairedButton.addActionListener(this::onMarkRepaired);
        JButton quantityButton = new JButton("Change Quantity");
        quantityButton.addActionListener(this::onChangeQuantity);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.add(reportButton);
        south.add(repairedButton);
        south.add(quantityButton);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel searchPanel(JTable... tables) {
        searchedTables.addAll(Arrays.asList(tables));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applySearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applySearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applySearch();
            }
        });
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(new JLabel("Search:"));
        panel.add(searchField);
        return panel;
    }

    private void applySearch() {
        String text = searchField.getText().trim();
        RowFilter<Object, Object> filter = text.isEmpty() ? null : RowFilter.<Object, Object>regexFilter("(?i)" + Pattern.quote(text));
        for (JTable table : searchedTables) {
            ((DefaultRowSorter<?, ?>) table.getRowSorter()).setRowFilter(filter);
        }
    }

    private JScrollPane titledScroll(String title, JComponent view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        return scroll;
    }

    private static DefaultTableModel tableModel(String[] columns, Class<?>... types) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return types[column];
            }
        };
    }

    private static JTable table(DefaultTableModel model, int... columnWidths) {
        JTable table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int column = columnAtPoint(e.getPoint());
                Object value = row < 0 || column < 0 ? null : getValueAt(row, column);
                return value == null || value.toString().isEmpty() ? null : value.toString();
            }
        };
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        for (int c = 0; c < columnWidths.length; c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(columnWidths[c]);
        }
        return table;
    }

    private void onLogout(ActionEvent e) {
        dispose();
        new LoginWindow().setVisible(true);
    }

    private void onAdvanceDay(ActionEvent e) {
        if (!db.updateCurrentDay(currentDay + 1)) return;
        currentDay++;
        showSuccess("-- Day advanced to Day " + currentDay + " --");
        refreshAll();
    }

    private void onSubmitRequest(ActionEvent e) {
        String blockReason = borrowingBlockReason();
        if (blockReason != null) {
            showProblem(blockReason);
            return;
        }
        String itemId = selectedId(catalogTable);
        if (itemId == null) {
            showProblem("Select an item from the catalog first.");
            return;
        }
        Item item = db.getItem(itemId);
        if (item == null || item.available < 1) {
            showProblem("That item is not available.");
            return;
        }
        int units = parseWholeNumber(borrowQuantityField.getText());
        if (units < 1) {
            showProblem("Enter how many units to borrow (1 or more).");
            return;
        }
        if (units > item.available) {
            showProblem("You asked for " + units + ", but only " + item.available
                    + (item.available == 1 ? " is" : " are") + " available.");
            return;
        }
        int days;
        try {
            days = Integer.parseInt(borrowDaysField.getText().trim());
            if (days < 1) days = 1;
        } catch (NumberFormatException ex) {
            days = 3;
        }
        int dueDay = currentDay + days;

        Request r = new Request(null, user.fullName, itemId, dueDay);
        r.studentNo = user.loginId;
        r.quantity = units;
        if (!db.submitRequest(r)) {
            refreshAll();
            return;
        }
        showSuccess("Submitted " + r.id + " for " + itemLabel(item.name, units) + " (" + user.fullName + "). Due: Day " + dueDay + ". Status: PENDING");
        borrowQuantityField.setText("1");
        refreshAll();
    }

    private void onCancelRequest(ActionEvent e) {
        String requestId = selectedId(myRequestsTable);
        if (requestId == null) {
            showProblem("Select one of your requests first.");
            return;
        }
        Request r = db.getRequest(requestId);
        if (r == null || !user.loginId.equalsIgnoreCase(r.studentNo)) {
            showProblem("You can only cancel your own requests.");
            return;
        }
        if (!r.status.equals("PENDING")) {
            showProblem("Only PENDING requests can be cancelled.");
            return;
        }
        if (db.cancelRequest(r)) showSuccess("Cancelled " + requestId + ".");
        refreshAll();
    }

    private void onDecideRequest(boolean approve) {
        String requestId = selectedId(pendingTable);
        if (requestId == null) {
            showProblem("Select a pending request first.");
            return;
        }
        Request r = db.getRequest(requestId);
        if (r == null || !r.status.equals("PENDING")) {
            showProblem("Request not found or already handled.");
            return;
        }
        boolean saved = approve ? db.approveRequest(r) : db.rejectRequest(r);
        if (saved) showSuccess((approve ? "Approved " : "Rejected ") + requestId + ".");
        refreshAll();
    }

    private void onMarkReturned(boolean damaged) {
        String requestId = selectedId(approvedTable);
        if (requestId == null) {
            showProblem("Select an approved (borrowed) item first.");
            return;
        }
        Request r = db.getRequest(requestId);
        if (r == null || !r.status.equals("APPROVED")) {
            showProblem("Request not found or not currently borrowed.");
            return;
        }
        int damagedUnits = 0;
        int damageFee = 0;
        if (damaged) {
            int[] damage = askDamage(r);
            if (damage == null) return;
            damagedUnits = damage[0];
            damageFee = damage[1];
        }

        int daysLate = currentDay - r.dueDay;
        int penalty = daysLate > 0 ? daysLate * PENALTY_PER_DAY * r.quantity : 0;
        if (!db.recordReturn(r, penalty, damagedUnits, damageFee)) {
            refreshAll();
            return;
        }
        String message;
        if (daysLate > 0) {
            message = "Marked " + requestId + " as RETURNED — " + daysLate + " day(s) LATE (due Day " + r.dueDay
                    + ", today is Day " + currentDay + "). Penalty: " + PESO + penalty
                    + (r.quantity > 1 ? " (" + r.quantity + " units)" : "");
        } else {
            message = "Marked " + requestId + " as RETURNED on time (due Day " + r.dueDay + "). No penalty.";
        }
        if (damagedUnits > 0) {
            Item item = db.getItem(r.itemId);
            message += "\n" + (item != null ? item.name : r.itemId)
                    + (r.quantity > 1 ? " (" + damagedUnits + " of " + r.quantity + " units)" : "")
                    + " was returned damaged and is now UNDER MAINTENANCE."
                    + (damageFee > 0 ? " Damage fee: " + PESO + damageFee + "." : "");
        }
        showSuccess(message);
        refreshAll();
    }

    private void onMarkRepaired(ActionEvent e) {
        String itemId = selectedId(inventoryTable);
        if (itemId == null) {
            showProblem("Select an item under maintenance first.");
            return;
        }
        Item item = db.getItem(itemId);
        if (item == null || item.underMaintenance < 1) {
            showProblem("That item is not under maintenance.");
            return;
        }
        if (!db.repairUnit(itemId)) {
            refreshAll();
            return;
        }
        int stillInRepair = item.underMaintenance - 1;
        if (stillInRepair == 0) {
            showSuccess(item.id + " - " + item.name + " is repaired and back in the catalog.");
        } else {
            showSuccess("One unit of " + item.id + " - " + item.name + " is repaired and back in the catalog. "
                    + stillInRepair + " still under maintenance.");
        }
        refreshAll();
    }

    private void onChangeQuantity(ActionEvent e) {
        String itemId = selectedId(inventoryTable);
        Item item = itemId == null ? null : db.getItem(itemId);
        if (item == null) {
            showProblem("Select an item in the inventory first.");
            return;
        }
        String answer = askNumber("Change Quantity", "New total number of units for " + item.id + " - " + item.name + ":",
                String.valueOf(item.quantity));
        if (answer == null) return;
        int quantity = parseWholeNumber(answer);
        int notOnShelf = item.quantity - item.available;
        if (quantity < 1) {
            showProblem("Enter a whole number of 1 or more.");
            return;
        }
        if (quantity < notOnShelf) {
            showProblem("Can't set " + item.id + " to " + quantity + ": " + notOnShelf + " unit(s) are in use or under maintenance.");
            return;
        }
        if (db.updateItemQuantity(itemId, quantity)) showSuccess(item.id + " - " + item.name + " now has " + quantity + " unit(s).");
        refreshAll();
    }

    private void onMarkPaid(ActionEvent e) {
        String requestId = selectedId(unpaidTable);
        if (requestId == null) {
            showProblem("Select an unpaid charge first.");
            return;
        }
        Request r = db.getRequest(requestId);
        if (r == null || amountOwed(r) <= 0 || r.penaltyPaid) {
            showProblem("Charge not found or already paid.");
            return;
        }
        String answer = JOptionPane.showInputDialog(this, "Official receipt (OR) number for " + requestId
                + " (" + PESO + amountOwed(r) + "):", "Mark Paid", JOptionPane.QUESTION_MESSAGE);
        if (answer == null) return;
        String receiptNo = answer.trim();
        if (receiptNo.isEmpty()) {
            showProblem("Enter the official receipt (OR) number.");
            return;
        }
        String usedFor = db.findRequestWithReceipt(receiptNo);
        if (usedFor != null) {
            showProblem("OR " + receiptNo + " was already used for " + usedFor + ".");
            return;
        }
        if (!db.markPaid(requestId, receiptNo, currentDay)) {
            refreshAll();
            return;
        }
        showSuccess("Marked " + requestId + " (" + PESO + amountOwed(r) + ") as paid. OR " + receiptNo + ".");
        refreshAll();
    }

    private String borrowingBlockReason() {
        int unpaid = 0;
        Request overdue = null;
        for (Request r : db.loadRequests()) {
            if (!user.loginId.equalsIgnoreCase(r.studentNo)) continue;
            if (!r.penaltyPaid) unpaid += amountOwed(r);
            if (overdue == null && r.status.equals("APPROVED") && currentDay > r.dueDay) overdue = r;
        }
        if (unpaid > 0) {
            return "You have " + PESO + unpaid + " in unpaid charges. Pay the custodian before borrowing again.";
        }
        if (overdue != null) {
            return "You have an overdue item (" + overdue.id + ", due Day " + overdue.dueDay + "). Return it before borrowing again.";
        }
        return null;
    }

    private void onAddItem(ActionEvent e) {
        String id = newItemIdField.getText().trim();
        String name = newItemNameField.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            showProblem("Enter both an item ID and a name.");
            return;
        }
        int quantity = parseWholeNumber(newItemQuantityField.getText());
        if (quantity < 1) {
            showProblem("Enter a quantity of 1 or more.");
            return;
        }
        if (db.getItem(id) != null) {
            showProblem("Item ID " + id + " already exists.");
            return;
        }
        Item item = new Item(id, name);
        item.quantity = quantity;
        item.available = quantity;
        if (db.insertItem(item)) {
            showSuccess("Added item " + id + " - " + name + (quantity == 1 ? "." : " (" + quantity + " units)."));
            newItemIdField.setText("");
            newItemNameField.setText("");
            newItemQuantityField.setText("1");
        }
        refreshAll();
    }

    private void onGenerateReport(ActionEvent e) {
        List<Item> currentItems = db.loadItems();
        List<Request> currentRequests = db.loadRequests();
        DefaultTableModel table = tableModel(new String[]{"Item", "Times borrowed", "Units borrowed"},
                String.class, Long.class, Long.class);
        for (Item item : currentItems) {
            long count = 0;
            long units = 0;
            for (Request r : currentRequests) {
                if (r.itemId.equals(item.id) && (r.status.equals("APPROVED") || r.status.equals("RETURNED"))) {
                    count++;
                    units += r.quantity;
                }
            }
            table.addRow(new Object[]{item.name, count, units});
        }
        showReportWindow(table);
    }

    private void showReportWindow(DefaultTableModel table) {
        if (reportWindow != null) reportWindow.dispose();
        reportWindow = new JDialog(this, "Utilization Report", false);
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(ev -> reportWindow.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(closeButton);
        reportWindow.add(new JScrollPane(table(table, 200, 110, 110)), BorderLayout.CENTER);
        reportWindow.add(buttons, BorderLayout.SOUTH);
        reportWindow.setSize(460, 260);
        reportWindow.setLocationRelativeTo(this);
        reportWindow.setVisible(true);
    }

    private void showLoginReminders() {
        if (user.role.equals("STUDENT")) showStudentReminders();
        if (user.role.equals("CUSTODIAN")) showOverdueLoans();
    }

    private void showStudentReminders() {
        List<Item> items = db.loadItems();
        List<String> overdue = new ArrayList<>();
        List<String> dueSoon = new ArrayList<>();
        for (Request r : db.loadRequests()) {
            if (!r.status.equals("APPROVED") || !user.loginId.equalsIgnoreCase(r.studentNo)) continue;
            String loan = r.id + " " + itemLabel(getItemNameFromList(items, r.itemId), r.quantity);
            int daysLate = currentDay - r.dueDay;
            if (daysLate > 0) {
                overdue.add(loan + " (was due Day " + r.dueDay + ")");
            } else if (daysLate >= -1) {
                dueSoon.add(loan + (daysLate == 0 ? " (today)" : " (tomorrow)"));
            }
        }
        List<String> lines = new ArrayList<>();
        if (!overdue.isEmpty()) {
            lines.add("Overdue: " + String.join(", ", overdue) + ". Please return overdue items as soon as possible.");
        }
        if (!dueSoon.isEmpty()) lines.add("Due soon: " + String.join(", ", dueSoon) + ".");
        if (!lines.isEmpty()) showStatus(String.join("\n", lines), overdue.isEmpty() ? WARNING_COLOR : PROBLEM_COLOR);
    }

    private void showOverdueLoans() {
        List<Item> items = db.loadItems();
        List<String> overdue = new ArrayList<>();
        for (Request r : db.loadRequests()) {
            if (r.status.equals("APPROVED") && currentDay > r.dueDay) {
                overdue.add(r.id + " " + itemLabel(getItemNameFromList(items, r.itemId), r.quantity)
                        + " - " + r.studentName + " (was due Day " + r.dueDay + ")");
            }
        }
        if (!overdue.isEmpty()) showProblem("Overdue loans: " + String.join(", ", overdue) + ".");
    }

    private String getItemNameFromList(List<Item> itemsList, String itemId) {
        for (Item item : itemsList) {
            if (item.id.equals(itemId)) return item.name;
        }
        return itemId;
    }

    private String itemLabel(String itemName, int units) {
        return units == 1 ? itemName : itemName + " x" + units;
    }

    private String selectedId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        return (String) table.getModel().getValueAt(table.convertRowIndexToModel(row), 0);
    }

    private int[] askDamage(Request r) {
        JTextField unitsField = new JTextField("1", 4);
        unitsField.setName("damagedUnits");
        JTextField feeField = new JTextField("0", 6);
        feeField.setName("damageFee");
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        if (r.quantity > 1) {
            form.add(new JLabel("Damaged units (of " + r.quantity + "):"));
            form.add(unitsField);
        }
        form.add(new JLabel("Damage fee (" + PESO + "):"));
        form.add(feeField);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(r.id + " came back damaged."), BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, panel, "Returned Damaged", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        int units = r.quantity == 1 ? 1 : parseWholeNumber(unitsField.getText());
        if (units < 1 || units > r.quantity) {
            showProblem("Enter a number from 1 to " + r.quantity + ".");
            return null;
        }
        int fee = parseWholeNumber(feeField.getText());
        if (fee < 0) {
            showProblem("Enter the damage fee as a whole number of pesos (0 or more).");
            return null;
        }
        return new int[]{units, fee};
    }

    private String askNumber(String title, String question, String suggestion) {
        Object answer = JOptionPane.showInputDialog(this, question, title, JOptionPane.QUESTION_MESSAGE, null, null, suggestion);
        return answer == null ? null : answer.toString();
    }

    private int parseWholeNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void showSuccess(String message) {
        showStatus(message, SUCCESS_COLOR);
    }

    private void showProblem(String message) {
        showStatus(message, PROBLEM_COLOR);
    }

    private void showStatus(String message, Color color) {
        statusArea.setForeground(color);
        statusArea.setText(message);
    }

    private String statusText(Request r, boolean overdue) {
        String text = r.status;
        if ("DAMAGED".equals(r.returnCondition)) {
            text += r.quantity == 1 ? " (DAMAGED)" : " (" + r.damagedQuantity + " of " + r.quantity + " DAMAGED)";
        }
        return overdue ? text + " (OVERDUE)" : text;
    }

    private String chargesText(Request r) {
        List<String> charges = new ArrayList<>();
        if (r.penalty > 0) charges.add(PESO + r.penalty + " penalty");
        if (r.damageFee > 0) charges.add(PESO + r.damageFee + " damage");
        if (charges.isEmpty()) return "";
        String paid = r.receiptNo == null ? "PAID" : "PAID (OR " + r.receiptNo + ")";
        return String.join(" + ", charges) + " — " + (r.penaltyPaid ? paid : "UNPAID");
    }

    private int amountOwed(Request r) {
        return r.penalty + r.damageFee;
    }

    private void refreshAll() {
        currentDayLabel.setText("Simulated Day: " + currentDay + "   (penalty: " + PESO + PENALTY_PER_DAY + "/day late per unit)");

        List<Item> currentItems = db.loadItems();
        List<Request> currentRequests = db.loadRequests();

        catalogModel.setRowCount(0);
        inventoryModel.setRowCount(0);
        for (Item item : currentItems) {
            if (item.underMaintenance < item.quantity) {
                catalogModel.addRow(new Object[]{item.id, item.name, item.available, item.quantity});
            }
            int inUse = item.quantity - item.available - item.underMaintenance;
            inventoryModel.addRow(new Object[]{item.id, item.name, item.quantity, item.available, inUse, item.underMaintenance});
        }

        myRequestsModel.setRowCount(0);
        pendingModel.setRowCount(0);
        approvedModel.setRowCount(0);
        unpaidModel.setRowCount(0);
        for (Request r : currentRequests) {
            String itemName = getItemNameFromList(currentItems, r.itemId);
            int daysLate = currentDay - r.dueDay;
            boolean overdue = r.status.equals("APPROVED") && daysLate > 0;
            if (user.loginId.equalsIgnoreCase(r.studentNo)) {
                myRequestsModel.addRow(new Object[]{r.id, itemName, r.quantity, statusText(r, overdue), r.dueDay, chargesText(r)});
            }
            if (r.status.equals("PENDING")) {
                pendingModel.addRow(new Object[]{r.id, r.studentName, itemName, r.quantity, r.dueDay});
            }
            if (r.status.equals("APPROVED")) {
                approvedModel.addRow(new Object[]{r.id, r.studentName, itemName, r.quantity, r.dueDay, overdue ? daysLate : null});
            }
            if (amountOwed(r) > 0 && !r.penaltyPaid) {
                unpaidModel.addRow(new Object[]{r.id, r.studentName, itemName, r.penalty, r.damageFee, amountOwed(r)});
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
    }
}
