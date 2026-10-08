package jp.pirijuggler.paper.reel;

import jp.pirijuggler.common.reel.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * First-stop-only exhaustive bonus-exclusive audit.
 * Calls the production controllers; does not infer a first stop from an eventual 3-reel result.
 * A hit means pressed index + realized stop + slip is impossible for every non-bonus draw.
 */
public class FirstStopAuditTest {
  record Event(String family,String role,String bonus,String premium,Reel reel,int pressed,int stopped,int slip,
               Symbol aim,Symbol top,Symbol middle,Symbol bottom) {
    String key() { return reel.name()+"/"+pressed+"/"+stopped+"/"+slip; }
    String csv(boolean exclusive) { return String.join(",",family,role,bonus,premium,reel.name(),
      Integer.toString(pressed),Integer.toString(stopped),Integer.toString(slip),
      aim.name(),top.name(),middle.name(),bottom.name(),Boolean.toString(exclusive)); }
  }
  private static final String[] HEADER = {"family","role","bonus","premium","first_reel","pressed_index",
    "stop_index","slip","aim_middle_symbol","result_top","result_middle","result_bottom","bonus_exclusive"};
  private static final String[] SMALL = {"MISS","REPLAY","GRAPE","CHERRY","BELL","PIERO"};
  private static final String[] AWARDS = {"BIG","REG","CHERRY_BIG","CHERRY_REG","PIERO_BIG","PIERO_REG"};
  private static String bonus(String role) { return role.endsWith("BIG")||role.startsWith("BIG")||role.startsWith("ONE_")?"BIG":
    role.endsWith("REG")||role.equals("REG")?"REG":"NONE"; }
  private static Event jugglerEvent(StopSolver solver, String role, String premium, Reel reel, int pressed) {
    InternalRole internal=InternalRole.valueOf(role);
    boolean isB=premium.equals("B"),isF=premium.equals("F");
    DisplayRole display=internal.display(isB);
    DisplayRole direct=(isB||isF)?null:internal.directEntryDisplay();
    boolean allowBar=!premium.equals("NONE");
    boolean forbidRight = display!=DisplayRole.GRAPE&&display!=DisplayRole.BONUS&&
       display!=DisplayRole.BONUS_CHERRY&&display!=DisplayRole.PIERO_BONUS&&display!=DisplayRole.PREMIUM_B;
    StopSolver.Choice x=solver.choose(display,direct,0,new StopTriplet(0,0,0),reel,pressed,
       isF,allowBar,forbidRight,direct!=null);
    int s=x.stopIndex();
    return new Event("JUGGLER_FAMILY",role,bonus(role),premium,reel,pressed,s,x.slip(),
       FixedReels.row(reel,pressed,0),FixedReels.row(reel,s,-1),FixedReels.row(reel,s,0),FixedReels.row(reel,s,1));
  }
  private static Event skillEvent(SkillStopControl solver, String role, String premium, Reel reel, int pressed) {
    SkillStopRole internal=SkillStopRole.valueOf(role);
    SkillStopControl.Context c=SkillStopControl.Context.normal(internal,SkillStopControl.Premium.valueOf(premium));
    SkillStopControl.Choice x=solver.choose(c,SkillStopHistory.empty(),reel,pressed);
    int s=x.stopIndex();
    return new Event("SKILL_STOP",role,bonus(role),premium,reel,pressed,s,x.slip(),
       SkillStopReels.row(reel,pressed,0),SkillStopReels.row(reel,s,-1),SkillStopReels.row(reel,s,0),SkillStopReels.row(reel,s,1));
  }
  private static List<Event> juggler() {
    var solver=new StopSolver(new StopCatalogue());
    var out=new ArrayList<Event>();
    for(var role:SMALL)for(Reel reel:Reel.values())for(int p=0;p<21;p++)out.add(jugglerEvent(solver,role,"NONE",reel,p));
    for(var role:AWARDS) {
      var premiums=new ArrayList<String>();premiums.add("NONE");
      if(bonus(role).equals("BIG")) {premiums.addAll(List.of("A","C","D","E","F"));if(role.equals("CHERRY_BIG"))premiums.add("B");}
      for(var premium:premiums)for(Reel reel:Reel.values())for(int p=0;p<21;p++)
        out.add(jugglerEvent(solver,role,premium,reel,p));
    }
    return out;
  }
  private static List<Event> skill() {
    var solver=new SkillStopControl();
    var out=new ArrayList<Event>();
    for(var role:SMALL)for(Reel reel:Reel.values())for(int p=0;p<21;p++)out.add(skillEvent(solver,role,"NONE",reel,p));
    for(SkillStopRole role:SkillStopRole.values()){
      if(role.bonus()==null)continue;
      var premiums=new ArrayList<String>();premiums.add("NONE");
      if(role.bigFamily()) {premiums.addAll(List.of("A","C","D","E","F"));if(role==SkillStopRole.CHERRY_BIG)premiums.add("B");}
      for(var premium:premiums)for(Reel reel:Reel.values())for(int p=0;p<21;p++)
        out.add(skillEvent(solver,role.name(),premium,reel,p));
    }
    return out;
  }
  @Test void exhaustiveFirstStopOnly() throws Exception {
    var all=new ArrayList<Event>();all.addAll(juggler());all.addAll(skill());
    assertEquals(all.size(),all.stream().map(x->x.family()+"/"+x.role()+"/"+x.premium()+"/"+x.reel()+"/"+x.pressed()).distinct().count());
    Map<String,Set<String>> nonBonus=new HashMap<>();
    for(var e:all)if(e.bonus().equals("NONE"))nonBonus.computeIfAbsent(e.family(),x->new HashSet<>()).add(e.key());
    var positives=new ArrayList<Event>();
    var csvAll=new ArrayList<String>();csvAll.add(String.join(",",HEADER));
    for(var e:all){
      boolean exclusive=!e.bonus().equals("NONE")&&!nonBonus.get(e.family()).contains(e.key());
      csvAll.add(e.csv(exclusive));if(exclusive)positives.add(e);
    }
    Files.createDirectories(Path.of("build"));
    Files.write(Path.of("build","first-stop-audit-all.csv"),csvAll);
    var csv=new ArrayList<String>();csv.add(String.join(",",HEADER));
    for(var e:positives)csv.add(e.csv(true));
    Files.write(Path.of("build","first-stop-audit-exclusive.csv"),csv);
    StringBuilder summary=new StringBuilder();
    for(String family:List.of("JUGGLER_FAMILY","SKILL_STOP")){
      var familyAll=all.stream().filter(x->x.family().equals(family)).toList();
      var familyPos=positives.stream().filter(x->x.family().equals(family)).toList();
      long big=familyPos.stream().filter(x->x.bonus().equals("BIG")).count();
      long reg=familyPos.stream().filter(x->x.bonus().equals("REG")).count();
      summary.append(family).append(" total=").append(familyAll.size())
        .append(" small=").append(familyAll.stream().filter(x->x.bonus().equals("NONE")).count())
        .append(" exclusive=").append(familyPos.size())
        .append(" big=").append(big).append(" reg=").append(reg).append("\n");
      for(var reel:Reel.values()){
        var values=familyPos.stream().filter(x->x.reel()==reel).toList();
        summary.append("  ").append(reel.name()).append("=").append(values.size()).append("\n");
      }
      Map<String,List<Event>> byRole=new LinkedHashMap<>();
      for(var e:familyPos)byRole.computeIfAbsent(e.role()+"/"+e.premium(),k->new ArrayList<>()).add(e);
      for(var entry:byRole.entrySet()){
        summary.append("  ").append(entry.getKey()).append(" ").append(entry.getValue().size()).append(" ");
        for(var e:entry.getValue()){
          summary.append(e.reel().name().charAt(0)).append(":").append(e.pressed()).append("->")
            .append(e.stopped()).append("(").append(e.slip()).append(") ");
        }
        summary.append("\n");
      }
    }
    Files.writeString(Path.of("build","first-stop-audit-summary.txt"),summary.toString());
    System.out.println("FIRST_STOP_AUDIT_BEGIN\n"+summary+"FIRST_STOP_AUDIT_END");
    assertTrue(all.size()>1000);
  }
}