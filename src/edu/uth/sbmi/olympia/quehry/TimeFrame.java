package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Annotation;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.util.Log;

import java.util.Arrays;
import java.util.List;

/**
 * The implicit time frame in text.
 */
public class TimeFrame extends Annotation {
  private static final Log log = new Log(TimeFrame.class);
  public static final List<String> POSSIBLE_TIME_FRAMES =
          Arrays.asList("pmh", "history", "plan", "status", "visit");

  private final String value;

  /**
   * Creates a new <code>TimeFrame</code> with a <var>value</var>.
   */
  public TimeFrame(final Text text, final String value) {
    super(text);
    if (value == null) {
      throw new IllegalArgumentException("value cannot be NULL");
    } else {
      assert POSSIBLE_TIME_FRAMES.contains(value): "invalid value for time frame: " + value;
    }
    this.value = value;
  }

  /**
   * Returns the <var>value</var> of this <code>TimeFrame</code>.
   */
  public String getValue() {
    return value;
  }
}
