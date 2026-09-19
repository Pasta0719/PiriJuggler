package jp.pirijuggler.paper.game;

import jp.pirijuggler.paper.machine.MachineType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Registry that keeps machine-family selection out of MachineService. */
public final class GameEngines {
    private final Map<MachineType, GameEngine> engines = new EnumMap<>(MachineType.class);

    public GameEngines register(MachineType type, GameEngine engine) {
        engines.put(Objects.requireNonNull(type), Objects.requireNonNull(engine));
        return this;
    }

    public GameEngine require(MachineType type) {
        GameEngine engine = engines.get(Objects.requireNonNull(type));
        if (engine == null) throw new IllegalStateException("No game engine registered for " + type);
        return engine;
    }
}
