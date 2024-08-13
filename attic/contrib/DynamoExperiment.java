package awscat.contrib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Joiner;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.RateLimiter;

import helpers.LocalMeter;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

public class DynamoExperiment {

  static AtomicLong id = new AtomicLong();

  public static void main(String... args) throws Exception {

    final int wcuLimit=4000;

    String tableName = "Dlcm-rrizun_MetaStore";
    DynamoDbAsyncClient client = DynamoDbAsyncClient.builder().build();

    RateLimiter rateLimiter = RateLimiter.create(wcuLimit);

    int totalSegments = Math.max(wcuLimit/128, 1);
    log("totalSegments:"+totalSegments);

    int produceFactor=1;

    ExecutorService executor = Executors.newFixedThreadPool(totalSegments*produceFactor);

    LocalMeter consumedCapacityUnitsMeter = new LocalMeter();

    while (true) {
    // for (int c = 0; c < 500000/25; ++c) {

      // produce at twice consume rate (divide by 2)
      Number sleepMillis = 25.0*1000/wcuLimit/produceFactor/2;
      // Thread.sleep(1);
      Thread.sleep(sleepMillis.longValue());

      // submit 25 wcu of work
      executor.execute(()->{

        try {

          List<WriteRequest> requestItems = new ArrayList<>();
          for (int i = 0; i < 25; ++i) {
            Map<String, AttributeValue> item = new HashMap<>();
            String key = "tax:"+Hashing.sha256().hashLong(id.incrementAndGet()).toString();
            item.put("key", AttributeValue.builder().s(key).build());
            item.put("val1", AttributeValue.builder().s(key).build());
            item.put("val2", AttributeValue.builder().s(key).build());
            item.put("val3", AttributeValue.builder().s(key).build());
            PutRequest putRequest = PutRequest.builder().item(item).build();
            WriteRequest writeRequest = WriteRequest.builder().putRequest(putRequest).build();
            requestItems.add(writeRequest);
          }

          BatchWriteItemRequest batchWriteItemRequest = BatchWriteItemRequest.builder()
          //
          .requestItems(Map.of(tableName, requestItems))
          //
          .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
          //
          .build();
    
          BatchWriteItemResponse batchWriteItemResponse = client.batchWriteItem(batchWriteItemRequest).get();
    
          Number consumedCapacityUnits = batchWriteItemResponse.consumedCapacity().iterator().next().capacityUnits();
          consumedCapacityUnitsMeter.add(consumedCapacityUnits.longValue());
          if (consumedCapacityUnits.intValue() > 0)
            rateLimiter.acquire(consumedCapacityUnits.intValue());
    
          // log(batchWriteItemResponse);
          
          if (batchWriteItemResponse.hasUnprocessedItems()) {
            if (!batchWriteItemResponse.unprocessedItems().isEmpty()) {
              log("########## unprocessedItems ##########"+batchWriteItemResponse.unprocessedItems().values().iterator().next());
              System.exit(1);
            }
          }
  
        } catch (Exception e) {
          log(e);
        } finally {
          log("consumed", "wcuLimit", wcuLimit, "totalSegments", totalSegments, "sleepMillis", sleepMillis, "stats", consumedCapacityUnitsMeter);
        }

      });

    }

    // Thread.sleep(Long.MAX_VALUE);

  }

  static void log(Object... args) {    
    System.out.println(""+System.currentTimeMillis()+" "+Joiner.on(" ").useForNull("null").join(args));
  }

  static {
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("ROOT")).setLevel(ch.qos.logback.classic.Level.INFO);
  }
  
}
