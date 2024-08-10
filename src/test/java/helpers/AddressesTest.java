package helpers;

import com.google.gson.*;

import percent25.awscat.*;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

public class AddressesTest {

  class Options {
    String foo;
    boolean bar1;
    boolean bar2;
    boolean bar3;
    int baz0;
    int baz1;
    public String toString() {
      return new Gson().toJson(this);
    }
  }

  @Test
  public void argsTest() {

    var options = Addresses.options("basepart,foo=abc,bar1,bar2=true,bar3=false,baz1=1", Options.class);

    log(options);

    assertThat(options.foo).isEqualTo("abc");
    assertThat(options.bar1).isTrue();
    assertThat(options.bar2).isTrue();
    assertThat(options.bar3).isFalse();

    assertThat(options.baz0).isEqualTo(0);
    assertThat(options.baz1).isEqualTo(1);

  }

  private void log(Object... args) {
    new LogHelper(this).debug(args);
  }
  
}
