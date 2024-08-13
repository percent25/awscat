package percent25.awscat.plugins;

import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.*;
import com.google.common.util.concurrent.*;
import com.google.gson.*;

import org.springframework.stereotype.Service;

import helpers.*;
import percent25.awscat.*;
import software.amazon.awssdk.services.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.model.*;

class DynamoInputPlugin implements InputPlugin {

  private final DynamoReader dynamoReader;

  /**
   * ctor
   * 
   * @param client
   * @param tableName
   * @param totalSegments
   * @param readLimiter
   */
  public DynamoInputPlugin(DynamoDbAsyncClient client, String tableName, int totalSegments, AbstractThrottle readLimiter) {
    debug("ctor");
    dynamoReader = new DynamoReader(client, tableName, totalSegments, readLimiter);
  }

  @Override
  public void setListener(Function<Iterable<JsonElement>, ListenableFuture<?>> listener) {
    dynamoReader.setListener(listener);
  }

  @Override
  public ListenableFuture<?> run(int mtuHint) throws Exception {
    debug("run", "mtuHint", mtuHint);
    return dynamoReader.scan(mtuHint);
  }

  @Override
  public void closeNonBlocking() {
    dynamoReader.closeNonBlocking();
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}

@Service
public class DynamoInputPluginProvider extends AbstractPluginProvider implements InputPluginProvider {

  class Options extends AwsOptions{
    public int c; // concurrency, aka totalSegments
    public int rcu;
    public String toString() {
      return new Gson().toJson(this);
    }
  }

  private String tableName;
  private Options options;

  public DynamoInputPluginProvider() {
    super("dynamo:<tableName>", Options.class);
  }

  public String toString() {
    // return new Gson().toJson(this);
    return MoreObjects.toStringHelper(this).add("tableName", tableName).add("options", options).toString();
  }

  @Override
  public boolean canActivate(String address) {
    return Set.of("dynamo", "dynamodb").contains(Addresses.base(address).split(":")[0]);
  }

  @Override
  public InputPlugin activate(String address) throws Exception {
    tableName = Addresses.base(address).split(":")[1];  
    options = Addresses.options(address, Options.class);  

    DynamoDbClient client = AwsHelper.build(DynamoDbClient.builder(), options);
    Supplier<DescribeTableResponse> describeTableResponseSupplier = Suppliers.memoizeWithExpiration(()->{
      return client.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
    }, 25, TimeUnit.SECONDS);
    
    // Iterable<String> keySchema = Lists.transform(describeTable.get().table().keySchema(), e->e.attributeName());      

    // https://aws.amazon.com/blogs/developer/rate-limited-scans-in-amazon-dynamodb/
    int c = Runtime.getRuntime().availableProcessors();
    if (options.c > 0)
      c = options.c;
    else {
      if (options.rcu > 0)
        c = (options.rcu + 127) / 128;
    }

    AbstractThrottle readLimiter = new AbstractThrottleGuava(() -> {
      if (options.rcu > 0)
        return options.rcu;
      Number provisionedRcu = describeTableResponseSupplier.get().table().provisionedThroughput().readCapacityUnits();
      if (provisionedRcu.longValue() > 0)
        return provisionedRcu;
      return Double.MAX_VALUE; // on-demand/pay-per-request
    });

    DynamoDbAsyncClient asyncClient = AwsHelper.buildAsync(DynamoDbAsyncClient.builder(), options);
    return new DynamoInputPlugin(asyncClient, tableName, c, readLimiter);
  }
  
  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}
