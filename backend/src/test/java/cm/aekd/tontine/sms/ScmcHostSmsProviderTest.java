package cm.aekd.tontine.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Requêtes et réponses conformes à https://sms.scmchost.com/docs/. */
class ScmcHostSmsProviderTest {

    private MockRestServiceServer server;
    private ScmcHostSmsProvider provider;
    private final SmsProperties properties = new SmsProperties();

    @BeforeEach
    void setUp() {
        properties.getScmchost().setApiKey("sk_test");
        properties.getScmchost().setOtpTemplateId("tpl_otp");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new ScmcHostSmsProvider(properties, builder, new ObjectMapper());
    }

    @Test
    void sendsTheCodeThroughTheApprovedTemplate() {
        server.expect(requestTo("https://api.scmchost.com/messages/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "sk_test"))
                .andExpect(header("Idempotency-Key", "otp-1"))
                .andExpect(jsonPath("$.template_id").value("tpl_otp"))
                .andExpect(jsonPath("$.recipients[0]").value("+237600000001"))
                .andExpect(jsonPath("$.variables.CODE").value("482913"))
                .andExpect(jsonPath("$.variables.TEMPS").value("5"))
                .andExpect(jsonPath("$.variables.NOM_APP").value("AEKD Tontine"))
                .andRespond(withSuccess("""
                        {"success": true, "data": {"sent": 1, "failed": 0,
                          "results": [{"recipient": "+237600000001", "status": "accepted"}]}}
                        """, MediaType.APPLICATION_JSON));

        provider.sendLoginCode("+237600000001", "482913", Duration.ofMinutes(5), "otp-1");

        server.verify();
    }

    @Test
    void recipientRefusalIsAFailure() {
        server.expect(requestTo("https://api.scmchost.com/messages/send"))
                .andRespond(withSuccess("""
                        {"success": true, "data": {"sent": 0, "failed": 1,
                          "results": [{"recipient": "+237600000001", "status": "failed", "reason": "numero invalide"}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.sendLoginCode("+237600000001", "1", Duration.ofMinutes(5), "otp-2"))
                .isInstanceOf(SmsSendException.class)
                .hasMessageContaining("numero invalide");
    }

    @Test
    void insufficientBalanceIsExplained() {
        server.expect(requestTo("https://api.scmchost.com/messages/send"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\": false, \"error\": \"Solde insuffisant\"}"));

        assertThatThrownBy(() -> provider.sendLoginCode("+237600000001", "1", Duration.ofMinutes(5), "otp-3"))
                .isInstanceOf(SmsSendException.class)
                .hasMessageContaining("Solde SCMC Host insuffisant")
                .hasMessageContaining("HTTP 402");
    }

    @Test
    void notConfiguredWithoutKeyOrTemplate() {
        properties.getScmchost().setApiKey(null);
        ScmcHostSmsProvider unconfigured = new ScmcHostSmsProvider(properties, RestClient.builder(), new ObjectMapper());

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.sendLoginCode("+237600000001", "1", Duration.ofMinutes(5), "otp-4"))
                .hasMessageContaining("n'est pas configuré");
    }
}
