package ch.bonflow.masterdata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Venue wide settings that are changed in the admin UI and travel to the app with the master data. */
@Service
public class SettingsService {

    static final String COURSES_ENABLED = "courses.enabled";

    private final AppSettingRepository settings;

    SettingsService(AppSettingRepository settings) {
        this.settings = settings;
    }

    /** Whether orders can assign articles to courses ("Gang"). On by default. */
    @Transactional(readOnly = true)
    public boolean coursesEnabled() {
        return settings.findById(COURSES_ENABLED).map(s -> Boolean.parseBoolean(s.getValue())).orElse(true);
    }

    @Transactional
    public void setCoursesEnabled(boolean enabled) {
        AppSetting setting = settings.findById(COURSES_ENABLED)
                .orElseGet(() -> new AppSetting(COURSES_ENABLED, "true"));
        setting.setValue(Boolean.toString(enabled));
        settings.save(setting);
    }
}
