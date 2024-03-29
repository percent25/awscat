package percent25.awscat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;

public interface OutputPlugin {

  ListenableFuture<?> write(JsonElement jsonElement);

  ListenableFuture<?> flush();
  
}
