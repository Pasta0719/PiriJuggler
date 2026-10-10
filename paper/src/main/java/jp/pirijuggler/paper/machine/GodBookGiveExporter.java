package jp.pirijuggler.paper.machine;

import com.google.gson.Gson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.List;
import java.util.Objects;

/**
 * Produces a Minecraft Java 1.21 /give command from the ACTUAL pages of a signed book.
 * No manually reconstructed original text is used. Preserves rich page components.
 * Original pages precede the optional addendum in exactly their original order.
 */
public final class GodBookGiveExporter {
    private static final Gson GSON = new Gson();
    private static final GsonComponentSerializer COMPONENTS = GsonComponentSerializer.gson();

    private GodBookGiveExporter(){}

    public static String command(String recipient,String title,String author,int generation,
                                 List<Component> originalPages,List<String> addedPages){
        if(recipient==null||!recipient.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Invalid player name");
        if(title==null||title.isBlank()||author==null||author.isBlank())
            throw new IllegalArgumentException("Original signed book needs a title and author");
        Objects.requireNonNull(originalPages,"Original pages");
        Objects.requireNonNull(addedPages,"Addendum");
        if(originalPages.size()<12||originalPages.size()+addedPages.size()>100)
            throw new IllegalArgumentException("Expected original GOD book with at least 12 pages");
        if(generation<0||generation>3)throw new IllegalArgumentException("Invalid book generation");

        // 1.21's written_book_content pages accept JSON text components as SNBT strings.
        // Outer SNBT escaping is deliberately separate from inner JSON escaping.
        var command=new StringBuilder("/give ").append(recipient)
                .append(" minecraft:written_book[minecraft:written_book_content={title:")
                .append(snbtString(title)).append(",author:").append(snbtString(author))
                .append(",generation:").append(generation).append(",resolved:true,pages:[");
        boolean first=true;
        for(Component page:originalPages){
            if(!first)command.append(",");
            command.append(snbtString(COMPONENTS.serialize(page)));
            first=false;
        }
        for(String page:addedPages){
            if(!first)command.append(",");
            command.append(snbtString(COMPONENTS.serialize(Component.text(page))));
            first=false;
        }
        command.append("]}] 1");
        if(command.length()>32000)
            throw new IllegalArgumentException("Written book is too long for a single /give command");
        return command.toString();
    }

    /** JSON quoting is also valid for Minecraft SNBT double-quoted strings. */
    private static String snbtString(String source){
        return GSON.toJson(Objects.requireNonNull(source));
    }
}
