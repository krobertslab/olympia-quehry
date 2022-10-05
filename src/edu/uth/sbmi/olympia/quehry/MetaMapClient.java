package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.text.Sentence;
import edu.uth.sbmi.olympia.text.Text;
import edu.uth.sbmi.olympia.text.Token;
import edu.uth.sbmi.olympia.util.Config;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Place;
import edu.uth.sbmi.olympia.util.Strings;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Mapping;
import gov.nih.nlm.nls.metamap.MetaMapApi;
import gov.nih.nlm.nls.metamap.MetaMapApiImpl;
import gov.nih.nlm.nls.metamap.PCM;
import gov.nih.nlm.nls.metamap.Position;
import gov.nih.nlm.nls.metamap.Result;
import gov.nih.nlm.nls.metamap.Utterance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetaMapClient implements AutoCloseable {
    private static final Log log = new Log(MetaMapClient.class);
    private MetaMapApi api;
    private final Place mmServerPath;
    private final String mmServerInitScript;
    private final Set<String> specialTermsToExclude;
    private Process process;
    private final String FULL_METAMAP_OPTIONS_RELAXED = "-C -y -D -b -z -c -g -V USAbase";
    private final String FULL_METAMAP_OPTIONS_STRICT = "-A -y -D -b -z -c -g -V USAbase";
    private final String REDUCED_METAMAP_OPTIONS_RELAXED = "-C -y -D -b -z -c -V USAbase";
    private final String REDUCED_METAMAP_OPTIONS_STRICT = "-A -y -D -b -z -c -V USAbase";
    private final int MAX_METAMAP_TRIES = 7;
    private final int METAMAP_API_TIMEOUT = 30 * 1000;

    public MetaMapClient() throws IOException, InterruptedException {
        mmServerPath = Config.get(MetaMapClient.class, "mmServerPath").toPlace();
        mmServerInitScript = "./bin/mmserver" + Config.get(MetaMapClient.class, "mmServerVersion").toString();
        startMetaMapServer();
        final Place specialTermsFile = Config.get(MetaMapClient.class, "specialTermsToExclude").toPlace();
        specialTermsToExclude = loadSpecialTerms(specialTermsFile);
    }

    private Set<String> loadSpecialTerms(final Place file) throws IOException {
        final Set<String> specialTerms = new HashSet<>();
        for (final String line : file.readLines()) {
            if (line.startsWith("#")) {
                continue;
            }

            final List<String> split = Strings.split(line, ":");
            assert split.size() == 2 :
                    "should be just 2 fields (found " + split.size() + "): " + line;
            final String termCUI = split.get(0);
            specialTerms.add(termCUI);
        }

        return specialTerms;
    }

    public List<Concept> fetchConcepts(final Document question) throws Exception {
        int tryCount = 0;
        List<Concept> resultList;

        while(true) {
            try {
                String metaMapOptionsToUse;
                if (tryCount == 0) {
                    metaMapOptionsToUse = FULL_METAMAP_OPTIONS_RELAXED;
                } else if (tryCount == 1) {
                    metaMapOptionsToUse = FULL_METAMAP_OPTIONS_STRICT;
                } else if (tryCount == 2) {
                    metaMapOptionsToUse = REDUCED_METAMAP_OPTIONS_RELAXED;
                } else {
                    metaMapOptionsToUse = REDUCED_METAMAP_OPTIONS_STRICT;
                }

                log.DBG("[Try {0}] MetaMap options: {1}", tryCount + 1, metaMapOptionsToUse);
                resultList = fetchConcepts(question, metaMapOptionsToUse);
                break;
            } catch (Exception exception) {
                log.severe("Caught an exception: {0}", exception);
                if (++tryCount == MAX_METAMAP_TRIES) {
                    resultList = new ArrayList<>();
                    break;
                }

                destroyMetaMapServer();
                startMetaMapServer();
            }
        }

        return resultList;
    }

    private void startMetaMapServer() throws InterruptedException, IOException {
        log.DBG("Starting the mmserver...");
        ProcessBuilder pb = new ProcessBuilder(mmServerInitScript);
        pb.directory(mmServerPath.toFile());
        process = pb.start();
        Thread.sleep(5000);
        api = new MetaMapApiImpl();
        api.setTimeout(METAMAP_API_TIMEOUT);
    }

    private void destroyMetaMapServer() throws InterruptedException {
        log.DBG("Destroying the mmserver...");
        api.disconnect();
        process.destroy();
        process.waitFor(10, TimeUnit.SECONDS);
    }

    private List<Concept> fetchConcepts(final Document question, String metamapOptions) throws Exception {
        api.setOptions(metamapOptions);
        final List<Concept> concepts = new ArrayList<>();
        log.DBG("Calling the server...");
        List<Result> resultList = api.processCitationsFromString(question.asRawString());
        Result result = resultList.get(0);

        for (Utterance utterance: result.getUtteranceList()) {
            log.DBG("Utterance:");
            log.DBG(" Id: " + utterance.getId());
            log.DBG(" Utterance text: " + utterance.getString());
            log.DBG(" Position: " + utterance.getPosition());

            for (PCM pcm: utterance.getPCMList()) {
                log.DBG("Phrase:");
                log.DBG("  text: " + pcm.getPhrase().getPhraseText());

                log.DBG("Candidates:");
                for (Ev candidate: pcm.getCandidateList()) {
                    final int score = - candidate.getScore(); // Because MetaMap returns a negative score

                    log.DBG("  Score: " + score);
                    log.DBG("    Concept Id: " + candidate.getConceptId());
                    log.DBG("    Concept Name: " + candidate.getConceptName());
                    log.DBG("    Preferred Name: " + candidate.getPreferredName());
                    log.DBG("    Matched Words: " + candidate.getMatchedWords());
                    log.DBG("    Semantic Types: " + candidate.getSemanticTypes());
                    log.DBG("    Positional Info: " + candidate.getPositionalInfo());
                    final int startIndex = candidate.getPositionalInfo().get(0).getX();
                    final Position lastPIObject =
                        candidate.getPositionalInfo().get(candidate.getPositionalInfo().size() - 1);
                    final int lastPIStartInd = lastPIObject.getX();
                    final int lastPILength = lastPIObject.getY();
                    final int endIndex = lastPIStartInd + lastPILength - 1;
                    final Token firstToken = question.findToken(startIndex);
                    final Token lastToken = question.findToken(endIndex);
                    final Text span = firstToken.union(lastToken);
                    final String type = candidate.getSemanticTypes().get(0);
                    String cui = candidate.getConceptId();
                    cui = cui.startsWith("C") ? cui.substring(1) : cui;
                    final String value = candidate.getPreferredName();
                    if (specialTermsToExclude.contains(cui)) {
                        log.DBG("Excluded special term: name = {0}, type = {1}, cui = {2}",
                            candidate.getConceptName(), type, cui);
                        continue;
                    }

                    Concept concept = new Concept(span, type, null, cui, value, score);
                    concepts.add(concept);
                }
            }
        }
        return concepts;
    }

    public static void main(String[] argv) throws Exception {
        argv = Config.init("olympia.properties", argv);

        final String[] inputQuestion;
        if (argv.length == 0) {
            inputQuestion = null;
        }
        else {
            inputQuestion = argv;
        }
        assert inputQuestion != null;
        try (final MetaMapClient metaMapClient = new MetaMapClient()) {
            for (int i = 0; i < inputQuestion.length; i++) {
                log.DBG("[Question. {0}] {1}", i + 1, inputQuestion[i]);
                Document document = new Document(inputQuestion[i]);
                document.annotate(Sentence.TYPE);
                final List<Concept> concepts = metaMapClient.fetchConcepts(document);
                log.DBG("Extracted the following concepts: {0}", concepts);
            }
        }
    }

    @Override
    public void close() throws Exception {
        destroyMetaMapServer();
    }
}
