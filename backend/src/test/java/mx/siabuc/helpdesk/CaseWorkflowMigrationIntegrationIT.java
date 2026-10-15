package mx.siabuc.helpdesk;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseWorkflowMigrationIntegrationIT extends ApiIntegrationSupport {
    @Autowired DataSource source;
    @Autowired JdbcTemplate db;

    @Test void upgradeFrom25PreservesCasesEntriesBalancesAndSavedViewIntent() throws Exception {
        String schema="upgrade183_"+UUID.randomUUID().toString().replace("-","");
        db.execute("CREATE SCHEMA "+schema);
        try {
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("25").load().migrate();
            try(Connection connection=source.getConnection()) {
                connection.setSchema(schema);
                try {
                    var fixture=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                    fixture.update("INSERT INTO institutions(id,name,email_allocated_minutes,email_used_minutes) VALUES (1,'Institución anterior',60,12)");
                    fixture.update("INSERT INTO app_users(id,username,display_name,password_hash,role) VALUES (1,'legacy183','Anterior','unused','ADMIN')");
                    fixture.update("INSERT INTO support_cases(id,institution_id,title,description,channel,status,created_by) VALUES (1,1,'Abierto anterior','Conservar','EMAIL','OPEN',1),(2,1,'Cerrado anterior','Conservar','EMAIL','CLOSED',1)");
                    fixture.update("INSERT INTO case_entries(case_id,description,channel,minutes,created_by) VALUES (1,'Atención anterior','EMAIL',12,1)");
                    fixture.update("INSERT INTO saved_case_views(owner_id,name,filters) VALUES (1,'Pendientes','{\"status\":\"OPEN\",\"search\":\"catálogo\"}'),(1,'Cerrados','{\"status\":\"CLOSED\"}')");
                } finally { connection.setSchema("public"); }
            }
            var result=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(db.queryForList("SELECT status FROM "+schema+".support_cases ORDER BY id",String.class)).containsExactly("OPEN","CLOSED");
            assertThat(db.queryForObject("SELECT email_used_minutes FROM "+schema+".institutions WHERE id=1",Long.class)).isEqualTo(12);
            assertThat(db.queryForObject("SELECT description FROM "+schema+".case_entries WHERE case_id=1",String.class)).isEqualTo("Atención anterior");
            assertThat(db.queryForObject("SELECT filters->>'status' FROM "+schema+".saved_case_views WHERE name='Pendientes'",String.class)).isEqualTo("active");
            assertThat(db.queryForObject("SELECT filters->>'search' FROM "+schema+".saved_case_views WHERE name='Pendientes'",String.class)).isEqualTo("catálogo");
            for(String state:new String[]{"IN_PROGRESS","WAITING_CLIENT","RESOLVED","CLOSED"})
                assertThat(db.update("UPDATE "+schema+".support_cases SET status=? WHERE id=1",state)).isEqualTo(1);
            assertThatThrownBy(()->db.update("UPDATE "+schema+".support_cases SET status='INVALID' WHERE id=1")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally { db.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
