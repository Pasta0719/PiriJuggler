package jp.pirijuggler.paper.threading;

import jp.pirijuggler.common.protocol.ErrorCode;
import jp.pirijuggler.paper.action.ActionGate;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TaskExecutorsTest {
    private static final class TestMain implements MainThread {
        private final Thread owner = Thread.currentThread();
        private final BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        public boolean isMainThread() { return Thread.currentThread() == owner; }
        public void execute(Runnable runnable) { callbacks.add(runnable); }
        void next() throws InterruptedException {
            Runnable callback = callbacks.poll(10, TimeUnit.SECONDS);
            assertNotNull(callback, "worker did not deliver completion");
            requireMainThread();
            callback.run();
        }
    }

    @Test void databaseIsSerialSimulatorIsSeparateAndCallbacksRunOnMainThread() throws Exception {
        TestMain main = new TestMain();
        try (TaskExecutors executors = new TaskExecutors(main)) {
            var db1 = executors.database(() -> Thread.currentThread(), (thread, error) -> {
                assertTrue(main.isMainThread()); assertNull(error); assertTrue(thread.getName().startsWith("piri-db-"));
            });
            main.next(); db1.join();
            List<Thread> threads = new ArrayList<>();
            var db2 = executors.database(() -> { threads.add(Thread.currentThread()); return 1; }, (value, error) -> assertNull(error));
            var db3 = executors.database(() -> { threads.add(Thread.currentThread()); return 2; }, (value, error) -> assertNull(error));
            main.next(); main.next(); db2.join(); db3.join();
            assertEquals(2, threads.size());
            assertSame(threads.getFirst(), threads.getLast());
            CountDownLatch dbStarted = new CountDownLatch(1);
            CountDownLatch releaseDb = new CountDownLatch(1);
            var blockingDb = executors.database(() -> {
                dbStarted.countDown(); assertTrue(releaseDb.await(10, TimeUnit.SECONDS)); return 1;
            }, (value, error) -> assertNull(error));
            try {
                assertTrue(dbStarted.await(10, TimeUnit.SECONDS));
                var simulation = executors.simulator(() -> Thread.currentThread(), (thread, error) -> {
                    assertTrue(main.isMainThread()); assertNull(error);
                    assertTrue(thread.getName().startsWith("piri-simulator-"));
                    assertNotSame(threads.getFirst(), thread);
                });
                main.next(); simulation.join();
                assertFalse(blockingDb.isDone());
            } finally { releaseDb.countDown(); }
            main.next(); blockingDb.join();
        }
    }

    @Test void busyLastsUntilMainThreadCallbackEvenWhenDatabaseFails() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            TestMain main = new TestMain();
            ActionGate gate = new ActionGate(main, 0);
            try (TaskExecutors executors = new TaskExecutors(main)) {
                ActionGate.Lease lease = gate.beginBusy();
                var work = executors.database(() -> {
                    assertFalse(main.isMainThread());
                    if (fail) throw new IOException("test failure");
                    return 7;
                }, (value, error) -> {
                    try {
                        assertTrue(main.isMainThread()); assertTrue(gate.actionBusy());
                        if (fail) assertInstanceOf(IOException.class, error);
                        else { assertNull(error); assertEquals(7, value); }
                    } finally { lease.close(); }
                });
                assertEquals(ErrorCode.BUSY, gate.accept(1).error());
                main.next(); work.join();
                assertFalse(gate.actionBusy());
                assertEquals(0, gate.lastClientSequence());
            }
        }
    }

    @Test void shutdownRejectsNewWorkAndDeliversFailureOnMainThread() throws Exception {
        TestMain main = new TestMain();
        TaskExecutors executors = new TaskExecutors(main);
        executors.close();
        var completion = executors.database(() -> { throw new AssertionError("must never run"); }, (value, error) -> {
            assertTrue(main.isMainThread()); assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, error);
        });
        main.next(); completion.join();
    }
}
