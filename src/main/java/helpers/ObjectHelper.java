package helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;
import com.google.gson.ToNumberPolicy;

public class ObjectHelper {

  // to plain-old-java-object
  public static Object toObject(JsonElement jsonElement) {
    if (jsonElement.isJsonArray()) {
      List<Object> array = new ArrayList<>();
      for (JsonElement element : jsonElement.getAsJsonArray())
        array.add(toObject(element)); // recurse
      return array;
    }
    if (jsonElement.isJsonObject()) {
      Map<String, Object> object = new LinkedHashMap<>();
      for (Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet())
        object.put(entry.getKey(), toObject(entry.getValue())); // recurse
      return object;
    }
    //###
    // https://github.com/IBM/java-sdk-core/blob/main/src/main/java/com/ibm/cloud/sdk/core/util/MapValueObjectTypeAdapter.java
    if (jsonElement.isJsonPrimitive()) {
      if (jsonElement.getAsJsonPrimitive().isNumber())
        return jsonElement.getAsNumber();
    }
    //###
    return new Gson().fromJson(jsonElement, Object.class);
  }

  public static void main(String... args) {
    var gson = new GsonBuilder()
        .setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER)
        .create();
    // Ugh: round-trip json number thru Object: 1 -> 1.0
    Object object = gson.fromJson("1", Object.class);
    System.out.println(object); // 1.0
    System.out.println(object.getClass()); // java.lang.Double
    System.out.println(gson.toJson(object)); // 1.0

    // Ugh: round-trip json number thru Number: 1 -> {"value":"1"}
    Number number = gson.fromJson("1", Number.class);
    System.out.println(number); // 1
    System.out.println(number.getClass()); // LazilyParsedNumber
    System.out.println(gson.toJson(number)); // {"value":"1"}

    System.out.println(toObject(new JsonStreamParser("1").next()));
    System.out.println(toObject(new JsonStreamParser("[1,2,3]").next()));
    System.out.println(toObject(new JsonStreamParser("{a:1,b:[1,2,3]}").next()));
  }

}
