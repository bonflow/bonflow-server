package ch.bonflow.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import ch.bonflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123")
@Import(TestcontainersConfiguration.class)
@Transactional
class SettingsTests {

    @Autowired
    SettingsService settings;
    @Autowired
    MasterDataService masterData;

    @Test
    void coursesAreEnabledByDefaultAndTheSwitchReachesTheApp() {
        assertThat(settings.coursesEnabled()).isTrue();
        assertThat(masterData.snapshot().settings().coursesEnabled()).isTrue();

        settings.setCoursesEnabled(false);

        assertThat(settings.coursesEnabled()).isFalse();
        assertThat(masterData.snapshot().settings().coursesEnabled()).isFalse();

        settings.setCoursesEnabled(true);
        assertThat(masterData.snapshot().settings().coursesEnabled()).isTrue();
    }
}
