import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BorrowEaseDatabase {

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("SQLite JDBC driver not found. Make sure sqlite-jdbc-*.jar is on the classpath.", ex);
        }
    }

    private static final String DB_URL = "jdbc:sqlite:borrowease.db";
    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;

    private final Consumer<String> logger;

    public BorrowEaseDatabase(Consumer<String> logger) {
        this.logger = logger;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public void initDatabase() {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS items (" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, available INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE TABLE IF NOT EXISTS requests (" +
                    "id TEXT PRIMARY KEY, student_name TEXT NOT NULL, item_id TEXT NOT NULL, " +
                    "status TEXT NOT NULL, due_day INTEGER NOT NULL, penalty INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS settings (" +
                    "name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
            st.execute("INSERT OR IGNORE INTO settings (name, value) VALUES ('current_day', 0)");
            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "login_id TEXT PRIMARY KEY COLLATE NOCASE, full_name TEXT NOT NULL, role TEXT NOT NULL, " +
                    "password_salt TEXT NOT NULL, password_hash TEXT NOT NULL)");
            if (!hasColumn(st, "requests", "student_no")) {
                st.execute("ALTER TABLE requests ADD COLUMN student_no TEXT");
            }
            if (!hasColumn(st, "requests", "penalty_paid")) {
                st.execute("ALTER TABLE requests ADD COLUMN penalty_paid INTEGER NOT NULL DEFAULT 0");
            }
            if (!hasColumn(st, "requests", "return_condition")) {
                st.execute("ALTER TABLE requests ADD COLUMN return_condition TEXT");
            }
            if (!hasColumn(st, "items", "under_maintenance")) {
                st.execute("ALTER TABLE items ADD COLUMN under_maintenance INTEGER NOT NULL DEFAULT 0");
            }
            if (!hasColumn(st, "items", "quantity")) {
                st.execute("ALTER TABLE items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1");
            }
            if (!hasColumn(st, "requests", "quantity")) {
                st.execute("ALTER TABLE requests ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1");
            }
            if (!hasColumn(st, "requests", "damaged_quantity")) {
                st.execute("ALTER TABLE requests ADD COLUMN damaged_quantity INTEGER NOT NULL DEFAULT 0");
                st.execute("UPDATE requests SET damaged_quantity = quantity WHERE return_condition = 'DAMAGED'");
            }
            if (!hasColumn(st, "requests", "damage_fee")) {
                st.execute("ALTER TABLE requests ADD COLUMN damage_fee INTEGER NOT NULL DEFAULT 0");
            }
            if (!hasColumn(st, "requests", "receipt_no")) {
                st.execute("ALTER TABLE requests ADD COLUMN receipt_no TEXT COLLATE NOCASE");
            }
            if (!hasColumn(st, "requests", "paid_day")) {
                st.execute("ALTER TABLE requests ADD COLUMN paid_day INTEGER");
            }
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS requests_receipt_no ON requests (receipt_no)");

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM items")) {
                rs.next();
                if (rs.getInt("total") == 0) {
                    st.execute("INSERT INTO items (id, name, quantity, available) VALUES ('EQ-01','Digital Multimeter',5,5)");
                    st.execute("INSERT INTO items (id, name, quantity, available) VALUES ('EQ-02','Projector',3,3)");
                    st.execute("INSERT INTO items (id, name, quantity, available) VALUES ('RM-01','Computer Laboratory 1',1,1)");
                    st.execute("INSERT INTO items (id, name, quantity, available) VALUES ('RM-02','Conference Room B',1,1)");
                }
            }
        } catch (SQLException ex) {
            log("Couldn't open the database: " + describe(ex));
        }
        addStaffAccount("custodian", "Default Custodian", "CUSTODIAN", "custodian123");
        addStaffAccount("admin", "Default Administrator", "ADMINISTRATOR", "admin123");
    }

    private boolean hasColumn(Statement st, String table, String column) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) return true;
            }
        }
        return false;
    }

    private void addStaffAccount(String loginId, String fullName, String role, String password) {
        if (getUser(loginId) != null) return;
        User user = new User(loginId, fullName, role);
        user.passwordSalt = PasswordHasher.newSalt();
        user.passwordHash = PasswordHasher.hash(password.toCharArray(), user.passwordSalt);
        insertUser(user);
    }

    public List<Item> loadItems() {
        List<Item> list = new ArrayList<>();
        String sql = "SELECT id, name, quantity, available, under_maintenance FROM items ORDER BY id";
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Item item = new Item(rs.getString("id"), rs.getString("name"));
                item.quantity = rs.getInt("quantity");
                item.available = rs.getInt("available");
                item.underMaintenance = rs.getInt("under_maintenance");
                list.add(item);
            }
        } catch (SQLException ex) {
            log("Couldn't read the database: " + describe(ex));
        }
        return list;
    }

    public List<Request> loadRequests() {
        List<Request> list = new ArrayList<>();
        String sql = "SELECT id, student_no, student_name, item_id, quantity, status, due_day, penalty, penalty_paid, return_condition, "
                + "damaged_quantity, damage_fee, receipt_no FROM requests";
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Request r = new Request(rs.getString("id"), rs.getString("student_name"),
                        rs.getString("item_id"), rs.getInt("due_day"));
                r.studentNo = rs.getString("student_no");
                r.quantity = rs.getInt("quantity");
                r.status = rs.getString("status");
                r.penalty = rs.getInt("penalty");
                r.penaltyPaid = rs.getInt("penalty_paid") == 1;
                r.returnCondition = rs.getString("return_condition");
                r.damagedQuantity = rs.getInt("damaged_quantity");
                r.damageFee = rs.getInt("damage_fee");
                r.receiptNo = rs.getString("receipt_no");
                list.add(r);
            }
        } catch (SQLException ex) {
            log("Couldn't read the database: " + describe(ex));
        }
        return list;
    }

    public Item getItem(String itemId) {
        String sql = "SELECT id, name, quantity, available, under_maintenance FROM items WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Item item = new Item(rs.getString("id"), rs.getString("name"));
                    item.quantity = rs.getInt("quantity");
                    item.available = rs.getInt("available");
                    item.underMaintenance = rs.getInt("under_maintenance");
                    return item;
                }
            }
        } catch (SQLException ex) {
            log("Couldn't read the database: " + describe(ex));
        }
        return null;
    }

    public Request getRequest(String requestId) {
        String sql = "SELECT id, student_no, student_name, item_id, quantity, status, due_day, penalty, penalty_paid, return_condition, "
                + "damaged_quantity, damage_fee, receipt_no FROM requests WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Request r = new Request(rs.getString("id"), rs.getString("student_name"),
                            rs.getString("item_id"), rs.getInt("due_day"));
                    r.studentNo = rs.getString("student_no");
                    r.quantity = rs.getInt("quantity");
                    r.status = rs.getString("status");
                    r.penalty = rs.getInt("penalty");
                    r.penaltyPaid = rs.getInt("penalty_paid") == 1;
                    r.returnCondition = rs.getString("return_condition");
                    r.damagedQuantity = rs.getInt("damaged_quantity");
                    r.damageFee = rs.getInt("damage_fee");
                    r.receiptNo = rs.getString("receipt_no");
                    return r;
                }
            }
        } catch (SQLException ex) {
            log("Couldn't read the database: " + describe(ex));
        }
        return null;
    }

    public boolean insertItem(Item item) {
        return save(conn -> execute(conn, "INSERT INTO items (id, name, quantity, available) VALUES (?, ?, ?, ?)",
                item.id, item.name, item.quantity, item.available));
    }

    public boolean submitRequest(Request r) {
        return save(conn -> {
            r.id = "REQ-" + (highestRequestNumber(conn) + 1);
            execute(conn, "INSERT INTO requests (id, student_name, item_id, status, due_day, penalty, student_no, quantity) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", r.id, r.studentName, r.itemId, r.status, r.dueDay, r.penalty, r.studentNo, r.quantity);
            execute(conn, "UPDATE items SET available = available - ? WHERE id = ? AND available >= ?", r.quantity, r.itemId, r.quantity);
        });
    }

    public boolean cancelRequest(Request r) {
        return save(conn -> {
            execute(conn, "UPDATE requests SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'", r.id);
            execute(conn, "UPDATE items SET available = available + ? WHERE id = ?", r.quantity, r.itemId);
        });
    }

    public boolean approveRequest(Request r) {
        return save(conn -> execute(conn, "UPDATE requests SET status = 'APPROVED' WHERE id = ? AND status = 'PENDING'", r.id));
    }

    public boolean rejectRequest(Request r) {
        return save(conn -> {
            execute(conn, "UPDATE requests SET status = 'REJECTED' WHERE id = ? AND status = 'PENDING'", r.id);
            execute(conn, "UPDATE items SET available = available + ? WHERE id = ?", r.quantity, r.itemId);
        });
    }

    public boolean recordReturn(Request r, int penalty, int damagedUnits, int damageFee) {
        return save(conn -> {
            execute(conn, "UPDATE requests SET status = 'RETURNED', penalty = ?, return_condition = ?, damaged_quantity = ?, damage_fee = ? "
                    + "WHERE id = ? AND status = 'APPROVED'", penalty, damagedUnits > 0 ? "DAMAGED" : "OK", damagedUnits, damageFee, r.id);
            execute(conn, "UPDATE items SET available = available + ?, under_maintenance = under_maintenance + ? WHERE id = ?",
                    r.quantity - damagedUnits, damagedUnits, r.itemId);
        });
    }

    public boolean repairUnit(String itemId) {
        return save(conn -> execute(conn, "UPDATE items SET under_maintenance = under_maintenance - 1, available = available + 1 "
                + "WHERE id = ? AND under_maintenance > 0", itemId));
    }

    public boolean updateItemQuantity(String itemId, int quantity) {
        return save(conn -> execute(conn, "UPDATE items SET available = available + (? - quantity), quantity = ? "
                + "WHERE id = ? AND available + (? - quantity) >= 0", quantity, quantity, itemId, quantity));
    }

    public boolean markPaid(String requestId, String receiptNo, int paidDay) {
        return save(conn -> execute(conn, "UPDATE requests SET penalty_paid = 1, receipt_no = ?, paid_day = ? WHERE id = ? AND penalty_paid = 0",
                receiptNo, paidDay, requestId));
    }

    public String findRequestWithReceipt(String receiptNo) {
        String sql = "SELECT id FROM requests WHERE receipt_no = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, receiptNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("id") : null;
            }
        } catch (SQLException ex) {
            log("Couldn't read the database: " + describe(ex));
            return null;
        }
    }

    public int loadCurrentDay() {
        String sql = "SELECT value FROM settings WHERE name = 'current_day'";
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt("value") : 0;
        } catch (SQLException ex) {
            log("Couldn't read the database: " + describe(ex));
            return 0;
        }
    }

    public boolean updateCurrentDay(int currentDay) {
        return save(conn -> execute(conn, "UPDATE settings SET value = ? WHERE name = 'current_day'", currentDay));
    }

    public User getUser(String loginId) {
        String sql = "SELECT login_id, full_name, role, password_salt, password_hash FROM users WHERE login_id = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User(rs.getString("login_id"), rs.getString("full_name"), rs.getString("role"));
                    user.passwordSalt = rs.getString("password_salt");
                    user.passwordHash = rs.getString("password_hash");
                    return user;
                }
            }
        } catch (SQLException ex) {
            log("Couldn't read the database: " + describe(ex));
        }
        return null;
    }

    public boolean insertUser(User user) {
        return save(conn -> execute(conn, "INSERT INTO users (login_id, full_name, role, password_salt, password_hash) VALUES (?, ?, ?, ?, ?)",
                user.loginId, user.fullName, user.role, user.passwordSalt, user.passwordHash));
    }

    private interface Work {
        void run(Connection conn) throws SQLException;
    }

    private boolean save(Work work) {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            log("Couldn't save: " + describe(ex));
            return false;
        }
    }

    private static void execute(Connection conn, String sql, Object... values) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("that record has changed since the list was loaded. The lists now show the latest data.");
            }
        }
    }

    private static int highestRequestNumber(Connection conn) throws SQLException {
        String sql = "SELECT MAX(CAST(SUBSTR(id, 5) AS INTEGER)) FROM requests WHERE id LIKE 'REQ-%'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String describe(SQLException ex) {
        int code = ex.getErrorCode() & 0xFF;
        if (code == SQLITE_BUSY || code == SQLITE_LOCKED) {
            return "the database is locked. If DB Browser is open, click Write Changes or close it, then try again.";
        }
        return ex.getMessage();
    }

    private void log(String message) {
        logger.accept(message);
    }
}
