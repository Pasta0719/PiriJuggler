package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.session.Session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillStopGameTest extends GameFixture {
    private static final long START=1_000_000_000L;
    private SkillStopGame skill(){
        var deterministic=new java.util.LinkedHashMap<>(config);
        var premium=new java.util.LinkedHashMap<>((java.util.Map<String,Object>)config.get("premium"));
        premium.put("big_chance_weight",0);deterministic.put("premium",premium);
        return new SkillStopGame(new RandomStreams(42),main,deterministic);
    }
    private Session act(SkillStopGame game,Session s,PacketType action,Integer input) throws Exception {
        var t=game.plan(s,action,s.sequence()+1,1,NOW+s.sequence(),START+1_000_000_000L,0,input);var after=store.commit(t);game.committed(t,START+1_000_000_000L);return after;
    }
    private Session draw(SkillStopGame game,Session s,SkillStopRole role,PremiumPolicy.Type premium) throws Exception {
        s=act(game,s,PacketType.SPACE_ACTION,null);var t=game.plan(s,PacketType.SPACE_ACTION,s.sequence()+1,1,NOW,START,0,null,role,premium);s=store.commit(t);game.committed(t,START);return s;
    }
    private Session top(SkillStopGame game,Session s,Reel reel,int top) throws Exception {return act(game,s,switch(reel){case LEFT->PacketType.STOP_LEFT;case CENTER->PacketType.STOP_CENTER;case RIGHT->PacketType.STOP_RIGHT;},SkillStopReels.stopIndex(top));}
    @Test void grapeCherryPieroPayActualNewAmounts() throws Exception {
        var g=skill();Session s=seat(50,1000);
        s=draw(g,s,SkillStopRole.GRAPE,null);s=top(g,s,Reel.CENTER,20);assertEquals(SkillStopReels.stopIndex(2),s.number("display_center_stop"));s=top(g,s,Reel.LEFT,2);s=top(g,s,Reel.RIGHT,4);assertEquals(8,s.number("pay_display"));
        s=draw(g,s,SkillStopRole.CHERRY,null);s=top(g,s,Reel.CENTER,20);s=top(g,s,Reel.LEFT,5);assertEquals(SkillStopReels.stopIndex(6),s.number("display_left_stop"));s=top(g,s,Reel.RIGHT,1);assertEquals(4,s.number("pay_display"));
        s=draw(g,s,SkillStopRole.PIERO,null);s=top(g,s,Reel.CENTER,20);s=top(g,s,Reel.LEFT,11);s=top(g,s,Reel.RIGHT,3);assertEquals(10,s.number("pay_display"));
    }
    @Test void allOneMedalDrawsProtectBigBitEntry() throws Exception {
        var g=skill();Session original=seat(50,1000);
        for(SkillStopRole role:SkillStopRole.values())if(role.oneMedal()){
            // Independent fixture snapshot, plan each complete spin through the production game/round.
            Session s=draw(g,original,role,null);s=top(g,s,Reel.CENTER,20);assertEquals(SkillStopReels.stopIndex(20),s.number("display_center_stop"));s=top(g,s,Reel.LEFT,10);s=top(g,s,Reel.RIGHT,20);
            assertEquals(Session.GameState.BIG_READY,s.state());assertEquals(0,s.number("pay_display"));
            var values=new java.util.LinkedHashMap<>(s.snapshot());values.put("game_state","SEATED_READY");values.put("bonus_type",null);values.put("lamp_on",0);values.put("notice_state","NONE");db.sql("UPDATE player_sessions SET game_state='SEATED_READY',bonus_type=NULL,lamp_on=0,notice_state='NONE' WHERE session_id=?",s.id().toString());original=new Session(values);
        }
    }
    @Test void oneMedalRetrievalPaysOneAndRetainsBig() throws Exception {
        var g=skill();Session s=draw(g,seat(50,1000),SkillStopRole.ONE_CD,null);s=top(g,s,Reel.CENTER,20);s=top(g,s,Reel.RIGHT,2);s=top(g,s,Reel.LEFT,17);
        assertEquals(1,s.number("pay_display"));assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());assertEquals("BONUS_PENDING",s.publicState().get("gameState").getAsString());assertFalse(s.publicState().has("bonusType"));
    }
    @Test void wrongTypeIsKicked() throws Exception {
        var g=skill();Session s=draw(g,seat(50,1000),SkillStopRole.REG,null);s=top(g,s,Reel.CENTER,20);s=top(g,s,Reel.LEFT,10);s=top(g,s,Reel.RIGHT,20);
        assertEquals(Session.GameState.BONUS_PENDING_REG,s.state());assertNotEquals(SkillStopReels.stopIndex(20),s.number("display_right_stop"));
    }
    @Test void premiumFSecondStopKicksSevenTenpai() throws Exception {
        var g=skill();Session s=draw(g,seat(50,1000),SkillStopRole.BIG,PremiumPolicy.Type.F);s=top(g,s,Reel.CENTER,20);s=top(g,s,Reel.LEFT,10);
        assertNotEquals(SkillStopReels.stopIndex(10),s.number("display_left_stop"));s=top(g,s,Reel.RIGHT,20);assertEquals(Session.GameState.BONUS_PENDING_BIG,s.state());
    }
    @Test void captureResumePreservesInputs() throws Exception {
        var g=skill();Session s=draw(g,seat(50,1000),SkillStopRole.BIG,null);s=top(g,s,Reel.CENTER,20);String history=s.text("machine_state_json");
        g.forget(s.id());assertTrue(g.resume(s,START).isPresent());s=top(g,s,Reel.LEFT,10);s=top(g,s,Reel.RIGHT,20);assertEquals(Session.GameState.BIG_READY,s.state());assertTrue(history.contains("skillInputs"));
    }
    @Test void databaseReseatPreservesPartialSkillStopHistory() throws Exception {
        var g=skill();Session s=seat(50,1000);
        db.sql("UPDATE machines SET machine_type='SKILL_STOP' WHERE machine_id=?",s.machine());
        s=draw(g,s,SkillStopRole.BIG,null);s=top(g,s,Reel.CENTER,20);
        String saved=s.text("machine_state_json");
        db.sql("UPDATE player_sessions SET lifecycle='SUSPENDED_GRACE',lock_expires_at=? WHERE session_id=?",NOW+60_000,s.id().toString());
        g.forget(s.id());s=db.seat(s.player(),s.machine(),NOW+1);
        assertEquals(saved,s.text("machine_state_json"));assertTrue(g.resume(s,START).isPresent());
        s=top(g,s,Reel.LEFT,10);s=top(g,s,Reel.RIGHT,20);assertEquals(Session.GameState.BIG_READY,s.state());
    }
}
