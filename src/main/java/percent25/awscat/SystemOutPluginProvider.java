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
public class SystemOutPluginProvider extends AbstractPluginProvider implements OutputPluginProvider {

  @VisibleForTesting
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?
  public static PrintStream stdout = System.out;
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?
  //###TODO use OutputCaptureExtension?

  // out.txt,append=true
  class SystemOutOptions {
    public boolean append;
    public String toString() {
      return new Gson().toJson(this);
    }
  }

  public SystemOutPluginProvider() {
    super("<filename>", SystemOutOptions.class);
  }

  @Override
  public boolean canActivate(String address) {
    return true;
  }

  @Override
  public Supplier<OutputPlugin> activate(String address) throws Exception {
    var filename = Addresses.base(address);
    var options = Addresses.options(address, SystemOutOptions.class);
    var out = "-".equals(filename) ? stdout : new PrintStream(new BufferedOutputStream(new FileOutputStream(filename, options.append))); //###TODO leaked resource
    return ()->new SystemOutPlugin(out);
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}
