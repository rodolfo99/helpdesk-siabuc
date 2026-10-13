package mx.siabuc.helpdesk;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientFinanceAccessService {
    private final JdbcTemplate db;private final HelpdeskService helpdesk;
    public ClientFinanceAccessService(JdbcTemplate db,HelpdeskService helpdesk){this.db=db;this.helpdesk=helpdesk;}
    public record Access(long institutionId,String institutionName,boolean active,boolean enabled,Instant updatedAt){}
    public boolean enabled(long institutionId){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM client_finance_access WHERE institution_id=? AND enabled)",Boolean.class,institutionId));}
    public Models.User requireClient(){Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.USER || actor.institutionId()==null || !enabled(actor.institutionId()))throw ApiException.forbidden();return actor;}
    public boolean myEnabled(){Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.USER)throw ApiException.forbidden();return enabled(actor.institutionId());}
    public List<Access> settings(){admin();return db.query("SELECT i.id,i.name,i.active,COALESCE(a.enabled,FALSE) AS enabled,a.updated_at FROM institutions i LEFT JOIN client_finance_access a ON a.institution_id=i.id ORDER BY i.name,i.id",(r,n)->new Access(r.getLong("id"),r.getString("name"),r.getBoolean("active"),r.getBoolean("enabled"),r.getTimestamp("updated_at")==null?null:r.getTimestamp("updated_at").toInstant()));}
    @Transactional public Access update(long institutionId,boolean enabled){
        Models.User actor=admin();List<Boolean> institutions=db.query("SELECT active FROM institutions WHERE id=? FOR UPDATE",(r,n)->r.getBoolean(1),institutionId);
        if(institutions.isEmpty())throw ApiException.missing();if(enabled&&!institutions.get(0))throw ApiException.conflict("La institución está dada de baja.");
        boolean before=enabled(institutionId);
        db.update("INSERT INTO client_finance_access(institution_id,enabled,updated_by) VALUES(?,?,?) ON CONFLICT(institution_id) DO UPDATE SET enabled=EXCLUDED.enabled,updated_by=EXCLUDED.updated_by,updated_at=NOW()",institutionId,enabled,actor.id());
        if(before!=enabled)db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,'CLIENT_FINANCE_ACCESS','INSTITUTION',?,?)",actor.username(),institutionId,"Portal financiero cliente "+(enabled?"habilitado: pólizas, importes, pagos, recibos y comprobantes propios, incluidas evidencias anteriores; solicitudes de validación.":"deshabilitado: se conserva todo el historial."));
        return settings().stream().filter(a->a.institutionId()==institutionId).findFirst().orElseThrow();
    }
    private Models.User admin(){Models.User actor=helpdesk.me();if(actor.role()!=Models.Role.ADMIN)throw ApiException.forbidden();return actor;}
}
