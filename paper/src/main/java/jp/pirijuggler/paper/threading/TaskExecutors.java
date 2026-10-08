package jp.pirijuggler.paper.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public final class TaskExecutors implements AutoCloseable {
    private static final int DB_PRIORITY_GAMEPLAY=0;
    private static final int DB_PRIORITY_NORMAL=10;
    private static final AtomicLong DB_SEQUENCE=new AtomicLong();

    private static final class DbTask<T> implements Runnable,Comparable<DbTask<?>> {
        private final int priority;
        private final long sequence=DB_SEQUENCE.getAndIncrement();
        private final Callable<T> operation;
        private final CompletableFuture<T> future=new CompletableFuture<>();
        private DbTask(int priority,Callable<T> operation){this.priority=priority;this.operation=operation;}
        @Override public void run(){
            try{future.complete(operation.call());}
            catch(Throwable error){future.completeExceptionally(error);}
        }
        @Override public int compareTo(DbTask<?> other){
            int byPriority=Integer.compare(priority,other.priority);
            return byPriority!=0?byPriority:Long.compare(sequence,other.sequence);
        }
    }

    private final MainThread mainThread;
    private final ThreadPoolExecutor database = new ThreadPoolExecutor(
            1,1,0L,TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            Thread.ofPlatform().name("piri-db-",1).factory());
    private final ExecutorService readOnly = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("piri-read-", 1).factory());
    private final ExecutorService simulator = Executors.newFixedThreadPool(1, Thread.ofPlatform().name("piri-simulator-", 1).factory());

    public TaskExecutors(MainThread mainThread) { this.mainThread = mainThread; }

    /** Ordered DB drain for shutdown; completion needs no main-thread callback. */
    public <T> Future<T> databaseBarrier(Callable<T> operation) {
        DbTask<T> task=new DbTask<>(DB_PRIORITY_NORMAL,operation);
        try{database.execute(task);}catch(RuntimeException error){task.future.completeExceptionally(error);}
        return task.future;
    }

    /** Normal durable DB work. Gameplay uses gameplayDatabase and jumps ahead of queued normal work. */
    public <T> CompletableFuture<Void> database(Callable<T> operation, BiConsumer<T, Throwable> completion) {
        return submitDatabase(DB_PRIORITY_NORMAL,operation,completion);
    }

    /** Highest-priority durable gameplay write. FIFO is preserved between gameplay operations. */
    public <T> CompletableFuture<Void> gameplayDatabase(Callable<T> operation, BiConsumer<T, Throwable> completion) {
        return submitDatabase(DB_PRIORITY_GAMEPLAY,operation,completion);
    }

    /** Read-only work that opens its own SQLite connection and must never queue behind gameplay writes. */
    public <T> CompletableFuture<Void> readOnly(Callable<T> operation, BiConsumer<T, Throwable> completion) {
        return submit(readOnly, operation, completion);
    }

    public <T> CompletableFuture<Void> simulator(Callable<T> operation, BiConsumer<T, Throwable> completion) {
        return submit(simulator, operation, completion);
    }

    private <T> CompletableFuture<Void> submitDatabase(int priority,Callable<T> operation,BiConsumer<T,Throwable> completion){
        DbTask<T> task=new DbTask<>(priority,operation);
        try{database.execute(task);}
        catch(RuntimeException error){task.future.completeExceptionally(error);}
        return deliver(task.future,completion);
    }

    private <T> CompletableFuture<Void> submit(ExecutorService executor, Callable<T> operation, BiConsumer<T, Throwable> completion) {
        CompletableFuture<T> work;
        try {
            work = CompletableFuture.supplyAsync(() -> {
                try { return operation.call(); } catch (Exception exception) { throw new CompletionException(exception); }
            }, executor);
        } catch (RuntimeException exception) {
            work = CompletableFuture.failedFuture(exception);
        }
        return deliver(work,completion);
    }

    private <T> CompletableFuture<Void> deliver(CompletableFuture<T> work,BiConsumer<T,Throwable> completion){
        CompletableFuture<Void> delivered=new CompletableFuture<>();
        work.whenComplete((result,error)->{
            Throwable cause=error instanceof CompletionException&&error.getCause()!=null?error.getCause():error;
            try{
                mainThread.execute(()->{
                    try{
                        mainThread.requireMainThread();
                        completion.accept(result,cause);
                        delivered.complete(null);
                    }catch(Throwable callbackError){delivered.completeExceptionally(callbackError);}
                });
            }catch(RuntimeException dispatchError){delivered.completeExceptionally(dispatchError);}
        });
        return delivered;
    }

    @Override public void close() {
        database.shutdown();
        readOnly.shutdown();
        simulator.shutdown();
        try {
            if (!database.awaitTermination(5, TimeUnit.SECONDS)) database.shutdownNow();
            if (!readOnly.awaitTermination(5, TimeUnit.SECONDS)) readOnly.shutdownNow();
            if (!simulator.awaitTermination(5, TimeUnit.SECONDS)) simulator.shutdownNow();
        } catch (InterruptedException exception) {
            database.shutdownNow();
            readOnly.shutdownNow();
            simulator.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
