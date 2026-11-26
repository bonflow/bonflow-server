package ch.famfrehner.bonflow.display;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tells open station views that their station has changed. Views register a callback and reload
 * the board when it fires; they must not rely on the notification alone (they also poll).
 */
@Component
public class StationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StationBroadcaster.class);

    private final Map<Long, Set<Runnable>> listeners = new ConcurrentHashMap<>();

    /** @return call to unregister */
    public Runnable register(long stationId, Runnable onChange) {
        listeners.computeIfAbsent(stationId, id -> new CopyOnWriteArraySet<>()).add(onChange);
        return () -> {
            Set<Runnable> set = listeners.get(stationId);
            if (set != null) {
                set.remove(onChange);
            }
        };
    }

    public void broadcast(long stationId) {
        for (Runnable listener : listeners.getOrDefault(stationId, Set.of())) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                // e.g. the browser disconnected; its view unregisters itself on detach
                log.debug("Station listener failed for station {}", stationId, e);
            }
        }
    }

    int listenerCount(long stationId) {
        return listeners.getOrDefault(stationId, Set.of()).size();
    }
}
