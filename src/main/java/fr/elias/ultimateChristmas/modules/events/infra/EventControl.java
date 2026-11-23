package fr.elias.ultimateChristmas.modules.events.infra;

import java.util.List;

/** Narrow API shared by event commands and the future admin GUI. */
public interface EventControl {
    fr.elias.ultimateChristmas.modules.platform.api.ChristmasPlatform platform();

    record Status(String id, boolean enabled, boolean active,
                  Boolean override, String schedule) {}

    List<Status> statuses();

    /** Force a session-only start or stop; auto returns to calendar control. */
    boolean startNow(String id);
    boolean stopNow(String id);
    boolean auto(String id);

    /** Persistently enable/disable an event in events.yml. */
    boolean setEnabled(String id, boolean enabled);
}
