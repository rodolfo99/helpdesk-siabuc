package mx.siabuc.helpdesk;

import static mx.siabuc.helpdesk.Models.*;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentService {
    static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    static final long MAX_CASE_BYTES = 100L * 1024 * 1024;
    static final int MAX_CASE_FILES = 100;
    private final JdbcTemplate db;
    private final HelpdeskService helpdesk;
    private final NotificationService notifications;
    public AttachmentService(JdbcTemplate db, HelpdeskService helpdesk, NotificationService notifications) {
        this.db = db; this.helpdesk = helpdesk; this.notifications = notifications;
    }

    public record Attachment(long id, long caseId, Long entryId, String filename, String contentType,
            long sizeBytes, String sha256, String uploadedBy, Instant createdAt) {}
    public record Download(Attachment metadata, byte[] bytes) {}
    private record Scope(long institutionId, String status) {}
    private static final String SELECT = "SELECT a.id,a.case_id,a.entry_id,a.filename,a.content_type,a.size_bytes,a.sha256,a.created_at,u.username AS uploader FROM case_attachments a JOIN app_users u ON u.id=a.uploaded_by";
    private static final RowMapper<Attachment> ROW = (r,n) -> new Attachment(r.getLong("id"), r.getLong("case_id"),
        r.getObject("entry_id", Long.class), r.getString("filename"), r.getString("content_type"), r.getLong("size_bytes"),
        r.getString("sha256"), r.getString("uploader"), r.getTimestamp("created_at").toInstant());

    public List<Attachment> list(long caseId, Long entryId) {
        User actor = actor(); scope(caseId, actor); entry(caseId, entryId, actor);
        return entryId == null ? db.query(SELECT + " WHERE a.case_id=? AND a.entry_id IS NULL ORDER BY a.id", ROW, caseId)
            : db.query(SELECT + " WHERE a.case_id=? AND a.entry_id=? ORDER BY a.id", ROW, caseId, entryId);
    }

    public Download download(long id) {
        User actor = actor();
        List<Attachment> matches = db.query(SELECT + " WHERE a.id=?", ROW, id);
        if (matches.isEmpty()) throw ApiException.missing();
        Attachment file = matches.get(0);
        scope(file.caseId(), actor); entry(file.caseId(), file.entryId(), actor);
        byte[] bytes = db.queryForObject("SELECT file_bytes FROM case_attachments WHERE id=?", byte[].class, id);
        return new Download(file, bytes);
    }

    @Transactional
    public Attachment upload(long caseId, Long entryId, String originalFilename, byte[] bytes, UUID requestId) {
        User actor = actor();
        if (requestId == null) throw ApiException.bad("Indique el identificador del envío del archivo.");
        Scope initial = scope(caseId, actor);
        // Serialize with case mutations: institution first, then case. Limits include private files too.
        Boolean active = db.queryForObject("SELECT active FROM institutions WHERE id=? FOR UPDATE", Boolean.class, initial.institutionId());
        db.queryForObject("SELECT id FROM support_cases WHERE id=? FOR UPDATE", Long.class, caseId);
        Scope current = scope(caseId, actor);
        boolean internal = entry(caseId, entryId, actor);
        String filename = filename(originalFilename);
        String type = contentType(filename, bytes);
        String digest = digest(bytes);
        List<Attachment> duplicate = db.query(SELECT + " WHERE a.uploaded_by=? AND a.request_id=?", ROW, actor.id(), requestId);
        if (!duplicate.isEmpty()) {
            Attachment previous = duplicate.get(0);
            if (previous.caseId() != caseId || !Objects.equals(previous.entryId(), entryId)
                    || !previous.sha256().equals(digest) || !previous.filename().equals(filename))
                throw ApiException.conflict("El identificador del envío ya corresponde a otro archivo.");
            return previous;
        }
        if (!Boolean.TRUE.equals(active)) throw ApiException.conflict("La institución está dada de baja.");
        if (!Models.CaseStatus.valueOf(current.status()).active()) throw ApiException.conflict("Reabra el caso antes de agregar archivos.");
        long count = db.queryForObject("SELECT COUNT(*) FROM case_attachments WHERE case_id=?", Long.class, caseId);
        long size = db.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM case_attachments WHERE case_id=?", Long.class, caseId);
        if (count >= MAX_CASE_FILES || size + bytes.length > MAX_CASE_BYTES)
            throw ApiException.conflict("El caso alcanzó su límite de adjuntos: 100 archivos o 100 MiB en total.");
        Long id = db.queryForObject("""
            INSERT INTO case_attachments(case_id,entry_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes)
            VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class, caseId, entryId, actor.id(), requestId, filename, type, bytes.length, digest, bytes);
        String description = "Archivo adjunto: " + filename + " (" + bytes.length + " bytes)."
            + (entryId == null ? " Asociado al caso." : " Asociado al elemento de historial #" + entryId + ".");
        // File additions have their own zero-minute history entry, with the same visibility as their parent.
        db.update("INSERT INTO case_entries(case_id,description,channel,minutes,internal,created_by) VALUES (?,?,NULL,0,?,?)",
            caseId, description, internal, actor.id());
        db.update("UPDATE support_cases SET updated_at=NOW() WHERE id=?", caseId);
        db.update("INSERT INTO audit_log(actor,action,entity_type,entity_id,details) VALUES (?,'ADD_ATTACHMENT','ATTACHMENT',?,?)",
            actor.username(), id, "Caso #" + caseId + "; archivo=" + filename + "; SHA-256=" + digest + "; interno=" + internal);
        notifications.caseEvent(caseId, "ATTACHMENT_ADDED", description, internal, actor.username());
        return db.queryForObject(SELECT + " WHERE a.id=?", ROW, id);
    }

    private User actor() {
        User actor = helpdesk.me();
        if (actor.role() == Role.BILLING) throw ApiException.forbidden();
        return actor;
    }
    private Scope scope(long caseId, User actor) {
        List<Scope> cases = db.query("SELECT institution_id,status FROM support_cases WHERE id=?",
            (r,n) -> new Scope(r.getLong("institution_id"), r.getString("status")), caseId);
        if (cases.isEmpty()) throw ApiException.missing();
        Scope current = cases.get(0);
        if (actor.role() == Role.USER && !Objects.equals(actor.institutionId(), current.institutionId())) throw ApiException.missing();
        return current;
    }
    private boolean entry(long caseId, Long entryId, User actor) {
        if (entryId == null) return false;
        List<Boolean> entries = db.query("SELECT internal FROM case_entries WHERE id=? AND case_id=?", (r,n) -> r.getBoolean("internal"), entryId, caseId);
        if (entries.isEmpty() || (entries.get(0) && !isSupportStaff(actor.role()))) throw ApiException.missing();
        return entries.get(0);
    }

    static String filename(String original) {
        if (original == null) throw ApiException.bad("Indique el nombre del archivo.");
        String leaf = original.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (leaf.isBlank() || leaf.length() > 180 || leaf.equals(".") || leaf.equals(".."))
            throw ApiException.bad("El nombre del archivo debe contener entre 1 y 180 caracteres.");
        return leaf;
    }
    static String contentType(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw ApiException.bad("El archivo está vacío.");
        if (bytes.length > MAX_FILE_BYTES) throw new ApiException(413, "Cada archivo puede ocupar hasta 10 MiB.");
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (extension.equals("png") && starts(bytes, new byte[] {(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if ((extension.equals("jpg") || extension.equals("jpeg")) && bytes.length >= 5
            && (bytes[0]&255) == 255 && (bytes[1]&255) == 216 && (bytes[2]&255) == 255
            && (bytes[bytes.length-2]&255) == 255 && (bytes[bytes.length-1]&255) == 217) return "image/jpeg";
        if (extension.equals("pdf") && starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))
            && new String(bytes, Math.max(0, bytes.length-1024), Math.min(1024,bytes.length), StandardCharsets.ISO_8859_1).contains("%%EOF")) return "application/pdf";
        if (List.of("txt","log","csv","json").contains(extension)) {
            String text;
            try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
            catch (CharacterCodingException ex) { throw ApiException.bad("Los archivos de diagnóstico deben ser texto UTF-8."); }
            if (text.codePoints().anyMatch(c -> (Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
                || text.stripLeading().startsWith("#!") || text.startsWith("MZ")
                || java.util.regex.Pattern.compile("(?is)<\\s*(?:!doctype\\s+html|html|head|body|script|iframe|svg|object|embed|a|img|div|style|link|form|input)(?:\\s|>|/)").matcher(text).find())
                throw ApiException.bad("El archivo contiene HTML, contenido ejecutable o datos binarios no permitidos.");
            return "text/plain;charset=UTF-8";
        }
        throw ApiException.bad("Tipo de archivo no permitido o contenido incompatible. Use PNG, JPG, PDF, TXT, LOG, CSV o JSON UTF-8.");
    }
    private static boolean starts(byte[] value, byte[] prefix) {
        return value.length >= prefix.length && Arrays.equals(Arrays.copyOf(value,prefix.length), prefix);
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
