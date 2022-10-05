package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Annotation;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.util.Log;

import java.util.List;

/**
 * Keeps all the information regarding the Annotated Answer.
 */
public class Answer extends Annotation {
  private static final Log log = new Log(Answer.class);

  private final String patientID;
  private final List<String> resourceIDs;

  /**
   * Creates a new <code>Answer</code> with <var>components</var> and <var>simpleValue</var>.
   * @throws IllegalArgumentException If <var>components</var> or <var>simpleValue</var> is <code>null</code>.
   */
  public Answer(final Text text,
                final String patientID,
                final List<String> resourceIDs) {
    super(text);
    if (patientID == null) {
      throw new IllegalArgumentException("patientID cannot be NULL");
    }
    if (resourceIDs == null) {
      throw new IllegalArgumentException("resourceIDs cannot be NULL");
    }
    this.patientID = patientID;
    this.resourceIDs = resourceIDs;
  }

  /**
   * Creates a copy of <code>Answer</code> from the given <var>text</var> and <var>answer</var>.
   */
  public Answer(final Text text, final Answer answer) {
    this(text, answer.getPatientID(), answer.getResourceIDs());
  }

  /**
   * Returns the <var>patientID</var> for the answer.
   */
  public String getPatientID() {
    return this.patientID;
  }
  
  /**
   * Returns the <var>getResourceIDs</var> for the answer.
   */
  public List<String> getResourceIDs() {
    return resourceIDs;
  }

  public String toString() {
    return "Patient ID: " + patientID +
        "\nResource IDs: " + resourceIDs;
  }
}