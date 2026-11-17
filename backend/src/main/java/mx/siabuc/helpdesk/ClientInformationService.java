package mx.siabuc.helpdesk;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, client-safe projection of support agreements and licenses.
 * Scope is always derived from the authenticated institution; callers never
 * select institution or policy IDs. Commercial SIABUC9 validation is separate.
 */
@Service
public class ClientInformationService {
    public record Policy(long id, String folio, String description, LocalDate validFrom, LocalDate validUntil,
                         String status, String validity, Long licenseId, String licenseNumber) {}
    public record License(long id, String number, long emailRemainingMinutes, long remoteRemainingMinutes) {}
    public record Summary(long institutionId, String institutionName, List<Policy> policies, List<License> licenses) {}

    private final JdbcTemplate db;

    public ClientInformationService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(Models.User user) {
        if (user.role() != Models.Role.USER || user.institutionId() == null)
            throw ApiException.forbidden();
        long institutionId = user.institutionId();
        String institutionName = db.queryForObject(
            "SELECT name FROM institutions WHERE id=? AND active=TRUE", String.class, institutionId);
        LocalDate today = LocalDate.now(ZoneId.of("America/Mexico_City"));

        List<Policy> policies = db.query("""
            SELECT p.id, p.folio, p.description, p.valid_from, p.valid_until, p.status,
                   l.id AS license_id, l.number AS license_number
            FROM support_policies p
            LEFT JOIN support_licenses l ON l.id=p.license_id AND l.institution_id=p.institution_id
            WHERE p.institution_id=?
            ORDER BY p.id DESC
            """, (rs, n) -> {
                LocalDate from = rs.getDate("valid_from").toLocalDate();
                LocalDate until = rs.getDate("valid_until").toLocalDate();
                String validity = today.isBefore(from) ? "UPCOMING" : today.isAfter(until) ? "EXPIRED" : "CURRENT";
                return new Policy(rs.getLong("id"), rs.getString("folio"), rs.getString("description"),
                    from, until, rs.getString("status"), validity,
                    rs.getObject("license_id", Long.class), rs.getString("license_number"));
            }, institutionId);

        List<License> licenses = db.query("""
            SELECT id, number, email_allocated_minutes-email_used_minutes AS email_remaining_minutes,
                   remote_allocated_minutes-remote_used_minutes AS remote_remaining_minutes
            FROM support_licenses WHERE institution_id=? ORDER BY number, id
            """, (rs, n) -> new License(rs.getLong("id"), rs.getString("number"),
                rs.getLong("email_remaining_minutes"), rs.getLong("remote_remaining_minutes")),
            institutionId);

        return new Summary(institutionId, institutionName, policies, licenses);
    }
}
