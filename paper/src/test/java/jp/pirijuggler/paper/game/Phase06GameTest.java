package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.database.StartupProfile;
import jp.pirijuggler.paper.reel.InternalRole;
import jp.pirijuggler.paper.session.Session;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase06GameTest extends GameFixture {
    private NormalGame phase06(InternalRole role) {
        var cfg=new LinkedHashMap<String,Object>(config);
        var p=new LinkedHashMap<String,Object>(StartupProfile.map(config.get("premium")));p.put("big_chance_weight",0);cfg.put("premium",p);
        return new NormalGame(forced(role),new RandomStreams(7),SOLVER,main,cfg);
    }
    private Session stopAll(NormalGame game,Session s,long nano) throws Exception {
        for(var type:List.of(PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT))s=action(game,s,type,nano+1_000_000_000L);return s;
    }
    private Session enterBonus(NormalGame game,Session s,String type,long nano) throws Exception {
        s=spin(game,s,nano);assertEquals(Session.GameState.valueOf("BONUS_PENDING_"+type),s.state());
        s=action(game,s,PacketType.SPACE_ACTION,nano+2_000_000_000L);assertEquals(Session.GameState.valueOf("BONUS_ENTRY_BETTED_"+type),s.state());
        s=action(game,s,PacketType.SPACE_ACTION,nano+2_100_000_000L);assertEquals(Session.GameState.valueOf("BONUS_ENTRY_SPINNING_"+type),s.state());
        s=stopAll(game,s,nano+2_100_000_000L);assertEquals(Session.GameState.valueOf(type+"_READY"),s.state());return s;
    }
    private Session bonusGame(NormalGame game,Session s,String type,long nano) throws Exception {
        s=action(game,s,PacketType.SPACE_ACTION,nano);assertEquals(Session.GameState.valueOf(type+"_BETTED"),s.state());
        s=action(game,s,PacketType.SPACE_ACTION,nano+100_000_000L);assertEquals(Session.GameState.valueOf(type+"_SPINNING"),s.state());
        return stopAll(game,s,nano+100_000_000L);
    }

    @Test void bigEntryIsOneBetAndBigEndsAtTwentyGamesGross280() throws Exception {
        var game=phase06(InternalRole.BIG);Session s=seat(50,1000);long initial=s.number("credit")+s.number("held_medals");
        s=enterBonus(game,s,"BIG",0);assertEquals(1,scalar("SELECT big_count FROM machine_period_stats"));assertEquals(0,s.number("bonus_payout_count"));
        for(int i=1;i<=20;i++){
            s=bonusGame(game,s,"BIG",4_000_000_000L+i*2_000_000_000L);
            if(i<20){assertEquals(Session.GameState.BIG_READY,s.state());assertEquals(i*14,s.number("bonus_payout_count"));}
        }
        assertEquals(Session.GameState.SEATED_READY,s.state());assertEquals(0,s.number("bonus_payout_count"));assertEquals(0,s.number("lamp_on"));
        assertEquals(initial-3-1-20*2+20*14,s.number("credit")+s.number("held_medals"));
        assertEquals(1,scalar("SELECT count(*) FROM bonus_history WHERE bonus_type='BIG'"));
    }

    @Test void regEntryIsOneBetAndRegEndsAtEightGamesGross112() throws Exception {
        var game=phase06(InternalRole.REG);Session s=seat(50,1000);long initial=s.number("credit")+s.number("held_medals");
        s=enterBonus(game,s,"REG",0);assertEquals(1,scalar("SELECT reg_count FROM machine_period_stats"));
        for(int i=1;i<=8;i++)s=bonusGame(game,s,"REG",4_000_000_000L+i*2_000_000_000L);
        assertEquals(Session.GameState.SEATED_READY,s.state());assertEquals(0,s.number("bonus_payout_count"));
        assertEquals(initial-3-1-8*2+8*14,s.number("credit")+s.number("held_medals"));
        assertEquals(1,scalar("SELECT count(*) FROM bonus_history WHERE bonus_type='REG'"));
    }
}
