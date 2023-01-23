package helpers;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.concurrent.ConcurrentSkipListMap;

public class LocalMeter {

  private final long windowSecondsMax = 900;
  private final ConcurrentSkipListMap<Long, Double> values = new ConcurrentSkipListMap<>();

  /**
   * add
   * 
   * @param value
   */
  public void add(Number value) {
    long now = now();
    values.headMap(now - windowSecondsMax).clear();
    values.compute(now, (k, v) -> {
      return (v == null ? 0.0 : v) + value.doubleValue();
    });
  }

  /**
   * avg
   * 
   * @param windowSeconds
   * @return
   */
  public Number avg(long windowSeconds) {
    return sum(windowSeconds).doubleValue() / windowSeconds;
  }

  /**
   * sum
   * 
   * @param windowSeconds
   * @return
   */
  public Number sum(long windowSeconds) {
    double sum = 0;
    for (double value : values.tailMap(now() - windowSeconds, true).values())
      sum += value;
    return sum;
  }

  private long now() {
    return System.currentTimeMillis()/1000;
  }

  private Number num(Number num) {
    return num.longValue();
    // DecimalFormat df = new DecimalFormat("#.#");
    // df.setRoundingMode(RoundingMode.HALF_EVEN);
    // return df.format(num);
  }

  public String toString() {
    return String.format("%s/%s/%s %s/%s/%s",
      num(avg(1)), num(avg(5)), num(avg(15)),
      num(avg(60)), num(avg(300)), num(avg(900)));
  }

}
