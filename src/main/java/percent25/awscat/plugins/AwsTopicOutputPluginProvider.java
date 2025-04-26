package percent25.awscat.plugins;

import java.util.function.Supplier;

import com.google.common.base.MoreObjects;
import com.google.gson.Gson;

import org.springframework.stereotype.Service;

import helpers.ConcatenatedJsonWriter;
import helpers.ConcatenatedJsonWriterTransportAwsTopic;
import percent25.awscat.Addresses;
import percent25.awscat.OutputPlugin;
import percent25.awscat.OutputPluginProvider;
import software.amazon.awssdk.services.sns.SnsAsyncClient;

@Service
public class AwsTopicOutputPluginProvider implements OutputPluginProvider{

    class Options extends AwsOptions {
        public String toString() {
            return new Gson().toJson(this);
        }
    }

    private String topicArn;
    private Options addressOptions;

    public String toString() {
        return MoreObjects.toStringHelper(this).add("topicArn", topicArn).toString();
    }

    @Override
    public String help() {
        return "arn:aws:sns:region:account_ID:topic_name"; // https://docs.aws.amazon.com/IAM/latest/UserGuide/reference-arns.html
    }

    @Override
    public boolean canActivate(String address) {
        topicArn = Addresses.base(address);
        addressOptions = Addresses.options(address, Options.class);
        return topicArn.matches("arn:(.+):sns:(.+):(\\d{12}):(.+)");
    }

    @Override
    public Supplier<OutputPlugin> activate(String address) throws Exception {
        SnsAsyncClient snsClient = AwsHelper.buildAsync(SnsAsyncClient.builder(), addressOptions);
        // sns transport is thread-safe
        ConcatenatedJsonWriter.Transport transport = new ConcatenatedJsonWriterTransportAwsTopic(snsClient, topicArn);
        // ConcatenatedJsonWriter is not thread-safe
        // which makes ConcatenatedJsonWriterOutputPlugin not thread-safe
        return ()->new ConcatenatedJsonWriterOutputPlugin(new ConcatenatedJsonWriter(transport));
    }

}