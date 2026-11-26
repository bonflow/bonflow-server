package ch.bonflow.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.aura.Aura;

/**
 * Vaadin app shell. {@code @Push} enables WebSocket push, which the station displays
 * rely on for live ticket updates (docs/architecture.md section 6).
 *
 * <p>Once an app shell exists, Vaadin no longer loads a theme by itself, so the Aura theme is
 * loaded explicitly.
 */
@Push
@StyleSheet(Aura.STYLESHEET)
public class AppShell implements AppShellConfigurator {
}
