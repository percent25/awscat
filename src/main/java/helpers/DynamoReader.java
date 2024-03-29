package helpers;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.function.Function;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.util.concurrent.*;
import com.google.gson.*;

import software.amazon.awssdk.services.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.model.*;

public class DynamoReader {

  private final DynamoDbAsyncClient client;
  private final String tableName;
  // private final Iterable<String> keySchema;
  private final int totalSegments;
  private final AbstractThrottle readLimiter;

  //### TODO THIS DOES NOT NEED TO BE A MEMBER VARIABLE
  //### TODO THIS DOES NOT NEED TO BE A MEMBER VARIABLE
  //### TODO THIS DOES NOT NEED TO BE A MEMBER VARIABLE
  private final List<Map<String, AttributeValue>> exclusiveStartKeys = Lists.newArrayList();
  //### TODO THIS DOES NOT NEED TO BE A MEMBER VARIABLE
  //### TODO THIS DOES NOT NEED TO BE A MEMBER VARIABLE
  //### TODO THIS DOES NOT NEED TO BE A MEMBER VARIABLE

  private boolean running;
  private Function<Iterable<JsonElement>, ListenableFuture<?>> listener;

  /**
   * ctor
   * 
   * @param client
   * @param tableName
   * @param totalSegments
   * @param readLimiter
   */
  public DynamoReader(DynamoDbAsyncClient client, String tableName, int totalSegments, AbstractThrottle readLimiter) {
    debug("ctor");

    this.client = client;
    this.tableName = tableName;
    // this.keySchema = keySchema;
    this.totalSegments = totalSegments;
    this.readLimiter = readLimiter;

    // DescribeTableRequest describeTableRequest = DescribeTableRequest.builder().tableName(tableName).build();
    // DescribeTableResponse describeTableResponse = client.describeTable(describeTableRequest).get();
    // Iterable<String> keySchema = Lists.transform(describeTableResponse.table().keySchema(), e->e.attributeName());      

    // https://aws.amazon.com/blogs/developer/rate-limited-scans-in-amazon-dynamodb
    exclusiveStartKeys.addAll(Collections.nCopies(totalSegments, null));
  }

  public String toString() {
    return MoreObjects.toStringHelper(this)
        //
        .add("tableName", tableName)
        //
        // .add("keySchema", keySchema)
        //
        .add("totalSegments", totalSegments)
        //
        .add("readLimiter", readLimiter)
        //
        .toString();
  }

  public void setListener(Function<Iterable<JsonElement>, ListenableFuture<?>> listener) {
    this.listener = listener;
  }

  public ListenableFuture<?> scan(int mtuHint) throws Exception {
    debug("scan", "mtuHint", mtuHint);

    running = true;

    return new FutureRunner() {
      {
        for (int i = 0; i < totalSegments; ++i)
          doSegment(i);
      }
      void doSegment(int segment) {
        if (running) {
          debug("doSegment", segment);

          run(() -> {
    
            // pre-throttle
            // https://aws.amazon.com/blogs/developer/rate-limited-scans-in-amazon-dynamodb
            //###TODO make async
            //###TODO make async
            //###TODO make async
            readLimiter.asyncAcquire(128).get(); //###TODO
            //###TODO make async
            //###TODO make async
            //###TODO make async

            // STEP 1 Do the scan
            ScanRequest scanRequest = ScanRequest.builder()
                //
                .tableName(tableName)
                //
                .exclusiveStartKey(exclusiveStartKeys.get(segment))
                //
                // .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                //
                .segment(segment)
                //
                .totalSegments(totalSegments)
                //
                .build();

            debug("doSegment", segment, scanRequest);

            return lf(client.scan(scanRequest));
          }, scanResponse -> {

            debug("doSegment", segment, scanResponse.count());

            exclusiveStartKeys.set(segment, scanResponse.lastEvaluatedKey());

            // STEP 2 Process the scan
            List<ListenableFuture<?>> futures = new ArrayList<>();

            List<JsonElement> jsonElements = new ArrayList<>();
            for (Map<String, AttributeValue> item : scanResponse.items())
              jsonElements.add(DynamoHelper.parse(item));
            if (jsonElements.size()>0) {
              for (List<JsonElement> partition : Lists.partition(jsonElements, mtuHint>0?mtuHint:jsonElements.size())) {
                run(()->{
                  ListenableFuture<?> lf = listener.apply(partition);
                  futures.add(lf);
                  return lf;
                });
              }  
            }

            run(()->{
              return Futures.allAsList(futures);
            }, result->{ // if success then continue
              if (!exclusiveStartKeys.get(segment).isEmpty()) //###TODO check for null here?
                doSegment(segment);
            });

          }, e->{
            debug("doSegment", segment, e);
            e.printStackTrace();
            throw new RuntimeException(e);
          });
        } // if (running)
      }
    }.get();
  }

  // non-blocking
  public void closeNonBlocking() {
    debug("closeNonBlocking");
    running = false;
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

  public static void main(String... args) throws Exception {

    LogHelper.debug = true;

    DynamoDbAsyncClient client = DynamoDbAsyncClient.builder() //
        // .httpClient(AwsCrtAsyncHttpClient.create()) //
        // .endpointOverride(URI.create("http://localhost:4566")) //
        // .region(Region.US_EAST_1) //
        // .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))) // https://github.com/localstack/localstack/blob/master/README.md#setting-up-local-region-and-credentials-to-run-localstack
        .build();

    DynamoReader dynamoReader = new DynamoReader(client, "OnDemand", 16, permits->Futures.immediateVoidFuture());

    dynamoReader.setListener(jsonElements->{
      System.out.println("size="+Iterables.size(jsonElements));
      // for (JsonElement jsonElement : jsonElements)
      //   System.out.println(jsonElement);
      return Futures.immediateVoidFuture();
    });
    
    ListenableFuture<?> lf = dynamoReader.scan(-1);
    Thread.sleep(25_000);
    dynamoReader.closeNonBlocking();
    lf.get();

  }

}
