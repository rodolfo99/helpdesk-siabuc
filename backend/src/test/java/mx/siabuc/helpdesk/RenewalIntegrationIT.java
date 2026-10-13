package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class RenewalIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired RenewalService renewals;
    private static LocalDate today(){return QuoteIntegrationIT.today();}
    private Map<String,Object> input(long institution,LocalDate until){var p=new LinkedHashMap<>(PolicyIntegrationIT.policyPayload(institution,"2400.50",240,60));p.put("validFrom",until.minusYears(1).toString());p.put("validUntil",until.toString());return p;}
    private long policy(long institution,LocalDate until) throws Exception {return success(admin.post("/policies",input(institution,until))).path("policy").path("id").asLong();}
    private JsonNode settings() throws Exception {return success(admin.get("/renewals/settings"));}
    private void settings(boolean enabled,int days) throws Exception {success(admin.put("/renewals/settings",Map.of("enabled",enabled,"daysBeforeExpiry",days)));}
    private void restore(JsonNode saved) throws Exception {settings(saved.path("enabled").asBoolean(),saved.path("daysBeforeExpiry").asInt());}
    private long notices(long policy){return db.queryForObject("SELECT COUNT(*) FROM renewal_notices WHERE policy_id=?",Long.class,policy);}
    private boolean hasPolicy(JsonNode array,long policy){for(JsonNode item:array)if(item.path("policyId").asLong()==policy)return true;return false;}
    private JsonNode notice(JsonNode array,long id){for(JsonNode item:array)if(item.path("id").asLong()==id)return item;throw new AssertionError("Aviso no encontrado "+id);}
    private Client billing() throws Exception {String name=unique("renewalBilling");success(admin.post("/users",Map.of("username",name,"displayName","Cobranza renovaciones","password",PASSWORD,"role","BILLING","active",true,"email","renewals@example.test")));return login(name,PASSWORD);}

    @Test void thresholdIncludesExpiredAndBoundaryButExcludesCancelledInactiveFutureAndRenewed() throws Exception {
        JsonNode saved=settings();try {
            settings(false,10);long institution=institution(0,0),inactiveInstitution=institution(0,0);
            long expired=policy(institution,today().minusDays(3)),boundary=policy(institution,today().plusDays(10)),later=policy(institution,today().plusDays(11));
            long cancelled=policy(institution,today()),inactive=policy(inactiveInstitution,today()),previous=policy(institution,today());
            success(admin.post("/policies/"+cancelled+"/cancel",Map.of("reason","Cancelada antes del vencimiento")));db.update("UPDATE institutions SET active=FALSE WHERE id=?",inactiveInstitution);
            long quote=success(admin.post("/quotes",QuoteIntegrationIT.quotePayload(institution,previous))).path("quote").path("id").asLong();
            success(admin.post("/quotes/"+quote+"/send",Map.of("evidence","Oferta entregada")));success(admin.post("/quotes/"+quote+"/decision",QuoteIntegrationIT.decision("ACCEPTED",today())));
            success(admin.post("/quotes/"+quote+"/convert",Map.of("policyFolio",unique("RENEWED-"))));
            JsonNode upcoming=success(admin.get("/renewals")).path("upcoming");assertThat(hasPolicy(upcoming,expired)).isTrue();assertThat(hasPolicy(upcoming,boundary)).isTrue();
            for(long excluded:new long[]{later,cancelled,inactive,previous})assertThat(hasPolicy(upcoming,excluded)).isFalse();
            for(JsonNode p:upcoming)if(p.path("policyId").asLong()==expired)assertThat(p.path("daysUntilExpiry").asLong()).isEqualTo(-3);
            settings(true,10);success(admin.post("/renewals/scan",Map.of()));assertThat(notices(expired)).isEqualTo(1);assertThat(notices(boundary)).isEqualTo(1);
            for(long excluded:new long[]{later,cancelled,inactive,previous})assertThat(notices(excluded)).isZero();assertBalance(institution,0,0,0,0);
        } finally {restore(saved);}
    }

    @Test void notificationsAreDeduplicatedIndependentOfPausedMailAndDisabledStatePreservesHistory() throws Exception {
        JsonNode saved=settings(),mail=success(admin.get("/notifications/config"));
        try {
            settings(false,30);success(admin.post("/notifications/config",Map.of("paused",true)));
            long institution=institution(0,0),first=policy(institution,today().plusDays(5));
            success(admin.post("/renewals/scan",Map.of()));assertThat(notices(first)).isZero();
            settings(true,30);success(admin.post("/renewals/scan",Map.of()));assertThat(notices(first)).isEqualTo(1);
            success(admin.post("/renewals/scan",Map.of()));renewals.scanDuePolicies();assertThat(notices(first)).isEqualTo(1);
            settings(false,30);long second=policy(institution,today().plusDays(2));success(admin.post("/renewals/scan",Map.of()));assertThat(notices(second)).isZero();assertThat(notices(first)).isEqualTo(1);
            settings(true,365);success(admin.post("/renewals/scan",Map.of()));assertThat(notices(first)).isEqualTo(1);assertThat(notices(second)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE recipient_type='INSTITUTION' AND recipient_entity_id=?",Long.class,institution)).isZero();
            assertBalance(institution,0,0,0,0);
        } finally {restore(saved);success(admin.post("/notifications/config",Map.of("paused",mail.path("paused").asBoolean())));}
    }

    @Test void readingNoticeIsPersonalAndRepeatedReadDoesNotChangeItsTimestamp() throws Exception {
        JsonNode saved=settings();try {
            settings(true,30);long policy=policy(institution(0,0),today());Client billing=billing();success(billing.post("/renewals/scan",Map.of()));
            long id=db.queryForObject("SELECT id FROM renewal_notices WHERE policy_id=?",Long.class,policy);
            assertThat(notice(success(billing.get("/renewals")).path("notices"),id).path("readAt").isNull()).isTrue();
            success(billing.post("/renewals/notices/"+id+"/read",Map.of()));JsonNode read=notice(success(billing.get("/renewals")).path("notices"),id).path("readAt");assertThat(read.isNull()).isFalse();
            assertThat(notice(success(admin.get("/renewals")).path("notices"),id).path("readAt").isNull()).isTrue();
            success(billing.post("/renewals/notices/"+id+"/read",Map.of()));assertThat(notice(success(billing.get("/renewals")).path("notices"),id).path("readAt")).isEqualTo(read);
            success(admin.post("/renewals/notices/"+id+"/read",Map.of()));assertThat(db.queryForObject("SELECT COUNT(*) FROM renewal_notice_reads WHERE notice_id=?",Long.class,id)).isEqualTo(2);
            assertStatus(admin.post("/renewals/notices/9223372036854775807/read",Map.of()),404);
        } finally {restore(saved);}
    }

    @Test void changedExpirationCreatesNewSnapshotWhileOldNoticeRemainsHistorical() throws Exception {
        JsonNode saved=settings();try {
            settings(true,30);long institution=institution(0,0);var input=input(institution,today().plusDays(1));
            long policy=success(admin.post("/policies",input)).path("policy").path("id").asLong();success(admin.post("/renewals/scan",Map.of()));
            long first=db.queryForObject("SELECT id FROM renewal_notices WHERE policy_id=?",Long.class,policy);String original=input.get("folio").toString();
            input.put("validUntil",today().plusDays(2).toString());input.put("folio",unique("UPDATED-"));success(admin.put("/policies/"+policy,input));success(admin.post("/renewals/scan",Map.of()));
            assertThat(notices(policy)).isEqualTo(2);JsonNode old=notice(success(admin.get("/renewals")).path("notices"),first);
            assertThat(old.path("policyFolio").asText()).isEqualTo(original);assertThat(old.path("expirationDate").asText()).isEqualTo(today().plusDays(1).toString());assertThat(old.path("stillRelevant").asBoolean()).isFalse();
            long second=db.queryForObject("SELECT MAX(id) FROM renewal_notices WHERE policy_id=?",Long.class,policy);assertThat(notice(success(admin.get("/renewals")).path("notices"),second).path("stillRelevant").asBoolean()).isTrue();
            success(admin.post("/policies/"+policy+"/cancel",Map.of("reason","Cancelación documentada")));success(admin.post("/renewals/scan",Map.of()));
            assertThat(notices(policy)).isEqualTo(2);assertThat(notice(success(admin.get("/renewals")).path("notices"),second).path("stillRelevant").asBoolean()).isFalse();
        } finally {restore(saved);}
    }

    @Test void billingCanConfigureBoundedWholeDaysAndInvalidInputDoesNotReplaceSettings() throws Exception {
        JsonNode saved=settings();try {
            Client billing=billing();JsonNode configured=success(billing.put("/renewals/settings",Map.of("enabled",false,"daysBeforeExpiry",0)));assertThat(configured.path("enabled").asBoolean()).isFalse();
            for(Object value:new Object[]{-1,366,1.5})assertStatus(billing.put("/renewals/settings",Map.of("enabled",true,"daysBeforeExpiry",value)),400);
            assertStatus(billing.put("/renewals/settings",Map.of("daysBeforeExpiry",20)),400);assertStatus(billing.put("/renewals/settings",Map.of("enabled",true)),400);
            assertThat(settings()).isEqualTo(configured);assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='UPDATE_RENEWAL_SETTINGS' AND actor=?",Long.class,success(billing.get("/auth/me")).path("username").asText())).isEqualTo(1);
        } finally {restore(saved);}
    }
}
