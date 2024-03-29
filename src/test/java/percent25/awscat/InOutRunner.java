package percent25.awscat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;

import percent25.awscat.Main;

public class InOutRunner {

  private final InputSource source;
  private final OutputTarget target;

  public InOutRunner(InputSource source, OutputTarget target) {
    this.source = source;
    this.target = target;
  }

  public void run() {
    run("{id:{s:abc123}}");
  }

  public void run(String json) {
    JsonElement jsonElement = new JsonStreamParser(json).next();
    source.setUp();
    try {
      target.setUp();
      try {
        JsonElement[] receivedJsonElement = new JsonElement[1];
        try {
          // load
          source.load(jsonElement);
          // invoke
          String sourceAddress = source.address();
          String targetAddress = target.address();
          assertThatCode(() -> {
            Main.main(sourceAddress, targetAddress, "--limit=1");
          }).doesNotThrowAnyException();
          // verify
          receivedJsonElement[0] = target.verify();
        } catch (Exception e) {
          e.printStackTrace();
        }
        assertThat(receivedJsonElement[0]).isEqualTo(jsonElement);
      } finally {
        target.tearDown();
      }
    } finally {
      source.tearDown();
    }
  }

}
