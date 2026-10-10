package jp.pirijuggler.paper.machine;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GodGuideAddendumTest {
    @Test void appendsToOriginalTwelvePagesWithoutRewritingAnyOldWords() {
        var old=java.util.stream.IntStream.rangeClosed(1,12)
                .mapToObj(i->"Original page "+i+"\nFormer text remains exactly")
                .toList();
        var updated=GodGuideAddendum.appended(old);
        assertEquals(17,updated.size());
        assertEquals(old,updated.subList(0,12));
        assertEquals(GodGuideAddendum.pages(),updated.subList(12,17));
        assertTrue(GodGuideAddendum.alreadyIncluded(updated));
        assertEquals(updated,GodGuideAddendum.appended(updated),"repeat commands must not duplicate pages");
        assertFalse(GodGuideAddendum.alreadyIncluded(old));
    }

    @Test void retainsApprovedDisclosureAndReadableMinecraftBookFormatting() {
        var pages=GodGuideAddendum.pages();
        assertEquals(5,pages.size());
        for(String page:pages){
            assertTrue(page.split("\\n",-1).length<=13,"Page should stay readable in Minecraft: "+page);
            assertTrue(page.length()<240,"Page too long");
            assertFalse(page.contains("JUGGLER"));
            assertFalse(page.contains("。"));
            assertFalse(page.contains("GOD in GOD"));
            assertFalse(page.contains("継続率"));
            assertFalse(page.contains("天国移行率"));
        }
        assertTrue(pages.get(0).contains("5連 次G BONUS確定"));
        assertTrue(pages.get(1).contains("3連 次G BONUS確定"));
        assertTrue(pages.get(2).contains("PIERO")==false);
        assertTrue(pages.get(2).contains("ピエロ"));
        assertTrue(pages.get(3).contains("最大20G"));
        assertTrue(pages.get(3).contains("最大15G"));
    }

    @Test void doesNotTreatAnyRandomBookAsOriginalGodManual() {
        assertThrows(IllegalArgumentException.class,()->GodGuideAddendum.appended(List.of("one page")));
    }
}
