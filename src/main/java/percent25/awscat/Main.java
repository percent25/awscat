package percent25.awscat;

import java.io.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

import javax.annotation.*;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.*;
import com.google.gson.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.beans.factory.config.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;

import helpers.*;

// https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle
@SpringBootApplication
public class Main implements ApplicationRunner {

  static {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
  }

  // public static void main(String... args) {
  //   try {
  //     List<InputPluginProvider> inputPluginProviders = Lists.newArrayList();
  //     List<OutputPluginProvider> outputPluginProviders = Lists.newArrayList();
  //     ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(true);
  //     for (BeanDefinition component : provider.findCandidateComponents("awscat")) {
  //       Class<?> plugin = Class.forName(component.getBeanClassName());
  //       if (InputPluginProvider.class.isAssignableFrom(plugin)) {
  //         inputPluginProviders.add(InputPluginProvider.class.cast(plugin.getDeclaredConstructor().newInstance()));
  //       }
  //       if (OutputPluginProvider.class.isAssignableFrom(plugin)) {
  //         outputPluginProviders.add(OutputPluginProvider.class.cast(plugin.getDeclaredConstructor().newInstance()));
  //       }
  //     }
  //     new Main("???", inputPluginProviders, outputPluginProviders).run(new DefaultApplicationArguments(args));
  //   } catch (Exception e) {
  //     throw new RuntimeException(e);
  //   }
  // }

  public static void main(String... args) {
    // System.err.println("main"+Arrays.asList(args));
    // args = new String[]{"dynamo:MyTable"};
    // args= new String[]{"dynamo:MyTableOnDemand,rcu=128","dynamo:MyTableOnDemand,delete=true,wcu=5"};
    SpringApplication.run(Main.class, args);
    // System.exit(SpringApplication.exit(SpringApplication.run(Main.class, "arn:aws:sqs:us-east-1:000000000000:MyQueue,endpoint=http://localhost:4566,limit=1")));
  }

  private final List<InputPluginProvider> inputPluginProviders = new ArrayList<>();
  private final List<OutputPluginProvider> outputPluginProviders = new ArrayList<>();

  AtomicLong in = new AtomicLong(); // pre-filter
  AtomicLong success = new AtomicLong(); // output plugin
  AtomicLong failure = new AtomicLong(); // output plugin

  private final LocalMeter rateOut = new LocalMeter();

  class Working {
    final Number in; // pre-filter
    final Number out;
    final Number err;
    String rateOut;
    Working(Number in, Number success, Number failure) {
      this.in = in;
      this.out = success;
      this.err = failure;
    }
    public String toString() {
      return getClass().getSimpleName()+new Gson().toJson(this);
    }
  }

  // lazy
  private String failuresFileName;
  private PrintStream failuresPrintStream;
  private final Supplier<PrintStream> failuresPrintStreamSupplier = Suppliers.memoize(()->{
    try {
      String now = CharMatcher.anyOf("1234567890").retainFrom(Instant.now().toString().substring(0, 20));
      String randomString = Hashing.sha256().hashInt(new SecureRandom().nextInt()).toString().substring(0, 7);
      //###TODO BUFFEREDOUTPUTSTREAM HERE??
      //###TODO BUFFEREDOUTPUTSTREAM HERE??
      //###TODO BUFFEREDOUTPUTSTREAM HERE??
      return failuresPrintStream = new PrintStream(failuresFileName = String.format("failures-%s-%s.json", now, randomString));
      //###TODO BUFFEREDOUTPUTSTREAM HERE??
      //###TODO BUFFEREDOUTPUTSTREAM HERE??
      //###TODO BUFFEREDOUTPUTSTREAM HERE??
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  });

  private final String projectVersion;

  /**
   * ctor
   */
  public Main(@Value("${project-version}") String projectVersion, List<InputPluginProvider> inputPluginProviders, List<OutputPluginProvider> outputPluginProviders) {
    debug("ctor");
    this.projectVersion = projectVersion;
    this.inputPluginProviders.addAll(inputPluginProviders);
    
    //###TODO NOT NEEDED
    //###TODO NOT NEEDED
    //###TODO NOT NEEDED
    this.inputPluginProviders.add(new SystemInPluginProvider()); // ensure last
    //###TODO NOT NEEDED
    //###TODO NOT NEEDED
    //###TODO NOT NEEDED

    this.outputPluginProviders.addAll(outputPluginProviders);

    //###TODO NOT NEEDED
    //###TODO NOT NEEDED
    //###TODO NOT NEEDED
    this.outputPluginProviders.add(new SystemOutPluginProvider()); // ensure last
    //###TODO NOT NEEDED
    //###TODO NOT NEEDED
    //###TODO NOT NEEDED
  }

  /**
   * run
   */
  @Override
  public void run(ApplicationArguments args) throws Exception {
    CommandOptions options = parseOptions(args, CommandOptions.class);

    stderr("awscat.jar", projectVersion, options);

    LogHelper.debug = options.debug || options.trace;
    LogHelper.trace = options.trace;

    boolean help = false;
    if (options.help)
    	help = true;
    if (options.version)
    	help = true;
    if (ImmutableSet.of().equals(Set.of(args.getNonOptionArgs())))
    	help = true;
    if (ImmutableSet.of("-h").equals(Set.of(args.getNonOptionArgs())))
    	help = true;
    if (ImmutableSet.of("-v").equals(Set.of(args.getNonOptionArgs())))
    	help = true;

    if (help) {

      final String indentString = "  ";

      stderr("Usage:");
      stderr(indentString, "awscat.jar [options] <source> [<target>]");
      
      stderr("options:");
      stderr(indentString, "--help");
      stderr(indentString, "--version");
      stderr(indentString, "--js");
      stderr(indentString, "--debug"); // this is spring boot's debug

      stderr("sources:");
      for (InputPluginProvider pluginProvider : inputPluginProviders) {
        stderr(indentString, pluginProvider.help());
      }
      stderr("targets:");
      for (OutputPluginProvider pluginProvider : outputPluginProviders) {
        stderr(indentString, pluginProvider.help());
      }

      return;
    }

    // input plugin
    String source = "-";
    if (args.getNonOptionArgs().size()>0)
      source = args.getNonOptionArgs().get(0);
    InputPluginProvider inputPluginProvider = resolveInputPlugin(source);
    stderr("inputPlugin", inputPluginProvider.name(), inputPluginProvider);
    InputPlugin inputPlugin = inputPluginProvider.activate(source);
    
    // output plugin
    String target = "-";
    if (args.getNonOptionArgs().size()>1)
      target = args.getNonOptionArgs().get(1);
    OutputPluginProvider outputPluginProvider = resolveOutputPlugin(target);
    stderr("outputPlugin", outputPluginProvider.name(), outputPluginProvider);
    Supplier<OutputPlugin> outputPluginSupplier = outputPluginProvider.activate(target);

    // ----------------------------------------------------------------------
    // main loop
    // ----------------------------------------------------------------------

    long effectiveLimit = options.limit > 0 ? options.limit : Long.MAX_VALUE;

    inputPlugin.setListener(jsonElements->{
      debug("listener", Iterables.size(jsonElements));
      return new FutureRunner() {
        Working work = new Working(in, success, failure);
        {
          run(()->{
            ExpressionsJs expressionsJs = new ExpressionsJs(); // throws
            OutputPlugin outputPlugin = outputPluginSupplier.get(); // throws
            for (JsonElement jsonElement : jsonElements) {



              //###TODO "in" is wrong here
              //###TODO "in" is wrong here
              //###TODO "in" is wrong here
              long preCount = in.getAndIncrement();
              if (preCount < effectiveLimit) {
                //###TODO "in" is wrong here
                //###TODO "in" is wrong here
                //###TODO "in" is wrong here



                run(() -> {
                  boolean filter = true;
                  expressionsJs.e(jsonElement); //###TODO .deepCopy
                  for (String js : options.js)
                    filter = filter && expressionsJs.eval(js);
                  if (filter) {
                    run(() -> {
                      return outputPlugin.write(expressionsJs.e());
                    }, result -> {
                      success.incrementAndGet();
                    }, e -> {
                      stderr(e);
                      failure.incrementAndGet();
                      failuresPrintStreamSupplier.get().println(jsonElement); // pre-transform
                    }, () -> {
                      rateOut.add(1);
                    });
                  }
                  return Futures.immediateVoidFuture();
                });
              }

              if (preCount + 1 == effectiveLimit) {
                stderr("CLOSING");
                inputPlugin.closeNonBlocking();
              }
            }
            return outputPlugin.flush();
          }, ()->{
            work.rateOut = rateOut.toString();
            stderr(work);
            //###TODO flush failuresPrintStream here??
            //###TODO flush failuresPrintStream here??
            //###TODO flush failuresPrintStream here??
          });
        }
      }.get();

    });

    inputPlugin.run(outputPluginProvider.mtuHint()).get();

  }

  @PreDestroy
  public void destroy() {
    if (failuresFileName != null) {
      try {
        failuresPrintStream.close();
      } finally {
        stderr(" ########## " + failuresFileName + " ########## ");
      }
    }
  }

  private <T> T parseOptions(ApplicationArguments args, Class<T> classOfT) throws Exception {
    JsonObject options = new Gson().toJsonTree(classOfT.getConstructor().newInstance()).getAsJsonObject();
    for (String name : args.getOptionNames()) {
        String lowerCamel = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name);
        if (args.getOptionValues(name).size()==0) {
            options.addProperty(lowerCamel, true);
        } else {
            for (String value : args.getOptionValues(name)) {
                JsonElement jsonElement = options.get(lowerCamel);
                if (jsonElement == null || !jsonElement.isJsonArray())
                    options.addProperty(lowerCamel, value);
                else
                    jsonElement.getAsJsonArray().add(value);
            }    
        }
    }
    return new Gson().fromJson(options, classOfT);
  }

  private InputPluginProvider resolveInputPlugin(String source) {
    for (InputPluginProvider provider : inputPluginProviders) {
      try {
        if (provider.canActivate(source))
          return provider;
      } catch (Exception e) {
        stderr(provider.name(), e);
      }
    }
    return new SystemInPluginProvider();
  }

  private OutputPluginProvider resolveOutputPlugin(String target) {
    for (OutputPluginProvider provider : outputPluginProviders) {
      try {
        if (provider.canActivate(target))
          return provider;
      } catch (Exception e) {
        stderr(provider.name(), e);
      }
    }
    return new SystemOutPluginProvider();
  }

  // private static AppState parseState(String base64) throws Exception {
  //   byte[] bytes = BaseEncoding.base64().decode(base64);
  //   ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
  //   InputStream in = new GZIPInputStream(bais);
  //   String json = CharStreams.toString(new InputStreamReader(in));

  //   AppState state = new Gson().fromJson(json, AppState.class);

  //   // sigh.
  //   for (Map<String, AttributeValue> exclusiveStartKey : state.exclusiveStartKeys) {
  //     exclusiveStartKey.putAll(Maps.transformValues(exclusiveStartKey, (value) -> {
  //       //###TODO handle all types
  //       //###TODO handle all types
  //       //###TODO handle all types
  //       return AttributeValue.builder().s(value.s()).build();
  //     }));
  //   }

  //   return state;
  // }

  // private static String renderState(AppState state) throws Exception {
  //   ByteArrayOutputStream baos = new ByteArrayOutputStream();
  //   try (OutputStream out = new GZIPOutputStream(baos, true)) {
  //     out.write(new Gson().toJson(state).getBytes());
  //   }
  //   return BaseEncoding.base64().encode(baos.toByteArray());
  // }

  private void stderr(Object... args) {
    List<String> parts = Lists.newArrayList();
    for (Object arg : args)
      parts.add("" + arg);
    System.err.println(String.join(" ", parts));
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

  private void trace(Object... args) {
    new LogHelper(this).trace(args);
  }
}
