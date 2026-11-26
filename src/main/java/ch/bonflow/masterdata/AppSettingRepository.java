package ch.bonflow.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
