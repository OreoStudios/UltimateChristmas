package fr.elias.ultimateChristmas;

import fr.elias.ultimateChristmas.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TextPlaceholderTest {

    @Test
    void formattedValuesRenderTheirColourCodes() {
        Component message = Text.parse("<green>Now playing <reset>%track%",
                Map.of("track", Text.formatted("&6&lJoy to the World")));
        assertEquals("Now playing Joy to the World", Text.plain(message));

        Component title = message.children().stream()
                .filter(child -> Text.plain(child).contains("Joy")).findFirst().orElseThrow();
        Component styled = title.children().isEmpty() ? title : title.children().getFirst();
        assertEquals(NamedTextColor.GOLD, styled.color());
        assertEquals(TextDecoration.State.TRUE, styled.decoration(TextDecoration.BOLD));
    }

    @Test
    void plainValuesStayLiteral() {
        Component message = Text.parse("Hi %player%", Map.of("player", "&6<red>Bob"));
        assertEquals("Hi &6<red>Bob", Text.plain(message));
    }
}
