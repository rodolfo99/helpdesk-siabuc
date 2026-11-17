package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-user unread public support messages; never includes internal notes or client replies. */
@Service
public class ClientUnreadService {
    public record UnreadTicket(long caseId, long unreadCount) {}
    public record ReadRequest(Long lastVisibleEntryId) {}

    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;

    public ClientUnreadService(JdbcTemplate db, HelpdeskService helpdesk) {
        this.db = db;
        this.helpdesk = helpdesk;
    }

    private Models.User requireClient() {
        Models.User actor = helpdesk.me();
        if (actor.role() != Models.Role.USER || actor.institutionId() == null)
            throw ApiException.forbidden();
        return actor;
    }

    /** Only existing, non-internal, non-voided entries authored by support staff count as replies. */
    @Transactional(readOnly = true)
    public List<UnreadTicket> unread() {
        Models.User actor = requireClient();
        return db.query("""
            SELECT e.case_id, COUNT(*) AS unread_count
            FROM case_entries e
            JOIN support_cases c ON c.id=e.case_id
            JOIN app_users author ON author.id=e.created_by
            LEFT JOIN client_case_reads seen ON seen.case_id=c.id AND seen.user_id=?
            WHERE c.institution_id=?
              AND e.id>COALESCE(seen.last_seen_entry_id, 0)
              AND NOT e.internal AND NOT e.voided
              AND author.role IN ('ADMIN','TECH','BILLING')
            GROUP BY e.case_id
            ORDER BY e.case_id
            """, (rs, n) -> new UnreadTicket(rs.getLong("case_id"), rs.getLong("unread_count")),
            actor.id(), actor.institutionId());
    }

    /**
     * Mark only the snapshot the client actually fetched as seen. If support writes
     * another message during this request it remains unread for the next visit.
     * The submitted high-water mark must belong to a public entry on this ticket.
     */
    @Transactional
    public void markRead(long caseId, ReadRequest input) {
        Models.User actor = requireClient();
        if (caseId <= 0) throw ApiException.missing();
        Long lastSeen = input == null ? null : input.lastVisibleEntryId();
        if (lastSeen == null || lastSeen < 0) throw ApiException.bad("Indique la última entrada visible del caso.");
        List<Long> caseIds = db.queryForList(
            "SELECT id FROM support_cases WHERE id=? AND institution_id=?",
            Long.class, caseId, actor.institutionId());
        if (caseIds.isEmpty()) throw ApiException.missing();
        if (lastSeen > 0) {
            List<Long> allowed = db.queryForList("""
                SELECT id FROM case_entries WHERE id=? AND case_id=? AND NOT internal
                """, Long.class, lastSeen, caseId);
            if (allowed.isEmpty()) throw ApiException.bad("La entrada indicada no pertenece al historial público del caso.");
        }
        db.update("""
            INSERT INTO client_case_reads(user_id,case_id,last_seen_entry_id)
            VALUES (?,?,?)
            ON CONFLICT (user_id,case_id)
            DO UPDATE SET last_seen_entry_id=GREATEST(client_case_reads.last_seen_entry_id,EXCLUDED.last_seen_entry_id),
                          read_at=NOW()
            """, actor.id(), caseId, lastSeen);
    }
}
