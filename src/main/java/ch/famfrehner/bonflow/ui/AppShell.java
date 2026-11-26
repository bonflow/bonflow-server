package ch.famfrehner.bonflow.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;

/**
 * Vaadin app shell. {@code @Push} enables WebSocket push, which the station displays
 * rely on for live ticket updates (docs/architecture.md section 6).
 */
@Push
public class AppShell implements AppShellConfigurator {
}
