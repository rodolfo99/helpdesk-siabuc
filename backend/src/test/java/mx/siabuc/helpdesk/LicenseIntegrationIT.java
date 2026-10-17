package mx.siabuc.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class LicenseIntegrationIT extends ApiIntegrationSupport {
    @Autowired JdbcTemplate db;

    private JsonNode defaults(long email, long remote) throws Exception {
        long revision = success(admin.get("/license-support-defaults")).path("revision").asLong();
        return success(admin.put("/license-support-defaults", Map.of("emailMinutes", email, "remoteMinutes", remote, "revision", revision)));
    }
    private JsonNode license(long institution, long revision) throws Exception {
        return success(admin.post("/licenses", Map.of("institutionId", institution, "number", unique("LIC-"), "defaultsRevision", revision)));
    }
    private JsonNode licenseById(long id) throws Exception {
        for (var value : success(admin.get("/licenses"))) if (value.path("id").asLong() == id) return value;
        throw new AssertionError("Licencia no encontrada");
    }
    private long licensedCase(long institution, long license) throws Exception {
        var input = new LinkedHashMap<>(casePayload(institution, "EMAIL")); input.put("licenseId", license);
        return success(admin.post("/cases", input)).path("id").asLong();
    }
    private Map<String,Object> policyInput(long institution, long license) {
        return Map.of("institutionId",institution,"licenseId",license,"folio",unique("POL-"),"description","Soporte adicional de la licencia",
            "validFrom",LocalDate.now().toString(),"validUntil",LocalDate.now().plusYears(1).toString(),"agreedAmount",100,"emailMinutes",60,"remoteMinutes",30);
    }
    private Map<String,Object> paymentInput(long institution, long policy) {
        return Map.ofEntries(Map.entry("institutionId",institution),Map.entry("policyId",policy),Map.entry("requestId",UUID.randomUUID().toString()),
            Map.entry("reference",unique("TRANSFER-")),Map.entry("amount",100),Map.entry("currency","MXN"),Map.entry("paidOn",LocalDate.now().toString()),
            Map.entry("method","TRANSFER"),Map.entry("emailMinutes",60),Map.entry("remoteMinutes",30),Map.entry("notes","Pago verificado para ampliar la licencia"));
    }

    @Test void defaultsAreSeparatePersistedAndNeverRewriteExistingLicenses() throws Exception {
        long institution = institution(45,15);
        var firstDefaults = defaults(120,35);
        var first = license(institution,firstDefaults.path("revision").asLong());
        var secondDefaults = defaults(300,70);
        var second = license(institution,secondDefaults.path("revision").asLong());
        assertThat(licenseById(first.path("id").asLong()).path("emailRemainingMinutes").asLong()).isEqualTo(120);
        assertThat(licenseById(first.path("id").asLong()).path("initialRemoteMinutes").asLong()).isEqualTo(35);
        assertThat(second.path("initialEmailMinutes").asLong()).isEqualTo(300);
        assertThat(second.path("remoteRemainingMinutes").asLong()).isEqualTo(70);
        assertBalance(institution,0,465,0,120);
        assertStatus(admin.put("/license-support-defaults",Map.of("emailMinutes",1,"remoteMinutes",1,"revision",firstDefaults.path("revision").asLong())),409);
        assertStatus(admin.post("/licenses",Map.of("institutionId",institution,"number",unique("stale"),"defaultsRevision",firstDefaults.path("revision").asLong())),409);
        assertBalance(institution,0,465,0,120);
        var duplicate=Map.of("institutionId",institution,"number","  "+first.path("number").asText().toLowerCase()+"  ","defaultsRevision",secondDefaults.path("revision").asLong());
        assertStatus(admin.post("/licenses",duplicate),409);
        assertBalance(institution,0,465,0,120);
    }

    @Test void topupsAreAdditiveAuditedAndIdempotent() throws Exception {
        var config=defaults(120,60);long institution=institution(0,0),id=license(institution,config.path("revision").asLong()).path("id").asLong();
        var input=Map.of("requestId",UUID.randomUUID().toString(),"emailMinutes",25,"remoteMinutes",40,"reason","Ampliación autorizada");
        success(admin.post("/licenses/"+id+"/hours",input));success(admin.post("/licenses/"+id+"/hours",input));
        assertBalance(institution,0,145,0,100);
        assertThat(licenseById(id).path("initialEmailMinutes").asLong()).isEqualTo(120);
        assertThat(success(admin.get("/licenses/"+id+"/history")).size()).isEqualTo(2);
        var changed=new LinkedHashMap<String,Object>(input);changed.put("emailMinutes",26);
        assertStatus(admin.post("/licenses/"+id+"/hours",changed),409);
        changed.put("requestId",UUID.randomUUID().toString());changed.put("reason"," ");
        assertStatus(admin.post("/licenses/"+id+"/hours",changed),400);
        assertBalance(institution,0,145,0,100);
    }

    @Test void consumptionAndReversalStayInTheSelectedLicenseAndChannel() throws Exception {
        long revision=defaults(60,40).path("revision").asLong(),institution=institution(10,0);
        long first=license(institution,revision).path("id").asLong(),second=license(institution,revision).path("id").asLong();
        long caseId=licensedCase(institution,first);
        var after=addEntry(admin,caseId,"Atención por correo","EMAIL",50,false,200);
        long entry=after.path("entries").get(0).path("id").asLong();
        assertThat(licenseById(first).path("emailRemainingMinutes").asLong()).isEqualTo(10);
        assertThat(licenseById(second).path("emailRemainingMinutes").asLong()).isEqualTo(60);
        addEntry(admin,caseId,"No debe tomar saldo de la otra licencia","EMAIL",11,false,409);
        addEntry(admin,caseId,"Atención remota","REMOTE",20,false,200);
        assertBalance(institution,50,80,20,60);
        success(admin.post("/cases/"+caseId+"/entries/"+entry+"/void",Map.of("reason","Corrección de atención")));
        assertThat(licenseById(first).path("emailRemainingMinutes").asLong()).isEqualTo(60);
        assertThat(licenseById(first).path("remoteRemainingMinutes").asLong()).isEqualTo(20);
        assertStatus(admin.post("/cases/"+caseId+"/entries/"+entry+"/void",Map.of("reason","Reintento")),409);
    }

    @Test void legacyCasesAndManualInstitutionEditsCannotSpendReservedLicenseHours() throws Exception {
        long institution=institution(10,0),legacy=supportCase(institution,"EMAIL");
        long license=license(institution,defaults(60,0).path("revision").asLong()).path("id").asLong();
        addEntry(admin,legacy,"Usa sólo el saldo anterior","EMAIL",10,false,200);
        addEntry(admin,legacy,"No toma horas de la licencia","EMAIL",1,false,409);
        assertStatus(admin.post("/cases",casePayload(institution,"EMAIL")),409);
        var input=institutionPayload("Institución con licencia",10,0);input.put("active",true);
        assertStatus(admin.put("/institutions/"+institution,input),409);
        assertThat(licenseById(license).path("emailRemainingMinutes").asLong()).isEqualTo(60);
        assertBalance(institution,10,60,0,0);
    }

    @Test void policyPaymentsCreditAndReverseOnlyTheirLicense() throws Exception {
        long institution=institution(0,0),revision=defaults(60,30).path("revision").asLong();
        long first=license(institution,revision).path("id").asLong(),second=license(institution,revision).path("id").asLong();
        var policyInput=policyInput(institution,first);
        long policy=success(admin.post("/policies",policyInput)).path("policy").path("id").asLong();
        var otherLicense=new LinkedHashMap<>(policyInput);otherLicense.put("licenseId",second);
        assertStatus(admin.put("/policies/"+policy,otherLicense),409);
        long payment=success(admin.post("/payments",paymentInput(institution,policy))).path("id").asLong();
        assertThat(licenseById(first).path("emailRemainingMinutes").asLong()).isEqualTo(120);
        assertThat(licenseById(second).path("emailRemainingMinutes").asLong()).isEqualTo(60);
        long caseId=licensedCase(institution,first);
        var after=addEntry(admin,caseId,"Uso de tiempo contratado","EMAIL",80,false,200);
        assertStatus(admin.post("/payments/"+payment+"/void",Map.of("reason","Debe conservar el saldo de la otra licencia")),409);
        success(admin.post("/cases/"+caseId+"/entries/"+after.path("entries").get(0).path("id").asLong()+"/void",Map.of("reason","Corrección de consumo")));
        success(admin.post("/payments/"+payment+"/void",Map.of("reason","Corrección de pago")));
        assertThat(licenseById(first).path("emailRemainingMinutes").asLong()).isEqualTo(60);
        assertBalance(institution,0,120,0,60);
    }

    @Test void failedPaymentRollsBackLicenseInstitutionAndHistoryTogether() throws Exception {
        long institution=institution(0,0),id=license(institution,defaults(60,30).path("revision").asLong()).path("id").asLong();
        long policy=success(admin.post("/policies",policyInput(institution,id))).path("policy").path("id").asLong();
        faultNotifications.forcePaymentFailureFor(institution);
        try { assertStatus(admin.post("/payments",paymentInput(institution,policy)),409); }
        finally { faultNotifications.resetPaymentFailure(); }
        assertBalance(institution,0,60,0,30);
        assertThat(licenseById(id).path("emailRemainingMinutes").asLong()).isEqualTo(60);
        assertThat(success(admin.get("/licenses/"+id+"/history")).size()).isEqualTo(1);
    }

    @Test void permissionsAndInstitutionIsolationApplyToLicensesAndDefaults() throws Exception {
        long a=institution(0,0),b=institution(0,0),revision=defaults(60,30).path("revision").asLong();
        long mine=license(a,revision).path("id").asLong(),other=license(b,revision).path("id").asLong();
        var user=institutionUser(a);
        assertThat(ids(success(user.get("/licenses?institutionId="+b)))).containsExactly(mine);
        assertStatus(user.get("/license-support-defaults"),403);
        assertStatus(user.put("/license-support-defaults",Map.of("emailMinutes",1,"remoteMinutes",1,"revision",revision)),403);
        assertStatus(user.get("/licenses/"+mine+"/history"),403);
        assertStatus(user.post("/licenses/"+mine+"/hours",Map.of("requestId",UUID.randomUUID().toString(),"emailMinutes",1,"remoteMinutes",0,"reason","Sin permiso")),403);
        var ticket=Map.of("title","Ayuda con SIABUC","description","Solicito ayuda con la importación de registros.","channel","EMAIL","licenseId",other);
        assertStatus(user.post("/client-portal/tickets",ticket),409);
        var own=new LinkedHashMap<String,Object>(ticket);own.put("licenseId",mine);own.put("institutionId",b);
        assertThat(success(user.post("/client-portal/tickets",own)).path("institutionId").asLong()).isEqualTo(a);
        assertStatus(admin.post("/policies",policyInput(a,other)),409);
        for (String role : new String[]{"TECH","BILLING"}) {
            String name=unique(role);var data=userPayload(name,a,true);data.put("role",role);data.put("institutionId",null);
            success(admin.post("/users",data));var staff=login(name,PASSWORD);
            assertStatus(staff.get("/license-support-defaults"),403);
            assertStatus(staff.post("/licenses",Map.of("institutionId",a,"number",unique("denied"),"defaultsRevision",revision)),403);
        }
    }

    @Test void invalidDefaultsMissingSetupAndMaximumCannotCreditHours() throws Exception {
        long institution=institution(1_000_000_000,0),revision=defaults(1,0).path("revision").asLong();
        assertStatus(admin.post("/licenses",Map.of("institutionId",institution,"number",unique("overflow"),"defaultsRevision",revision)),409);
        assertBalance(institution,0,1_000_000_000,0,0);
        for (Object invalid : new Object[]{-1,1_000_000_001L,1.5})
            assertStatus(admin.put("/license-support-defaults",Map.of("emailMinutes",invalid,"remoteMinutes",0,"revision",revision)),400);
        db.update("UPDATE license_support_defaults SET configured=FALSE WHERE id=1");
        try { assertStatus(admin.post("/licenses",Map.of("institutionId",institution,"number",unique("unconfigured"),"defaultsRevision",revision)),409); }
        finally { db.update("UPDATE license_support_defaults SET configured=TRUE WHERE id=1"); }
    }

    @Test void concurrentRetriesCreditOneTopup() throws Exception {
        long institution=institution(0,0),id=license(institution,defaults(60,0).path("revision").asLong()).path("id").asLong();
        var first=login("testadmin","PruebaSegura123!");var second=login("testadmin","PruebaSegura123!");
        var input=Map.of("requestId",UUID.randomUUID().toString(),"emailMinutes",15,"remoteMinutes",0,"reason","Asignación simultánea");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(() -> first.post("/licenses/"+id+"/hours",input));
            var two=pool.submit(() -> second.post("/licenses/"+id+"/hours",input));
            success(one.get(20,TimeUnit.SECONDS));success(two.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertBalance(institution,0,75,0,0);
        assertThat(success(admin.get("/licenses/"+id+"/history")).size()).isEqualTo(2);
    }
}
