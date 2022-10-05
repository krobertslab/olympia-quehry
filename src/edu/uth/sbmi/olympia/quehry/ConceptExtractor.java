package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.util.Config;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Place;
import edu.uth.sbmi.olympia.util.json.JSONUtil;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts all the {@link Concept}s from a given question {@link Document}.
 */
public class ConceptExtractor implements AutoCloseable {
    private static final Log log = new Log(ConceptExtractor.class);
    private final MetaMapClient metaMapClient;
    private final Map<String, List<Pattern>> referencePatterns;

    public ConceptExtractor() throws IOException, InterruptedException, ParseException {
        metaMapClient = new MetaMapClient();
        final Place referencesFile = Config.get(ConceptExtractor.class, "referencePatterns").toPlace();
        referencePatterns = JSONUtil.getReferencePatterns(referencesFile);
    }

    public void annotateConcepts(Document question) throws Exception {
        List<Concept> references = extractReferences(question);
        List<Concept> metaMapConcepts = this.metaMapClient.fetchConcepts(question);
        List<String> referencesDetails = references.stream().map(c -> Arrays.asList(c, c.getType()).toString())
            .collect(Collectors.toList());
        log.DBG("Found references:");
        log.DBG("  {0}", String.join("\n  ", referencesDetails));
        List<String> conceptDetails = metaMapConcepts.stream()
            .map(c -> Arrays.asList(c, '"' + c.getValue() + '"', c.getType(), c.getCUI()).toString())
            .collect(Collectors.toList());
        log.DBG("Found metamap concepts:");
        log.DBG("  {0}", String.join("\n  ", conceptDetails));

        references = filterContainedAndDuplicateConcepts(references);

        for (Concept ref: references) {
            ref.attach();
        }

        for (Concept c: metaMapConcepts) {
            c.attach();
        }
    }

    private List<Concept> extractReferences(Document question) throws IOException, ParseException {
        final List<Concept> concepts = new ArrayList<>();
        for (Map.Entry<String, List<Pattern>> referenceEntry: this.referencePatterns.entrySet()) {
            for (Pattern pattern: referenceEntry.getValue()) {
                final List<Text> matchedSpans = question.findAll(pattern);
                for (Text span: matchedSpans) {
                    Concept concept = new Concept(span, referenceEntry.getKey());
                    concepts.add(concept);
                }
            }
        }

        return concepts;
    }

    public static List<Concept> removeDuplicateConcepts(final List<Concept> concepts) {
        return removeDuplicateConcepts(concepts, false);
    }

    public static List<Concept> removeDuplicateConcepts(final List<Concept> concepts, final boolean keepHighestScore) {
        Map<String, Concept> map = new HashMap<>();
        for (Concept concept: concepts) {
            String key = concept.getText().getStartTokenOffset() + "," + concept.getText().getEndTokenOffset();
            if (!map.containsKey(key)) {
                map.put(key, concept);
            } else if (keepHighestScore) {
                final Integer score = concept.getScore();
                final Concept existingConcept = map.get(key);
                final Integer existingScore = existingConcept.getScore();
                if (score == null) {
                    continue;
                }
                if (existingScore != null && existingScore >= score) {
                    continue;
                }
                map.put(key, concept);
            }
        }

        final List<Concept> deduplicatedConcepts = new ArrayList<>(map.values());

        return deduplicatedConcepts;
    }

    public static Concept getConceptWithCondition(
        final List<Concept> concepts, final Comparator<? super Concept> comparator) {
        if (concepts.size() == 0) {
            log.DBG("Empty list of concepts, returning null");
            return null;
        }
        log.DBG("Before sort:");
        printConcepts(concepts);
        concepts.sort(comparator);
        log.DBG("After sort:");
        printConcepts(concepts);
        return concepts.get(0);
    }

    private static void printConcepts(List<Concept> concepts) {
        final List<String> conceptDetails = concepts.stream()
            .map(c -> Arrays.asList(c, c.getType(), c.getCUI(), c.getScore()).toString()).collect(Collectors.toList());
        log.DBG("  {0}", String.join("\n  ", conceptDetails));
    }

    public static List<Concept> removeContainedConcepts(final List<Concept> concepts) {
        final List<Concept> filteredConcepts = new ArrayList<>();
        for (Concept c1: concepts) {
            boolean containedInOther = false;
            for (Concept c2: concepts) {
                if (c1 != c2 && c1.getText().isSuper(c2)) {
                    containedInOther = true;
                    break;
                }
            }

            if (!containedInOther) {
                filteredConcepts.add(c1);
            }
        }

        return filteredConcepts;
    }

    private List<Concept> filterContainedAndDuplicateConcepts(final List<Concept> concepts) {
        // Remove the duplicate concepts
        final List<Concept> deduplicatedConcepts = removeDuplicateConcepts(concepts);

        // Remove the concepts which are contained in other bigger concepts
        final List<Concept> filteredConcepts = removeContainedConcepts(deduplicatedConcepts);

        return filteredConcepts;
    }

    public static List<Concept> prioritizeReferencesOverMetaMapConcepts(
            final List<Concept> metaMapConcepts,
            final List<Concept> references) {
        final List<Concept> metaMapConceptsCopy = new ArrayList<>(metaMapConcepts);
        for (Concept c: metaMapConcepts) {
            if (references.stream().anyMatch(c::isSuper)) {
                metaMapConceptsCopy.remove(c);
            }
        }

        return metaMapConceptsCopy;
    }

    @Override
    public void close() throws Exception {
        metaMapClient.close();
    }
}
