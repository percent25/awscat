package helpers;

import java.util.*;
import java.util.concurrent.*;

import com.google.common.collect.*;
import com.google.common.util.concurrent.*;
import com.google.gson.*;

import software.amazon.awssdk.services.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * DynamoWriter
 * 
 * <p>pipelined
 * <p>not thread-safe
 */
public class DynamoWriter {

  private class VoidFuture extends AbstractFuture<Void> {
    public boolean setVoid() {
      return super.set(null);
    }

    public boolean setException(Throwable throwable) {
      return super.setException(throwable);
    }
  }

  private final DynamoDbAsyncClient client;
  private final String tableName;
  private final List<String> keySchema;
  private final Semaphore sem;
  private final AbstractThrottle writeLimiter;
  private final boolean delete; // PutItem vs DeleteItem

  private class PartitionValue {
    public final Map<String, AttributeValue> item;
    public final WriteRequest writeRequest;
    public final VoidFuture lf;
    public PartitionValue(Map<String, AttributeValue> item, WriteRequest writeRequest, VoidFuture lf) {
      this.item = item;
      this.writeRequest = writeRequest;
      this.lf = lf;
    }
  }
  private Multimap<Map<String, AttributeValue>/*key*/, PartitionValue> partition = ArrayListMultimap.create();

  private final List<ListenableFuture<?>> batchWriteItemFutures = Lists.newArrayList();

  // ###TODO
  // ###TODO
  // ###TODO
  private static LocalMeter wcuMeter = new LocalMeter();
  // ###TODO
  // ###TODO
  // ###TODO

  public DynamoWriter(DynamoDbAsyncClient client, String tableName, List<KeySchemaElement> keySchema, Semaphore sem, AbstractThrottle writeLimiter, boolean delete) {
    debug("ctor", client, tableName, keySchema, writeLimiter, delete);
    this.client = client;
    this.tableName = tableName;
    this.keySchema = Lists.transform(keySchema, e->e.attributeName());
    this.sem = sem;
    this.writeLimiter = writeLimiter;
    this.delete = delete;
  }

  public ListenableFuture<?> write(JsonElement jsonElement) {
    return new FutureRunner() {
      {
        run(() -> {
          VoidFuture lf = new VoidFuture();

          Map<String, AttributeValue> item = DynamoHelper.render(jsonElement);
          Map<String, AttributeValue> key = Maps.toMap(keySchema, k -> item.get(k));

          // assume write request
          PutRequest putRequest = PutRequest.builder().item(item).build();
          WriteRequest writeRequest = WriteRequest.builder().putRequest(putRequest).build();

          // delete request?
          if (delete) {
            // yes
            DeleteRequest deleteRequest = DeleteRequest.builder().key(key).build();
            writeRequest = WriteRequest.builder().deleteRequest(deleteRequest).build();
          }

          partition.put(key, new PartitionValue(item, writeRequest, lf));

          // A single BatchWriteItem operation can contain up to 25 PutItem or DeleteItem
          // requests. The total size of all the items written cannot exceed 16 MB.

          if (partition.size() == 25) //###TODO use .keySet().size() ??
            partition = doBatchWriteItem(partition);

          return lf;
        });
      }
    }.get();
  }

  public ListenableFuture<?> flush() {
    return new FutureRunner() {
      {
        run(() -> {
          if (partition.size() > 0) //###TODO use .keySet().size() ??
            partition = doBatchWriteItem(partition);
          return Futures.successfulAsList(batchWriteItemFutures);
        });
      }
    }.get();
  }

  class DoBatchWriteItemWork {
    public boolean success;
    public String failureMessage;
    public int unprocessedItems;
    public int consumedCapacityUnits;
    public String wcuMeter;
    public String toString() {
      return "DoBatchWriteItemWork" + new Gson().toJson(this);
    }
  }

  // https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchWriteItem.html
  private Multimap<Map<String, AttributeValue>/*key*/, PartitionValue> doBatchWriteItem(Multimap<Map<String, AttributeValue>/*key*/, PartitionValue> partition) {

    //###TODO use semaphore?? ans: no.. pre-throttle works

    // run(()->{
    //   sem.acquire();
    //   List<WriteRequest> requestItems = new ArrayList<>();
    //   for (JsonElement jsonElement : partition)
    //   {
    //     Map<String, AttributeValue> item = render(jsonElement);
    //     PutRequest putRequest = PutRequest.builder().item(item).build();
    //     WriteRequest writeRequest = WriteRequest.builder().putRequest(putRequest).build();
    //     if (delete) {
    //       Map<String, AttributeValue> key = Maps.toMap(keySchema, k->item.get(k));
    //       DeleteRequest deleteRequest = DeleteRequest.builder().key(key).build();
    //       writeRequest = WriteRequest.builder().deleteRequest(deleteRequest).build();
    //     }
    //     requestItems.add(writeRequest);
    //   }
    //   batchWriteItem(++num, requestItems).addListener(()->{
    //     sem.release();
    //   }, MoreExecutors.directExecutor());
    // });

    ListenableFuture<?> lf = new FutureRunner() {
      // VoidFuture throttle = new VoidFuture();
      DoBatchWriteItemWork work = new DoBatchWriteItemWork();
      {
        BiMap<Map<String, AttributeValue>/*key*/, WriteRequest> writeRequests = HashBiMap.create();
        //###TODO SCRUTINIZE THIS FOR FAILURE CASES
        //###TODO SCRUTINIZE THIS FOR FAILURE CASES
        //###TODO SCRUTINIZE THIS FOR FAILURE CASES
        run(() -> {

          //###TODO WOULD BE NICE TO HAVE ASYNC SEMAPHORE ACQUIRE
          //###TODO WOULD BE NICE TO HAVE ASYNC SEMAPHORE ACQUIRE
          //###TODO WOULD BE NICE TO HAVE ASYNC SEMAPHORE ACQUIRE
          sem.acquire(); 
          //###TODO WOULD BE NICE TO HAVE ASYNC SEMAPHORE ACQUIRE
          //###TODO WOULD BE NICE TO HAVE ASYNC SEMAPHORE ACQUIRE
          //###TODO WOULD BE NICE TO HAVE ASYNC SEMAPHORE ACQUIRE

          Map<Map<String, AttributeValue>/*key*/, Map<String, AttributeValue>/*item*/> items = new LinkedHashMap<Map<String, AttributeValue>/* key */, Map<String, AttributeValue>/* item */>();
          partition.keySet().forEach(key -> {
            PartitionValue partitionValue = Iterables.getLast(partition.get(key)); // last one wins
            items.put(key, partitionValue.item);
            writeRequests.put(key, partitionValue.writeRequest);
          });

          // pre-throttle
          // https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/CapacityUnitCalculations.html
          int[] permits = new int[1];
          for (Map<String, AttributeValue> item : items.values()) {
            int size = DynamoHelper.itemSize(item);
            permits[0] += (size + 1023) / 1024;
          }
          // System.out.println("permits="+permits[0]);
          // if (permits > 0)
          // writeLimiter.acquire(permits);
          // permits[0]=25; //###?!?
          return writeLimiter.asyncAcquire(permits[0]);
        }, () -> {

          run(() -> {

            BatchWriteItemRequest batchWriteItemRequest = BatchWriteItemRequest.builder()
                //
                .requestItems(Map.of(tableName, writeRequests.values()))
                //
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                //
                .build();

            return lf(client.batchWriteItem(batchWriteItemRequest));

          }, batchWriteItemResponse -> {

            for (ConsumedCapacity consumedCapacity : batchWriteItemResponse.consumedCapacity()) {
              wcuMeter.add(consumedCapacity.capacityUnits().longValue());
              work.consumedCapacityUnits += consumedCapacity.capacityUnits().intValue();
              int permits = consumedCapacity.capacityUnits().intValue();
              // if (permits > 0)
              // writeLimiter.acquire(permits);
            }

            // failure 500
            if (batchWriteItemResponse.hasUnprocessedItems()) {
              for (List<WriteRequest> unprocessedItems : batchWriteItemResponse.unprocessedItems().values()) {

                // ###TODO re-submit unprocessedItems
                // ###TODO re-submit unprocessedItems
                // ###TODO re-submit unprocessedItems
                // doWrite(batchWriteItemResponse.unprocessedItems().values().iterator().next());
                // ###TODO re-submit unprocessedItems
                // ###TODO re-submit unprocessedItems
                // ###TODO re-submit unprocessedItems

                for (WriteRequest writeRequest : unprocessedItems) {
                  Map<String, AttributeValue> key = writeRequests.inverse().get(writeRequest);
                  partition.removeAll(key).forEach(partitionValue -> {
                    ++work.unprocessedItems;
                    partitionValue.lf.setException(new Exception("unprocessedItem=" + writeRequest));
                  });
                }
              }
            }

            // success 200
            partition.values().forEach(partitionValue -> {
              partitionValue.lf.setVoid();
            });

            work.success = true;

          }, e -> {
            debug(e);
            e.printStackTrace();
            work.failureMessage = "" + e;
            partition.values().forEach(partitionValue -> {
              partitionValue.lf.setException(e);
            });
            // throw e;
          }, () -> { // finally
            sem.release();
            work.wcuMeter = wcuMeter.toString();
            debug(work); //###TODO NEED A CLOSER LOOK HERE
          });
        });
      }

    }.get();
    batchWriteItemFutures.add(lf);
    return ArrayListMultimap.create();
  }

  private void debug(Object... args) {
    new LogHelper(this).debug(args);
  }

  public static void main(String... args) throws Exception {

    DynamoDbAsyncClient client = DynamoDbAsyncClient.builder() //
        // .httpClient(AwsCrtAsyncHttpClient.create()) //
        // .endpointOverride(URI.create("http://localhost:4566")) //
        // .region(Region.US_EAST_1) //
        // https://github.com/localstack/localstack/blob/master/README.md#setting-up-local-region-and-credentials-to-run-localstack
        // .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))) //
        .build();

    final String tableName = "OnDemand";

    DescribeTableRequest describeTableRequest = DescribeTableRequest.builder().tableName(tableName).build();
    DescribeTableResponse describeTableResponse = client.describeTable(describeTableRequest).get();
    List<KeySchemaElement> keySchema = describeTableResponse.table().keySchema();
    
    Semaphore sem = new Semaphore(1);

    DynamoWriter dynamoWriter = new DynamoWriter(client, tableName, keySchema, sem, permits -> Futures.immediateVoidFuture(), false);

    Futures.allAsList(dynamoWriter.write(new JsonStreamParser("{id:{s:1}}").next()), dynamoWriter.flush()).get();

  }

}
