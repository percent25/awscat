package percent25.awscat.plugins;

import java.net.*;

import com.google.gson.*;

import org.springframework.core.env.*;
import org.springframework.util.*;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.awscore.client.builder.*;
import software.amazon.awssdk.regions.*;

class AwsOptions {
  public String endpoint;
  // public String region; //###TODO
  // public String access; //###TODO
  // public String secret; //###TODO
  public String profile;
}

public class AwsHelper {

  private static final Environment springEnv = new StandardEnvironment();

  /**
   * doOverrides
   * 
   * https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config
   * 
   * <li>os environment variables (lowest precedence)
   * <li>java system properties
   * <li>coommand line arguments (highest precedence)
   * 
   * @param <B>
   * @param builder
   * @param options
   * @return
   */
  private static <B extends AwsClientBuilder<B ,?>> B doOverrides(B builder, Object options) {
    AwsOptions awsOptions = new Gson().fromJson(new Gson().toJson(options), AwsOptions.class);

    // start w/os environment variables and/or java system properties
    String awsEndpoint = springEnv.getProperty("aws.endpoint");
    // command line argument?
    if (StringUtils.hasText(awsOptions.endpoint)) {
      // yes- higher precedence
      awsEndpoint = awsOptions.endpoint;
    }
    if (StringUtils.hasText(awsEndpoint)) {
      builder = builder.endpointOverride(URI.create(awsEndpoint));
      //###TODO ugly
      //###TODO ugly
      //###TODO ugly
      // https://github.com/localstack/localstack/blob/master/README.md#setting-up-local-region-and-credentials-to-run-localstack
      builder = builder.region(Region.US_EAST_1); // ### TODO for localstack
      builder = builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))); // ### TODO for localstack
      //###TODO ugly
      //###TODO ugly
      //###TODO ugly
    }
    // if (StringUtils.hasText(awsOptions.region)) {
    //   builder = builder.region(Region.US_EAST_1);
    // }
    if (StringUtils.hasText(awsOptions.profile)) {
      builder = builder.credentialsProvider(ProfileCredentialsProvider.create(awsOptions.profile));
    }
    return builder;
  }

  /**
   * build
   * 
   * <p>
   * configure aws endpoint and aws profile
   * 
   * @param <B>
   * @param <C>
   * @param builder
   * @param options
   * @return
   */
  public static <B extends AwsClientBuilder<B, C>, C> C build(B builder, Object options) {
    return doOverrides(builder, options).build();
  }

  /**
   * buildAsync
   * 
   * <p>
   * configure aws endpoint and aws profile
   * 
   * @param <B>
   * @param <C>
   * @param builder
   * @param options
   * @return
   */
  public static <B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>, C> C buildAsync(B builder, Object options) {
    return doOverrides(builder, options).build();
  }

}
