package jp.pirijuggler.paper.game.god;

import java.util.Arrays;

/**
 * Pure long-run expected-value solver for the GOD-family design.
 *
 * This intentionally models economics before presentation/gameplay code.
 * Rows are source states, columns are destination states. Each row must sum to 1.
 * payoutPercent is computed from the stationary state mix:
 *   100 * E[payout medals per game] / E[bet medals per game].
 */
public final class GodExpectedValueModel {
    public record Result(double[] stationary, double betPerGame, double payoutPerGame, double payoutPercent) {}

    private GodExpectedValueModel() {}

    public static Result solve(double[][] transition, double[] bet, double[] payout) {
        int n=transition.length;
        if(n==0||bet.length!=n||payout.length!=n)throw new IllegalArgumentException("shape");
        for(double[] row:transition)if(row.length!=n)throw new IllegalArgumentException("shape");
        for(int i=0;i<n;i++){
            double sum=0;
            for(double p:transition[i]){
                if(!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException("probability");
                sum+=p;
            }
            if(Math.abs(sum-1.0)>1e-9)throw new IllegalArgumentException("row sum");
            if(!Double.isFinite(bet[i])||bet[i]<0||!Double.isFinite(payout[i])||payout[i]<0)
                throw new IllegalArgumentException("reward");
        }

        double[] pi=new double[n];
        Arrays.fill(pi,1.0/n);
        double[] next=new double[n];
        for(int iter=0;iter<1_000_000;iter++){
            Arrays.fill(next,0);
            for(int i=0;i<n;i++)for(int j=0;j<n;j++)next[j]+=pi[i]*transition[i][j];
            double diff=0;
            for(int i=0;i<n;i++)diff=Math.max(diff,Math.abs(next[i]-pi[i]));
            double[] swap=pi;pi=next;next=swap;
            if(diff<1e-14)break;
            if(iter==999_999)throw new IllegalArgumentException("no convergence");
        }

        double in=0,out=0;
        for(int i=0;i<n;i++){in+=pi[i]*bet[i];out+=pi[i]*payout[i];}
        if(in<=0)throw new IllegalArgumentException("zero wager");
        return new Result(pi,in,out,out/in*100.0);
    }
}
