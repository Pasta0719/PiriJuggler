package jp.pirijuggler.paper.machine;

import com.google.gson.Gson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GodBookGiveExporterTest {
    private static final Gson GSON=new Gson();

    @Test void originalSignedBookPagesStayInOrderAndKeepFormattingAndLinebreaks() {
        var pages=new ArrayList<Component>();
        pages.add(Component.text("旧仕様\n赤と金の7 ").append(
                Component.text("太字 ' \\ \" 朱色").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)));
        for(int i=2;i<=12;i++)pages.add(Component.text("旧ページ"+i+"\n行2"));
        String command=GodBookGiveExporter.command("Paspasta","GOD 攻略本","Original Author",2,
                pages,GodGuideAddendum.pages());

        assertTrue(command.startsWith("/give Paspasta minecraft:written_book[minecraft:written_book_content={title:"));
        assertTrue(command.contains("generation:2,resolved:true,pages:["));
        assertTrue(command.endsWith("]}] 1"));
        assertTrue(command.contains(GSON.toJson("GOD 攻略本")));
        assertTrue(command.contains(GSON.toJson("Original Author")));

        String previous=null;
        for(Component page:pages){
            String source=GsonComponentSerializer.gson().serialize(page);
            String encoded=GSON.toJson(source);
            int index=command.indexOf(encoded);
            assertTrue(index>0,"Original styled page is present exactly as serialized: "+encoded);
            if(previous!=null)assertTrue(index>command.indexOf(previous),"The old pages retain order");
            previous=encoded;
        }
        assertTrue(command.contains("\\n"),"Newline escapes survive nested JSON and SNBT encoding");
        for(String page:GodGuideAddendum.pages()){
            String encoded=GSON.toJson(GsonComponentSerializer.gson().serialize(Component.text(page)));
            assertTrue(command.contains(encoded),"Missing new guide page");
            assertTrue(command.indexOf(encoded)>command.indexOf(previous),"All new pages follow old pages");
        }
    }

    @Test void originalCanBeAlreadyExtendedWithoutDuplicatingTheAddendum(){
        List<Component> pages=new ArrayList<>();
        for(int i=0;i<12;i++)pages.add(Component.text("元ページ"+i));
        GodGuideAddendum.pages().forEach(p->pages.add(Component.text(p)));
        String command=GodBookGiveExporter.command("Player_2","EXTREME GOD","Admin",0,pages,List.of());
        for(String page:GodGuideAddendum.pages()){
            String encoded=GSON.toJson(GsonComponentSerializer.gson().serialize(Component.text(page)));
            assertEquals(command.indexOf(encoded),command.lastIndexOf(encoded),"No duplicate pages");
        }
    }

    @Test void refusesInvalidOriginalDataAndUnsafeRecipient(){
        List<Component> pages=java.util.stream.IntStream.range(0,12)
                .mapToObj(i->(Component)Component.text("page"+i)).toList();
        assertThrows(IllegalArgumentException.class,()->
                GodBookGiveExporter.command("@a","GOD","admin",0,pages,GodGuideAddendum.pages()));
        assertThrows(IllegalArgumentException.class,()->
                GodBookGiveExporter.command("Player","GOD","admin",4,pages,GodGuideAddendum.pages()));
        assertThrows(IllegalArgumentException.class,()->
                GodBookGiveExporter.command("Player","","admin",0,pages,GodGuideAddendum.pages()));
        assertThrows(IllegalArgumentException.class,()->
                GodBookGiveExporter.command("Player","GOD","admin",0,List.of(Component.text("1")),GodGuideAddendum.pages()));
    }
}
