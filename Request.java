public class Request {
    String id, studentNo, studentName, itemId, status;
    int quantity = 1;
    int dueDay;
    int penalty;
    boolean penaltyPaid;
    String returnCondition;
    int damagedQuantity;
    int damageFee;
    String receiptNo;
    Request(String id, String studentName, String itemId, int dueDay) {
        this.id = id;
        this.studentName = studentName;
        this.itemId = itemId;
        this.status = "PENDING";
        this.dueDay = dueDay;
    }
}
