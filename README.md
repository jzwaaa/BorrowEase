#BorrowEase#

**BorrowEase** is a Java-based desktop application developed to replace manual paper logbooks with an automated equipment and room borrowing system.

* **Student Role:** Browse real-time catalog availability, submit multi-unit borrow requests, cancel pending requests, and track due dates and penalty/damage charges (with borrowing blocked if holding overdue items or unpaid balances).


* **Custodian Role:** Review, approve, or reject incoming requests; process returns as "OK" or "Damaged" (routing broken units to maintenance); calculate late penalties (₱50/day per unit) and damage fees; and record payments with unique Official Receipt (OR) numbers.


* **Administrator Role:** Manage total inventory counts, add new equipment/rooms, restore repaired units back to the catalog, and generate equipment utilization reports.


* **Technical Stack & Architecture:** Built with Java Swing, backed by an embedded SQLite database (`borrowease.db`), secured with PBKDF2 password hashing, and equipped with a simulated timeline feature to test overdue deadlines and penalty calculations.
