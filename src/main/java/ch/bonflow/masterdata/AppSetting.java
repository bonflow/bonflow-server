package ch.bonflow.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One venue wide setting; see {@link SettingsService}. */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    protected AppSetting() {
    }

    AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    String getValue() {
        return value;
    }

    void setValue(String value) {
        this.value = value;
    }
}
