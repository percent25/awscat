package percent25.awscat.plugins;

import java.io.InputStreamReader;
import java.util.function.Function;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.util.concurrent.*;
import com.google.gson.*;

import org.springframework.stereotype.Service;

import helpers.*;
import percent25.awscat.*;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;

class AwsKinesisInputPlugin implements InputPlugin {

  private final AwsKinesisReceiver receiver;
  private Function<Iterable<JsonElement>, ListenableFuture<?>> listener;

  public AwsKinesisInputPlugin(AwsKinesisReceiver receiver) {
    debug("ctor");
    this.receiver = receiver;
    receiver.setListener(data -> {
      // Sun Feb 20 04:42:16 PST 2022 DEBUG [AwsKinesisReceiver] doShardIterator.workt
      // {"streamName":"NetBoxWebHookStack-NetBoxWebHookEventStream92261DC8-9LWDNez8q8xM","shardId":"shardId-000000000000","shardIterator":"AAAAAAAAAAGn5u2cRxY2GcgHtE/kQWWGmp8TzQn14Jap8Rk9bLME87QZ0Yjz8l1x2RudIjH+UQp+SPmc5in23+HCtQPEIJ/AaLLp1zenGY8Q52toAqhejH/yRk9CJJe8TIYhVeMtlrIVY4x8utYmxBgmC/hvBEdRGr5CUVndSvYiRf8fTbE0HdcWEs58MVftDXceyFoTrKUqOQXzFFArrJnoR4NI14osgE3MFlOSNHu19zKj1TzVCqncZP3L5YomSJk+0WwEeGG14tR0iT55AFOiFJ1WZS5kRP1U6iALYmiMR3XoDSp70g\u003d\u003d","success":true,"failureMessage":"com.google.gson.JsonSyntaxException:
      // com.google.gson.stream.MalformedJsonException: Unterminated object at line 8
      // column 101 path $.detail.data.device","in":1,"out":0,"err":1}
      return listener.apply(Lists.newArrayList(new JsonStreamParser(new InputStreamReader(data.asInputStream()))));
    });
  }

  public String toString() {
    return MoreObjects.toStringHelper(this).add("receiver", receiver).toString();
  }

  @Override
  public void setListener(Function<Iterable<JsonElement>, ListenableFuture<?>> listener) {
    this.listener = listener;
  }

  @Override
  public ListenableFuture<?> run(int mtuHint) throws Exception {
    debug("run", mtuHint);
    return receiver.start();
  }

  @Override
  public void closeNonBlocking() {
    debug("closeNonBlocking");
    receiver.closeNonBlocking();
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

}

class AwsKinesisInputPluginOptions extends AwsOptions {
  public String toString() {
    return new Gson().toJson(this);
  }
}

@Service
public class AwsKinesisInputPluginProvider extends AbstractPluginProvider implements InputPluginProvider {

  private String streamName;
  private AwsKinesisInputPluginOptions options;

  public AwsKinesisInputPluginProvider() {
    super("kinesis:<streamName>", AwsKinesisInputPluginOptions.class);
  }

  @Override
  public String toString() {
    return new LogHelper(this).string("streamName", streamName, "options", options);
    // return MoreObjects.toStringHelper(this).add("streamName", streamName).add("options", options).toString();
  }

  // https://docs.aws.amazon.com/general/latest/gr/sqs-service.html
  @Override
  public boolean canActivate(String address) {
    streamName = Addresses.base(address).split(":")[1];
    options = Addresses.options(address, AwsKinesisInputPluginOptions.class);
    return "kinesis".equals(address.split(":")[0]);
  }
  
  @Override
  public InputPlugin activate(String address) throws Exception {
    KinesisAsyncClient client = AwsHelper.buildAsync(KinesisAsyncClient.builder(), options);
    return new AwsKinesisInputPlugin(new AwsKinesisReceiver(client, streamName));
  }

}
