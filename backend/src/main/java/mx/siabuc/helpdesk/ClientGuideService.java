package mx.siabuc.helpdesk;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores client onboarding on the server, not in a browser-local flag. */
@Service
public class ClientGuideService {
    public record Status(boolean completed, Instant completedAt) {}

    private final JdbcTemplate db;

    public ClientGuideService(JdbcTemplate db) {
        this.db = db;
    }

    private void requireClient(Models.User actor) {
        if (actor.role() != Models.Role.USER || actor.institutionId() == null)
            throw ApiException.forbidden();
    }

    @Transactional(readOnly = true)
    public Status status(Models.User actor) {
        requireClient(actor);
        return db.query("SELECT completed_at FROM client_guide_completed WHERE user_id=?",
            rs -> rs.next() ? new Status(true, rs.getTimestamp(1).toInstant()) : new Status(false, null),
            actor.id());
    }

    @Transactional
    public Status complete(Models.User actor) {
        requireClient(actor);
        db.update("INSERT INTO client_guide_completed(user_id) VALUES(?) ON CONFLICT (user_id) DO NOTHING", actor.id());
        return status(actor);
    }
}
