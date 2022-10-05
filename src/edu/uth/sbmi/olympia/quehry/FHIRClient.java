package edu.uth.sbmi.olympia.quehry;

import edu.uth.sbmi.olympia.text.Document;
import edu.uth.sbmi.olympia.util.Config;
import edu.uth.sbmi.olympia.util.Log;
import edu.uth.sbmi.olympia.util.Place;
import edu.uth.sbmi.olympia.util.TreeNode;
import edu.uth.sbmi.olympia.util.json.JSONUtil;
import edu.uth.sbmi.olympia.util.xml.XMLUtil;
import org.jdom2.Element;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Wrapper code for converting the logical forms to FHIR queries and executing them.
 */
public class FHIRClient {
  private static final Log log = new Log(FHIRClient.class);

  private FHIRClient () { }

  public static FHIRResponse execute(TreeNode<String> logicalTreeNode, Document question) {
    return execute(new LogicalTree(logicalTreeNode, null), question, null, true);
  }

  public static FHIRResponse execute(LogicalTree logicalTree, Document question, TimeFrame timeFrame) {
    return execute(logicalTree, question, timeFrame, false);
  }

  public static FHIRResponse execute(LogicalTree logicalTree, Document question, TimeFrame timeFrame, final boolean isGold) {
    if (logicalTree == null) {
      return null;
    }

    Boolean enableFHIR = Config.get(FHIRClient.class, "enableFHIR").toBoolean();
    if (!enableFHIR) {
      log.DBG("FHIR disabled!");
      return null;
    }

    String pathToPythonScript = Config.get(FHIRClient.class, "pythonProjectPath").toPlace().getPath() + "/src/data";
    String pythonScriptName = "fhir_driver.py";
    String outputFilePath = "/tmp/outputFHIR.json";
    String currentTime = Config.get(FHIRClient.class, "currentTime").toString();
    String apiKey = Config.get(FHIRClient.class, "apiKey").toString();

    final Element element = LogicalTree.convertToXML(logicalTree, question, timeFrame, isGold);
    String xmlStr = XMLUtil.toString(element);
    log.DBG("logicalTree (XML): {0}", xmlStr);

    final Answer answer = question.getOnlySub(Answer.class);
    final String patientID = answer.getPatientID();

    String[] cmd = {
        "python3",
        pythonScriptName,
        "-lf_xml_str",
        xmlStr,
        "-output_file",
        outputFilePath,
        "-api_key",
        apiKey,
        "-pat_id",
        patientID,
        "-c_time",
        currentTime
    };

    FHIRResponse fhirResponse = null;
    try {
      log.info("Calling python script to convert logical form to FHIR query");
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(new File(pathToPythonScript));
      Process pr = pb.start();
      log.DBG("Output from the python script:");
      log.DBG(inputStreamToString(pr.getInputStream()));
      final int exitValue = pr.waitFor();

      if (exitValue == 0) {
        log.DBG("Python script completed successfully!");

        Place outputFile = Place.fromFile(outputFilePath);
        final String rootPredicate = logicalTree.getRoot().getItem();
        fhirResponse = JSONUtil.parseFHIRResponse(outputFile, rootPredicate);
        log.DBG(fhirResponse.toString());
      } else {
        log.severe("Error in the python script");
        log.severe("Exit value is: {0}", exitValue);
        log.severe(inputStreamToString(pr.getErrorStream()));
      }
    } catch (IOException | InterruptedException e) {
      log.severe("Error while calling the python script");
      log.severe(stackTraceToString(e));
    }

    return fhirResponse;
  }

  private static String inputStreamToString(InputStream is) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();
    String line;
    while((line = br.readLine()) != null) {
      sb.append(line);
      sb.append("\n");
    }

    return sb.toString().trim();
  }

  private static String stackTraceToString(Throwable e) {
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement element : e.getStackTrace()) {
      sb.append(element.toString());
      sb.append("\n");
    }

    return sb.toString().trim();
  }
}
