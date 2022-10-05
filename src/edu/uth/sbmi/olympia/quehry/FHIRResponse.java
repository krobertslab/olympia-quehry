package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Maps;
import org.jdom2.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FHIRResponse<T> {
  private static final Log log = new Log(FHIRResponse.class);

  private T answer;
  private String message;
  private String resource;
  private String status;

  private enum AnswerType {
    STRING,
    MAP,
    LIST,
    NULL
  }

  public FHIRResponse () { }

  public FHIRResponse (T answer, String message, String resource, String status) {
    this.answer = answer;
    this.message = message;
    this.resource = resource;
    this.status = status;
  }

  public T getAnswer() {
    return answer;
  }

  public String getAnswerType() {
    String answerType;
    if (this.answer instanceof String) {
      answerType = AnswerType.STRING.toString();
    } else if (this.answer instanceof Map) {
      answerType = AnswerType.MAP.toString();
    } else if (this.answer instanceof List) {
      answerType = AnswerType.LIST.toString();
    } else if (this.answer == null) {
      answerType = AnswerType.NULL.toString();
    } else {
      answerType = String.valueOf(this.answer.getClass());
      log.severe("Unknown FHIRResponse answer type: {0}", answerType);
    }
    return answerType;
  }

  public void setAnswer(T answer) {
    this.answer = answer;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getResource() {
    return resource;
  }

  public void setResource(String resource) {
    this.resource = resource;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public static List<Element> toXMLElements(final FHIRResponse fhirResponse) {
    List<Element> elementList = new ArrayList<>();
    final String answerType;
    final String message;
    final String resource;
    final String status;
    final String answer;

    if (fhirResponse != null) {
      if (fhirResponse.answer instanceof String) {
        answerType = "String";
      } else if (fhirResponse.answer instanceof Map) {
        answerType = "Map";
      } else if (fhirResponse.answer instanceof List) {
        answerType = "List";
      } else if (fhirResponse.answer == null) {
        answerType = "null";
      } else {
        answerType = String.valueOf(fhirResponse.answer.getClass());
        log.severe("Unknown FHIRResponse answer type: {0}", answerType);
      }
      message = String.valueOf(fhirResponse.message);
      resource = String.valueOf(fhirResponse.resource);
      status = String.valueOf(fhirResponse.status);
      answer = String.valueOf(fhirResponse.answer);
    } else {
      answerType = "null";
      message = "null";
      resource = "null";
      status = "null";
      answer = "null";
    }

    elementList.add(new Element("AnswerType").setAttribute("value", answerType));
    elementList.add(new Element("Message").setAttribute("value", message));
    elementList.add(new Element("Resource").setAttribute("value", resource));
    elementList.add(new Element("Status").setAttribute("value", status));
    elementList.add(new Element("Answer").setText(answer));
    return elementList;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FHIRResponse)) {
      return false;
    }
    FHIRResponse fhirResponse = (FHIRResponse) obj;
    return Objects.equals(this.answer, fhirResponse.answer)
        && Objects.equals(this.message, fhirResponse.message)
        && Objects.equals(this.resource, fhirResponse.resource)
        && Objects.equals(this.status, fhirResponse.status);
  }

  @Override
  public String toString() {
    final String answerType = this.getAnswerType();
    final String formattedAnswer;
    if (this.answer == null) {
      formattedAnswer = "null";
    }
    else if (answerType.equals(AnswerType.MAP.toString())) {
      formattedAnswer = Maps.prettyPrint((Map<?, ?>) this.answer);
    } else if (answerType.equals(AnswerType.LIST.toString())) {
      final List<String> formattedList = new ArrayList<>();
      for (Object map: (List<?>) this.answer) {
        String formattedMap = Maps.prettyPrint((Map<?, ?>) map);
        formattedList.add(formattedMap);
      }
      formattedAnswer = "[\n  " + String.join(",\n  ", formattedList) + "\n]";
    } else {
      formattedAnswer = String.valueOf(this.answer);
    }
    return "FHIRResponse{\n" +
        "  answerType=" + (this.answer != null ? this.answer.getClass() : "null") + ",\n" +
        "  answer=" + formattedAnswer + ",\n" +
        "  message=" + this.message + ",\n" +
        "  resource=" + this.resource + ",\n" +
        "  status=" + this.status + ",\n" +
        "}";
  }
}
