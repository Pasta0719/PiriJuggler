package jp.pirijuggler.paper.threading;

import java.util.concurrent.Executor;

public interface MainThread extends Executor {
    boolean isMainThread();

    default void requireMainThread() {
        if (!isMainThread()) throw new IllegalStateException("Game state must be accessed on the main thread");
    }
}
