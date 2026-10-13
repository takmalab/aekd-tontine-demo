package cm.aekd.tontine.sms;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsSettingsRepository extends JpaRepository<SmsSettings, Short> {

    default SmsSettings getSingleton() {
        return findById(SmsSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Ligne sms_settings absente (migration V20)"));
    }
}
