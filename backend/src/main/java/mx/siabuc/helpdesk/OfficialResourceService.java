package mx.siabuc.helpdesk;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editorial links to institution-hosted SIABUC resources. This does not fetch,
 * proxy, store, upload, or attest to the contents of the remote downloads.
 */
@Service
public class OfficialResourceService {
    public enum Kind { INSTALLER, PATCH, REPORT, DOCUMENT, VIDEO }
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
    public record Resource(long id, String title, Kind kind, String siabucVersion, String description,
                           String targetUrl, Status status, int revision, Instant updatedAt, Instant publishedAt) {}
    public record Input(String title, Kind kind, String siabucVersion, String description, String targetUrl,
                        Integer expectedRevision) {}
    public record StatusInput(Status status, Integer expectedRevision) {}

    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    public OfficialResourceService(JdbcTemplate db, HelpdeskService helpdesk) {
        this.db=db; this.helpdesk=helpdesk;
    }
    private static final String COLUMNS = "id,title,kind,siabuc_version,description,target_url,status,revision,updated_at,published_at";
    private static final RowMapper<Resource> ROW=(r,n)->new Resource(
        r.getLong("id"),r.getString("title"),Kind.valueOf(r.getString("kind")),
        r.getString("siabuc_version"),r.getString("description"),r.getString("target_url"),
        Status.valueOf(r.getString("status")),r.getInt("revision"),
        r.getTimestamp("updated_at").toInstant(),
        r.getTimestamp("published_at")==null?null:r.getTimestamp("published_at").toInstant());

    private Models.User viewer() {
        Models.User actor=helpdesk.me();
        if (actor.role()!=Models.Role.ADMIN && actor.role()!=Models.Role.TECH && actor.role()!=Models.Role.USER)
            throw ApiException.forbidden();
        return actor;
    }
    private Models.User admin() {
        Models.User actor=helpdesk.me();
        if (actor.role()!=Models.Role.ADMIN) throw ApiException.forbidden();
        return actor;
    }
    @Transactional(readOnly=true)
    public List<Resource> list(Kind kind, String q) {
        Models.User actor=viewer();
        String search=clean(q,"Búsqueda",120,false);
        StringBuilder query=new StringBuilder("SELECT "+COLUMNS+" FROM official_resources WHERE 1=1");
        List<Object> parameters=new ArrayList<>();
        if(actor.role()!=Models.Role.ADMIN) query.append(" AND status='PUBLISHED'");
        if(kind!=null){query.append(" AND kind=?");parameters.add(kind.name());}
        if(!search.isEmpty()){
            query.append(" AND (POSITION(LOWER(?) IN LOWER(title))>0 OR POSITION(LOWER(?) IN LOWER(description))>0)");
            parameters.add(search);parameters.add(search);
        }
        query.append(" ORDER BY updated_at DESC,id DESC LIMIT 200");
        return db.query(query.toString(),ROW,parameters.toArray());
    }
    @Transactional(readOnly=true)
    public Resource get(long id) {
        Models.User actor=viewer();
        return lookup(id,actor.role()!=Models.Role.ADMIN,false);
    }
    private Resource lookup(long id, boolean publishedOnly, boolean lock) {
        List<Resource> results=db.query("SELECT "+COLUMNS+" FROM official_resources WHERE id=?"
            +(publishedOnly?" AND status='PUBLISHED'":"")+(lock?" FOR UPDATE":""),
            ROW,id);
        if(results.isEmpty()) throw ApiException.missing();
        return results.get(0);
    }
    @Transactional
    public Resource create(Input input) {
        Models.User actor=admin();Input value=validate(input);
        long id=db.queryForObject("""
            INSERT INTO official_resources(title,kind,siabuc_version,description,target_url,created_by,updated_by)
            VALUES(?,?,?,?,?,?,?) RETURNING id
            """,Long.class,value.title(),value.kind().name(),value.siabucVersion(),
            value.description(),value.targetUrl(),actor.id(),actor.id());
        audit(actor,"CREATE",id);
        return lookup(id,false,false);
    }
    @Transactional
    public Resource update(long id, Input input) {
        Models.User actor=admin();Input value=validate(input);
        Resource current=lookup(id,false,true);revision(current,value.expectedRevision());
        if(current.status()!=Status.DRAFT) throw ApiException.conflict("Retire el recurso a borrador antes de editarlo.");
        db.update("""
            UPDATE official_resources SET title=?,kind=?,siabuc_version=?,description=?,target_url=?,
                revision=revision+1,updated_by=?,updated_at=NOW() WHERE id=?
            """,value.title(),value.kind().name(),value.siabucVersion(),value.description(),
            value.targetUrl(),actor.id(),id);
        audit(actor,"UPDATE",id);
        return lookup(id,false,false);
    }
    @Transactional
    public Resource changeStatus(long id, StatusInput input) {
        Models.User actor=admin();
        if(input==null||input.status()==null) throw ApiException.bad("Indique el estado del recurso.");
        Resource current=lookup(id,false,true);revision(current,input.expectedRevision());
        if(current.status()==input.status())return current;
        if(current.status()==Status.ARCHIVED && input.status()!=Status.DRAFT)
            throw ApiException.conflict("Recupere el recurso archivado como borrador antes de publicarlo.");
        if(current.status()==Status.PUBLISHED && input.status()==Status.ARCHIVED)
            throw ApiException.conflict("Retire la publicación antes de archivar.");
        db.update("""
            UPDATE official_resources SET status=?,revision=revision+1,updated_by=?,updated_at=NOW(),
                published_at=CASE WHEN ?='PUBLISHED' THEN NOW() ELSE published_at END WHERE id=?
            """,input.status().name(),actor.id(),input.status().name(),id);
        audit(actor,"STATUS_"+input.status().name(),id);
        return lookup(id,false,false);
    }
    private void audit(Models.User actor,String action,long id) {
        db.update("""
            INSERT INTO audit_log(actor,action,entity_type,entity_id,details)
            VALUES(?,?,'OFFICIAL_RESOURCE',?,?)
            """,actor.username(),"RESOURCE_"+action,id,"Recurso institucional #"+id);
    }
    private static void revision(Resource current,Integer version){
        if(version==null||version<1)throw ApiException.bad("Indique la revisión del recurso.");
        if(current.revision()!=version)throw ApiException.conflict("Otro administrador modificó el recurso. Vuelva a cargarlo.");
    }
    private static Input validate(Input input) {
        if(input==null||input.kind()==null)throw ApiException.bad("Indique el tipo de recurso.");
        String url=clean(input.targetUrl(),"Enlace HTTPS",2000,true);
        try{
            URI uri=new URI(url);
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            if(!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo()!=null || uri.getPort()!=-1 && uri.getPort()!=443
                || uri.getHost()==null || uri.getRawFragment()!=null
                || !("ucol.mx".equals(host)||host.endsWith(".ucol.mx")))
                throw ApiException.bad("Use un enlace HTTPS de un dominio institucional ucol.mx autorizado.");
        }catch(URISyntaxException e){throw ApiException.bad("La dirección del recurso no es válida.");}
        return new Input(clean(input.title(),"Título",180,true),input.kind(),
            clean(input.siabucVersion(),"Versión",120,false),
            clean(input.description(),"Descripción",1200,true),url,input.expectedRevision());
    }
    private static String clean(String raw,String name,int max,boolean required){
        String value=raw==null?"":raw.strip();
        if((required&&value.isEmpty())||value.length()>max
            ||value.codePoints().anyMatch(c->Character.isISOControl(c)))
            throw ApiException.bad(name+": texto inválido o demasiado largo.");
        return value;
    }
}
