package main.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;

import main.InputPlugin;
import main.InputPluginProvider;
import main.helpers.LogHelper;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;

class SystemInInputPlugin implements InputPlugin {

  private final InputStream in;
  private Function<Iterable<JsonElement>, ListenableFuture<?>> listener;

  //###TODO
  //###TODO
  //###TODO
  private final int concurrency = Runtime.getRuntime().availableProcessors();
  private final Semaphore sem = new Semaphore(concurrency); // backpressure
  //###TODO
  //###TODO
  //###TODO
  private final Object lock = new Object();

  public SystemInInputPlugin(InputStream in) {
    this.in = in;
  }

  @Override
  public void setListener(Function<Iterable<JsonElement>, ListenableFuture<?>> listener) {
    this.listener = listener;
  }

  @Override
  public ListenableFuture<?> read() throws Exception {
    final BufferedReader br = new BufferedReader(new InputStreamReader(in));
    try {
      List<JsonElement> partition = new ArrayList<>();
      JsonStreamParser parser = new JsonStreamParser(br);
      while (parser.hasNext()) {

        partition.add(parser.next());

        if (!parser.hasNext() || partition.size() == 1000) { // mtu
          try {
            // STEP 1
            sem.acquire();
            // STEP 2
            listener.apply(partition).addListener(()->{
              sem.release();
              synchronized (lock) {
                lock.notify();
              }
            }, MoreExecutors.directExecutor());
          } catch (Exception e) {
            log(e);
          }
          partition = new ArrayList<>();
        }
      }
      synchronized (lock) {
        while (sem.availablePermits() < concurrency) {
          lock.wait();
        }
      }
    } finally {
      br.close();
    }
    return Futures.immediateVoidFuture();
  }

  private void log(Object... args) {
    new LogHelper(this).log(args);
  }
}

@Service
public class SystemInInputPluginProvider implements InputPluginProvider {

  @Override
  public InputPlugin get(String arg, ApplicationArguments args) throws Exception {
    if ("-".equals(arg))
      return new SystemInInputPlugin(System.in);
    File f = new File(arg);
    if (f.exists())
      return new SystemInInputPlugin(new FileInputStream(f));
    return null;
  }

}