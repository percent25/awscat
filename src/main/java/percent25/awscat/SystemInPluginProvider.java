package percent25.awscat;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

import com.google.common.annotations.*;
import com.google.common.base.*;
import com.google.common.util.concurrent.*;
import com.google.gson.*;

import helpers.*;

class SystemInPlugin implements InputPlugin {

  @VisibleForTesting
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?
  public static InputStream stdin = System.in;
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?

  private final String filename;
  private final boolean cycle;

  private Function<Iterable<JsonElement>, ListenableFuture<?>> listener;

  //###TODO hard to cycle stdin !!
  //###TODO hard to cycle stdin !!
  //###TODO hard to cycle stdin !!
  public SystemInPlugin(String filename, boolean cycle) {
    //###TODO hard to cycle stdin !!
    //###TODO hard to cycle stdin !!
    //###TODO hard to cycle stdin !!
    debug("ctor", filename, cycle);
    this.filename = filename;
    this.cycle = cycle;
  }

  public String toString() {
    return MoreObjects.toStringHelper(this) //
        .add("filename", filename).add("cycle", cycle).toString();
  }

  @Override
  public void setListener(Function<Iterable<JsonElement>, ListenableFuture<?>> listener) {
    debug("setListener", listener);
    this.listener = listener;
  }

  private boolean running;

  @Override
  public ListenableFuture<?> run(int mtuHint) throws Exception {
    debug("read", "mtuHint", mtuHint);
    running = true;
    return new FutureRunner() {
      final int effectiveMtu = mtuHint > 0 ? mtuHint : 1000;
      final AtomicReference<List<JsonElement>> partition = new AtomicReference<>(new ArrayList<>());
      {
        do {
          final BufferedReader br = new BufferedReader(new InputStreamReader("-".equals(filename) ? stdin : new FileInputStream(filename)));
          try {
            JsonStreamParser parser = new JsonStreamParser(br);
            while (parser.hasNext()) {
              partition.get().add(parser.next());
              if (!parser.hasNext() || partition.get().size() == effectiveMtu) {
                run(() -> {
                  return listener.apply(partition.getAndSet(new ArrayList<>()));
                });
              }
              if (!running)
                break;
            }
          } finally {
            br.close();
          }
          if (!running)
            break;
        } while (cycle);
      }
    }.get();
  }

  @Override
  public void closeNonBlocking() {
    running = false;
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }
}

// @Service
public class SystemInPluginProvider extends AbstractPluginProvider implements InputPluginProvider {

  // e.g., in.json,cycle
  class SystemInOptions {
    public boolean cycle;
    public String toString() {
      return new Gson().toJson(this);
    }
  }

  public SystemInPluginProvider() {
      super("<filename>", SystemInOptions.class);
  }

  @Override
  public boolean canActivate(String address) {
    return true;
  }

  @Override
  public InputPlugin activate(String address) throws Exception {
    String filename = Addresses.base(address);
    SystemInOptions options = Addresses.options(address, SystemInOptions.class);
    return new SystemInPlugin(filename, options.cycle);
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}
