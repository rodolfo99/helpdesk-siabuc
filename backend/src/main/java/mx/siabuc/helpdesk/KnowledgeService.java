package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
    public enum Category { INSTALLATION, CATALOGING, LOANS, REPORTS, IMPORT, OTHER, GUIDES, FAQ, SUPPORT, POLICIES_LICENSES, TRAINING, CONTACT }
    public record Article(long id,String title,Category category,String siabucVersion,String summary,String body,
            Status status,int revision,Instant createdAt,Instant updatedAt,Instant publishedAt) {}
    public record Summary(long id,String title,Category category,String siabucVersion,String summary,
            Status status,int revision,Instant updatedAt,Instant publishedAt) {}
    public record Page(List<Summary> items,long total,int page,int pageSize) {}
    public record Input(String title,Category category,String siabucVersion,String summary,String body,
            String changeNote,Integer expectedRevision) {}
    public record StatusInput(Status status,Integer expectedRevision,String changeNote) {}
    public record History(long id,int revision,String action,String title,Category category,String siabucVersion,
            String summary,String body,Status status,String changeNote,String actor,Instant createdAt) {}
    public record LinkInput(Long articleId) {}
    public record CaseLink(long caseId,Summary article,Instant linkedAt) {}
    private record Scope(long institutionId,boolean institutionActive) {}
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    public KnowledgeService(JdbcTemplate db,HelpdeskService helpdesk) { this.db=db;this.helpdesk=helpdesk; }

    private static Instant time(ResultSet r,String field) throws SQLException {
        return r.getTimestamp(field)==null ? null : r.getTimestamp(field).toInstant();
    }
    private static Summary summary(ResultSet r) throws SQLException {
        return new Summary(r.getLong("id"),r.getString("title"),Category.valueOf(r.getString("category")),
            r.getString("siabuc_version"),r.getString("summary"),Status.valueOf(r.getString("status")),r.getInt("revision"),
            time(r,"updated_at"),time(r,"published_at"));
    }
    private static final RowMapper<Article> ARTICLE=(r,n)->new Article(r.getLong("id"),r.getString("title"),
        Category.valueOf(r.getString("category")),r.getString("siabuc_version"),r.getString("summary"),r.getString("body"),
        Status.valueOf(r.getString("status")),r.getInt("revision"),time(r,"created_at"),time(r,"updated_at"),time(r,"published_at"));

    public Page list(String q,Category category,String siabucVersion,Status status,int page,int pageSize) {
        User actor=actor();
        if(page<1 || page>100000 || pageSize<1 || pageSize>100) throw ApiException.bad("La página debe ser positiva y el tamaño entre 1 y 100.");
        String search=text(q,"Búsqueda",200,false),version=text(siabucVersion,"Versión de SIABUC",120,false);
        List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE 1=1");
        // USER can never broaden the scope with a status parameter, including guessed IDs elsewhere.
        Status visible=actor.role()!=Role.ADMIN ? Status.PUBLISHED : status;
        if(visible!=null){where.append(" AND status=?");args.add(visible.name());}
        if(category!=null){where.append(" AND category=?");args.add(category.name());}
        if(!search.isEmpty()){
            where.append(" AND (POSITION(LOWER(?) IN LOWER(title))>0 OR POSITION(LOWER(?) IN LOWER(summary))>0 OR POSITION(LOWER(?) IN LOWER(body))>0)");
            args.add(search);args.add(search);args.add(search);
        }
        if(!version.isEmpty()){where.append(" AND POSITION(LOWER(?) IN LOWER(siabuc_version))>0");args.add(version);}
        long count=db.queryForObject("SELECT COUNT(*) FROM knowledge_articles"+where,Long.class,args.toArray());
        args.add(pageSize);args.add((page-1)*pageSize);
        List<Summary> rows=db.query("SELECT * FROM knowledge_articles"+where+" ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->summary(r),args.toArray());
        return new Page(rows,count,page,pageSize);
    }
    public Article get(long id) { return find(id,actor(),false); }

    @Transactional
    public Article create(Input input) {
        User actor=admin();Input value=validate(input);
        Long id=db.queryForObject("""
            INSERT INTO knowledge_articles(title,category,siabuc_version,summary,body,created_by,updated_by)
            VALUES (?,?,?,?,?,?,?) RETURNING id
            """,Long.class,value.title(),value.category().name(),value.siabucVersion(),value.summary(),value.body(),actor.id(),actor.id());
        Article result=find(id,actor,false);record(result,"CREATE",value.changeNote(),actor);return result;
    }
    @Transactional
    public Article update(long id,Input input) {
        User actor=admin();Article previous=find(id,actor,true);Input value=validate(input);
        revision(previous,value.expectedRevision());
        if(previous.status()!=Status.DRAFT) throw ApiException.conflict("Pase el artículo a borrador antes de editarlo. Mientras sea borrador no será visible para clientes.");
        db.update("""
            UPDATE knowledge_articles SET title=?,category=?,siabuc_version=?,summary=?,body=?,revision=revision+1,
            updated_by=?,updated_at=NOW() WHERE id=?
            """,value.title(),value.category().name(),value.siabucVersion(),value.summary(),value.body(),actor.id(),id);
        Article result=find(id,actor,false);record(result,"EDIT",value.changeNote(),actor);return result;
    }
    @Transactional
    public Article changeStatus(long id,StatusInput input) {
        User actor=admin();Article previous=find(id,actor,true);
        if(input==null || input.status()==null) throw ApiException.bad("Indique el estado editorial.");
        revision(previous,input.expectedRevision());
        String note=text(input.changeNote(),"Motivo del cambio",1000,true);
        if(previous.status()==input.status())return previous;
        if(previous.status()==Status.ARCHIVED && input.status()!=Status.DRAFT)
            throw ApiException.conflict("Recupere el artículo como borrador antes de publicarlo.");
        String action=input.status()==Status.PUBLISHED ? "PUBLISH" : input.status()==Status.ARCHIVED ? "ARCHIVE"
            : previous.status()==Status.ARCHIVED ? "RESTORE_DRAFT" : "UNPUBLISH";
        db.update("""
            UPDATE knowledge_articles SET status=?,revision=revision+1,updated_by=?,updated_at=NOW(),
            published_at=CASE WHEN ?='PUBLISHED' THEN NOW() ELSE published_at END WHERE id=?
            """,input.status().name(),actor.id(),input.status().name(),id);
        Article result=find(id,actor,false);record(result,action,note,actor);return result;
    }
    public List<History> history(long id) {
        User actor=admin();find(id,actor,false);
        return db.query("""
            SELECT h.*,u.username AS actor_name FROM knowledge_article_history h JOIN app_users u ON u.id=h.actor
            WHERE h.article_id=? ORDER BY h.revision DESC
            """,(r,n)->new History(r.getLong("id"),r.getInt("revision"),r.getString("action"),r.getString("title"),
                Category.valueOf(r.getString("category")),r.getString("siabuc_version"),r.getString("summary"),r.getString("body"),
                Status.valueOf(r.getString("status")),r.getString("change_note"),r.getString("actor_name"),time(r,"created_at")),id);
    }
    public List<CaseLink> links(long caseId) {
        User actor=actor();scope(caseId,actor,false);
        return linksFor(caseId,actor);
    }
    @Transactional
    public List<CaseLink> link(long caseId,LinkInput input) {
        User actor=staff();Scope scope=scope(caseId,actor,true);
        if(!scope.institutionActive())throw ApiException.conflict("La institución está dada de baja.");
        if(input==null || input.articleId()==null || input.articleId()<1)throw ApiException.bad("Seleccione un artículo publicado.");
        Article article=find(input.articleId(),actor,true);
        if(article.status()!=Status.PUBLISHED)throw ApiException.conflict("Sólo se pueden vincular artículos publicados.");
        int changed=db.update("""
            INSERT INTO knowledge_case_links(case_id,article_id,linked_by) VALUES (?,?,?)
            ON CONFLICT(case_id,article_id) DO UPDATE SET active=TRUE,linked_by=EXCLUDED.linked_by,linked_at=NOW(),removed_by=NULL,removed_at=NULL
            WHERE NOT knowledge_case_links.active
            """,caseId,article.id(),actor.id());
        if(changed>0)recordLink(caseId,article.id(),"LINK",actor);
        return linksFor(caseId,actor);
    }
    @Transactional
    public List<CaseLink> unlink(long caseId,long articleId) {
        User actor=staff();Scope scope=scope(caseId,actor,true);
        if(!scope.institutionActive())throw ApiException.conflict("La institución está dada de baja.");
        int changed=db.update("UPDATE knowledge_case_links SET active=FALSE,removed_by=?,removed_at=NOW() WHERE case_id=? AND article_id=? AND active",actor.id(),caseId,articleId);
        if(changed>0)recordLink(caseId,articleId,"UNLINK",actor);
        return linksFor(caseId,actor);
    }
    private List<CaseLink> linksFor(long caseId,User actor) {
        // Filter before mapping: no hidden title, article ID, count or link metadata reaches USER.
        return db.query("""
            SELECT a.*,l.linked_at FROM knowledge_case_links l JOIN knowledge_articles a ON a.id=l.article_id
            WHERE l.case_id=? AND l.active
            """+(actor.role()!=Role.ADMIN ? " AND a.status='PUBLISHED'" : "")+" ORDER BY l.linked_at DESC,a.id DESC",
            (r,n)->new CaseLink(caseId,summary(r),time(r,"linked_at")),caseId);
    }
    private Scope scope(long caseId,User actor,boolean lock) {
        List<Scope> values=db.query("SELECT c.institution_id,i.active FROM support_cases c JOIN institutions i ON i.id=c.institution_id WHERE c.id=?"+(lock?" FOR UPDATE OF c":""),
            (r,n)->new Scope(r.getLong("institution_id"),r.getBoolean("active")),caseId);
        if(values.isEmpty() || (actor.role()==Role.USER && !Objects.equals(actor.institutionId(),values.get(0).institutionId())))throw ApiException.missing();
        return values.get(0);
    }
    private Article find(long id,User actor,boolean lock) {
        List<Article> values=db.query("SELECT * FROM knowledge_articles WHERE id=?"+(actor.role()!=Role.ADMIN?" AND status='PUBLISHED'":"")+(lock?" FOR UPDATE":""),ARTICLE,id);
        if(values.isEmpty())throw ApiException.missing();return values.get(0);
    }
    private User actor(){User actor=helpdesk.me();if(!isSupportStaff(actor.role()) && actor.role()!=Role.USER)throw ApiException.forbidden();return actor;}
    private User staff(){User actor=helpdesk.me();if(!isSupportStaff(actor.role()))throw ApiException.forbidden();return actor;}
    private User admin(){User actor=helpdesk.me();if(actor.role()!=Role.ADMIN)throw ApiException.forbidden();return actor;}
    private static void revision(Article article,Integer revision){if(revision==null || revision<1)throw ApiException.bad("Indique la revisión que está editando.");if(article.revision()!=revision)throw ApiException.conflict("Otro administrativo modificó el artículo. Recargue la versión actual antes de guardar.");}
    private static Input validate(Input input){
        if(input==null || input.category()==null)throw ApiException.bad("Indique la categoría del artículo.");
        return new Input(text(input.title(),"Título",180,true),input.category(),text(input.siabucVersion(),"Versión de SIABUC",120,false),
            text(input.summary(),"Resumen",1000,true),text(input.body(),"Solución",50000,true),text(input.changeNote(),"Nota editorial",1000,false),input.expectedRevision());
    }
    private static String text(String value,String label,int max,boolean required){
        String result=value==null?"":value.strip();
        if((required && result.isEmpty()) || result.length()>max || result.codePoints().anyMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
            throw ApiException.bad(label+": indique "+(required?"entre 1 y ":"hasta ")+max+" caracteres de texto.");
        return result;
    }
    private void record(Article article,String action,String note,User actor){
        db.update("""
            INSERT INTO knowledge_article_history(article_id,revision,action,title,category,siabuc_version,summary,body,status,change_note,actor)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """,article.id(),article.revision(),action,article.title(),article.category().name(),article.siabucVersion(),article.summary(),article.body(),article.status().name(),note,actor.id());
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?, 'KNOWLEDGE',?,?)",actor.username(),"KNOWLEDGE_"+action,article.id(),"Revisión "+article.revision()+"; estado="+article.status().name());
    }
    private void recordLink(long caseId,long articleId,String action,User actor){
        db.update("INSERT INTO knowledge_link_events(case_id,article_id,action,actor) VALUES (?,?,?,?)",caseId,articleId,action,actor.id());
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,?,'CASE',?,?)",actor.username(),"KNOWLEDGE_"+action,caseId,"Artículo #"+articleId+"; sin descuento de minutos.");
    }
}
