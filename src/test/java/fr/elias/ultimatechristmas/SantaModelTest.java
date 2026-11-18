package fr.elias.ultimateChristmas;

import com.google.gson.JsonParser;
import fr.elias.ultimateChristmas.core.model.ModelRig;
import fr.elias.ultimateChristmas.modules.santa.infra.GiftLanding;
import fr.elias.ultimateChristmas.modules.santa.lifecycle.SantaParameters;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SantaModelTest {

    private ModelRig rig() {
        return ModelRig.fromJson(JsonParser.parseReader(new InputStreamReader(
                getClass().getResourceAsStream("/rigs/santa_claus.json"), StandardCharsets.UTF_8))
                .getAsJsonObject(), NamespacedKey::fromString);
    }

    @Test
    void everyConfiguredClipExistsInTheRig() {
        ModelRig rig = rig();
        SantaParameters.Model model = SantaParameters.Model.DEFAULTS;
        for (String clip : List.of(model.idle(), model.walk(), model.arrive(), model.greet(),
                model.dropGift(), model.clicked(), model.celebrate(),
                model.farewell())) {
            assertTrue(rig.has(clip), "santa_claus rig is missing clip " + clip);
        }
        assertTrue(rig.animation(model.idle()).loop());
        assertTrue(rig.animation(model.walk()).loop());
        assertFalse(rig.animation(model.dropGift()).loop());
    }

    @Test
    void thePresentLandsOnTheGroundInFrontOfSantaBeforeTheClipEnds() {
        ModelRig rig = rig();
        GiftLanding landing = GiftLanding.find(rig, "drop_gift", "gift");
        assertNotNull(landing);
        double length = rig.animation("drop_gift").length();
        assertTrue(landing.landTime() > 1.5 && landing.landTime() < length,
                "lands at " + landing.landTime());
        assertEquals(0, landing.position().y, 0.25, "rests at foot level");
        assertTrue(Math.abs(landing.position().z) > 0.4, "set down in front of him");
    }

    @Test
    void unknownClipOrBoneHasNoLanding() {
        assertNull(GiftLanding.find(rig(), "no_such_clip", "gift"));
        assertNull(GiftLanding.find(rig(), "drop_gift", "no_such_bone"));
        assertNull(GiftLanding.find(null, "drop_gift", "gift"));
    }
}
