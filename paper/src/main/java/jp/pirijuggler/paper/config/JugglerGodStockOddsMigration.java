package jp.pirijuggler.paper.config;

import java.util.regex.Pattern;

/**
 * Split bonus-stock probability from the normal-game base.
 * Upgrade known unmodified rows only. Never silently overwrite custom base
 * or an operator-provided stock probability.
 */
public final class JugglerGodStockOddsMigration {
    private JugglerGodStockOddsMigration() {}

    public record Result(String text,int adjusted,int stockKeysAdded,int customKept) {}
    private static final int[] GOD_REFERENCE={0,297986,296030,298549,303386,306204,289197};
    private static final int[] EXT_REFERENCE={0,224070,221440,225491,230039,235721,223878};
    private static final int[] GOD_REBALANCED={0,270000,267000,270500,270500,268000,255000};
    private static final int[] EXT_REBALANCED={0,198000,192500,194000,197000,199500,187000};
    private static final int[] GOD_STOCK={0,743613,734884,734653,739194,741839,696323};
    private static final int[] EXT_STOCK={0,564190,554200,558800,562600,571106,537600};
    private static final int[] GOD_SMALL={0,813500,809600,819100,827000,841000,833500};
    private static final int[] GOD_TWO={0,94884,105041,114326,130995,139760,153160};
    private static final int[] EXT_TWO={0,72001,78911,92247,110987,127811,139688};
    private static final Pattern ROW=Pattern.compile("^\\s{4}'([1-6])':\\s*\\{.*\\}\\s*$");
    private static final Pattern BASE=Pattern.compile("(?<![\\w])bonus_scale_ppm:\\s*(\\d+)");
    private static final Pattern SMALL=Pattern.compile("(?<![\\w])small_role_scale_ppm:\\s*(\\d+)");
    private static final Pattern TWO=Pattern.compile("(?<![\\w])precursor_two_high_ppm:\\s*(\\d+)");
    private static final Pattern STOCK=Pattern.compile("(?<![\\w])bonus_stock_scale_ppm:\\s*(\\d+)");

    public static Result migrate(String input) {
        String[] lines=input.split("\\n",-1);
        String profile="";
        int adjusted=0,added=0,custom=0;
        for(int i=0;i<lines.length;i++){
            String line=lines[i];
            if(line.matches("^[a-z_]+:.*")) profile=line.substring(0,line.indexOf(':'));
            boolean extreme="juggler_god_extreme".equals(profile);
            if(!extreme&&!"juggler_god".equals(profile))continue;
            var match=ROW.matcher(line);
            if(!match.matches())continue;
            int setting=Integer.parseInt(match.group(1));
            int ref=(extreme?EXT_REFERENCE:GOD_REFERENCE)[setting];
            int tuned=(extreme?EXT_REBALANCED:GOD_REBALANCED)[setting];
            int stock=(extreme?EXT_STOCK:GOD_STOCK)[setting];
            int small=(extreme?700000:GOD_SMALL[setting]);
            int two=(extreme?EXT_TWO:GOD_TWO)[setting];
            var baseMatcher=BASE.matcher(line);
            var smallMatcher=SMALL.matcher(line);
            var twoMatcher=TWO.matcher(line);
            if(!baseMatcher.find()||!smallMatcher.find())continue;
            int observed=Integer.parseInt(baseMatcher.group(1));
            int observedSmall=Integer.parseInt(smallMatcher.group(1));
            boolean standard=observedSmall==small
                    &&twoMatcher.find()&&Integer.parseInt(twoMatcher.group(1))==two;
            boolean hasStock=STOCK.matcher(line).find();
            // A known unmodified profile is safe to rebalance once.
            if(observed==ref&&standard&&!hasStock){
                line=line.substring(0,baseMatcher.start(1))+tuned+line.substring(baseMatcher.end(1));
                adjusted++;
            }else if(observed!=tuned||!standard){
                custom++;
            }
            if(!hasStock){
                line=line.replaceFirst("\\}\\s*$",", bonus_stock_scale_ppm: "+stock+"}");
                added++;
            }
            lines[i]=line;
        }
        return new Result(String.join("\n",lines),adjusted,added,custom);
    }
}
