package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import org.jdom2.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LogicalFormPrediction {
  private Document questionWithConceptsUsedInPrediction;

  private LogicalTree predictedLogicalTree;
  private Boolean generatedLogicalTreesIncludeGold;
  private boolean predictedLogicalTreeMatchGold;

  private List<Concept> predictedConcepts;
  private boolean predictedConceptsMatchGoldCUI;
  private boolean predictedConceptsMatchGoldBoundary;

  private FHIRResponse fhirResponsePrediction;

  public LogicalFormPrediction(
      Document questionWithConceptsUsedInPrediction,
      LogicalTree predictedLogicalTree,
      boolean predictedLogicalTreeMatchGold,
      List<Concept> predictedConcepts,
      boolean predictedConceptsMatchGoldCUI,
      boolean predictedConceptsMatchGoldBoundary) {
    this.questionWithConceptsUsedInPrediction = questionWithConceptsUsedInPrediction;
    this.predictedLogicalTree = predictedLogicalTree;
    this.predictedLogicalTreeMatchGold = predictedLogicalTreeMatchGold;
    this.predictedConcepts = predictedConcepts;
    this.predictedConceptsMatchGoldCUI = predictedConceptsMatchGoldCUI;
    this.predictedConceptsMatchGoldBoundary = predictedConceptsMatchGoldBoundary;
  }

  public void setGeneratedLogicalTreesIncludeGold(boolean generatedLogicalTreesIncludeGold) {
    if (this.generatedLogicalTreesIncludeGold == null) {
      this.generatedLogicalTreesIncludeGold = generatedLogicalTreesIncludeGold;
    } else {
      throw new RuntimeException("generatedLogicalTreesIncludeGold is already set");
    }
  }

  public boolean doesPredictedLFMatchGold(boolean predictedTimeFrameMatchGold) {
    return this.predictedLogicalTreeMatchGold
        && predictedTimeFrameMatchGold
        && this.predictedConceptsMatchGoldCUI;
  }

  private FHIRResponse getFhirResponsePrediction(final TimeFrame predictedTimeFrame) {
    if (this.fhirResponsePrediction == null) {
      this.fhirResponsePrediction = FHIRClient.execute(
          this.predictedLogicalTree, this.questionWithConceptsUsedInPrediction, predictedTimeFrame);
    }
    return this.fhirResponsePrediction;
  }

  public List<Element> getXML(
      final TimeFrame predictedTimeFrame,
      final boolean predictedTimeFrameMatchGold,
      final FHIRResponse fhirResponseGold) {
    final List<Element> lfPredictionList = new ArrayList<>();
    lfPredictionList.add(
        new Element("GeneratedLogicalTreesIncludeGold")
            .setAttribute("value", String.valueOf(this.generatedLogicalTreesIncludeGold)));
    lfPredictionList.add(
        new Element("PredictedLogicalTree")
            .addContent(
                this.predictedLogicalTree != null
                    ? LogicalTree.flattenTree(this.predictedLogicalTree.getRoot())
                    : "null")
            .setAttribute("matchGold", String.valueOf(this.predictedLogicalTreeMatchGold)));
    Element predictedConceptsElem =
        new Element("PredictedConcepts")
            .setAttribute("matchGoldCUI", String.valueOf(this.predictedConceptsMatchGoldCUI))
            .setAttribute("matchGoldBoundary", String.valueOf(this.predictedConceptsMatchGoldBoundary));
    for (Concept c: this.predictedConcepts) {
      predictedConceptsElem.addContent(
          new Element("Concept")
              .setAttribute("text", c.asRawString())
              .setAttribute("type", c.getType())
              .setAttribute("value", String.valueOf(c.getValue()))
              .setAttribute("CUI", String.valueOf(c.getCUI())));
    }
    lfPredictionList.add(predictedConceptsElem);
    lfPredictionList.add(
        new Element("PredictedLFMatchGold")
            .setAttribute("value", String.valueOf(this.doesPredictedLFMatchGold(predictedTimeFrameMatchGold))));
    FHIRResponse fhirRespPred = this.getFhirResponsePrediction(predictedTimeFrame);
    lfPredictionList.add(
        new Element("PredictedFHIRResponse")
            .setAttribute(
                "matchGold",
                String.valueOf(Objects.equals(fhirResponseGold, fhirRespPred)))
            .setAttribute(
                "answerMatchGold",
                String.valueOf(
                    Objects.equals(
                        fhirResponseGold != null ? fhirResponseGold.getAnswer() : null,
                        fhirRespPred != null ? fhirRespPred.getAnswer() : null)))
            .addContent(FHIRResponse.toXMLElements(fhirRespPred)));

    return lfPredictionList;
  }
}
