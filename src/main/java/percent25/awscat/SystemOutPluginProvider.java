package percent25.awscat;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import helpers.LogHelper;

class SystemOutPlugin implements OutputPlugin {

  private final PrintStream out;

  public SystemOutPlugin(PrintStream out) {
    debug("ctor");
    this.out = out;
  }

  @Override
  public ListenableFuture<?> write(JsonElement jsonElement) {
    out.println(jsonElement);
    return Futures.immediateVoidFuture();
  }

  @Override
  public ListenableFuture<?> flush() {
    out.flush();
    return Futures.immediateVoidFuture();
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}

// @Service
public class SystemOutPluginProvider implements OutputPluginProvider {

  @VisibleForTesting
  public static PrintStream stdout = System.out;

  // out.txt,append=true
  class SystemOutOptions {
    boolean append;
    public String toString() {
      return new Gson().toJson(this);
    }
  }

  private String filename;
  private SystemOutOptions options;

  public String toString() {
    return MoreObjects.toStringHelper(this).add("filename", filename).add("options", options).toString();
  }

  @Override
  public String help() {
      return "<filename>[,append]";
  }

  @Override
  public boolean canActivate(String address) {
    filename = Addresses.base(address);
    options = Addresses.options(address, SystemOutOptions.class);
    return true;
  }

  @Override
  public Supplier<OutputPlugin> activate(String address) throws Exception {
    PrintStream out = "-".equals(filename) ? stdout : new PrintStream(new BufferedOutputStream(new FileOutputStream(filename, options.append)));
    return ()->new SystemOutPlugin(out);
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}
