public class User {
    String loginId, fullName, role;
    String passwordSalt, passwordHash;
    User(String loginId, String fullName, String role) {
        this.loginId = loginId;
        this.fullName = fullName;
        this.role = role;
    }
}
