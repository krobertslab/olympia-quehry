package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Annotation;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.util.Log;

/**
 * A concept in text, specifically intended for semantic parsing simplification.
 *
 * @author Kirk Roberts - kirk.roberts@uth.tmc.edu
 */
public class Concept extends Annotation {
  private static final Log log = new Log(Concept.class);

  private final Text text; 
  private final String type;
  private final String pos;
  private final String cui;
  private final String value;
  private final Integer score;

  /**
   * Creates a new <code>Concept</code> with only a <var>type</var>.
   */
  public Concept(final Text text, final String type) {
    this(text, type, null, null, null);
  }

  /**
   * Creates a new <code>Concept</code> with a <var>type</var> and
   * <var>pos</var> (part-of-speech), <var>cui</var>, and <var>value</var>.
   * Except for <var>type</var>, any of these may be <code>null</code>.
   * @throws IllegalArgumentException If <var>type</var> is <code>null</code>.
   */
  public Concept(final Text text,
                 final String type,
                 final String pos,
                 final String cui,
                 final String value) {
    this(text, type, pos, cui, value, null);
  }

  /**
   * Creates a new <code>Concept</code> with a <var>type</var> and
   * <var>pos</var> (part-of-speech), <var>cui</var>, <var>value</var>, and <var>score</var>.
   * Except for <var>type</var>, any of these may be <code>null</code>.
   * @throws IllegalArgumentException If <var>type</var> is <code>null</code>.
   */
  public Concept(final Text text,
                 final String type,
                 final String pos,
                 final String cui,
                 final String value,
                 final Integer score) {
    super(text);
    this.text = text;
    this.type = type;
    this.pos = pos;
    this.cui = cui;
    this.value = value;
    this.score = score;
    if (type == null) {
      throw new IllegalArgumentException("type cannot be NULL");
    }
  }

  /**
   * Creates a copy of <code>Concept</code> from the given <var>text</var> and <var>concept</var>.
   */
  public Concept(final Text text, final Concept concept) {
    this(text, concept.getType(), concept.getPOS(), concept.getCUI(), concept.getValue(), concept.getScore());
  }

  /**
   * Returns the type of this <code>Concept</code>.
   */
  public String getType() {
    return type;
  }

  /**
   * Returns the text of this <code>Concept</code>.
   */
  public Text getText() {
    return text;
  }
  
  /**
   * Returns the CUI of this <code>Concept</code>.
   */
  public String getCUI() {
    return cui;
  }
  
  /**
   * Returns the CUI of this <code>Concept</code>.
   */
  public String getPOS() {
    return pos;
  }

  /**
   * Returns the value of this <code>Concept</code>.
   */
  public String getValue() {
    return value;
  }

  public Integer getScore() {
    return score;
  }
}
