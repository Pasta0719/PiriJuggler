package jp.pirijuggler.paper.game.god;

import java.util.ArrayList;
import java.util.List;

/** Comparative EV table for candidate visible bell-chain thresholds. */
public final class GodBellChainComparison {
    public record Row(
            int threshold,
            double expectedQualifyingBellsPerSet,
            double maxExpectedStocksPerSet,
            double maxNetMedalsPerSet,
            double awardChanceForBudget
    ) {}

    private GodBellChainComparison() {}

    public static List<Row> compare(
            int games,
            double bellProbability,
            int minThreshold,
            int maxThreshold,
            double targetNetMedalsPerSet,
            double netMedalsPerStock
    ) {
        if (minThreshold < 1 || maxThreshold < minThreshold)
            throw new IllegalArgumentException("threshold range");

        List<Row> rows=new ArrayList<>();
        for(int threshold=minThreshold;threshold<=maxThreshold;threshold++){
            var maximum=GodBellChainModel.evaluate(
                    games,bellProbability,threshold,1.0,netMedalsPerStock);
            double chance;
            if(targetNetMedalsPerSet==0){
                chance=0.0;
            }else if(maximum.expectedNetMedals()+1e-12<targetNetMedalsPerSet){
                chance=Double.NaN;
            }else{
                chance=GodBellChainModel.solveAwardChance(
                        games,bellProbability,threshold,targetNetMedalsPerSet,netMedalsPerStock);
            }
            rows.add(new Row(
                    threshold,
                    maximum.expectedQualifyingBells(),
                    maximum.expectedVStocks(),
                    maximum.expectedNetMedals(),
                    chance));
        }
        return List.copyOf(rows);
    }
}
