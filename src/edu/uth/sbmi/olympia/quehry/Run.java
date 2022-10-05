package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.*;
import edu.uth.sbmi.olympia.text.io.XMLDocumentReader;
import edu.uth.sbmi.olympia.text.io.XMLDocumentWriter;
import edu.uth.sbmi.olympia.util.Timer;
import edu.uth.sbmi.olympia.util.*;
import edu.uth.sbmi.olympia.util.io.CSV;
import edu.uth.sbmi.olympia.util.xml.XMLUtil;
import org.jdom2.Element;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Driver class for running the end-to-end system.
 */
@SuppressWarnings("Duplicates")
public class Run {
  private static final Log log = new Log(Run.class);

  private final Place dependencyCache = Place.fromFile(
      "resources/edu/uth/sbmi/olympia/quehry/dependency_cache.xml");
  private Set<String> conceptCodesInEHR;

  /**
   * Processes the end-to-end system.
   */
  public void process() throws Exception {
    // Load Lexicon
    final Place lexFile = Config.get(Run.class, "lexicon").toPlace();
    final List<LexiconEntry> lexEntries = ReadLexicon.parseLexicon(lexFile);
    
    // Load Questions
    final Place questionFile =
        Config.get(Run.class, "questions").toPlace();
    final ReadQuestions questionReader = new ReadQuestions(questionFile);
    List<Document> questions = questionReader.readQuestionsWithAnswers();

    // Load Questions with additional annotated concepts
    final Place questionsWithAdditionalConceptsFile =
        Config.get(Run.class, "questionsWithAdditionalConcepts").toPlace();
    final ReadQuestions questionsWithAdditionalConceptsReader = new ReadQuestions(questionsWithAdditionalConceptsFile);
    List<Document> questionsWithAdditionalConcepts = questionsWithAdditionalConceptsReader.readQuestionsWithAnswers();

    questions = questions.stream().skip(10).limit(5).collect(Collectors.toList());

    // Filter "questionsWithAdditionalConcepts" based on the document IDs available in "questions"
    final Set<String> includedDocIds = questions.stream().map(Document::getDocumentID).collect(Collectors.toSet());
    questionsWithAdditionalConcepts = questionsWithAdditionalConcepts.stream()
        .filter(question -> includedDocIds.contains(question.getDocumentID())).collect(Collectors.toList());

    // Annotate Dependencies
    annotateDependency(questions);
    annotateDependency(questionsWithAdditionalConcepts);

    // Generate LogicalTrees for the questions with original concept annotations
    final Map<String, Boolean> ltCoverageWithGoldConcepts = new HashMap<>();
    final Map<Document, List<LogicalTree>> logicalTrees =
        generateLogicalTrees(questions, lexEntries, ltCoverageWithGoldConcepts, false);

    final Pair<Map<Document, List<LogicalTree>>, List<Document>> filteredPair =
        filterLogicalTreesAndQuestionsWithNoGold(logicalTrees, questions);
    final Map<Document, List<LogicalTree>> filteredLogicalTreesOnlyGold = filteredPair.getFirst();

    log.info("Performing leave-one-out evaluation:");
    final Timer timer = Timer.get(Run.class, "leave-one-out");
    timer.start();
    final MachineLogicalTreeScorer scorer = new SVMLogicalTreeScorer();
    scorer.train(filteredLogicalTreesOnlyGold); // Pre-computing all the features
    final Pair<Double, Map<String, LogicalFormPrediction>> lfAccAndPredPairWithGoldConcepts =
        scorer.leaveOneOut(logicalTrees, filteredLogicalTreesOnlyGold, logicalTrees);
    final Map<String, LogicalFormPrediction> lfPredictionsWithGoldConcepts =
            lfAccAndPredPairWithGoldConcepts.getSecond();
    timer.stop();
    log.info("Time: {0}ms", timer.totalTime());

    log.info("Start simulating an end-to-end flow");
    // Make a copy of questions, stripping off the original concept annotations (for simulating an end-to-end flow)
    List<Document> questionsCopyWithoutOriginalConcepts =
        questions.stream().map(Document::new).collect(Collectors.toList());

    annotateDependency(questionsCopyWithoutOriginalConcepts);

    // Annotate concepts for these duplicated questions
    try (ConceptExtractor conceptExtractor = new ConceptExtractor()) {
      log.info("Automatically annotating concepts...");
      for (Document question: questionsCopyWithoutOriginalConcepts) {
        log.DBG("Question {0}: {1}", question.getDocumentID(), question.wrap());
        conceptExtractor.annotateConcepts(question);
        log.DBG("All concepts:");
        printConcepts(question);
      }
    }

    log.info("Calculating recall before filtering the concepts...");
    final Pair<Map<String, Boolean>, Map<String, Boolean>> recall =
        calculateRecallForAnnotatedConcepts(questionsCopyWithoutOriginalConcepts, questionsWithAdditionalConcepts);
    final Map<String, Boolean> mmCoverageForCUI = recall.getFirst();
    final Map<String, Boolean> mmCoverageForBoundary = recall.getSecond();
    log.DBG("mmCoverageForCUI: {0}", mmCoverageForCUI);
    log.DBG("mmCoverageForBoundary: {0}", mmCoverageForBoundary);


    log.info("Using the top ranked concept from MetaMap");
    final Comparator<Concept> conceptScoreComparator =
        Comparator.comparing(Concept::getScore, Comparator.reverseOrder())
            .thenComparing(concept -> concept.getText().getCharLength(), Comparator.reverseOrder())
            .thenComparing(concept -> concept.getText().asRawString())
            .thenComparing(Concept::getCUI);
    final List<Document> questionsCopyWithTopRankedMetaMapConcept =
        getQuestionsCopyWithConceptsSatisfyingCondition(
            questions, questionsCopyWithoutOriginalConcepts, conceptScoreComparator);

    log.info("  Selected top ranked concepts:");
    for (Document question: questionsCopyWithTopRankedMetaMapConcept) {
      log.DBG("Question {0}: {1}", question.getDocumentID(), question.wrap());
      printConcepts(question);
    }

    log.info("Calculating recall after selecting the top ranked concept...");
    calculateRecallForAnnotatedConcepts(questionsCopyWithTopRankedMetaMapConcept, questionsWithAdditionalConcepts);

    log.info("Generate LogicalTrees for questions with the top ranked concept...");
    final Map<String, Boolean> ltCoverageWithTopRankedConcept = new HashMap<>();
    final Map<Document, List<LogicalTree>> logicalTreesTopRankedConcept =
        generateLogicalTrees(
            questionsCopyWithTopRankedMetaMapConcept, lexEntries, ltCoverageWithTopRankedConcept, true);

    log.info("Performing leave-one-out evaluation for the questions with top ranked concept...");
    final Pair<Map<Document, List<LogicalTree>>, List<Document>> filteredPairTopRankedConcept =
        filterLogicalTreesAndQuestionsWithNoGold(
            logicalTreesTopRankedConcept, questionsCopyWithTopRankedMetaMapConcept);
    final Map<Document, List<LogicalTree>> logicalTreesTopRankedConceptOnlyGold =
        filteredPairTopRankedConcept.getFirst();
    final MachineLogicalTreeScorer scorerEndToEndTopRankedConcept = new SVMLogicalTreeScorer();
    scorerEndToEndTopRankedConcept.train(logicalTreesTopRankedConceptOnlyGold);

    final Map<String, List<LogicalTree>> documentIDtoLogicalTrees =
        logicalTrees.entrySet().stream().collect(Collectors.toMap(
            e -> e.getKey().getDocumentID(),
            Map.Entry::getValue
        ));

    Map<Document, List<LogicalTree>> logicalTreesWithAdditionalConcepts =
        questionsWithAdditionalConcepts.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                e -> documentIDtoLogicalTrees.get(e.getDocumentID())
            ));

    final Pair<Double, Map<String, LogicalFormPrediction>> lfAccAndPredPairWithTopRankedConcept =
        scorerEndToEndTopRankedConcept.leaveOneOut(
            logicalTreesWithAdditionalConcepts, logicalTreesTopRankedConceptOnlyGold, logicalTreesTopRankedConcept);
    final Map<String, LogicalFormPrediction> lfPredictionsWithTopRankedConcept =
        lfAccAndPredPairWithTopRankedConcept.getSecond();


    log.info("Using the longest concept from MetaMap");
    final Comparator<Concept> conceptLengthComparator =
        Comparator.comparing((Concept concept) -> concept.getText().getCharLength(), Comparator.reverseOrder())
            .thenComparing(Concept::getScore, Comparator.reverseOrder())
            .thenComparing(concept -> concept.getText().asRawString())
            .thenComparing(Concept::getCUI);
    final List<Document> questionsCopyWithLongestMetaMapConcept =
        getQuestionsCopyWithConceptsSatisfyingCondition(
            questions, questionsCopyWithoutOriginalConcepts, conceptLengthComparator);

    log.info("  Selected longest concepts:");
    for (Document question: questionsCopyWithLongestMetaMapConcept) {
      log.DBG("Question {0}: {1}", question.getDocumentID(), question.wrap());
      printConcepts(question);
    }

    log.info("Calculating recall after selecting the longest concept...");
    calculateRecallForAnnotatedConcepts(questionsCopyWithLongestMetaMapConcept, questionsWithAdditionalConcepts);

    log.info("Generate LogicalTrees for questions with the longest concept...");
    final Map<String, Boolean> ltCoverageWithLongestConcept = new HashMap<>();
    final Map<Document, List<LogicalTree>> logicalTreesLongestConcept =
        generateLogicalTrees(
            questionsCopyWithLongestMetaMapConcept, lexEntries, ltCoverageWithLongestConcept, true);

    log.info("Performing leave-one-out evaluation for the questions with longest concept...");
    final Pair<Map<Document, List<LogicalTree>>, List<Document>> filteredPairLongestConcept =
        filterLogicalTreesAndQuestionsWithNoGold(
            logicalTreesLongestConcept, questionsCopyWithLongestMetaMapConcept);
    final Map<Document, List<LogicalTree>> logicalTreesLongestConceptOnlyGold =
        filteredPairLongestConcept.getFirst();
    final MachineLogicalTreeScorer scorerEndToEndLongestConcept = new SVMLogicalTreeScorer();
    scorerEndToEndLongestConcept.train(logicalTreesLongestConceptOnlyGold);

    final Pair<Double, Map<String, LogicalFormPrediction>> lfAccAndPredPairWithLongestConcept =
        scorerEndToEndLongestConcept.leaveOneOut(
            logicalTreesWithAdditionalConcepts, logicalTreesLongestConceptOnlyGold, logicalTreesLongestConcept);
    final Map<String, LogicalFormPrediction> lfPredictionsWithLongestConcept =
        lfAccAndPredPairWithLongestConcept.getSecond();


    log.info("Filtering concepts based on the ones present in EHR...");
    // Loading the concepts present in EHR
    final Place conceptCodesInEHRFile = Config.get(Run.class, "conceptCodesInEHR").toPlace();
    final CSV conceptCodesInEHRCsv = new CSV(conceptCodesInEHRFile);
    this.conceptCodesInEHR =
        conceptCodesInEHRCsv.getAllUniqueValuesInColumn(conceptCodesInEHRCsv.getHeaderIndex("code"));
    for (Document question: questionsCopyWithoutOriginalConcepts) {
      log.DBG("Question {0}: {1}", question.getDocumentID(), question.wrap());
      final List<Concept> metaMapConcepts =
          question.getAnnotations(Concept.class).stream().filter(c -> c.getCUI() != null).collect(Collectors.toList());
      final List<Concept> references =
          question.getAnnotations(Concept.class).stream().filter(c -> c.getCUI() == null).collect(Collectors.toList());
      List<Concept> metaMapConceptsFiltered =
          ConceptExtractor.prioritizeReferencesOverMetaMapConcepts(metaMapConcepts, references);

      final List<Concept> metaMapConceptsInEHR =
          metaMapConceptsFiltered.stream().filter(c -> conceptCodesInEHR.contains(c.getCUI()))
              .collect(Collectors.toList());
      List<Concept> metaMapConceptsCandidates;
      if (metaMapConceptsInEHR.size() == 0) {
        metaMapConceptsCandidates = metaMapConceptsFiltered;
      } else {
        metaMapConceptsCandidates = metaMapConceptsInEHR;
      }
      metaMapConceptsCandidates = ConceptExtractor.removeDuplicateConcepts(metaMapConceptsCandidates, true);
      final Set<Concept> finalMetaMapConceptsCandidates = new HashSet<>(metaMapConceptsCandidates);
      List<Concept> metaMapConceptsToRemove =
          metaMapConcepts.stream().filter(c -> !finalMetaMapConceptsCandidates.contains(c))
              .collect(Collectors.toList());
      for (Concept concept: metaMapConceptsToRemove) {
        concept.detach();
      }
      log.DBG("Filtered concepts:");
      printConcepts(question);
    }

    // Calculate recall for the MetaMap annotated concepts
    log.info("Calculating recall after filtering the concepts...");
    calculateRecallForAnnotatedConcepts(questionsCopyWithoutOriginalConcepts, questionsWithAdditionalConcepts);

    // Generate LogicalTrees for the questions with automatically annotated concepts
    final Map<String, Boolean> ltCoverageWithAllMMConcepts = new HashMap<>();
    final Map<Document, List<LogicalTree>> logicalTreesAutoAnnotated =
            generateLogicalTrees(questionsCopyWithoutOriginalConcepts, lexEntries, ltCoverageWithAllMMConcepts, true);

    log.info("Performing leave-one-out evaluation for the auto-annotated questions...");
    final Pair<Map<Document, List<LogicalTree>>, List<Document>> filteredPairAutoAnnotated =
        filterLogicalTreesAndQuestionsWithNoGold(logicalTreesAutoAnnotated, questionsCopyWithoutOriginalConcepts);
    final Map<Document, List<LogicalTree>> logicalTreesAutoAnnotatedOnlyGold = filteredPairAutoAnnotated.getFirst();
    final MachineLogicalTreeScorer scorerEndToEnd = new SVMLogicalTreeScorer();
    scorerEndToEnd.train(logicalTreesAutoAnnotatedOnlyGold);

    final Pair<Double, Map<String, LogicalFormPrediction>> lfAccAndPredPairWithAllMMConcepts =
        scorerEndToEnd.leaveOneOut(
            logicalTreesWithAdditionalConcepts, logicalTreesAutoAnnotatedOnlyGold, logicalTreesAutoAnnotated);
    final Map<String, LogicalFormPrediction> lfPredictionsWithAllMMConcepts = lfAccAndPredPairWithAllMMConcepts.getSecond();


    log.info("Using the longest concept from MetaMap (after filtering the concepts based on EHR)");
    final List<Document> questionsCopyWithLongestMetaMapConceptPostEHRFiltering =
        getQuestionsCopyWithConceptsSatisfyingCondition(
            questions, questionsCopyWithoutOriginalConcepts, conceptLengthComparator);

    log.info("  Selected longest concepts (post EHR filtering):");
    for (Document question: questionsCopyWithLongestMetaMapConceptPostEHRFiltering) {
      log.DBG("Question {0}: {1}", question.getDocumentID(), question.wrap());
      printConcepts(question);
    }

    log.info("Calculating recall after selecting the longest concept (post EHR filtering)...");
    final Map<String, Boolean> ltCoverageWithLongestConceptPostEHRFiltering = new HashMap<>();
    final Map<Document, List<LogicalTree>> logicalTreesLongestConceptPostEHRFiltering =
        generateLogicalTrees(
            questionsCopyWithLongestMetaMapConceptPostEHRFiltering,
            lexEntries,
            ltCoverageWithLongestConceptPostEHRFiltering,
            true);

    log.info("Performing leave-one-out evaluation for the questions with longest concept (post EHR filtering)...");
    final Pair<Map<Document, List<LogicalTree>>, List<Document>> filteredPairLongestConceptPostEHRFiltering =
        filterLogicalTreesAndQuestionsWithNoGold(
            logicalTreesLongestConceptPostEHRFiltering, questionsCopyWithLongestMetaMapConceptPostEHRFiltering);
    final Map<Document, List<LogicalTree>> logicalTreesLongestConceptPostEHRFilteringOnlyGold =
        filteredPairLongestConceptPostEHRFiltering.getFirst();
    final MachineLogicalTreeScorer scorerEndToEndLongestConceptPostEHRFiltering = new SVMLogicalTreeScorer();
    scorerEndToEndLongestConceptPostEHRFiltering.train(logicalTreesLongestConceptPostEHRFilteringOnlyGold);

    final Pair<Double, Map<String, LogicalFormPrediction>> lfAccAndPredPairWithLongestConceptPostEHRFiltering =
        scorerEndToEndLongestConceptPostEHRFiltering.leaveOneOut(
            logicalTreesWithAdditionalConcepts,
            logicalTreesLongestConceptPostEHRFilteringOnlyGold,
            logicalTreesLongestConceptPostEHRFiltering);
    final Map<String, LogicalFormPrediction> lfPredictionsWithLongestConceptPostEHRFiltering =
        lfAccAndPredPairWithLongestConceptPostEHRFiltering.getSecond();


    // Building a TimeFrame classifier
    log.info("Running the time frame classifier...");
    final MachineTimeFrameScorer timeFrameScorer = new SVMTimeFrameScorer();
    final Map<Document, List<TimeFrame>> timeFramesMap = new LinkedHashMap<>();
    for (final Document question : questions) {
      List<TimeFrame> possibleTimeFrames =
              TimeFrame.POSSIBLE_TIME_FRAMES.stream().map(
                      tfs -> new TimeFrame(question, tfs)).collect(Collectors.toList());
      timeFramesMap.put(question, possibleTimeFrames);
    }
    timeFrameScorer.train(timeFramesMap); // Pre-computing all the features
    final Pair<Double, Map<String, Pair<TimeFrame, Boolean>>> tfAccAndPredPair = timeFrameScorer.leaveOneOut(timeFramesMap);
    final Map<String, Pair<TimeFrame, Boolean>> tfPredictions = tfAccAndPredPair.getSecond();

    final Element predictionsSet = new Element("PredictionsSet");
    log.DBG("Document keys for lfPredictionsWithGoldConcepts: {0}", lfPredictionsWithGoldConcepts.keySet());
    log.DBG("Document keys for lfPredictionsWithTopRankedConcept: {0}", lfPredictionsWithTopRankedConcept.keySet());
    log.DBG("Document keys for lfPredictionsWithLongestConcept: {0}", lfPredictionsWithLongestConcept.keySet());
    log.DBG("Document keys for lfPredictionsWithAllMMConcepts: {0}", lfPredictionsWithAllMMConcepts.keySet());
    log.DBG("Document keys for lfPredictionsWithLongestConceptPostEHRFiltering: {0}",
        lfPredictionsWithLongestConceptPostEHRFiltering.keySet());
    final Map<String, Prediction> predictionMap = new HashMap<>();
    for (Document question: questions) {
      log.DBG("Processing predictions for document {0}", question.getDocumentID());
      lfPredictionsWithGoldConcepts.get(question.getDocumentID())
          .setGeneratedLogicalTreesIncludeGold(ltCoverageWithGoldConcepts.get(question.getDocumentID()));
      lfPredictionsWithTopRankedConcept.get(question.getDocumentID())
          .setGeneratedLogicalTreesIncludeGold(ltCoverageWithTopRankedConcept.get(question.getDocumentID()));
      lfPredictionsWithLongestConcept.get(question.getDocumentID())
          .setGeneratedLogicalTreesIncludeGold(ltCoverageWithLongestConcept.get(question.getDocumentID()));
      lfPredictionsWithAllMMConcepts.get(question.getDocumentID())
          .setGeneratedLogicalTreesIncludeGold(ltCoverageWithAllMMConcepts.get(question.getDocumentID()));
      lfPredictionsWithLongestConceptPostEHRFiltering.get(question.getDocumentID())
          .setGeneratedLogicalTreesIncludeGold(
              ltCoverageWithLongestConceptPostEHRFiltering.get(question.getDocumentID()));
      Prediction prediction =
          new Prediction(
              question,
              lfPredictionsWithGoldConcepts.get(question.getDocumentID()),
              lfPredictionsWithTopRankedConcept.get(question.getDocumentID()),
              lfPredictionsWithLongestConcept.get(question.getDocumentID()),
              lfPredictionsWithAllMMConcepts.get(question.getDocumentID()),
              lfPredictionsWithLongestConceptPostEHRFiltering.get(question.getDocumentID()),
              mmCoverageForCUI.get(question.getDocumentID()),
              mmCoverageForBoundary.get(question.getDocumentID()),
              tfPredictions.get(question.getDocumentID()).getFirst(),
              tfPredictions.get(question.getDocumentID()).getSecond());
      predictionMap.put(question.getDocumentID(), prediction);

      predictionsSet.addContent(predictionMap.get(question.getDocumentID()).getXML());
    }


    final Place xmlFile = Place.fromFile("resources/edu/uth/sbmi/olympia/quehry/system_predictions.xml");
    XMLUtil.writeFile(predictionsSet, xmlFile);
  }

  private List<Document> getQuestionsCopyWithConceptsSatisfyingCondition(
      List<Document> questionsOriginal,
      List<Document> questionsWithAllAnnotatedConcepts,
      Comparator<Concept> conceptScoreComparator) {
    final List<Document> questionsCopyWithConceptsSatisfyingCondition =
        questionsOriginal.stream().map(Document::new).collect(Collectors.toList());

    annotateDependency(questionsCopyWithConceptsSatisfyingCondition);

    final Map<String, Document> questionsCopyDocIDMap =
        questionsCopyWithConceptsSatisfyingCondition.stream().collect(Collectors.toMap(
            Document::getDocumentID, Function.identity()
        ));

    for (final Document question: questionsWithAllAnnotatedConcepts) {
      log.DBG("Question {0}: {1}", question.getDocumentID(), question.wrap());
      final List<Concept> metaMapConcepts =
          question.getAnnotations(Concept.class).stream().filter(c -> c.getCUI() != null).collect(Collectors.toList());
      log.DBG("metaMapConcepts:");
      printConcepts(metaMapConcepts);
      final List<Concept> references =
          question.getAnnotations(Concept.class).stream().filter(c -> c.getCUI() == null).collect(Collectors.toList());
      log.DBG("references:");
      printConcepts(references);
      final List<Concept> metaMapConceptsFiltered =
          ConceptExtractor.prioritizeReferencesOverMetaMapConcepts(metaMapConcepts, references);
      log.DBG("metaMapConceptsFiltered:");
      printConcepts(metaMapConceptsFiltered);
      final List<Concept> metaMapConceptsCandidates =
          ConceptExtractor.removeDuplicateConcepts(metaMapConceptsFiltered, true);
      log.DBG("metaMapConceptsCandidates:");
      printConcepts(metaMapConceptsCandidates);

      final Concept conceptSatisfyingCondition =
          ConceptExtractor.getConceptWithCondition(metaMapConceptsCandidates, conceptScoreComparator);
      log.DBG("metaMapConceptsCandidates (after sort):");
      printConcepts(metaMapConceptsCandidates);

      Document questionWithSelectedConcept =
          questionsCopyDocIDMap.get(question.getDocumentID());

      final List<Concept> conceptSatisfyingConditionList;
      if (conceptSatisfyingCondition != null) {
        conceptSatisfyingConditionList = Collections.singletonList(conceptSatisfyingCondition);
        copyConceptToQuestion(conceptSatisfyingCondition, questionWithSelectedConcept);
      } else {
        conceptSatisfyingConditionList = Collections.emptyList();
      }

      log.DBG("conceptSatisfyingCondition:");
      printConcepts(conceptSatisfyingConditionList);

      // Also add references to this question
      for (final Concept reference: references) {
        copyConceptToQuestion(reference, questionWithSelectedConcept);
      }
    }

    return questionsCopyWithConceptsSatisfyingCondition;
  }

  private void copyConceptToQuestion(final Concept concept, final Document question) {
    final Text conceptText = concept.getText();
    final Token firstToken = question.getToken(conceptText.getStartTokenOffset());
    final Token lastToken = question.getToken(conceptText.getEndTokenOffset() - 1);
    final Text conceptSpan = firstToken.union(lastToken);

    Concept conceptCopy = new Concept(conceptSpan, concept);
    conceptCopy.attach();
  }

  private Pair<Map<String, Boolean>, Map<String, Boolean>> calculateRecallForAnnotatedConcepts(
      List<Document> questionsCopyWithoutOriginalConcepts, List<Document> questionsWithGoldConcepts) {
    final Map<String, Boolean> mmCoverageForCUI = new HashMap<>();
    final Map<String, Boolean> mmCoverageForBoundary = new HashMap<>();
    int DBG_autoConceptMatchCUI = 0;
    int DBG_autoConceptMatchBoundary = 0;
    int DBG_multipleGoldConceptsWithCUI = 0;
    int DBG_noGoldConceptWithCUI = 0;
    for (int i = 0; i < questionsCopyWithoutOriginalConcepts.size(); i++) {
      Document qCopy = questionsCopyWithoutOriginalConcepts.get(i);
      Document qOrig = questionsWithGoldConcepts.get(i);
      final List<Concept> autoAnnotatedConcepts =
          qCopy.getAnnotations(Concept.class).stream().filter(c -> c.getCUI() != null).collect(Collectors.toList());
      final List<Concept> goldConcepts =
          qOrig.getAnnotations(Concept.class).stream().filter(c -> c.getCUI() != null).collect(Collectors.toList());
      final Map<String, List<Concept>> goldBoundariesToConcepts = goldConcepts.stream()
          .collect(Collectors.groupingBy(
              (Concept concept) -> concept.getStartTokenOffset() + "," + concept.getEndTokenOffset(),
              Collectors.mapping(
                  Function.identity(), Collectors.toList()
              )));
      if (goldBoundariesToConcepts.size() > 1) {
        log.DBG("More than one gold concepts for question {0}", qOrig.getDocumentID());
        DBG_multipleGoldConceptsWithCUI++;
      } else if (goldBoundariesToConcepts.size() == 0) {
        DBG_noGoldConceptWithCUI++;
      }

      boolean DBG_doesAutoConceptMatchCUIForAll = true;
      boolean DBG_doesAutoConceptMatchBoundaryForAll = true;
      for (final List<Concept> congruentConcepts: goldBoundariesToConcepts.values()) {
        // To check if this boundary is covered by the auto-annotated concepts
        final Concept goldConcept = congruentConcepts.get(0);
        // To check if any of the gold CUIs are covered by the auto-annotated concepts
        final Set<String> goldCUIs = congruentConcepts.stream().map(Concept::getCUI).collect(Collectors.toSet());

        log.DBG("Gold boundary: {0}", goldConcept);
        log.DBG("Additional CUIs:");
        final List<String> conceptDetails = congruentConcepts.stream()
            .map(c -> Arrays.asList(c.getValue(), c.getType(), c.getCUI()).toString()).collect(Collectors.toList());
        log.DBG("  {0}", String.join("\n  ", conceptDetails));
        boolean DBG_doesAutoConceptMatchCUI = false;
        boolean DBG_doesAutoConceptMatchBoundary = false;
        for (final Concept autoConcept : autoAnnotatedConcepts) {
          log.DBG("Checking automatically annotated concept: {0} {1} ", autoConcept,
              Arrays.asList(autoConcept.getValue(), autoConcept.getType(), autoConcept.getCUI()).toString());
          if (autoConcept.isCongruent(goldConcept, true)) {
            // A match at boundary level
            log.DBG("  Boundary-level match");
            DBG_doesAutoConceptMatchBoundary = true;
          }
          if (goldCUIs.contains(autoConcept.getCUI())) {
            // A match at CUI level
            DBG_doesAutoConceptMatchCUI = true;
            log.DBG("  CUI-level match");
          }
        }

        if (!DBG_doesAutoConceptMatchCUI) {
          DBG_doesAutoConceptMatchCUIForAll = false;
        }
        if (!DBG_doesAutoConceptMatchBoundary) {
          DBG_doesAutoConceptMatchBoundaryForAll = false;
        }
      }

      if (DBG_doesAutoConceptMatchCUIForAll) {
        DBG_autoConceptMatchCUI++;
        mmCoverageForCUI.put(qOrig.getDocumentID(), true);
      } else {
        mmCoverageForCUI.put(qOrig.getDocumentID(), false);
      }
      if (DBG_doesAutoConceptMatchBoundaryForAll) {
        DBG_autoConceptMatchBoundary++;
        mmCoverageForBoundary.put(qOrig.getDocumentID(), true);
      } else {
        mmCoverageForBoundary.put(qOrig.getDocumentID(), false);
      }
    }

    log.DBG("{0} -- multiple concepts with CUI", DBG_multipleGoldConceptsWithCUI);
    log.DBG("{0} -- no concepts with CUI", DBG_noGoldConceptWithCUI);
    int questionCount = questionsWithGoldConcepts.size();
    log.DBG("{0}/{1} ({2}%) Questions have all gold CUIs covered by MetaMap.",
        DBG_autoConceptMatchCUI,
        questionCount,
        100.0 * DBG_autoConceptMatchCUI / questionCount);
    log.DBG("{0}/{1} ({2}%) Questions have all gold boundaries covered by MetaMap.",
        DBG_autoConceptMatchBoundary,
        questionCount,
        100.0 * DBG_autoConceptMatchBoundary / questionCount);

    log.DBG("mmCoverageForCUI: {0}", mmCoverageForCUI);
    log.DBG("mmCoverageForBoundary: {0}", mmCoverageForBoundary);

    return Pair.of(mmCoverageForCUI, mmCoverageForBoundary);
  }

  private void printConcepts(Document question) {
    final List<String> conceptDetails = question.getAnnotations(Concept.class).stream()
        .map(c -> Arrays.asList(c, c.getType(), c.getCUI()).toString()).collect(Collectors.toList());
    log.DBG("  {0}", String.join("\n  ", conceptDetails));
  }

  private void printConcepts(List<Concept> concepts) {
    final List<String> conceptDetails = concepts.stream()
        .map(c -> Arrays.asList(c, c.getType(), c.getCUI(), c.getScore()).toString()).collect(Collectors.toList());
    log.DBG("  {0}", String.join("\n  ", conceptDetails));
  }


  private Map<Document, List<LogicalTree>> generateLogicalTrees(
      List<Document> questions,
      List<LexiconEntry> lexEntries,
      Map<String, Boolean> logicalTreeCoverage,
      boolean allowConceptTokensMatching) {
    // Create LexiconMatchTrees for each Document
    log.info("Creating LexiconMatchTrees...");
    final LexiconMatchTreeGenerator lexTreeGenerator =
            new LexiconMatchTreeGenerator(lexEntries, allowConceptTokensMatching);
    final Map<Document,List<LexiconMatchTree>> lexiconMatchTrees =
            new LinkedHashMap<>();
    for (final Document question : questions) {
      log.DBG("Question: {0}", question.getDocumentID());
      final Sentence sentence = question.getOnlyCongruent(Sentence.class);
      final List<LexiconMatchTree> lexMatchTrees = new ArrayList<>(lexTreeGenerator.generate(sentence));
      if (lexMatchTrees.isEmpty()) {
        log.DBG("No lexicon match trees for question");
      }
      lexiconMatchTrees.put(question, lexMatchTrees);
    }

    // Create LogicalTrees
    int DBG_questions_without_gold = 0;
    log.info("Creating LogicalTrees...");
    final Map<Document,List<LogicalTree>> logicalTrees = new LinkedHashMap<>();

    final LogicalTreeGenerator logicalTreeGenerator =
            new SimpleDependencyBasedLogicalTreeGenerator();
    final Set<String> DBG = new TreeSet<>();
    for (final Document question : questions) {
      log.DBG("Question {0}: {1}", question.getDocumentID(), question.wrap());

      // Begin DBG
      for (final LogicalForm logicalForm : question.getSub(LogicalForm.class)) {
        log.DBG("  LogicalForm: {0}", logicalForm.simpleLogicalForm());
      }
      // End DBG

      final LogicalForm logicalForm = question.getOnlySub(LogicalForm.class);
      final String gold = logicalForm.simpleLogicalForm();

      logicalTreeGenerator.DBG_gold = gold;
      log.DBG("  Gold: {0}", gold);
      for (final LexiconMatchTree lexMatchTree : lexiconMatchTrees.get(question)) {
        if (logicalTreeGenerator.getLogicalOps(lexMatchTree).isEmpty()) {
          log.DBG("  *** No LogicalOps in LexiconMatchTree.  Rules:");
          for (final String pattern : lexMatchTree.getMatchedRules()) {
            log.DBG("  ***   {0}", pattern);
          }
        }
      }

      logicalTrees.put(question,
              logicalTreeGenerator.getLogicalTrees(
                      lexiconMatchTrees.get(question)));
      final Map<String,Set<String>> matches = new TreeMap<>();
      final Map<String,Set<String>> tokenMatches = new TreeMap<>();
      for (final LexiconMatchTree lexMatchTree : lexiconMatchTrees.get(question)) {
        for (final LexiconMatch lexMatch : lexMatchTree.getLexiconMatches()) {
          final LexiconEntry entry = lexMatch.getEntry();
          final List<Token> tokens = lexMatch.getTokens();
          final String key = entry.getPattern();
          if (!matches.containsKey(key)) {
            matches.put(key, new TreeSet<String>());
            tokenMatches.put(key, new TreeSet<String>());
          }
          matches.get(key).add(entry.getLogicalForm());
          tokenMatches.get(key).add(tokens.toString());
        }
      }

      log.DBG("  ** All Lexicon Matches:");
      for (final String str : matches.keySet()) {
        log.DBG("       {0} -> {1}\t\tT - {2}", str, matches.get(str), Strings.join(tokenMatches.get(str), " ~~ "));
      }

      boolean hasGold = false;
      for (final LogicalTree logicalTree : logicalTrees.get(question)) {
        final String flatTree = LogicalTree.flattenTree(logicalTree.getRoot());
        log.DBG("  -> Candidate LogicalTree: {0}{1}", flatTree,
            flatTree.equals(gold) ? "  [GOLD]" : "");
        hasGold = hasGold || flatTree.equals(gold);
      }

      if (!hasGold) {
        log.DBG("No gold logical form");
        log.DBG("  Gold: {0}", gold);
        final Set<String> matchedLogicalOps = new HashSet<>();
        for (final LexiconMatchTree lexMatchTree : lexiconMatchTrees.get(question)) {
          final List<String> logicalOps = logicalTreeGenerator.getLogicalOps(lexMatchTree);
          matchedLogicalOps.addAll(logicalOps);
        }

        final Set<String> missedOps = new TreeSet<>();
        final String gold_parsable = gold.replace("(", " ")
                .replace(")", " ")
                .replace(" ^ ", " ")
                .replaceAll("\\s+", " ");
        for (final String op : Strings.split(gold_parsable, " ")) {
          if (op.length() == 0) {
            continue;
          }
          DBG.add(op);
          if (!matchedLogicalOps.contains(op)) {
            missedOps.add(op);
          }
        }

        log.DBG("  ** {0}No Gold LogicalTree [LEXICON]{1}",
                missedOps.isEmpty() ? "" : "  Missed Ops: " + missedOps);
        DBG_questions_without_gold++;
        logicalTreeCoverage.put(question.getDocumentID(), false);
      } else {
        logicalTreeCoverage.put(question.getDocumentID(), true);
      }
    }

    final Map<String, Integer> treeNumbers = new HashMap<>();

    int min = 999;
    int max = 0;

    for(Document question : questions){
      int no_trees = logicalTrees.get(question).size();
      String numberQ = question.getDocumentID();
      if(treeNumbers.containsKey(numberQ)){
        log.severe("Duplicate question");
        System.exit(1);
      }
      treeNumbers.put(numberQ, no_trees);
      if(min > no_trees && no_trees > 0){
        min = no_trees;
      }
      if(max < no_trees){
        max = no_trees;
      }
    }
    log.DBG("max = {0}, min = {1}", max, min);

    log.DBG("All Unmatched Ops: {0}", DBG);
    final int DBG_haveGold = questions.size() - DBG_questions_without_gold;
    log.DBG("{0}/{1} ({2}%) Questions have a gold LogicalTree candidate.",
            DBG_haveGold, questions.size(),
            100.0 * DBG_haveGold / questions.size());

    return logicalTrees;
  }

  private Pair<Map<Document, List<LogicalTree>>, List<Document>> filterLogicalTreesAndQuestionsWithNoGold(
      final Map<Document,List<LogicalTree>> logicalTrees, final List<Document> questions) {
    Map<Document, List<LogicalTree>> logicalTreesFiltered = new HashMap<>(logicalTrees);
    List<Document> questionsFiltered = new ArrayList<>(questions);
    // Removing the questions with no GOLD logical form
    logicalTreesFiltered.entrySet().removeIf(
        l -> l.getValue().stream().noneMatch(
            c -> LogicalTree.flattenTree(c.getRoot()).equals(
                l.getKey().getOnlySub(LogicalForm.class).simpleLogicalForm())));

    int questionCountBeforeRemoving = questionsFiltered.size();
    questionsFiltered.removeIf(q -> !logicalTreesFiltered.containsKey(q));
    int questionCountAfterRemoving = questionsFiltered.size();
    int questionsRemoved = questionCountBeforeRemoving - questionCountAfterRemoving;
    log.DBG("Number of questions removed with no GOLD logical form: {0}", questionsRemoved);
    log.DBG("  # questions before removing: {0}", questionCountBeforeRemoving);
    log.DBG("  # questions after removing: {0}", questionCountAfterRemoving);

    return Pair.of(logicalTreesFiltered, questionsFiltered);
  }

  /**
   * Annotates the {@link Dependency}s, or loads from a cache if available.
   */
  private void annotateDependency(final List<Document> questions) {
    final Map<String,Document> cached = new LinkedHashMap<>();
    if (dependencyCache.exists()) {
      final XMLDocumentReader xmlReader = new XMLDocumentReader();
      try {
        for (final Document question : xmlReader.readAll(dependencyCache)) {
          cached.put(question.asRawString(), question);
        }
      }
      catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
    
    log.info("Loaded {0} Questions from Dependency Cache", cached.size());
    boolean updateCache = false;
    for (final Document question : questions) {
      if (cached.containsKey(question.asRawString())) {
        final Document cachedQuestion = cached.get(question.asRawString());
        assert cachedQuestion.getTokenLength() == question.getTokenLength();
        // change the cached question for replacing each concept words with "concept" or "patient" etc.
        final List<Token> tokens = question.getTokens();
        for (final Dependency dependency :
             cachedQuestion.getSub(Dependency.class)) {
          final Text govCache = dependency.getGovernor();
          final Text depCache = dependency.getDependent();
          final Text gov = tokens.get(govCache.getStartTokenOffset()).union(
                           tokens.get(govCache.getEndTokenOffset()-1));
          final Text dep = tokens.get(depCache.getStartTokenOffset()).union(
                           tokens.get(depCache.getEndTokenOffset()-1));
          assert gov.asRawString().equals(govCache.asRawString());
          assert dep.asRawString().equals(depCache.asRawString());
          new Dependency(gov, dep, dependency.getType()).attach();
        }
        question.addAnnotatedType(Dependency.TYPE);
      }
      else {
        log.info("  Not in Dependency Cache: {1}: {0}", question.asRawString(),
            question.getDocumentID());
        question.annotate(Dependency.TYPE);
        cached.put(question.asRawString(), question);
        updateCache = true;
      }
    }
    if (updateCache) {
      log.info("Updating Dependency Cache");
      try {
        new XMLDocumentWriter().writeAll(cached.values(), dependencyCache);
      }
      catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
  }

  /**
   * Command line (intended entry).
   */
  public static void main(String[] argv) throws Exception {
    argv = Config.init("olympia.properties", argv);
 
    new Run().process();
  }
}
