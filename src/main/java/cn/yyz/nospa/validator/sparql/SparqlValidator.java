package cn.yyz.nospa.validator.sparql;

import cn.yyz.nospa.validator.Validator;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The class for the entry point of a SPARQL based validator
 * Created by yyz on 11/4/14.
 */
public class SparqlValidator implements Validator {
    private Model model;
    private Logger logger = LoggerFactory.getLogger(SparqlValidator.class);

    /**
     * Constructor of a SPARQL based validator for an RDF model
     * @param model a Jena RDF model
     */
    public SparqlValidator(Model model) {
        this.model = model;
    }

    /**
     * Constructor of a SPARQL based validator for a file
     * @param filename complete path of the cube file to be validated
     * @param format RDF serialization format of the cube file
     */
    public SparqlValidator(String filename, String format) {
        logger.debug("RDF Cube Validation Result");
        logger.debug("==========================");
        logger.debug("");
        logger.debug("Validator: SPARQL");
        logger.debug(new Date().toString());
        logger.debug(filename);
        logger.debug("");
        logger.info("Loading cube file ...");
        model = ModelFactory.createDefaultModel();
        InputStream inputStream = FileManager.get().open(filename);
        if (inputStream == null) {
            String msg = "File " + filename + " not found";
            logger.error(msg);
            throw new IllegalArgumentException(msg);
        }
        model.read(inputStream, null, format);
    }

    /**
     * Export the current RDF model to a file.
     * @param outputPath file path used for output
     * @param outputFormat RDF serialization format (eg., RDF/XML, TURTLE, ...)
     */
    public void exportModel(String outputPath, String outputFormat) {
        logger.info("Exporting current model to the file " + outputPath);
        try {
            Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outputPath), "utf-8"));
            model.write(writer, outputFormat);
            writer.close();
            logger.info("Exporting completed successfully");
        } catch (IOException ioe) {
            logger.error("The provided file path is not writable");
        }
    }

    /**
     * Normalizes an abbreviated Data Cube with SPARQL queries.
     */
    public void normalize() {
        logger.info("Normalizing cube with SPARQL queries ...");
        String queryString1 = NormalizationAlgorithm.PHASE1.getValue();
        String queryString2 = NormalizationAlgorithm.PHASE2.getValue();
        UpdateAction.parseExecute(queryString1, model);
        UpdateAction.parseExecute(queryString2, model);
    }

    /**
     * A shortcut function to execute all constraint validations.
     */
    public void validateAll() {
        String ic1 = "Integrity Constraint 1: Unique DataSet";
        String msg1 = "The following observations are associated with more than one dataset: ";
        logger.info("Validating " + ic1);
        logValidationResult(ic1, validate("IC1"), msg1);

        String ic2 = "Integrity Constraint 2: Unique DSD";
        String msg2 = "The following datasets are associated with more than one DSD: ";
        logger.info("Validating " + ic2);
        logValidationResult(ic2, validate("IC2"), msg2);

        String ic3 = "Integrity Constraint 3: DSD Includes Measure";
        String msg3 = "The following DSDs do not include at least one declared measure: ";
        logger.info("Validating " + ic3);
        logValidationResult(ic3, validate("IC3"), msg3);

        String ic4 = "Integrity Constraint 4: Dimensions Have Range";
        String msg4 = "The following dimensions do not have a declared rdfs:range: ";
        logger.info("Validating " + ic4);
        logValidationResult(ic4, validate("IC4"), msg4);

        String ic5 = "Integrity Constraint 5: Concept Dimensions Have Code Lists";
        String msg5 = "The following concept dimensions do not have a code list: ";
        logger.info("Validating " + ic5);
        logValidationResult(ic5, validate("IC5"), msg5);

        String ic6 = "Integrity Constraint 6: Only Attributes May Be Optional";
        String msg6 = "The following component properties are not delared as attributes: ";
        logger.info("Validating " + ic6);
        logValidationResult(ic6, validate("IC6"), msg6);

        String ic7 = "Integrity Constraint 7: Slice Keys Must Be Declared";
        String msg7 = "The following slice keys are not associated with DSDs: ";
        logger.info("Validating " + ic7);
        logValidationResult(ic7, validate("IC7"), msg7);

        String ic8 = "Integrity Constraint 8: Slice Keys Consistent With DSD";
        String msg8 = "The following component properties on slice keys are\" +\n" +
                "                \" not associated with DSDs: ";
        logger.info("Validating " + ic8);
        logValidationResult(ic8, validate("IC8"), msg8);

        String ic9 = "Integrity Constraint 9: Unique Slice Structure";
        String msg9 = "The following slice keys are associated with more than one slice structure: ";
        logger.info("Validating " + ic9);
        logValidationResult(ic9, validate("IC9"), msg9);

        String ic10 = "Integrity Constraint 10: Slice Dimensions Complete";
        String msg10 = " does not have values for the following dimensions: ";
        logger.info("Validating " + ic10);
        logValidationResult(ic10, validate("IC10"), msg10);

        String ic11 = "Integrity Constraint 11: All Dimensions Required";
        String msg11 = " does not have values for the following dimensions: ";
        logger.info("Validating " + ic11);
        logValidationResult(ic11, validate("IC11"), msg11);

        String ic12 = "Integrity Constraint 12: No Duplicate Observations";
        String msg12 = " has the same values as: ";
        logger.info("Validating " + ic12);
        logValidationResult(ic12, validate("IC12"), msg12);

        String ic13 = "Integrity Constraint 13: Required Attributes";
        String msg13 = " does not have values for the following required attributes: ";
        logger.info("Validating " + ic13);
        logValidationResult(ic13, validate("IC13"), msg13);

        String ic14 = "Integrity Constraint 14: All Measures Present";
        String msg14 = " does not have values for the following declared measures: ";
        logger.info("Validating " + ic14);
        logValidationResult(ic14, validate("IC14"), msg14);

        String ic15 = "Integrity Constraint 15: Measure Dimension Consistent";
        String msg15 = " corresponds to a wrong measure or does not have a value on: ";
        logger.info("Validating " + ic15);
        logValidationResult(ic15, validate("IC15"), msg15);

        String ic16 = "Integrity Constraint 16: Single Measure On Measure Dimension Observation";
        String msg16 = " has the following multiple measures: ";
        logger.info("Validating " + ic16);
        logValidationResult(ic16, validate("IC16"), msg16);

        String ic17 = "Integrity Constraint 17: All Measures Present In Measures Dimension Cube";
        String msg17 = " shares the same dimension values with the following number of observations: ";
        logger.info("Validating " + ic17);
        logValidationResult(ic17, validate("IC17"), msg17);

        String ic18 = "Integrity Constraint 18: Consistent Dataset Links";
        String msg18 = " should be associated to the following dataset: ";
        logger.info("Validating " + ic18);
        logValidationResult(ic18, validate("IC18"), msg18);

        String ic19 = "Integrity Constraint 19: Codes From Code List";
        String msg19 = " is not included in the following code lists: ";
        logger.info("Validating " + ic19);
        logValidationResult(ic19, validate("IC19"), msg19);

        String ic20 = "Integrity Constraint 20: Codes From Hierarchy";
        String msg20 = " is not connected to the following code lists along a direct path: ";
        logger.info("Validating " + ic20);
        logValidationResult(ic20, validateIC20_21("IC20"), msg20);

        String ic21 = "Integrity Constraint 21: Codes From Hierarchy (Inverse)";
        String msg21 = " is not connected to the following code lists along an inverse path: ";
        logger.info("Validating " + ic21);
        logValidationResult(ic21, validateIC20_21("IC21"), msg21);
    }

    /**
     * The function to validate constraints by SPARQL queries
     * @param constraint name of constraint (e.g., IC1)
     */
    public ResultSet validate(String constraint) {
        return validate(IntegrityConstraint.valueOf(constraint));
    }

    /**
     * The function to validate constraints by SPARQL queries
     * @param constraint constraint defined in an enum class
     */
    public ResultSet validate(IntegrityConstraint constraint) {
        String prefix = IntegrityConstraint.PREFIX.getValue();
        Query query = QueryFactory.create(prefix + constraint.getValue());
        QueryExecution qe = QueryExecutionFactory.create(query, model);
        return qe.execSelect();
    }

    public ResultSet validateIC20_21(String constraint) {
        ResultSet pcpSet = validate(IntegrityConstraint.valueOf(constraint + "A"));
        if (!pcpSet.hasNext()) return pcpSet;
        String var = pcpSet.getResultVars().get(0);
        String prefix = IntegrityConstraint.PREFIX.getValue();
        String queryBase = IntegrityConstraint.valueOf(constraint + "B").getValue();
        Set<QuerySolution> solutionSet = new HashSet<QuerySolution>();
        while (pcpSet.hasNext()) {
            String pcp = pcpSet.next().get(var).toString();
            Query query = QueryFactory.create(prefix + queryBase.replace("$p", pcp));
            QueryExecution qe = QueryExecutionFactory.create(query, model);
            ResultSet resultSet = qe.execSelect();
            while (resultSet.hasNext()) {
                solutionSet.add(resultSet.next());
            }
        }
        return (ResultSet) (solutionSet.iterator());
    }

    public void logValidationResult(String icName, ResultSet resultSet, String msg) {
        logger.debug(icName);
        logger.debug(new String(new char[icName.length()]).replace("\0", "-"));
        logger.debug("");
        if (!resultSet.hasNext()) logger.debug("Pass.");
        else {
            List<String> variables = resultSet.getResultVars();
            if (variables.size() == 1) {
                logger.debug(msg);
                String var = variables.get(0);
                while (resultSet.hasNext()) {
                    QuerySolution querySolution = resultSet.next();
                    logger.debug("    " + querySolution.get(var).toString());
                }
            }
            else if (variables.size() == 2) {
                String var1 = variables.get(0);
                String var2 = variables.get(1);
                while (resultSet.hasNext()) {
                    QuerySolution querySolution = resultSet.next();
                    logger.debug(querySolution.get(var1).toString() + msg);
                    logger.debug("    " + querySolution.get(var2).toString());
                }
            }
        }
        logger.debug("");
    }
}
