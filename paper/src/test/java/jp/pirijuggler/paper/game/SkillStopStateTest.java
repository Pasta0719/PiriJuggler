package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.machine.DomainException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillStopStateTest extends GameFixture {
    private static final long NANO=1_000_000_000L;
    private SkillStopGame game(){
        var c=new LinkedHashMap<>(config);var premium=new LinkedHashMap<>((Map<String,Object>)config.get("premium"));premium.put("big_chance_weight",0);c.put("premium",premium);
        return new SkillStopGame(new RandomStreams(42),main,c);
    }
    private Session seatSkill() throws Exception {Session s=seat(50,10000);db.sql("UPDATE machines SET machine_type='SKILL_STOP' WHERE machine_id=?",s.machine());return s;}
    private Session apply(SkillStopGame g,GameTransition t) throws Exception {Session s=store.commit(t);g.committed(t,NANO);return s;}
    private Session act(SkillStopGame g,Session s,PacketType action,Integer input) throws Exception {return apply(g,g.plan(s,action,s.sequence()+1,1,NOW+s.sequence(),NANO+1_000_000_000L,0,input));}
    private Session top(SkillStopGame g,Session s,int reel,int top) throws Exception {return act(g,s,new PacketType[]{PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT}[reel],SkillStopReels.stopIndex(top));}
    private Session forced(SkillStopGame g,Session s,SkillStopRole role) throws Exception {
        s=act(g,s,PacketType.SPACE_ACTION,null);
        return apply(g,g.plan(s,PacketType.SPACE_ACTION,s.sequence()+1,1,NOW,NANO,0,null,role,null));
    }
    private Session entry(SkillStopGame g,Session s,String type) throws Exception {
        s=forced(g,s,SkillStopRole.valueOf(type));s=top(g,s,1,20);s=top(g,s,0,10);return top(g,s,2,type.equals("BIG")?20:19);
    }
    private Session lever(SkillStopGame g,Session s,SkillStopBonus.Target target) throws Exception {
        s=act(g,s,PacketType.SPACE_ACTION,null);
        return apply(g,g.planBonus(s,PacketType.SPACE_ACTION,s.sequence()+1,1,NOW,NANO,0,null,target));
    }
    private Session finish(SkillStopGame g,Session s,SkillStopBonus.Target symbols) throws Exception {
        int[] tops=switch(symbols){case BAR->new int[]{3,7,19};case BELL->new int[]{9,19,1};case PIERO->new int[]{11,3,3};case AUTO->new int[]{1,1,1};};
        for(int r=0;r<3;r++)s=top(g,s,r,tops[r]);return s;
    }
    private Session remaining(Session s,int n) throws Exception {
        var json=s.machineState();new SkillStopBonus(n,SkillStopBonus.Target.AUTO).write(json);
        db.sql("UPDATE player_sessions SET machine_state_json=? WHERE session_id=?",json.toString(),s.id().toString());return db.state().session(s.player());
    }
    @Test void bigAndRegExhaustByGamesWithFourteenPaidEveryGame() throws Exception {
        for(String type:List.of("BIG","REG")){
            var g=game();Session s=entry(g,seatSkill(),type);int games=type.equals("BIG")?22:9;assertEquals(games,SkillStopBonus.read(s).remaining());
            for(int i=0;i<games;i++){
                s=lever(g,s,SkillStopBonus.Target.AUTO);assertTrue(Set.of("GRAPE","CHERRY").contains(s.text("internal_role")));
                s=finish(g,s,SkillStopBonus.Target.AUTO);assertEquals(14,s.number("pay_display"));assertEquals(games-i-1,SkillStopBonus.read(s).remaining());
                assertEquals(i==games-1?Session.GameState.SEATED_READY:Session.GameState.valueOf(type+"_READY"),s.state());
            }
        }
    }
    @Test void finalSuccessAddsTwoBeforeEndAndAddedGamesAlsoDraw() throws Exception {
        var g=game();Session s=remaining(entry(g,seatSkill(),"REG"),1);
        s=finish(g,lever(g,s,SkillStopBonus.Target.BAR),SkillStopBonus.Target.BAR);
        assertEquals(2,SkillStopBonus.read(s).remaining());assertEquals(Session.GameState.REG_READY,s.state());
        for(var target:List.of(SkillStopBonus.Target.BELL,SkillStopBonus.Target.PIERO)){
            s=lever(g,s,target);assertEquals(target,SkillStopBonus.read(s).target());s=finish(g,s,target);
        }
        assertEquals(4,SkillStopBonus.read(s).remaining());assertEquals(42,s.number("bonus_payout_count"));
    }
    @Test void wrongTargetIsNotKickedButFailsAndPaysFourteen() throws Exception {
        var g=game();Session s=remaining(entry(g,seatSkill(),"BIG"),1);
        s=finish(g,lever(g,s,SkillStopBonus.Target.BELL),SkillStopBonus.Target.BAR);
        assertEquals(Session.GameState.SEATED_READY,s.state());assertEquals(14,s.number("pay_display"));
        assertEquals(SkillStopReels.stopIndex(3),s.number("display_left_stop"));assertEquals(SkillStopReels.stopIndex(7),s.number("display_center_stop"));assertEquals(SkillStopReels.stopIndex(19),s.number("display_right_stop"));
    }
    @Test void repeatedThirdStopAndRepeatedTransactionDoNotPayOrAddAgain() throws Exception {
        var g=game();Session s=remaining(entry(g,seatSkill(),"BIG"),1);s=lever(g,s,SkillStopBonus.Target.PIERO);s=top(g,s,0,11);s=top(g,s,1,3);
        var t=g.plan(s,PacketType.STOP_RIGHT,s.sequence()+1,1,NOW,NANO+1_000_000_000L,0,SkillStopReels.stopIndex(3));
        var after=apply(g,t);long assets=after.number("credit")+after.number("held_medals");
        assertEquals(after.sequence(),store.commit(t).sequence());assertEquals(assets,db.state().session(s.player()).number("credit")+db.state().session(s.player()).number("held_medals"));assertEquals(2,SkillStopBonus.read(after).remaining());
        final Session old=s;assertThrows(DomainException.class,()->store.commit(g.plan(old,PacketType.STOP_RIGHT,old.sequence()+1,1,NOW,NANO+1_000_000_000L,0,SkillStopReels.stopIndex(3))));
        var rejected=g.plan(after,PacketType.STOP_RIGHT,after.sequence()+1,1,NOW,NANO,0,0);assertFalse(rejected.finished());assertEquals(0,rejected.payout());assertEquals(2,SkillStopBonus.read(apply(g,rejected)).remaining());
    }
    @Test void restartPreservesFixedTargetPartialStopsRemainingAndMachineLock() throws Exception {
        var g=game();Session s=remaining(entry(g,seatSkill(),"BIG"),1);s=lever(g,s,SkillStopBonus.Target.BAR);s=top(g,s,0,3);s=top(g,s,1,7);
        String saved=s.text("machine_state_json"),spin=s.text("spin_id"),period=s.text("source_business_period_id");UUID owner=s.player();int machine=s.machine();long assets=s.number("credit")+s.number("held_medals");
        db.close();db.open(2,NOW+500,config,new SplittableRandom(2),x->{});
        s=db.state().session(owner);assertEquals(Session.Lifecycle.SUSPENDED_GRACE,s.lifecycle());assertEquals(Long.MAX_VALUE,s.number("lock_expires_at"));assertEquals(saved,s.text("machine_state_json"));assertEquals(period,s.text("source_business_period_id"));
        assertThrows(DomainException.class,()->db.seat(UUID.randomUUID(),machine,NOW+501));
        s=db.seat(owner,machine,NOW+501);g=game();assertTrue(g.resume(s,NANO).isPresent());assertEquals(spin,s.text("spin_id"));
        s=top(g,s,2,19);assertEquals(2,SkillStopBonus.read(s).remaining());assertEquals(assets+14,s.number("credit")+s.number("held_medals"));
    }
    @Test void expiryIdleAndDirectReseatKeepPendingRightsInsteadOfAutoSettlement() throws Exception {
        var g=game();Session s=forced(g,seatSkill(),SkillStopRole.ONE_A);s=top(g,s,1,20);s=top(g,s,2,2);s=top(g,s,0,1);
        assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());assertEquals(0,s.number("pay_display"));String saved=s.text("machine_state_json");
        db.disconnect(s.player(),NOW,1,g.capture(s,NANO));db.expire(NOW+2);s=db.state().session(s.player());assertEquals(saved,s.text("machine_state_json"));assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());
        s=db.seat(s.player(),s.machine(),NOW+3);assertEquals(List.of(s.player()),db.maintain(NOW+1000,1));s=db.state().session(s.player());assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());assertEquals(Long.MAX_VALUE,s.number("lock_expires_at"));
        db.sql("UPDATE player_sessions SET lock_expires_at=? WHERE session_id=?",NOW,s.id().toString());s=db.seat(s.player(),s.machine(),NOW+1001);assertEquals(saved,s.text("machine_state_json"));assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());
    }
    @Test void pendingReplayIsFreeRepeatedlyAndRightsRemain() throws Exception {
        var g=game();Session s=forced(g,seatSkill(),SkillStopRole.BIG);s=top(g,s,1,20);s=top(g,s,0,1);s=top(g,s,2,1);assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());
        for(int i=0;i<2;i++){
            long assets=s.number("credit")+s.number("held_medals");s=forced(g,s,SkillStopRole.REPLAY);
            s=top(g,s,1,20); // replay normally slides seven to lower
            s=top(g,s,0,2);s=top(g,s,2,3);
            assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());assertTrue(s.machineState().get("skillPendingReplay").getAsBoolean());assertEquals(assets-(i==0?1:0),s.number("credit")+s.number("held_medals"));
            db.disconnect(s.player(),NOW,1);db.expire(NOW+2);s=db.seat(s.player(),s.machine(),NOW+3);
        }
    }
    @Test void naturalDrawUsesLockedRatesAndUniformTargetsForBothTypes() {
        for(String type:List.of("BIG","REG")){
            int trials=150000,denominator=type.equals("BIG")?15:9;int[] counts=new int[4];var random=new SplittableRandom(1);
            for(int i=0;i<trials;i++)counts[new SkillStopBonus(1,SkillStopBonus.Target.AUTO).lever(type,random,null).target().ordinal()]++;
            for(int k=1;k<=3;k++)assertEquals(trials/(3.0*denominator),counts[k],trials/(3.0*denominator)*.08);
        }
    }
    @Test void challengeLeverPublishesFixedTargetAndSuccessOnlySchedulesOneNotice() throws Exception {
        var g=game();Session s=entry(g,seatSkill(),"BIG");
        for(var target:List.of(SkillStopBonus.Target.BAR,SkillStopBonus.Target.BELL,SkillStopBonus.Target.PIERO)){
            s=lever(g,s,target);
            var start=g.resume(s,NANO).orElseThrow().payload();
            assertEquals(target.name(),start.get("skillChallenge").getAsString());assertEquals(SkillStopBonus.read(s).remaining(),start.get("skillRemaining").getAsInt());
            int[] tops=switch(target){case BAR->new int[]{3,7,19};case BELL->new int[]{9,19,1};case PIERO->new int[]{11,3,3};default->throw new AssertionError();};
            s=top(g,s,0,tops[0]);s=top(g,s,1,tops[1]);
            var transition=g.plan(s,PacketType.STOP_RIGHT,s.sequence()+1,1,NOW,NANO+1_000_000_000L,0,SkillStopReels.stopIndex(tops[2]));
            var notices=g.scheduled(transition).stream().filter(p->p.packet().packetType()==PacketType.NOTICE).toList();
            assertEquals(1,notices.size());assertEquals(transition.publicDelayMs(),notices.getFirst().delayMs());
            assertTrue(notices.getFirst().packet().payload().get("skillChallengeSuccess").getAsBoolean());
            assertEquals("NOTICE",notices.getFirst().packet().payload().get("sound").getAsString());
            s=apply(g,transition);assertEquals("AUTO",s.publicState().get("skillChallenge").getAsString());
            var retry=g.plan(s,PacketType.STOP_RIGHT,s.sequence()+1,1,NOW,NANO,0,0);
            assertTrue(g.scheduled(retry).stream().noneMatch(p->p.packet().packetType()==PacketType.NOTICE));
        }
        s=lever(g,s,SkillStopBonus.Target.BELL);s=top(g,s,0,3);s=top(g,s,1,7);
        var wrong=g.plan(s,PacketType.STOP_RIGHT,s.sequence()+1,1,NOW,NANO+1_000_000_000L,0,SkillStopReels.stopIndex(19));
        assertTrue(g.scheduled(wrong).stream().noneMatch(p->p.packet().packetType()==PacketType.NOTICE));assertEquals(14,wrong.payout());
    }
}
