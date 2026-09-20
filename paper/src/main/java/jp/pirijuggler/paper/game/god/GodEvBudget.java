package jp.pirijuggler.paper.game.god;

public record GodEvBudget(
        double expectedNetPerGod,
        double expectedNetPerNormalGgInitial,
        double expectedNetPerSgg,
        double expectedNetPerBellVStock,
        double godRatePerGame,
        double normalGgInitialRatePerGame,
        double sggRatePerGame,
        double bellVStockRatePerGame
) {
    public GodEvBudget {
        nonNegative(expectedNetPerGod, "expectedNetPerGod");
        nonNegative(expectedNetPerNormalGgInitial, "expectedNetPerNormalGgInitial");
        nonNegative(expectedNetPerSgg, "expectedNetPerSgg");
        nonNegative(expectedNetPerBellVStock, "expectedNetPerBellVStock");
        probability(godRatePerGame, "godRatePerGame");
        probability(normalGgInitialRatePerGame, "normalGgInitialRatePerGame");
        probability(sggRatePerGame, "sggRatePerGame");
        probability(bellVStockRatePerGame, "bellVStockRatePerGame");
    }

    public double godPerGame() { return expectedNetPerGod * godRatePerGame; }
    public double normalGgPerGame() { return expectedNetPerNormalGgInitial * normalGgInitialRatePerGame; }
    public double sggPerGame() { return expectedNetPerSgg * sggRatePerGame; }
    public double bellVPerGame() { return expectedNetPerBellVStock * bellVStockRatePerGame; }

    public double totalPositiveNetPerGame() {
        return godPerGame() + normalGgPerGame() + sggPerGame() + bellVPerGame();
    }

    private static void nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name);
    }

    private static void probability(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(name);
    }
}
