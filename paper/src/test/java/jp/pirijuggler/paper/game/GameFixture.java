package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.config.ConfigValidation;
import jp.pirijuggler.paper.database.*;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.reel.*;
import jp.pirijuggler.paper.session.Session;
import jp.pirijuggler.paper.threading.MainThread;
import jp.pirijuggler.common.protocol.PacketType;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

abstract class GameFixture {
    @TempDir Path directory;
    PiriDatabase db;GameStore store;Map<String,Object> config;
    final Thread owner=Thread.currentThread();
    final MainThread main=new MainThread(){public void execute(Runnable r){r.run();}public boolean isMainThread(){return Thread.currentThread()==owner;}};
    static final StopSolver SOLVER=new StopSolver(new StopCatalogue());
    static final long NOW=1_700_000_000_000L;
    @BeforeEach void open() throws Exception {
        config=defaults();db=new PiriDatabase(directory.resolve("piri.db"));db.open(1,NOW,config,new SplittableRandom(1),ignored->{});store=new GameStore(db);
    }
    @AfterEach void close() throws Exception{db.close();}
    static Map<String,Object> defaults() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("piri.specRoot"),"paper/src/main/resources/config.yml"))){return ConfigValidation.load(reader).values();}
    }
    static RoleWeights forced(InternalRole role) {
        var rows=new LinkedHashMap<String,Object>();
        for(int s=1;s<=6;s++){var row=new LinkedHashMap<String,Object>();for(var r:InternalRole.values())row.put(r.name().toLowerCase(Locale.ROOT),r==role?1_000_000_000:0);rows.put(Integer.toString(s),row);}
        return new RoleWeights(Map.of("probabilities",Map.of("settings",rows)));
    }
    NormalGame game(InternalRole role){return new NormalGame(forced(role),new RandomStreams(1),SOLVER,main);}
    Session seat(int credit,long held) throws Exception {
        int id=db.create(new Machine.Location(UUID.randomUUID(),"world",0,64,0,"NORTH"),NOW);UUID player=UUID.randomUUID();db.seat(player,id,NOW);
        db.sql("UPDATE player_sessions SET credit=?,held_medals=? WHERE player_uuid=?",credit,held,player.toString());return db.state().session(player);
    }
    Session action(NormalGame game,Session s,PacketType type,long nano) throws Exception {
        var t=game.plan(s,type,s.sequence()+1,1,NOW+s.sequence()+1,nano,0);Session after=store.commit(t);game.committed(t,nano);return after;
    }
    Session spin(NormalGame game,Session s,long nano) throws Exception {
        if(s.state()==Session.GameState.SEATED_READY)s=action(game,s,PacketType.SPACE_ACTION,nano);
        s=action(game,s,PacketType.SPACE_ACTION,nano);
        for(var type:List.of(PacketType.STOP_LEFT,PacketType.STOP_CENTER,PacketType.STOP_RIGHT))s=action(game,s,type,nano+1_000_000_000);
        return s;
    }
    long scalar(String sql,Object...args) throws Exception{return ((Number)db.rows(sql,args).getFirst().values().iterator().next()).longValue();}
}
