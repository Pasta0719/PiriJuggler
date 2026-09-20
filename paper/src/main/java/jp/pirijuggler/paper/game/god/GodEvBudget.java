package jp.pirijuggler.paper.game.god;

public record GodEvBudget(
        double expectedNetPerGod,
        double expectedNetPerNormalGgInitial,
        double expectedNetPerSgg,
        double godRatePerGame,
        double normalGgInitialRatePerGame,
        double sggRatePerGame
) {
    public GodEvBudget {
        nonNegative(expectedNetPerGod, "expectedNetPerGod");
        nonNegative(expectedNetPerNormalGgInitial, "expectedNetPerNormalGgInitial");
        nonNegative(expectedNetPerSgg, "expectedNetPerSgg");
        probability(godRatePerGame, "godRatePerGame");
        probability(normalGgInitialRatePerGame, "normalGgInitialRatePerGame");
        probability(sggRatePerGame, "sggRatePerGame");
    }

    public double godPerGame() { return expectedNetPerGod * godRatePerGame; }
    public double normalGgPerGame() { return expectedNetPerNormalGgInitial * normalGgInitialRatePerGame; }
    public double sggPerGame() { return expectedNetPerSgg * sggRatePerGame; }

    public double totalPositiveNetPerGame() {
        return godPerGame() + normalGgPerGame() + sggPerGame();
    }

    private static void nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name);
    }

    private static void probability(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(name);
    }
}
