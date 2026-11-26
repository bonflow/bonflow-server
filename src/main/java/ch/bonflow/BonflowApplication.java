package ch.bonflow;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BonflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(BonflowApplication.class, args);
    }

    /** Shared time source; tests replace it to control token and lockout expiry. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
