package percent25.awscat;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.util.*;

import com.google.common.collect.*;
import com.google.gson.*;

import percent25.awscat.Main;
import percent25.awscat.SystemInPlugin;
import percent25.awscat.SystemOutPluginProvider;

import org.junit.jupiter.api.*;

import software.amazon.awssdk.services.sqs.*;
import software.amazon.awssdk.services.sqs.model.*;

public class AwsQueueIT {

  // beforeAll
  static SqsClient client;

  @BeforeAll
  public static void createClient() {
    client = AwsBuilder.build(SqsClient.builder());
  }

  // beforeEach
  private String queueArn;
  private String queueUrl;

  @BeforeEach
  public void createQueue() throws Exception {
    String queueName = UUID.randomUUID().toString();

    CreateQueueRequest createQueueRequest = CreateQueueRequest.builder().queueName(queueName).build();
    print(createQueueRequest);
    CreateQueueResponse createQueueResponse = client.createQueue(createQueueRequest);
    print(createQueueResponse);

    GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder().queueName(queueName).build();
    GetQueueUrlResponse getQueueUrlResponse = client.getQueueUrl(getQueueUrlRequest);
    queueUrl = getQueueUrlResponse.queueUrl();

    GetQueueAttributesRequest getQueueAttributesRequest = GetQueueAttributesRequest.builder() //
        .queueUrl(queueUrl) //
        .attributeNames(QueueAttributeName.ALL) //
        .build();
    GetQueueAttributesResponse getQueueAttributesResponse = client.getQueueAttributes(getQueueAttributesRequest);
    queueArn = getQueueAttributesResponse.attributes().get(QueueAttributeName.QUEUE_ARN);

  }

  @AfterEach
  public void deleteQueue() throws Exception {
    DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder().queueUrl(queueUrl).build();
    print(deleteQueueRequest);
    DeleteQueueResponse deleteQueueResponse = client.deleteQueue(deleteQueueRequest);
    print(deleteQueueResponse);
  }

  /**
   * 
   */
  @Test
  public void basicSendTest() {
    // log("verifyTest");

    // Main.main(String.format("%s,endpoint=%s,limit=1", queueArn, endpointUrl));

        // // receive
        // ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder().queueUrl(queueUrl).build();
        // log(client.receiveMessage(receiveMessageRequest));


    // ### TODO here is where we would verify that awscat consumed
  }

  @Test
  public void basicReceiveTest() throws Exception {

        // STEP 1 send

        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken
        JsonElement json = json("{foo:1}");
        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken
              // SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(queueUrl).messageBody(messageBody).build();
              // log(sendMessageRequest);
              // SendMessageResponse sendMessageResponse = client.sendMessage(sendMessageRequest).get();
              // log(sendMessageResponse);

        SystemInPlugin.stdin = new ByteArrayInputStream(json.toString().getBytes());
        String targetAddress = queueArn;
        Main.main("-", targetAddress);

        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken

        // STEP 2 receive

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SystemOutPluginProvider.stdout = new PrintStream(baos);
        
        String sourceAddress = queueArn;
        Main.main(sourceAddress, "-", "--limit=1");

        assertThat(json(baos.toString())).isEqualTo(json);

        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken
        //###TODO aws queue receiver "limit" is broken

  }

  @Test
  public void stressTest() throws Exception {
    final int limit = 200;

    // send
    for (int i = 0; i < limit; ++i) {
      String message = UUID.randomUUID().toString();
      SendMessageRequest sendMessageRequest = SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build();
      SendMessageResponse sendMessageResponse = client.sendMessage(sendMessageRequest);
      print("stressTest", i, sendMessageRequest, sendMessageResponse);
    }

    // receive
    String sourceArg = queueArn;
    Main.main(sourceArg, String.format("--limit=%s", limit));
  }

  private JsonElement json(String json) {
    return new JsonStreamParser(json).next();
  }

  private void print(Object... args) {
    System.out.println(Lists.newArrayList(args));
  }

}
