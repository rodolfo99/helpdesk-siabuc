package mx.siabuc.helpdesk;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Administrative reporting; balances are never changed by a report or export. */
@Service
public class ReportService {
    static final int MAX_ROWS = 5000;
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    private final ZoneId zone;

    public ReportService(JdbcTemplate db, HelpdeskService helpdesk,
            @Value("${app.reporting-time-zone:America/Mexico_City}") String timeZone) {
        this.db = db; this.helpdesk = helpdesk; this.zone = ZoneId.of(timeZone);
    }

    public record Filters(Long institutionId, Long administratorId, String channel, LocalDate from, LocalDate to) {}
    public record CaseRow(long id, long institutionId, String institutionName, String title, String description,
            String channel, String status, Long administratorId, String administratorName, String createdBy, Instant createdAt) {}
    public record AttentionRow(long id, long caseId, long institutionId, String institutionName, String caseTitle,
            long administratorId, String administratorName, String username, String channel, long minutes,
            boolean internal, String description, Instant createdAt, boolean voided, String voidReason) {}
    public record Totals(long cases, long openCases, long closedCases, long attentionEntries, long voidedEntries,
            long emailMinutes, long remoteMinutes) {}
    public record Consumption(long institutionId, String institutionName, long administratorId,
            String administratorName, String channel, long entries, long minutes) {}
    public record Report(Instant generatedAt, String timeZone, Filters filters, Totals totals,
            List<CaseRow> cases, List<AttentionRow> attentions, List<Consumption> consumption) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report support(Long institutionId, Long administratorId, String channel, String from, String to) {
        if (helpdesk.me().role() != Models.Role.ADMIN) throw ApiException.forbidden();
        if ((institutionId != null && institutionId <= 0) || (administratorId != null && administratorId <= 0))
            throw ApiException.bad("Los identificadores de institución y administrativo deben ser positivos.");
        if (channel != null && !channel.isBlank() && !List.of("EMAIL", "REMOTE").contains(channel))
            throw ApiException.bad("Canal inválido. Utilice EMAIL o REMOTE.");
        channel = channel == null || channel.isBlank() ? null : channel;
        LocalDate today = LocalDate.now(zone);
        LocalDate first = date(from, today.withDayOfMonth(1)), last = date(to, today);
        if (first.isAfter(last) || ChronoUnit.DAYS.between(first, last) > 365)
            throw ApiException.bad("El periodo debe estar ordenado y abarcar como máximo 366 días.");
        Filters filters = new Filters(institutionId, administratorId, channel, first, last);
        Timestamp start = Timestamp.from(first.atStartOfDay(zone).toInstant());
        Timestamp end = Timestamp.from(last.plusDays(1).atStartOfDay(zone).toInstant());

        List<Object> cp = new ArrayList<>(List.of(start, end));
        String cw = " WHERE c.created_at>=? AND c.created_at<?";
        if (institutionId != null) { cw += " AND c.institution_id=?"; cp.add(institutionId); }
        if (administratorId != null) { cw += " AND c.assigned_admin_id=?"; cp.add(administratorId); }
        if (channel != null) { cw += " AND c.channel=?"; cp.add(channel); }
        List<CaseRow> cases = db.query("""
            SELECT c.*,i.name AS institution_name,a.display_name AS administrator_name,u.username AS creator
            FROM support_cases c JOIN institutions i ON i.id=c.institution_id
            JOIN app_users u ON u.id=c.created_by LEFT JOIN app_users a ON a.id=c.assigned_admin_id
            """ + cw + " ORDER BY c.created_at,c.id LIMIT " + (MAX_ROWS + 1), (r, n) ->
            new CaseRow(r.getLong("id"),r.getLong("institution_id"),r.getString("institution_name"),r.getString("title"),
                r.getString("description"),r.getString("channel"),r.getString("status"),r.getObject("assigned_admin_id",Long.class),
                r.getString("administrator_name"),r.getString("creator"),r.getTimestamp("created_at").toInstant()), cp.toArray());

        List<Object> ep = new ArrayList<>(List.of(start, end));
        String ew = " WHERE e.minutes>0 AND e.created_at>=? AND e.created_at<?";
        if (institutionId != null) { ew += " AND c.institution_id=?"; ep.add(institutionId); }
        if (administratorId != null) { ew += " AND e.created_by=?"; ep.add(administratorId); }
        if (channel != null) { ew += " AND e.channel=?"; ep.add(channel); }
        List<AttentionRow> entries = db.query("""
            SELECT e.*,c.institution_id,c.title AS case_title,i.name AS institution_name,
              u.display_name AS administrator_name,u.username
            FROM case_entries e JOIN support_cases c ON c.id=e.case_id
            JOIN institutions i ON i.id=c.institution_id JOIN app_users u ON u.id=e.created_by
            """ + ew + " ORDER BY e.created_at,e.id LIMIT " + (MAX_ROWS + 1), (r,n) ->
            new AttentionRow(r.getLong("id"),r.getLong("case_id"),r.getLong("institution_id"),r.getString("institution_name"),
                r.getString("case_title"),r.getLong("created_by"),r.getString("administrator_name"),r.getString("username"),
                r.getString("channel"),r.getLong("minutes"),r.getBoolean("internal"),r.getString("description"),
                r.getTimestamp("created_at").toInstant(),r.getBoolean("voided"),r.getString("void_reason")), ep.toArray());
        if (cases.size() + entries.size() > MAX_ROWS)
            throw ApiException.bad("El reporte supera 5000 registros. Reduzca el periodo o agregue filtros; no se han omitido filas.");
        // Bound large exports while retaining complete descriptions; never silently truncate records.
        long characters = cases.stream().mapToLong(c -> c.description().length()).sum()
            + entries.stream().mapToLong(e -> e.description().length() + (e.voidReason() == null ? 0 : e.voidReason().length())).sum();
        if (characters > 2_000_000)
            throw ApiException.bad("Las descripciones del reporte son demasiado extensas. Reduzca el periodo o agregue filtros.");
        long email = 0, remote = 0, voided = 0;
        Map<String, Consumption> groups = new LinkedHashMap<>();
        for (AttentionRow e : entries) {
            if (e.voided()) { voided++; continue; }
            if (e.channel().equals("EMAIL")) email += e.minutes(); else remote += e.minutes();
            String key = e.institutionId()+":"+e.administratorId()+":"+e.channel();
            Consumption previous = groups.get(key);
            groups.put(key, new Consumption(e.institutionId(), e.institutionName(), e.administratorId(),e.administratorName(),e.channel(),
                previous == null ? 1 : previous.entries()+1, previous == null ? e.minutes() : previous.minutes()+e.minutes()));
        }
        long opened = cases.stream().filter(c -> c.status().equals("OPEN")).count();
        return new Report(Instant.now(), zone.getId(), filters,
            new Totals(cases.size(), opened, cases.size()-opened, entries.size()-voided, voided, email, remote),
            List.copyOf(cases), List.copyOf(entries), List.copyOf(groups.values()));
    }

    private static LocalDate date(String text, LocalDate fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
            LocalDate date = LocalDate.parse(text);
            if (date.getYear() < 1900 || date.getYear() > 9998) throw new IllegalArgumentException();
            return date;
        } catch (RuntimeException e) { throw ApiException.bad("Fecha inválida. Utilice AAAA-MM-DD (año entre 1900 y 9998)."); }
    }
}
