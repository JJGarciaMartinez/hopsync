package hopsync.desktop.kit;

import hopsync.core.util.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandExportTest {

    @BeforeAll
    static void installTheme() {
        HopSyncDark.setup();
    }

    @Test
    void markSvgHoldsEveryPieceInItsColor() {
        String master = BrandExport.markSvg(false, LogoIcon.onDark());
        String small = BrandExport.markSvg(true, LogoIcon.onDark());
        assertEquals(4, master.split("<path").length - 1, "box, circle, square, triangle");
        assertEquals(3, small.split("<path").length - 1, "box, one piece, triangle");
        assertTrue(master.contains("data-part=\"box\" fill-rule=\"nonzero\" fill=\"#E9E6EF\""), master);
        assertTrue(master.contains("data-part=\"mine\" fill-rule=\"nonzero\" fill=\"#2EC4B6\""), master);
        assertTrue(master.contains("data-part=\"incoming\" fill-rule=\"nonzero\" fill=\"#FF6B4A\""), master);
    }

    @Test
    void logotypeIsOutlinedSoItNeedsNoFont() {
        String logo = BrandExport.horizontal(LogoIcon.onDark(), Palette.text());
        assertTrue(!logo.contains("<text"), "the wordmark is paths, not text");
        assertEquals(5, logo.split("<path").length - 1, "4 mark pieces + the wordmark outline");
    }

    @Test
    void tokensAreValidJsonWithTheThemeValues() throws Exception {
        Map<?, ?> tokens = (Map<?, ?>) Json.parse(BrandExport.pretty(BrandExport.tokens(), ""));
        Map<?, ?> colors = (Map<?, ?>) tokens.get("colors");
        List<?> ui = (List<?>) colors.get("interface");
        assertTrue(ui.stream().anyMatch(t -> "HopSync.mine".equals(((Map<?, ?>) t).get("key"))
            && "#2EC4B6".equals(((Map<?, ?>) t).get("value"))));
        assertEquals("#F2668B", ((Map<?, ?>) colors.get("state")).get("error"));
    }

    @Test
    void outputIsTheSameEveryTime() {
        assertEquals(BrandExport.pretty(BrandExport.tokens(), ""), BrandExport.pretty(BrandExport.tokens(), ""));
        assertEquals(BrandExport.appIconSvg(), BrandExport.appIconSvg());
    }
}
