package helpers;

import java.security.SecureRandom;
import java.time.Instant;

import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ListenableFuture;
import com.spotify.futures.CompletableFuturesExtra;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// kinesis firehose style s3 writer
public class ConcatenatedJsonWriterTransportAwsS3Kinesis implements ConcatenatedJsonWriter.Transport {
    private final S3AsyncClient client;
    private final String bucket;
    private final String deliveryStreamName;
    private final String deliveryStreamVersion;

    /**
     * ctor
     * 
     * @param client
     */
    public ConcatenatedJsonWriterTransportAwsS3Kinesis(S3AsyncClient client, String bucket, String deliveryStreamName) {
        debug("ctor", client, bucket, deliveryStreamName);
        this.client = client;
        this.bucket = bucket;
        this.deliveryStreamName = deliveryStreamName;
        this.deliveryStreamVersion = randomString();
    }

    @Override
    public int mtuBytes() {
        return 128 * 1024 * 1024; // like kinesis firehose
    }

    @Override
    public ListenableFuture<?> send(byte[] bytes) {
        String key = key(deliveryStreamName, deliveryStreamVersion);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucket).key(key).build();
        AsyncRequestBody requestBody = AsyncRequestBody.fromBytes(bytes);
        return CompletableFuturesExtra.toListenableFuture(client.putObject(putObjectRequest, requestBody));
    }

    // https://docs.aws.amazon.com/firehose/latest/dev/basic-deliver.html#s3-object-name
    static String key(String deliveryStreamName, String deliveryStreamVersion) {
        String now = Instant.now().toString();
        
        // https://docs.aws.amazon.com/firehose/latest/dev/s3-prefixes.html
        String prefix = now.substring(0, 10).replaceAll("[^\\d]+", "/");

        // DeliveryStreamName-DeliveryStreamVersion-YYYY-MM-dd-HH-MM-SS-RandomString
        String suffix = now.substring(0, 20).replaceAll("[^\\d]+", "-") + randomString();
        return String.format("%s/%s-%s-%s", prefix, deliveryStreamName, deliveryStreamVersion, suffix);
    }

    static String randomString() {
        return Hashing.sha256().hashInt(new SecureRandom().nextInt()).toString().substring(0, 7);
    }

    private void debug(Object... args) {
        new LogHelper(this).debug(args);
    }

    public static void main(String... args) {
        String deliveryStreamVersion = randomString();
        for (int i = 0; i< 10; ++i)
            System.out.println(key("awscat", deliveryStreamVersion));
    }

}
