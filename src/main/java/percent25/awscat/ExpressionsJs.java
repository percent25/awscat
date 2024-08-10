package percent25.awscat;

import java.security.*;
import java.text.*;
import java.time.*;
import java.util.*;

import com.google.common.io.*;
import com.google.gson.*;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.*;

import helpers.DynamoHelper;

public class ExpressionsJs {

  public class RootObject {
    private final String now;
    public RootObject(String now) {
      this.now = now;
    }
    public String now() {
      return now;
    }
    public String uuid() {
      return UUID.randomUUID().toString();
    }
    // returns a random string w/fixed length len
    public String fixedString(int len) {
      byte[] bytes = new byte[(3 * len + 3) / 4];
      new SecureRandom().nextBytes(bytes);
      String randomString = BaseEncoding.base64Url().encode(bytes).substring(0);
      return randomString.substring(0, Math.min(len, randomString.length()));
    }
    // returns a random string w/random length [1..len]
    public String randomString(int len) {
      byte[] bytes = new byte[new SecureRandom().nextInt((3 * len + 3) / 4) + 1];
      new SecureRandom().nextBytes(bytes);
      String randomString = BaseEncoding.base64Url().encode(bytes).substring(0);
      return randomString.substring(0, Math.min(len, randomString.length()));
    }
    public Object inferDynamoDbJson(Object value) {
      JsonElement jsonElement = toJsonElement(context.asValue(value));
      JsonElement dynamoDbJson = DynamoHelper.inferDynamoDbJson(jsonElement);
      return fromJsonElement(dynamoDbJson);
    }
    public String toString() {
      return new Gson().toJson(this);
    }
  }
  
  private final Context context;
  private final Value bindings;

  public ExpressionsJs() {
    this(Instant.now().toString());
  }
  
  public ExpressionsJs(String now) {
    context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
    bindings = context.getBindings("js");

    Value rootObject = context.asValue(new RootObject(now));
    for (String identifier : rootObject.getMemberKeys())
      bindings.putMember(identifier, rootObject.getMember(identifier));
  }

  // get current element
  public JsonElement e() {
    return toJsonElement(bindings.getMember("e"));
  }

  // set current element
  public void e(JsonElement e) {
    bindings.putMember("e", fromJsonElement(e));
  }

  // eval
  public boolean eval(String js) {
    Value value = context.eval("js", js);
    // coerce to truthy/falsey
    return context.eval("js", "(function(value){return !!value})").execute(value).asBoolean();
  }

  private JsonElement toJsonElement(Value value) {
    if (value.hasArrayElements())
      return new Gson().toJsonTree(value.as(List.class));
    return new Gson().toJsonTree(value.as(Object.class)); // unbox
  }

  private Object fromJsonElement(JsonElement jsonElement) {

    if (jsonElement.isJsonArray()) {
      JsonArray jsonArray = jsonElement.getAsJsonArray();
      return new ProxyArray() { // @see ProxyArray.fromList
        @Override
        public Object get(long index) {
            return fromJsonElement(jsonArray.get((int) index)); // recursive
        }
        @Override
        public void set(long index, Value value) {
            jsonArray.set((int) index, toJsonElement(value));
          }
        @Override
        public boolean remove(long index) {
            return jsonArray.remove((int) index) != null;
        }
        @Override
        public long getSize() {
          return jsonArray.size();
        }
      };
    }

    if (jsonElement.isJsonObject()) {
      var jsonObject = jsonElement.getAsJsonObject();
      return new ProxyObject() { // @see ProxyObject.fromMap
        @Override
        public Object getMember(String key) {
          return fromJsonElement(jsonObject.get(key)); // recursive
        }

        @Override
        public Object getMemberKeys() {
          var keys = jsonObject.keySet().toArray();
          return new ProxyArray() {

            @Override
            public Object get(long index) {
              return keys[(int) index];
            }

            @Override
            public void set(long index, Value value) {
              throw new UnsupportedOperationException("set");
            }

            @Override
            public long getSize() {
              return keys.length;
            }
          };
        }

        @Override
        public boolean hasMember(String key) {
            return jsonObject.has(key);
        }

        @Override
        public void putMember(String key, Value value) {
            jsonObject.add(key, toJsonElement(value));
          }

        @Override
        public boolean removeMember(String key) {
          return jsonObject.remove(key) != null;
        }
      };
    }

    // do not return Gson LazilyParsedNumber
    // because Value.isNumber is false for Gson LazilyParsedNumber
    if (jsonElement.isJsonPrimitive()) {
      if (jsonElement.getAsJsonPrimitive().isNumber()) {
        try {
          return NumberFormat.getInstance().parse(jsonElement.getAsString());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    return new Gson().fromJson(jsonElement, Object.class);
  }

  // static JsonElement json(String json) {
  //   return new JsonStreamParser(json).next();
  // }

  // private void debug(Object... args) {
  //   new LogHelper(this).debug(args);
  // }

  public static void main(String... args) {

    ExpressionsJs js = new ExpressionsJs();

    js.e(new JsonStreamParser("{}").next());
    
    System.out.println("eval="+js.eval("e.a=1"));
    System.out.println("eval="+js.eval("e.b=4/3")); // 1.3333333333333333
    System.out.println("e="+js.e());
  }

}
