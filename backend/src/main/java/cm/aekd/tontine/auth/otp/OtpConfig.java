package cm.aekd.tontine.auth.otp;

import cm.aekd.tontine.sms.SmsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OtpProperties.class, SmsProperties.class})
public class OtpConfig {
}
