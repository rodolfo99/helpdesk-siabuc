package mx.siabuc.helpdesk;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.assertThat;

class BackupIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    @BeforeEach void resetBackupFixtures(){
        db.update("DELETE FROM backup_attempts");db.update("DELETE FROM backup_runs");
        db.update("UPDATE backup_settings SET enabled=FALSE,destination_confirmed=FALSE,daily_time='03:00',time_zone='America/Mexico_City',retention_days=30,keep_last=7 WHERE id=1");
        db.update("UPDATE backup_runtime SET last_seen_at=NULL,writable=FALSE,destination_label='',last_error=NULL,lock_owner=NULL,lock_until=NULL WHERE id=1");
    }
    @Test void migrationAlwaysStartsDisabledAndDoesNotQueueAnyBackup() {
        String schema="backup_upgrade_"+UUID.randomUUID().toString().replace("-","");
        db.execute((ConnectionCallback<Void>) connection->{
            String original;try(var statement=connection.createStatement();var rows=statement.executeQuery("SHOW search_path")){rows.next();original=rows.getString(1);}
            try{
                try(var statement=connection.createStatement()){statement.execute("CREATE SCHEMA "+schema);statement.execute("SET search_path TO "+schema);statement.execute("CREATE TABLE app_users(id BIGINT PRIMARY KEY)");}
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V16__scheduled_backups.sql"));
                try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT enabled,destination_confirmed,(SELECT COUNT(*) FROM backup_runs) FROM backup_settings")){
                    rows.next();assertThat(rows.getBoolean(1)).isFalse();assertThat(rows.getBoolean(2)).isFalse();assertThat(rows.getLong(3)).isZero();
                }
            }finally{try(var statement=connection.prepareStatement("SELECT set_config('search_path',?,false)")){statement.setString(1,original);statement.execute();}try(var statement=connection.createStatement()){statement.execute("DROP SCHEMA "+schema+" CASCADE");}}
            return null;
        });
    }
    @Test void onlyAdministratorsCanInspectConfigureOrTriggerBackups() throws Exception {
        long institution=institution(100,100);Client customer=institutionUser(institution);
        String name=unique("backupbilling");success(admin.post("/users",Map.of("username",name,"displayName","Cobranza","password",PASSWORD,"role","BILLING","active",true)));
        Client billing=login(name,PASSWORD);
        for(Client client:new Client[]{customer,billing}){
            assertStatus(client.get("/backups/status"),403);assertStatus(client.get("/backups/1"),403);
            assertStatus(client.put("/backups/config",configuration(false,true)),403);
            assertStatus(client.post("/backups/enabled",Map.of("enabled",false)),403);
            assertStatus(client.post("/backups/run",Map.of()),403);
        }
        var initial=success(admin.get("/backups/status"));assertThat(initial.path("settings").path("enabled").asBoolean()).isFalse();
        assertThat(initial.path("worker").path("online").asBoolean()).isFalse();
        assertStatus(admin.post("/backups/run",Map.of("destination","/etc")),400);
    }
    @Test void configurationValidatesDestinationWorkerTimeZoneAndRetention() throws Exception {
        assertStatus(admin.put("/backups/config",configuration(true,false)),400);
        assertStatus(admin.put("/backups/config",configuration(true,true)),409);
        ready();
        var bad=new java.util.HashMap<String,Object>(configuration(true,true));bad.put("dailyTime","25:10");assertStatus(admin.put("/backups/config",bad),400);
        bad.put("dailyTime","03:00");bad.put("timeZone","Zona/Inventada");assertStatus(admin.put("/backups/config",bad),400);
        bad.put("timeZone","UTC");bad.put("retentionDays",0);assertStatus(admin.put("/backups/config",bad),400);
        bad.put("retentionDays",1.5);assertStatus(admin.put("/backups/config",bad),400);
        var configured=success(admin.put("/backups/config",configuration(false,true)));
        assertThat(configured.path("settings").path("enabled").asBoolean()).isFalse();
        assertThat(configured.path("settings").path("destinationConfirmed").asBoolean()).isTrue();
        assertThat(success(admin.post("/backups/enabled",Map.of("enabled",true))).path("settings").path("enabled").asBoolean()).isTrue();
        assertStatus(admin.post("/backups/enabled",Map.of("enabled","false")),400);
    }
    @Test void disablingCancelsOnlyWaitingScheduledRunsAndPreservesManualRunningAndSuccessfulCopies() throws Exception {
        ready();success(admin.put("/backups/config",configuration(true,true)));
        long queued=run("SCHEDULED","QUEUED","2026-01-01"),retry=run("SCHEDULED","RETRYING","2026-01-02"),
             running=run("SCHEDULED","RUNNING","2026-01-03"),complete=run("SCHEDULED","SUCCEEDED","2026-01-04"),manual=run("MANUAL","QUEUED",null);
        db.update("UPDATE backup_runs SET filename='siabuc-auto-1-00000000000000000000000000000000.dump',sha256=repeat('a',64),size_bytes=123 WHERE id=?",complete);
        var before=db.queryForMap("SELECT filename,sha256,size_bytes,purged_at FROM backup_runs WHERE id=?",complete);
        var disabled=success(admin.post("/backups/enabled",Map.of("enabled",false)));
        assertThat(disabled.path("settings").path("enabled").asBoolean()).isFalse();
        assertThat(disabled.path("settings").path("destinationConfirmed").asBoolean()).isTrue();
        assertThat(status(queued)).isEqualTo("CANCELLED");assertThat(status(retry)).isEqualTo("CANCELLED");
        assertThat(status(running)).isEqualTo("RUNNING");assertThat(status(manual)).isEqualTo("QUEUED");assertThat(status(complete)).isEqualTo("SUCCEEDED");
        assertThat(db.queryForMap("SELECT filename,sha256,size_bytes,purged_at FROM backup_runs WHERE id=?",complete)).isEqualTo(before);
        success(admin.post("/backups/enabled",Map.of("enabled",true)));
        assertThat(status(queued)).isEqualTo("CANCELLED");assertThat(status(retry)).isEqualTo("CANCELLED");
    }
    @Test void manualBackupIsAvailableWithAutomaticCalendarOffAndHasPersistentErrorHistory() throws Exception {
        ready();success(admin.put("/backups/config",configuration(false,true)));
        var requested=success(admin.post("/backups/run",Map.of()));long id=requested.path("id").asLong();
        assertThat(requested.path("kind").asText()).isEqualTo("MANUAL");assertThat(requested.path("status").asText()).isEqualTo("QUEUED");
        assertStatus(admin.post("/backups/run",Map.of()),409);
        db.update("UPDATE backup_runs SET status='FAILED',attempts=3,last_error='Espacio insuficiente',finished_at=NOW() WHERE id=?",id);
        db.update("INSERT INTO backup_attempts(run_id,attempt,status,error,finished_at) VALUES (?,3,'FAILED','Espacio insuficiente',NOW())",id);
        assertThat(success(admin.get("/backups/status")).path("lastFailure").path("id").asLong()).isEqualTo(id);
        assertThat(success(admin.get("/backups/"+id)).path("attempts").get(0).path("error").asText()).isEqualTo("Espacio insuficiente");
        long later=success(admin.post("/backups/run",Map.of())).path("id").asLong();
        db.update("UPDATE backup_runs SET status='SUCCEEDED',finished_at=NOW() WHERE id=?",later);
        assertThat(success(admin.get("/backups/status")).path("lastFailure").isNull()).isTrue();
        assertThat(status(id)).isEqualTo("FAILED");
    }
    private Map<String,Object> configuration(boolean enabled,boolean confirmed){return Map.of("enabled",enabled,"dailyTime","03:00","timeZone","America/Mexico_City","retentionDays",30,"keepLast",7,"destinationConfirmed",confirmed);}
    private void ready(){db.update("UPDATE backup_runtime SET last_seen_at=NOW(),writable=TRUE,destination_label='Destino privado de pruebas' WHERE id=1");}
    private long run(String kind,String status,String date){return db.queryForObject("INSERT INTO backup_runs(kind,status,scheduled_key) VALUES (?,?,?::date) RETURNING id",Long.class,kind,status,date);}
    private String status(long id){return db.queryForObject("SELECT status FROM backup_runs WHERE id=?",String.class,id);}
}
