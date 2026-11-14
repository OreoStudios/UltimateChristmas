package fr.elias.ultimateChristmas.modules.secretsanta.lifecycle;

import dev.oreo.modulith.core.ModuleConfiguration;

/**
 * Typed settings for the Secret Santa exchange, built by its functor.
 *
 * @param nicePoints       naughty/nice credit for delivering a gift
 * @param announceDelivery tell the server when a gift is delivered (without naming the giver)
 */
public record SecretSantaParameters(
        int nicePoints,
        boolean announceDelivery
) {

    public static final SecretSantaParameters DEFAULTS =
            new SecretSantaParameters(5, false);

    public static SecretSantaParameters from(ModuleConfiguration config,
                                             SecretSantaParameters defaults) {
        return new SecretSantaParameters(
                config.getInt("nice-points", defaults.nicePoints()),
                config.getBoolean("announce-delivery", defaults.announceDelivery()));
    }
}
