package awscat;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

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

  // out.txt,append=true
  class SystemOutOptions {
    boolean append;
    public String toString() {
      return new Gson().toJson(this);
    }
  }

  private String file;
  private SystemOutOptions options;

  public String toString() {
    return MoreObjects.toStringHelper(this).add("file", file).add("options", options).toString();
  }

  @Override
  public String help() {
      return "<filename>[,append]";
  }

  @Override
  public boolean canActivate(String arg) {
    file = Args.base(arg);
    options = Args.options(arg, SystemOutOptions.class);
    return true;
  }

  @Override
  public Supplier<OutputPlugin> activate(String arg) throws Exception {
    PrintStream out = "-".equals(file) ? Systems.stdout : new PrintStream(new BufferedOutputStream(new FileOutputStream(file, options.append)));
    return ()->new SystemOutPlugin(out);
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}
