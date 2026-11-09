package jp.pirijuggler.common.reel;

import java.util.*;

/** Executable exhaustive logic acceptance; does not claim Minecraft Paper/Fabric E2E. */
public final class HyperDogReelAcceptance {
    private static final Reel[][] ORDERS = {
        {Reel.LEFT,Reel.CENTER,Reel.RIGHT}, {Reel.LEFT,Reel.RIGHT,Reel.CENTER},
        {Reel.CENTER,Reel.LEFT,Reel.RIGHT}, {Reel.CENTER,Reel.RIGHT,Reel.LEFT},
        {Reel.RIGHT,Reel.LEFT,Reel.CENTER}, {Reel.RIGHT,Reel.CENTER,Reel.LEFT}
    };
    private static final int[] COUNTS = {5716,706,780,119,195,99,1,1,5,1,1,18,1};
    public static void main(String[] args) {run();}
    public static void run() {
        var solver=new HyperDogReelControl();long histories=0;
        for (var role:HyperDogReelControl.Role.values()) {
            if (HyperDogReelControl.legalTriplets(role)!=COUNTS[role.ordinal()])
                throw new AssertionError("count "+role+" expected "+COUNTS[role.ordinal()]+" got "+HyperDogReelControl.legalTriplets(role));
            for (Reel[] order:ORDERS)for(int a=0;a<21;a++) {
                var first=stop(solver,role,SkillStopHistory.empty(),order[0],a);
                for(int b=0;b<21;b++) {
                    var second=stop(solver,role,first,order[1],b);
                    for(int c=0;c<21;c++) {
                        var third=stop(solver,role,second,order[2],c);
                        if(!HyperDogReelControl.legalFinal(role,third.stops()))
                            throw new AssertionError("wrong result "+role+" "+Arrays.toString(third.stops()));
                        histories++;
                    }
                }
            }
            System.out.println("HYPER_DOG "+role+" acceptance PASS");
        }
        if(histories!=HyperDogReelControl.Role.values().length*6L*21*21*21)
            throw new AssertionError("histories "+histories);
        if(HyperDogReelControl.payout(HyperDogReelControl.Role.GRAPE,true)!=30 ||
            HyperDogReelControl.payout(HyperDogReelControl.Role.GRAPE,false)!=3 ||
            HyperDogReelControl.payout(HyperDogReelControl.Role.BAR,true)!=15)
            throw new AssertionError("payout contract");
        System.out.println("HYPER_DOG COMPLETE_STOP_INPUT_HISTORIES "+histories+" PASS");
    }
    private static SkillStopHistory stop(HyperDogReelControl solver,HyperDogReelControl.Role role,SkillStopHistory history,Reel reel,int pressed) {
        var choice=solver.choose(role,history,reel,pressed);
        if(choice.slip()<0 || choice.slip()>HyperDogReelControl.maxSlip(role))
            throw new AssertionError("slip "+role+" "+choice.slip());
        if(choice.slip()!=ReelMotion.slip(choice.stopIndex(),pressed))throw new AssertionError("reel direction");
        return history.append(reel,pressed,choice.stopIndex());
    }
    private HyperDogReelAcceptance(){}
}
