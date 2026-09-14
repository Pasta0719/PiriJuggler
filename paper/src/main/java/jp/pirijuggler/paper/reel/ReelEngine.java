package jp.pirijuggler.paper.reel;
public final class ReelEngine {
    private final StopSolver solver;private final ReelVerification.Report verification;
    public ReelEngine(){solver=new StopSolver(new StopCatalogue());verification=ReelVerification.verify(solver);}
    public StopSolver solver(){return solver;}public ReelVerification.Report verification(){return verification;}
}
