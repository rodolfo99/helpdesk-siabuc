package mx.siabuc.helpdesk;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public final class Models {
    private Models() {}
    public enum Role { ADMIN, USER, BILLING, TECH }
    public static boolean isSupportStaff(Role role) { return role == Role.ADMIN || role == Role.TECH; }
    public record SupportStaff(long id, String displayName, Role role) {}
    public enum PaymentMethod { TRANSFER, DEPOSIT, CASH, CREDIT_CARD, DEBIT_CARD, CHECK, OTHER, CARD }
    public enum Channel { EMAIL, REMOTE }
    public enum CaseStatus {
        // Keep OPEN on the wire and in existing data; its visible name is now Recibido.
        OPEN("Recibido"), IN_PROGRESS("En atención"), WAITING_CLIENT("Requiere respuesta del cliente"),
        RESOLVED("Resuelto"), CLOSED("Cerrado");
        private final String label;
        CaseStatus(String label) { this.label = label; }
        public String label() { return label; }
        public boolean active() { return this != RESOLVED && this != CLOSED; }
        public boolean canRunTimer() { return this == OPEN || this == IN_PROGRESS; }
        public boolean canChangeTo(CaseStatus next) {
            if (next == this) return true;
            if (active()) return next != OPEN;
            return next == IN_PROGRESS || next == OPEN || (this == RESOLVED && next == CLOSED);
        }
    }
    public record User(long id, String username, String displayName, Role role, Long institutionId, boolean active, String email) {}
    public record Institution(long id, String name, String contactName, String email, String phone, String notes,
        boolean active, long emailAllocatedMinutes, long emailUsedMinutes, long emailRemainingMinutes,
        long remoteAllocatedMinutes, long remoteUsedMinutes, long remoteRemainingMinutes, Long supportAdminId) {}
    public record SupportCase(long id, long institutionId, String institutionName, String title, String description,
        Channel channel, CaseStatus status, String createdBy, Instant createdAt, Instant updatedAt, long totalMinutes,
        Long assignedAdminId, String assignedAdminName, Long licenseId, String licenseNumber) {}
    public record Entry(long id, long caseId, String description, Channel channel, long minutes, boolean internal,
        String createdBy, Instant createdAt, boolean voided, String voidReason) {}
    public record CaseDetail(long id, long institutionId, String institutionName, String title, String description,
        Channel channel, CaseStatus status, String createdBy, Instant createdAt, Instant updatedAt, long totalMinutes,
        List<Entry> entries, Long assignedAdminId, String assignedAdminName, Long licenseId, String licenseNumber) {}
    public record Audit(long id, String actor, String action, String entityType, Long entityId, String details, Instant createdAt) {}
    public record InstitutionInput(String name, String contactName, String email, String phone, String notes,
        Long emailAllocatedMinutes, Long remoteAllocatedMinutes, Boolean active, Long supportAdminId) {}
    public record UserInput(String username, String displayName, String password, Role role, Long institutionId, Boolean active, String email) {}
    public record CaseInput(Long institutionId, String title, String description, Channel channel, Long assignedAdminId,
        CaseProfileService.ProfileInput profile, Long licenseId) {}
    public record CaseEdit(String title, String description, Channel channel, Long assignedAdminId) {}
    public record EntryInput(String description, Channel channel, Long minutes, Boolean internal) {}
    public record StatusInput(CaseStatus status, String reason) {}
    public record VoidInput(String reason) {}
    public record PasswordInput(String currentPassword, String newPassword) {}
    public record PaymentInput(String requestId, Long institutionId, String reference, BigDecimal amount, String currency,
        LocalDate paidOn, PaymentMethod method, Long emailMinutes, Long remoteMinutes, String notes, Long policyId) {}
    public record Payment(long id, String requestId, long institutionId, String institutionName, String reference,
        BigDecimal amount, String currency, LocalDate paidOn, PaymentMethod method, long emailMinutes, long remoteMinutes,
        String notes, String createdBy, Instant createdAt, boolean voided, String voidReason, String voidedBy, Instant voidedAt,
        Long policyId, String policyFolio) {}
}
