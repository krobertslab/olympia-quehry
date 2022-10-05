package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import org.jdom2.Attribute;
import org.jdom2.Element;

import java.util.List;

public class Prediction {
  private Document questionWithOrigAnnotations;
  private FHIRResponse fhirResponseGold;

  // MetaMap Coverage
  private boolean metaMapCandidatesIncludeGoldCUI;
  private boolean metaMapCandidatesIncludeGoldBoundary;

  // Using Gold concept annotations
  private LogicalFormPrediction lfPredWithGoldConcepts;

  // Using regex and Top Ranked MetaMap annotation
  private LogicalFormPrediction lfPredWithTopRankedMMConcept;

  // Using regex and Longest MetaMap annotation
  private LogicalFormPrediction lfPredWithLongestMMConcept;

  // Using regex and all MetaMap annotations
  private LogicalFormPrediction lfPredWithAllMMConcepts;

  // Using regex and Longest MetaMap annotation post filtering using the EHR concepts
  private LogicalFormPrediction lfPredWithLongestMMConceptPostEHRFiltering;

  private TimeFrame predictedTimeFrame;
  private boolean predictedTimeFrameMatchGold;

  public Prediction(
      Document questionWithOrigAnnotations,
      LogicalFormPrediction lfPredWithGoldConcepts,
      LogicalFormPrediction lfPredWithTopRankedMMConcept,
      LogicalFormPrediction lfPredWithLongestMMConcept,
      LogicalFormPrediction lfPredWithAllMMConcepts,
      LogicalFormPrediction lfPredWithLongestMMConceptPostEHRFiltering,
      boolean metaMapCandidateIncludeGoldCUI,
      boolean metaMapCandidateIncludeGoldBoundary,
      TimeFrame predictedTimeFrame,
      boolean predictedTimeFrameMatchGold) {
    this.questionWithOrigAnnotations = questionWithOrigAnnotations;
    this.lfPredWithGoldConcepts = lfPredWithGoldConcepts;
    this.lfPredWithTopRankedMMConcept = lfPredWithTopRankedMMConcept;
    this.lfPredWithLongestMMConcept = lfPredWithLongestMMConcept;
    this.lfPredWithAllMMConcepts = lfPredWithAllMMConcepts;
    this.lfPredWithLongestMMConceptPostEHRFiltering = lfPredWithLongestMMConceptPostEHRFiltering;
    this.metaMapCandidatesIncludeGoldCUI = metaMapCandidateIncludeGoldCUI;
    this.metaMapCandidatesIncludeGoldBoundary = metaMapCandidateIncludeGoldBoundary;
    this.predictedTimeFrame = predictedTimeFrame;
    this.predictedTimeFrameMatchGold = predictedTimeFrameMatchGold;

    this.fhirResponseGold = getFhirResponseGold();
  }

  private FHIRResponse getFhirResponseGold() {
    return FHIRClient.execute(
        questionWithOrigAnnotations.getOnlySub(LogicalForm.class).getLogicalFormTree(), questionWithOrigAnnotations);
  }

  public Element getXML() {
    final Element predictionElem = new Element("Prediction");
    predictionElem.setAttribute(new Attribute("id", this.questionWithOrigAnnotations.getDocumentID()));
    predictionElem.addContent(new Element("QuestionText").setText(this.questionWithOrigAnnotations.asRawString()));
    predictionElem.addContent(new Element("GoldLogicalTree")
        .setText(this.questionWithOrigAnnotations.getOnlySub(LogicalForm.class).simpleLogicalForm()));
    predictionElem.addContent(new Element("GoldTimeFrame")
        .setText(this.questionWithOrigAnnotations.getOnlySub(TimeFrame.class).getValue()));
    final List<Concept> goldConcepts = questionWithOrigAnnotations.getAnnotations(Concept.class);
    Element goldConceptsElem = new Element("GoldConcepts");
    for (Concept c: goldConcepts) {
      goldConceptsElem.addContent(
          new Element("Concept")
              .setAttribute("text", c.asRawString())
              .setAttribute("type", c.getType())
              .setAttribute("value", String.valueOf(c.getValue()))
              .setAttribute("CUI", String.valueOf(c.getCUI())));
    }
    predictionElem.addContent(goldConceptsElem);
    predictionElem.addContent(
        new Element("GoldFHIRResponse").addContent(FHIRResponse.toXMLElements(this.fhirResponseGold)));

    predictionElem.addContent(
        new Element("MMCandidatesIncludeGoldCUI")
            .setAttribute("value", String.valueOf(this.metaMapCandidatesIncludeGoldCUI)));
    predictionElem.addContent(
        new Element("MMCandidatesIncludeGoldBoundary")
            .setAttribute("value", String.valueOf(this.metaMapCandidatesIncludeGoldBoundary)));
    predictionElem.addContent(
        new Element("PredictedTimeFrame")
            .addContent(predictedTimeFrame.getValue())
            .setAttribute("matchGold", String.valueOf(this.predictedTimeFrameMatchGold)));

    final Element predictionUsingTopRankedMMConcept = new Element("UsingTopRankedMMConcept");
    predictionUsingTopRankedMMConcept.addContent(
        this.lfPredWithTopRankedMMConcept.getXML(
            this.predictedTimeFrame, this.predictedTimeFrameMatchGold, this.fhirResponseGold));
    predictionElem.addContent(predictionUsingTopRankedMMConcept);

    final Element predictionUsingLongestMMConcept = new Element("UsingLongestMMConcept");
    predictionUsingLongestMMConcept.addContent(
        this.lfPredWithLongestMMConcept.getXML(
            this.predictedTimeFrame, this.predictedTimeFrameMatchGold, this.fhirResponseGold));
    predictionElem.addContent(predictionUsingLongestMMConcept);

    final Element predictionUsingAllMMConcepts = new Element("UsingAllMMConcepts");
    predictionUsingAllMMConcepts.addContent(
        this.lfPredWithAllMMConcepts.getXML(
            this.predictedTimeFrame, this.predictedTimeFrameMatchGold, this.fhirResponseGold));
    predictionElem.addContent(predictionUsingAllMMConcepts);

    final Element predictionUsingLongestMMConceptPostEHRFiltering =
        new Element("UsingLongestMMConceptPostEHRFiltering");
    predictionUsingLongestMMConceptPostEHRFiltering.addContent(
        this.lfPredWithLongestMMConceptPostEHRFiltering.getXML(
            this.predictedTimeFrame, this.predictedTimeFrameMatchGold, this.fhirResponseGold));
    predictionElem.addContent(predictionUsingLongestMMConceptPostEHRFiltering);

    final Element predictionUsingGoldConcepts = new Element("UsingGoldConcepts");
    predictionUsingGoldConcepts.addContent(
        this.lfPredWithGoldConcepts.getXML(
            this.predictedTimeFrame, this.predictedTimeFrameMatchGold, this.fhirResponseGold));
    predictionElem.addContent(predictionUsingGoldConcepts);

    return predictionElem;
  }
}
