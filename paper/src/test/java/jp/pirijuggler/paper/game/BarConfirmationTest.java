package jp.pirijuggler.paper.game;

import jp.pirijuggler.common.protocol.*;
import jp.pirijuggler.common.reel.*;
import jp.pirijuggler.paper.reel.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarConfirmationTest extends GameFixture {
    @Test void barLinesAreForbiddenForEveryNonBonusCandidateOnAllFivePaylines(){
        int[] perLine=new int[5];int raw=0,strict=0;
        for(var e:SOLVER.catalogue().evaluations()){
            int bar=0;for(var line:Payline.values()){
                boolean all=true;for(var reel:Reel.values())all&=FixedReels.row(reel,e.stops().stop(reel),line.row(reel))==Symbol.BAR;
                if(all){bar|=1<<line.ordinal();perLine[line.ordinal()]++;}
            }
            assertEquals(bar,e.winningBarConfirmationLines());
            if(bar!=0){raw++;for(var role:DisplayRole.values())if(role!=DisplayRole.BONUS)assertFalse(e.valid(role));if(e.valid(DisplayRole.BONUS))strict++;}
        }
        assertEquals(20,raw);assertEquals(8,strict);for(int count:perLine)assertTrue(count>0);
        assertEquals(10,SOLVER.catalogue().candidates(DisplayRole.REG_ENTRY).size());
    }
    @Test void bothBonusTypesCanShowEveryBarShapeInEveryStopOrderWithoutPayoutOrDisclosure() throws Exception {
        var bars=SOLVER.catalogue().candidates(DisplayRole.BONUS).stream().filter(e->e.winningBarConfirmationLines()!=0).toList();
        for(var internal:List.of(InternalRole.BIG,InternalRole.REG))for(var target:bars)for(var order:ReelVerification.orders()){
            var game=game(internal);var s=seat(50,7);s=action(game,s,PacketType.SPACE_ACTION,0);s=action(game,s,PacketType.SPACE_ACTION,0);
            long previous=0;
            for(var reel:order){
                int stop=target.stops().stop(reel),turn=1;long time;
                do{time=Math.round((.5+(21*turn++-3.15-stop-.5)/18)*1e9);}while(time<500_000_000||time<=previous);
                var transition=game.plan(s,PacketType.valueOf("STOP_"+reel.name()),s.sequence()+1,1,NOW+s.sequence(),time,0);
                s=store.commit(transition);var wire=game.committed(transition,time);previous=time;
                assertEquals(stop,s.number("display_"+reel.name().toLowerCase(Locale.ROOT)+"_stop"));
                for(var packet:wire){assertNotEquals(PacketType.BONUS_START,packet.packetType());assertNotEquals(PacketType.PAYOUT,packet.packetType());assertFalse(packet.payload().toString().contains("BONUS_PENDING_"));}
            }
            assertEquals("BONUS_PENDING_"+internal.name(),s.state().name());assertEquals("BONUS_PENDING",s.publicState().get("gameState").getAsString());
            assertEquals(1,s.number("lamp_on"));assertEquals(0,s.number("pay_display"));assertEquals(0,s.number("bonus_payout_count"));assertEquals(47,s.number("credit"));assertEquals(7,s.number("held_medals"));
            assertEquals(-3,scalar("SELECT today_difference FROM machine_period_stats WHERE machine_id=?",s.machine()));
        }
    }
}
