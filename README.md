# BorrowEase

System Architecture
UML Use Case Diagram
Maps the system interactions between the Student, Custodian, and Administrator roles.
UML Sequence Diagram
Illustrates the backend communication between the Request Interface, Inventory Manager, and SQLite Database during a standard borrowing transaction.
What's Included
BorrowEaseGUI.java: The main interface window that dynamically loads the Student, Custodian, or Administrator tabs based on the logged-in user. Contains the main() execution method.
LoginWindow.java: The initial authentication and account creation interface.
BorrowEaseDatabase.java: Handles persistent SQLite database connectivity, executes automated schema updates, seeds default items and staff accounts, and manages data queries.
PasswordHasher.java: Secures user passwords using PBKDF2WithHmacSHA256 encryption with a 16-byte salt and 600,000 iterations.
Item.java: The data model representing equipment/rooms, tracking overall quantity, available units, and units under maintenance.
Request.java: The data model tracking student borrow requests, quantities, penalties, and return conditions.
User.java: The data model for user accounts and role permissions.
Requirements
A JDK (Java 8 or later).
The SQLite JDBC driver (sqlite-jdbc-3.53.4.0.jar). Place this in the root project folder.
How to Run
Run the run.bat file to compile and execute the SQLite-integrated system.
Logging In & Authentication
The login window features a Student / Staff toggle. Each mode strictly accepts its own account type.
Students: Ensure "Student" is selected. First-time users must click "Create account" to register their full name, student number, and a password (minimum 8 characters). Log in using your student number moving forward.
Staff: Toggle to "Staff" (the prompt changes to "Staff ID"). Use the system's seeded accounts[cite: 3, 14]:
Custodian: ID custodian | Password custodian123
Administrator: ID admin | Password admin123
Note: The status bar at the bottom of the window displays action results (green for success, red for errors). It also displays login reminders: students are alerted of loans due soon or overdue, while the custodian is alerted of all overdue system loans.
Core Features by Role
Student
Browse Catalog: View items and availability. Items are only hidden if all units are under maintenance.
Submit Requests: Request one or multiple units of an item. Note: The system blocks new requests if the student has unpaid charges or an overdue item.
Manage Loans: View request status, track due days, and see itemized charges (penalties and damage fees) marked as UNPAID or PAID with an OR number. Cancel pending requests.
Custodian
Manage Requests: Approve or reject incoming pending requests.
Process Returns: Mark items as "Returned OK" or "Returned Damaged". Damaged returns prompt for the number of damaged units (which are shifted to maintenance) and a damage fee. Late penalties are automatically calculated at ₱50 per day late per unit.
Clearances: View unpaid charges and mark them as Paid by inputting a unique Official Receipt (OR) number.
Administrator
Inventory Control: Add new items with specific quantities. Adjust total item quantities.
Maintenance: Track items currently "in use" or "under maintenance". Click "Mark Repaired" to restore one maintained unit back to the available catalog.
Reporting: Generate a Utilization Report in a new window, displaying total times borrowed and total units borrowed per item.
Inspecting the Database (borrowease.db)
Using DB Browser for SQLite, you can directly query the items, requests, settings, and users tables.
Security: Passwords are never stored as plain text, only as salted hashes.
Inventory Tracking: The items table tracks quantity (owned), available (on the shelf), and under_maintenance Do not manually edit quantities in the DB; use the Admin "Change Quantity" tool to keep availability synced.
Fines & Conditions: The requests table records return_condition, damaged_quantity, and damage_fee Financial tracking utilizes penalty_paid, receipt_no, and paid_day
Time Simulation: The settings table stores current_day. Close the application and set this to 0 to restart the timeline, or delete borrowease.db to factory reset the entire system (which will re-seed 5 Multimeters, 3 Projectors, and 1 of each room upon launch) Always close DB Browser before using the Java app to prevent database lock errors.
Known Limitations
No password change or reset functionality.
Staff accounts are currently limited to the two seeded defaults and cannot be added in-app.
All units in a multi-unit request must be returned together (no partial returns).
Time relies on a simulated integer (Advance Day) rather than the real-world system calendar.
Single local database file; concurrent multi-user network access has not been stress-tested.
