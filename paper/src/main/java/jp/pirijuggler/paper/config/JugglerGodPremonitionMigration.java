package jp.pirijuggler.paper.config;

/**
 * Pure, offline-testable migration of unmodified GOD tuning rows.
 * No dependency on Bukkit/Paper or file I/O.
 */
public final class JugglerGodPremonitionMigration {
    private JugglerGodPremonitionMigration(){}

    public record Result(String text,int changed,int skipped){}
    public static Result migrate(String original) {
        String[] lines=original.split("\\n",-1);
        int[] godOld={0,743613,734884,734653,739194,741839,696323};
        int[] godPrior={0,511253,515456,524724,541234,547868,521596};
        int[] godNew={0,297986,296030,298549,303386,306204,289197};
        int[] godRole={0,813500,809600,819100,827000,841000,833500};
        int[] godTwo={0,94884,105041,114326,130995,139760,153160};
        int[] exOld={0,564190,554200,558800,562600,571106,537600};
        int[] exPrior={0,373441,374111,389811,407855,424958,406753};
        int[] exNew={0,224070,221440,225491,230039,235721,223878};
        int[] exTwo={0,72001,78911,92247,110987,127811,139688};
        String profile="";
        int changed=0,skipped=0;
        var row=java.util.regex.Pattern.compile("^(\\s{4}'([1-6])':\\s*\\{)(.*)(\\}\\s*)$");
        var base=java.util.regex.Pattern.compile("bonus_scale_ppm:\\s*(\\d+)");
        var small=java.util.regex.Pattern.compile("small_role_scale_ppm:\\s*(\\d+)");
        for(int i=0;i<lines.length;i++){
            String line=lines[i];
            if(line.matches("^[a-z_]+:.*")){
                profile=line.substring(0,line.indexOf(':'));
            }
            if(!"juggler_god".equals(profile)&&!"juggler_god_extreme".equals(profile))continue;
            var match=row.matcher(line);
            if(!match.matches())continue;
            int setting=Integer.parseInt(match.group(2));
            boolean extreme="juggler_god_extreme".equals(profile);
            int[] previous=extreme?exPrior:godPrior;
            int[] older=extreme?exOld:godOld;
            int[] target=extreme?exNew:godNew;
            int[] precursors=extreme?exTwo:godTwo;
            var originalBase=base.matcher(line);
            var originalSmall=small.matcher(line);
            if(!originalBase.find()||!originalSmall.find())continue;
            int observed=Integer.parseInt(originalBase.group(1));
            int observedSmall=Integer.parseInt(originalSmall.group(1));
            boolean stockSmall=observedSmall==(extreme?700000:godRole[setting]);
            boolean stockBase=observed==older[setting]||observed==previous[setting];
            if(observed==target[setting]||stockBase&&stockSmall){
                String updated=stockBase&&stockSmall
                        ?line.substring(0,originalBase.start(1))+target[setting]+line.substring(originalBase.end(1))
                        :line;
                if(!updated.contains("precursor_two_high_ppm:"))
                    updated=updated.replaceFirst("\\}\\s*$",", precursor_two_high_ppm: "+precursors[setting]+"}");
                if(!updated.equals(line)){lines[i]=updated;changed++;}
            }else{
                // Custom operator tuning must never be silently replaced.
                skipped++;
            }
        }
        return new Result(String.join("\n",lines),changed,skipped);
    }
}
