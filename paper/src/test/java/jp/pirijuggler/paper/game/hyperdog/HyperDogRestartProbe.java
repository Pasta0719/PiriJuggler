package jp.pirijuggler.paper.game.hyperdog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fresh-JVM disk snapshot round-trip used by Phase03 acceptance.
 * It runs in an independent process, not just a new object in the JUnit JVM.
 * It deliberately never boots or modifies an existing production machine.
 */
public final class HyperDogRestartProbe {
    private HyperDogRestartProbe(){}

    public static void advance(HyperDogNormalGame game,int games) {
        long target=Math.addExact(game.games(),games);
        while(game.games()<target){
            if(game.waitingForAt())game.resumeAfterAt();
            if(game.pending()!=null){game.finish();continue;}
            if(game.canBet()){game.bet();continue;}
            if(game.canLever()){game.lever();game.finish();continue;}
            throw new IllegalStateException("normal stage unable to advance");
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("snapshot path and result path required");
        JsonObject json=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        HyperDogNormalGame game=HyperDogNormalGame.restore(json);
        if(!game.snapshot().equals(json))throw new IllegalStateException("restart snapshot differs");
        advance(game,25);
        Files.writeString(Path.of(args[1]),game.snapshot().toString());
        System.out.println("HYPER_DOG_PHASE03_FRESH_JVM_RESTART=PASS");
    }
}
