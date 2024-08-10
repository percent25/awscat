package helpers;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.Map.*;

import com.google.common.collect.*;
import com.google.gson.*;

import percent25.awscat.*;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.junit.jupiter.api.*;

// @Disabled
// https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions
public class ExpressionsJsTest {

  private final String now = Instant.now().toString();
  private final ExpressionsJs js = new ExpressionsJs(now);
  // private final JsonElement jsonElement = new JsonObject();
  // private final Expressions expressions = new Expressions(jsonElement);
  
  // the set of falsey values is static.. here are the falsey values
  private final Set<String> allFalsey = ImmutableSet.of( //
      "null", // json null
      "false", // json primitive bool
      "0", "-0", "0.0", "-0.0", // json primitive number
      "''"); // json primitive string      
      // "''", "'false'", "'0'", "'-0'", "'0.0'", "'-0.0'"); // json primitive string      

  // if it is not falsey then it is truthy.. here are some truthy values
  private final Set<String> someTruthy = ImmutableSet.of( //
      "true", // json primitive bool
      "1", "-1", "1.0", "-1.0", // json primitive number
      "'true'", // json primitive string
      "[]", "[null]", "[false]", "[0]", "['']", "['false']", "['0']", // any json array
      "{}"); // any json object

  @Test
  public void boolTest() {

    assertThat(filter("{id:{s:1}}", "e.id.s==1")).isEqualTo(true);
    assertThat(filter("{id:{s:1}}", "e.id.s==2")).isEqualTo(false);

    // System.out.println(parser.parseExpression("e.id?.s?.length").getValue(context));
    // System.out.println(parser.parseExpression("e.id?.s?.length>0").getValue(context));
    // System.out.println(" ### "+parser.parseExpression("e.idz!=null").getValue(context, boolean.class));

    // // System.out.println(parser.parseExpression("e.aaa={:}").getValue(context));
    // // System.out.println(parser.parseExpression("e.aaa.bbb=222").getValue(context));

    // System.out.println(parser.parseExpression("e.id.s='123'").getValue(context));
    // // System.out.println(parser.parseExpression("value={s:id.s.length}").getValue(context));
    // // System.out.println(parser.parseExpression("#this = 5.0").getValue(context));

  }

  @Test
  public void safeNavigationTest() {

    assertThrows(Exception.class, ()->{
      filter("null", "e.id.s==1"); // bad style
    });

    assertThrows(Exception.class, ()->{
      filter("null", "e.id?.s==1"); // bad style
    });

    assertThat(filter("null", "e?.id.s==1")).isEqualTo(false); // bad style

    assertThrows(Exception.class, ()->{
      filter("{}", "e?.id.s==1"); // bad style
    });

    assertThat(filter("null", "e?.id?.s==1")).isEqualTo(false); // good style
    assertThat(filter("false", "e?.id?.s==1")).isEqualTo(false); // good style
    assertThat(filter("0", "e?.id?.s==1")).isEqualTo(false); // good style
    assertThat(filter("true", "e?.id?.s==1")).isEqualTo(false); // good style
    assertThat(filter("1", "e?.id?.s==1")).isEqualTo(false); // good style
    assertThat(filter("{}", "e?.id?.s==1")).isEqualTo(false); // good style
    assertThat(filter("{id:{}}", "e?.id?.s==1")).isEqualTo(false); // good style
    assertThat(filter("{id:{s:1}}", "e?.id?.s==1")).isEqualTo(true); // good style

  }

  @Test
  public void outputTest() {

    // .eval("id?.s")

    // e = new Expressions(e).apply("e.id=222");
    // value = new Expressions(e).value("e?.id?.s?.length()>0");
        
    assertThat(output("{}", "e")).isEqualTo(jsonElement("{}")); // identity

    // unconditional transform
    assertThat(output("null", "e='abc'")).isEqualTo(jsonElement("'abc'"));
    assertThat(output("null", "e='abc'").getAsString()).isEqualTo("abc");

    assertThat(output("{}", "e=1")).isEqualTo(jsonElement("1"));
    assertThat(output("{}", "e=1").getAsInt()).isEqualTo(1);
    assertThat(output("{}", "e=1.2")).isEqualTo(jsonElement("1.2"));
    assertThat(output("{}", "e=1.2").getAsDouble()).isEqualTo(1.2);

    assertThat(output("{}", "e=now()").getAsString()).isEqualTo(now);
    assertThat(output("{}", "e=uuid()").getAsString()).hasSize(36);

    // assertThat(eval(e, "e=randomString(24)").getAsString()).hasSize(32);

    assertThat(output("{}", "e=[1,2,3]")).isEqualTo(jsonElement("[1,2,3]"));
    assertThat(output("{}", "e=[[1,2,3],[1,2,3],[1,2,3]]")).isEqualTo(jsonElement("[[1,2,3],[1,2,3],[1,2,3]]"));

    assertThat(output("{}", "e.id={}")).isEqualTo(jsonElement("{id:{}}"));
    assertThat(output("{}", "e.id={s:'foo'}")).isEqualTo(jsonElement("{id:{s:'foo'}}"));
    assertThat(output("{}", "e.id={s:'bar'}")).isEqualTo(jsonElement("{id:{s:'bar'}}"));

  }

  // https://developer.mozilla.org/en-US/docs/Glossary/Truthy
  @Test
  public void truthyTest() {

    // var truthy = ImmutableSet.of( //
    //     "true", // json primitive bool
    //     "1", "-1", "1.0", "-1.0", // json primitive number
    //     "'true'", "'not-empty'", // json primitive string
    //     "[]", "{}");

    for (String e : someTruthy) {
      for (Entry<String, String> entry : ImmutableMap.of("%s", "e", "[%s]", "e[0]", "{e:%s}", "e.e").entrySet()) {
        String fmt = entry.getKey();
        String str = entry.getValue();
        assertThat(filter(String.format(fmt, e), str)).as("jsonElement=%s", e).isEqualTo(true);
      }
    }

    assertThat(filter("[1]", "e")).isEqualTo(true); //###TODO put this in someTruthy ?

  }

  // https://developer.mozilla.org/en-US/docs/Glossary/Falsy
  @Test
  public void falseyTest() {

    // var falsey = ImmutableSet.of("null", // json null
    //     "false", // json primitive bool
    //     "0", "-0", "0.0", "-0.0", // json primitive number
    //     "'false'", "''", "'0'", "'-0'", "'0.0'", "'-0.0'"); // json primitive string

    for (String e : allFalsey) {
      for (Entry<String, String> entry : ImmutableMap.of("%s", "e", "[%s]", "e[0]", "{e:%s}", "e.e").entrySet()) {
        String fmt = entry.getKey();
        String str = entry.getValue();
        assertThat(filter(String.format(fmt, e), str)).as("jsonElement=%s fmt=%s str=%s", e, fmt, str).isEqualTo(false);
      }
    }

  }

  @Test
  public void nullToBoolTest() {

    assertThat(filter("{}", "e?.test")).isEqualTo(false);
    assertThat(filter("{test:true}", "e?.test")).isEqualTo(true);
    assertThat(filter("{test:'true'}", "e?.test")).isEqualTo(true);

    // assertThat(bool(json("{test:'false'}"), "e?.test")).isEqualTo(false);

    assertThat(filter("{test:''}", "e?.test")).isEqualTo(false);

    assertThat(filter("{test:'yeah'}", "e?.test")).isEqualTo(true);
    assertThat(filter("{test:'yeah'}", "e?.test!=null")).isEqualTo(true);

  }

  @Test
  public void testTest() {

    assertThat(filter("{}", "e.test")).isEqualTo(false);

    for (String e : allFalsey)
      assertThat(filter(String.format("{test:%s}", e), "e.test")).isEqualTo(false);

    // assertThat(bool(json("{test:null}"), "e.test")).isEqualTo(false);
    // assertThat(bool(json("{test:0}"), "e.test")).isEqualTo(false);
    // assertThat(bool(json("{test:0.0}"), "e.test")).isEqualTo(false);
    // assertThat(bool(json("{test:''}"), "e.test")).isEqualTo(false);
    // assertThat(bool(json("{test:'0'}"), "e.test")).isEqualTo(false);
    // assertThat(bool(json("{test:'false'}"), "e.test")).isEqualTo(false);

    assertThat(filter("{test:'true'}", "e.test")).isEqualTo(true);
    assertThat(filter("{test:'testing123'}", "e.test")).isEqualTo(true);

  }

  @Test
  public void versionTest() {

    // tricky: JsonNull -> JsonNull
    // assertThat(output(json("null"), "e.version = (e.version?e.version:0) + 1")).isEqualTo(json("{version:1}"));

    // hmm..
    // assertThat(output(json("'abc'"), "e?.version = (e?.version?:0) + 1")).isEqualTo(json("'abc'"));

    assertThat(output("{}", "e.version = e.version??0 + 1")).isEqualTo(jsonElement("{version:1}")); // spel equivalent is a syntax error
    assertThat(output("{}", "e.version = (e.version??0) + 1")).isEqualTo(jsonElement("{version:1}"));
    assertThat(output("{version:1}", "e.version = e.version + 1")).isEqualTo(jsonElement("{version:2}"));

  }

  @Test
  public void randomStringTest() {
    for (int i = 0; i < 10; ++i)
      System.out.println("e="+output("null", "e=randomString(4)"));
  }

  // aka filters
  private boolean filter(String input, String expressionString) {
    js.e(jsonElement(input));
    return js.eval(expressionString);
  }
  
  // aka transforms
  private JsonElement output(String input, String expressionString) {
    js.e(jsonElement(input));
    js.eval(expressionString);
    return js.e();
  }

  private JsonElement jsonElement(String json) {
    return new JsonStreamParser(json).next();
  }

  public static void main(String... args) throws Exception {

      //   // array?
      //   if (value.hasArrayElements())
      //   return new Gson().toJsonTree(value.as(new TypeLiteral<List<Object>>(){}));
      // // object?
      // if (value.hasMembers())
      //   return new Gson().toJsonTree(value.as(Object.class));
      // // primitive?
      // return new Gson().toJsonTree(value.as(Object.class));
  
    Context context = Context.create();
    Value value = context.asValue(ProxyArray.fromArray(1,2,3));
    System.out.println(value.hasArrayElements());

    Object valueAsObject = value.as(Object.class);
    System.out.println("valueAsObject="+valueAsObject.getClass()+valueAsObject);
    JsonElement jsonElement = new Gson().toJsonTree(valueAsObject);
    System.out.println("jsonElement="+jsonElement.getClass());
    System.out.println(jsonElement);

    Value number = context.asValue(1);
    System.out.println(number.isNumber());

  }
  
}
