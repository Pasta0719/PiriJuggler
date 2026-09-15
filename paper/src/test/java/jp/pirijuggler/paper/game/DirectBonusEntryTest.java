package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.common.reel.Reel;
import jp.pirijuggler.paper.reel.*;
import jp.pirijuggler.paper.session.Session;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectBonusEntryTest extends GameFixture {
    @Test void everyBonusRoleCanEnterDirectlyWhenEntrySymbolsAreAimed() throws Exception {
        for(var role:List.of(InternalRole.BIG,InternalRole.REG,InternalRole.CHERRY_BIG,InternalRole.CHERRY_REG,InternalRole.PIERO_BIG,InternalRole.PIERO_REG)){
            assertDirectEntry(role);
        }
    }

    private void assertDirectEntry(InternalRole internal) throws Exception {
        DisplayRole base=internal.display(false),entry=internal.directEntryDisplay();
        assertNotNull(entry);
        int[] presses=findDirectPresses(base,entry);
        NormalGame game=game(internal);Session s=seat(50,7);
        s=action(game,s,PacketType.SPACE_ACTION,0);
        s=action(game,s,PacketType.SPACE_ACTION,0);
        NormalGame.Transition last=null;
        PacketType[] stops={PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT};
        for(int i=0;i<stops.length;i++){
            long nano=1_000_000_000L+i;
            last=game.plan(s,stops[i],s.sequence()+1,1,NOW+s.sequence()+1,nano,0,presses[i]);
            s=store.commit(last);game.committed(last,nano);
        }
        assertNotNull(last);
        String bonus=GameRules.bonus(internal);
        assertEquals(Session.GameState.valueOf(bonus+"_READY"),s.state(),internal.toString());
        assertEquals(bonus,last.bonusStarted(),internal.toString());
        assertEquals(47,s.number("credit"),internal+" must not receive overlap payout on direct entry");
        assertEquals(7,s.number("held_medals"));
        assertEquals(0,s.number("pay_display"));
        assertEquals(0,s.number("bonus_payout_count"));
        assertTrue(game.scheduled(last).stream().anyMatch(x->x.packet().packetType()==PacketType.BONUS_START));
        var finalStops=new StopTriplet((int)s.number("display_left_stop"),(int)s.number("display_center_stop"),(int)s.number("display_right_stop"));
        assertTrue(SOLVER.catalogue().evaluation(finalStops).valid(entry),internal+" "+finalStops);
    }

    private static int[] findDirectPresses(DisplayRole base,DisplayRole entry){
        for(int p0=0;p0<21;p0++)for(int p1=0;p1<21;p1++)for(int p2=0;p2<21;p2++){
            int[] presses={p0,p1,p2};StopTriplet stopped=new StopTriplet(0,0,0);int mask=0;
            for(var reel:Reel.values()){
                var choice=SOLVER.choose(base,entry,mask,stopped,reel,presses[reel.ordinal()],false,false);
                stopped=stopped.with(reel,choice.stopIndex());mask|=reel.bit();
            }
            if(SOLVER.catalogue().evaluation(stopped).valid(entry))return presses;
        }
        throw new AssertionError("No direct-entry press sequence for "+base+" -> "+entry);
    }
}
