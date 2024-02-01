package helpers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import com.google.common.util.concurrent.*;
import com.spotify.futures.*;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;

/**
 * Opinionated robust facade/runner for listenablefuture(s).
 */
public class FutureRunner {

  private class VoidFuture extends AbstractFuture<Void> {
    public boolean setVoid() {
      return super.set(null);
    }
    public boolean setException(Throwable throwable) {
      return super.setException(throwable);
    }
  }

  private final VoidFuture vf = new VoidFuture();
  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicReference<Exception> firstException = new AtomicReference<>();

  private static final Timer timer = new HashedWheelTimer();

  public FutureRunner() {
    doTry();
  }

  public ListenableFuture<?> get() {
    return doFinally();
  }

  /**
   * run
   *
   * @param <T>
   * @param request
   */
  protected <T> void run(AsyncCallable<T> request) {
    run(request, unused -> {});
  }

  /**
   * run
   *
   * @param <T>
   * @param request
   * @param response
   */
  protected <T> void run(AsyncCallable<T> request, Consumer<T> response) {
    run(request, response, e -> { e.printStackTrace(); throw new RuntimeException(e); });
  }

  /**
   * run
   *
   * @param <T>
   * @param request
   * @param perRequestResponseFinally
   */
  protected <T> void run(AsyncCallable<T> request, Runnable perRequestResponseFinally) {
    run(request, unused -> {}, e->{ e.printStackTrace(); throw new RuntimeException(e); }, perRequestResponseFinally);
  }

  /**
   * run
   *
   * @param <T>
   * @param request
   * @param response
   * @param perRequestResponseCatch
   */
  protected <T> void run(AsyncCallable<T> request, Consumer<T> response, Consumer<Exception> perRequestResponseCatch) {
    run(request, response, perRequestResponseCatch, () -> {});
  }

  /**
   * run
   *
   * @param <T>
   * @param request
   * @param response
   * @param perRequestResponseCatch
   * @param perRequestResponseFinally
   */
  protected <T> void run(AsyncCallable<T> request, Consumer<T> response, Consumer<Exception> perRequestResponseCatch, Runnable perRequestResponseFinally) {
    try {
      doTry();
      ListenableFuture<T> lf = request.call(); // throws
      lf.addListener(() -> {
        try {
          response.accept(lf.get()); // throws
        } catch (Exception e) {
          try {
            perRequestResponseCatch.accept(e); // throws
          } catch (Exception e1) {
            doCatch(e1);
          }
        } finally {
          try {
            perRequestResponseFinally.run(); // throws
          } catch (Exception e2) {
            doCatch(e2);
          } finally {
            doFinally();
          }
        }
      }, MoreExecutors.directExecutor());
    } catch (Exception e) {
      try {
        try {
          perRequestResponseCatch.accept(e); // throws
        } catch (Exception e1) {
          doCatch(e1);
        }
      } finally {
        try {
          perRequestResponseFinally.run(); // throws
        } catch (Exception e2) {
          doCatch(e2);
        } finally {
          doFinally();
        }
      }
    }
  }

  private void doTry() {
    inFlight.incrementAndGet();
  }

  private void doCatch(Exception e) {
    firstException.compareAndSet(null, e);
  }

  private ListenableFuture<?> doFinally() {
    if (inFlight.decrementAndGet() == 0) {
      if (firstException.get() == null)
        vf.setVoid();
      else
        vf.setException(firstException.get());
    }
    return vf;
  }

  // ----------------------------------------------------------------------

  // convenience
  // Thread.sleep(millis);
  protected ListenableFuture<?> sleep(long millis) {
    // ### TODO hook up future.cancel to timer handle cancel
    // ### TODO hook up future.cancel to timer handle cancel
    // ### TODO hook up future.cancel to timer handle cancel
    return new AbstractFuture<Void>() {
      Timeout timeout;
      {
        timeout = timer.newTimeout(timer -> {
          set(null);
        }, millis, TimeUnit.MILLISECONDS);
      }
    };
  }

  // convenience
  protected <T> ListenableFuture<T> lf(CompletableFuture<T> cf) {
    return CompletableFuturesExtra.toListenableFuture(cf);
  }

}
