package awscat.plugins;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.util.concurrent.*;
import com.google.gson.*;

import awscat.*;

import org.springframework.stereotype.Service;

import helpers.*;
import software.amazon.awssdk.services.sqs.*;

class AwsQueueInputPlugin implements InputPlugin {

    private final AwsQueueReceiver receiver;
    private final int limit;

    private Function<Iterable<JsonElement>, ListenableFuture<?>> listener;

    private final VoidFuture lf = new VoidFuture();
    private final AtomicInteger count = new AtomicInteger();

    public AwsQueueInputPlugin(AwsQueueReceiver receiver, int limit) {
        debug("ctor");
        this.receiver = receiver;
        this.limit = limit;
        receiver.setListener(message -> {
            return new FutureRunner() {
                List<JsonElement> list;
                {
                    run(() -> {
                        count.updateAndGet(count -> {
                            list = Lists.newArrayList(Iterators.limit(new JsonStreamParser(message), limit - count));
                            return count + list.size();
                        });

                        return listener.apply(list);
                    }, () -> {
                        if (count.get() == limit) {
                            try {
                                receiver.close();
                            } finally {
                                lf.setVoid();
                            }
                        }
                    });
                }
            };
        });
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("receiver", receiver).toString();
    }

    @Override
    public ListenableFuture<?> read(int mtu) throws Exception {
        receiver.start();
        return lf;
    }

    @Override
    public void setListener(Function<Iterable<JsonElement>, ListenableFuture<?>> listener) {
        this.listener = listener;
    }

    private void debug(Object... args) {
        new LogHelper(this).debug(args);
    }

}

class AwsQueueInputPluginOptions extends AwsOptions {
    public int c;
    public int limit;

    public String toString() {
        return new Gson().toJson(this);
    }
}

@Service
public class AwsQueueInputPluginProvider extends AbstractInputPluginProvider {

    private String queueArnOrUrl;
    private AwsQueueInputPluginOptions options;

    public AwsQueueInputPluginProvider() {
        super("<queue-arn|queue-url>", AwsQueueInputPluginOptions.class);
    }

    @Override
    public String toString() {
        // return new Gson().toJson(this);
        return MoreObjects.toStringHelper(this).add("queueArnOrUrl", queueArnOrUrl).add("options", options).toString();
      }

    @Override
    public boolean canActivate(String arg) {
        queueArnOrUrl = Args.base(arg);
        options = Args.options(arg, AwsQueueInputPluginOptions.class);
        if (queueArnOrUrl.matches("arn:(.+):sqs:(.+):(\\d{12}):(.+)"))
            return true;
        if (queueArnOrUrl.matches("https://queue.amazonaws.(.*)/(\\d{12})/(.+)"))
            return true;
        if (queueArnOrUrl.matches("https://sqs.(.+).amazonaws.(.*)/(\\d{12})/(.+)"))
            return true;
        return false;
    }

    @Override
    public InputPlugin activate(String arg) throws Exception {
        int c = options.c > 0 ? options.c : Runtime.getRuntime().availableProcessors();
        SqsAsyncClient sqsClient = AwsHelper.configClient(SqsAsyncClient.builder(), options).build();
        return new AwsQueueInputPlugin(new AwsQueueReceiver(sqsClient, queueArnOrUrl, c), options.limit);
    }

}