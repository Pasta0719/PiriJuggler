package jp.pirijuggler.paper.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public final class TaskExecutors implements AutoCloseable {
    private final MainThread mainThread;
    private final ExecutorService database = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("piri-db-", 1).factory());
    private final ExecutorService simulator = Executors.newFixedThreadPool(1, Thread.ofPlatform().name("piri-simulator-", 1).factory());

    public TaskExecutors(MainThread mainThread) { this.mainThread = mainThread; }

    /** Ordered DB drain for shutdown; completion needs no main-thread callback. */
    public <T> java.util.concurrent.Future<T> databaseBarrier(Callable<T> operation) {
        return database.submit(operation);
    }

    /** Completion (including failure) is delivered on the main thread. */
    public <T> CompletableFuture<Void> database(Callable<T> operation, BiConsumer<T, Throwable> completion) {
        return submit(database, operation, completion);
    }

    public <T> CompletableFuture<Void> simulator(Callable<T> operation, BiConsumer<T, Throwable> completion) {
        return submit(simulator, operation, completion);
    }

    private <T> CompletableFuture<Void> submit(ExecutorService executor, Callable<T> operation, BiConsumer<T, Throwable> completion) {
        CompletableFuture<Void> delivered = new CompletableFuture<>();
        CompletableFuture<T> work;
        try {
            work = CompletableFuture.supplyAsync(() -> {
                try { return operation.call(); } catch (Exception exception) { throw new CompletionException(exception); }
            }, executor);
        } catch (RuntimeException exception) {
            work = CompletableFuture.failedFuture(exception);
        }
        work.whenComplete((result, error) -> {
            try {
                mainThread.execute(() -> {
                    try {
                        mainThread.requireMainThread();
                        completion.accept(result, error instanceof CompletionException ? error.getCause() : error);
                        delivered.complete(null);
                    } catch (Throwable exception) { delivered.completeExceptionally(exception); }
                });
            } catch (RuntimeException exception) { delivered.completeExceptionally(exception); }
        });
        return delivered;
    }

    @Override public void close() {
        database.shutdown();
        simulator.shutdown();
        try {
            if (!database.awaitTermination(5, TimeUnit.SECONDS)) database.shutdownNow();
            if (!simulator.awaitTermination(5, TimeUnit.SECONDS)) simulator.shutdownNow();
        } catch (InterruptedException exception) {
            database.shutdownNow();
            simulator.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
