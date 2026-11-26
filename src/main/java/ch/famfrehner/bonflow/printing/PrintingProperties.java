package ch.famfrehner.bonflow.printing;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param connectTimeout how long to wait for a printer to accept the connection
 * @param writeTimeout   how long a send may block once connected
 * @param zone           time zone of the time printed on tickets
 */
@ConfigurationProperties("bonflow.printing")
record PrintingProperties(
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration writeTimeout,
        @DefaultValue("Europe/Zurich") ZoneId zone) {
}
