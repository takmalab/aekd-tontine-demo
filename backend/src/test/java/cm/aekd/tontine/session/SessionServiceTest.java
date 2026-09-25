package cm.aekd.tontine.session;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionServiceTest {

    @Autowired
    private SessionService sessionService;

    @Test
    void createsSessionWithValidDateRange() {
        SessionResponse response = sessionService.create(
                new SessionRequest("Octobre 2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(response.id()).isNotNull();
        assertThat(response.label()).isEqualTo("Octobre 2026");

        SessionResponse fetched = sessionService.findById(response.id());
        assertThat(fetched.label()).isEqualTo("Octobre 2026");

        List<SessionResponse> all = sessionService.findAll();
        assertThat(all).extracting(SessionResponse::id).contains(response.id());
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> sessionService.create(
                new SessionRequest("Séance invalide", LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findByIdFailsForUnknownId() {
        assertThatThrownBy(() -> sessionService.findById(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
