package cn.yyz.nospa.validator.nonsparql;

import cn.yyz.nospa.validator.Validator;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.*;

/**
 * The class for the entry point of a validator
 * Created by Yang Yuanzhe on 9/26/14.
 */
public class NospaValidator implements Validator{
    private Logger logger = LoggerFactory.getLogger(NospaValidator.class);
    private Model model;

    /**
     * Constructor of a validator for an RDF model
     * @param model a Jena RDF model
     */
    public NospaValidator(Model model) {
        this.model = model;
    }

    /**
     * Constructor of a validator for a file
     * @param filename complete path of the cube file to be validated
     * @param format RDF serialization format of the cube file
     */
    public NospaValidator(String filename, String format) {
        logger.debug("RDF Cube Validation Result");
        logger.debug("==========================");
        logger.debug("");
        logger.debug("Validator: NoSPA");
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
     * Normalize an abbreviated Data Cube.
     */
    public void normalize() {
        Normalizer normalizer = new Normalizer(model);
        logger.info("Normalizing cube at phase 1 ...");
        normalizer.normalizePhase1();
        logger.info("Normalizing cube at phase 2 ...");
        normalizer.normalizePhase2();
    }

    /**
     * A shortcut function to execute all constraint validations.
     */
    public void validateAll() {
        logger.info("Validating all constraints ...");
        validateIC1();
        validateIC2();
        validateIC3();
        validateIC4();
        validateIC5();
        validateIC6();
        validateIC7();
        validateIC8();
        validateIC9();
        validateIC10();
        validateIC11_12();
        validateIC13();
        validateIC14();
        validateIC15_16();
        validateIC17();
        validateIC18();
        validateIC19();
        validateIC20_21();
    }    

    /**
     * Validate IC-1 Unique DataSet: Every qb:Observation has exactly one
     * associated qb:DataSet.
     * @return a map of observations with multiple datasets
     */
    public Map<Resource, Set<RDFNode>> validateIC1() {
        String icName = "Integrity Constraint 1: Unique DataSet";
        logger.info("Validating " + icName);
        ValidatorIC1 validatorIC1 = new ValidatorIC1(model);
        Map<Resource, Set<RDFNode>> datasetByObs = validatorIC1.validate();
        String logMsg = " is associated to the following datasets: ";
        logValidationResult(icName, datasetByObs, logMsg);
        return datasetByObs;
    }

    /**
     * Validate IC-2 Unique DSD: Every qb:DataSet has exactly one associated
     * qb:DataStructureDefinition.
     * @return a map of datasets with multiple dsds
     */
    public Map<Resource, Set<RDFNode>> validateIC2() {
        String icName = "Integrity Constraint 2: Unique DSD";
        logger.info("Validating " + icName);
        ValidatorIC2 validatorIC2 = new ValidatorIC2(model);
        Map<Resource, Set<RDFNode>> dsdByDataset = validatorIC2.validate();
        String logMsg = " is associated to the following DSDs: ";
        logValidationResult(icName, dsdByDataset, logMsg);
        return dsdByDataset;
    }

    /**
     * Validate IC-3 DSD includes measure: Every qb:DataStructureDefinition
     * must include at least one declared measure.
     * @return a set of DSDs without at least one declared measure
     */
    public Set<Resource> validateIC3() {
        String icName = "Integrity Constraint 3: DSD Includes Measure";
        logger.info("Validating " + icName);
        ValidatorIC3 validatorIC3 = new ValidatorIC3(model);
        Set<Resource> dsdWithoutMeasure = validatorIC3.validate();
        String logMsg = "The following DSDs do not include at least one declared measure: ";
        logValidationResult(icName, dsdWithoutMeasure, logMsg);
        return dsdWithoutMeasure;
    }

    /**
     * Validate IC-4 Dimensions have range: Every dimension declared in a
     * qb:DataStructureDefinition must have a declared rdfs:range.
     * @return a set of dimensions without a declared rdfs:range
     */
    public Set<Resource> validateIC4() {
        String icName = "Integrity Constraint 4: Dimensions Have Range";
        logger.info("Validating " + icName);
        ValidatorIC4 validatorIC4 = new ValidatorIC4(model);
        Set<Resource> dimWithoutRangeSet = validatorIC4.validate();
        String logMsg = "The following dimensions do not have a declared rdfs:range: ";
        logValidationResult(icName, dimWithoutRangeSet, logMsg);
        return dimWithoutRangeSet;
    }

    /**
     * Validate IC-5 Concept dimensions have code lists: Every dimension with
     * range skos:Concept must have a qb:codeList.
     * @return a set of concept dimensions without code lists
     */
    public Set<Resource> validateIC5() {
        String icName = "Integrity Constraint 5: Concept Dimensions Have Code Lists";
        logger.info("Validating " + icName);
        ValidatorIC5 validatorIC5 = new ValidatorIC5(model);
        Set<Resource> dimWithoutCodeList = validatorIC5.validate();
        String logMsg = "The following concept dimensions do not have a code list: ";
        logValidationResult(icName, dimWithoutCodeList, logMsg);
        return dimWithoutCodeList;
    }

    /**
     * Validate IC-6 Only attributes may be optional: The only components of a
     * qb:DataStructureDefinition that may be marked as optional, using
     * qb:componentRequired are attributes.
     * @return a set of component properties not declared as attributes
     */
    public Set<RDFNode> validateIC6() {
        String icName = "Integrity Constraint 6: Only Attributes May Be Optional";
        logger.info("Validating " + icName);
        ValidatorIC6 validatorIC6 = new ValidatorIC6(model);
        Set<RDFNode> compPropSet = validatorIC6.validate();
        String logMsg = "The following component properties are not delared as attributes: ";
        logValidationResult(icName, compPropSet, logMsg);
        return compPropSet;
    }

    /**
     * Validate IC-7 Slice Keys must be declared: Every qb:SliceKey must be
     * associated with a qb:DataStructureDefinition.
     * @return a set of Slice Keys not associated with DSDs
     */
    public Set<Resource> validateIC7() {
        String icName = "Integrity Constraint 7: Slice Keys Must Be Declared";
        logger.info("Validating " + icName);
        ValidatorIC7 validatorIC7 = new ValidatorIC7(model);
        Set<Resource> sliceKeySet = validatorIC7.validate();
        String logMsg = "The following slice keys are not associated with DSDs: ";
        logValidationResult(icName, sliceKeySet, logMsg);
        return sliceKeySet;
    }

    /**
     * Validate IC-8 Slice Keys consistent with DSD: Every qb:componentProperty
     * on a qb:SliceKey must also be declared as a qb:component of the
     * associated qb:DataStructureDefinition.
     * @return a set of component properties not associated with DSDs
     */
    public Set<RDFNode> validateIC8() {
        String icName = "Integrity Constraint 8: Slice Keys Consistent With DSD";
        logger.info("Validating " + icName);
        ValidatorIC8 validatorIC8 = new ValidatorIC8(model);
        Set<RDFNode> compWithoutDSD = validatorIC8.validate();
        String logMsg = "The following component properties on slice keys are" +
                " not associated with DSDs: ";
        logValidationResult(icName, compWithoutDSD, logMsg);
        return compWithoutDSD;
    }

    /**
     * Validate IC-9 Unique slice structure: Each qb:Slice must have exactly
     * one associated qb:sliceStructure.
     * @return a map of slices with multiple slice structures
     */
    public Map<Resource, Set<RDFNode>> validateIC9() {
        String icName = "Integrity Constraint 9: Unique Slice Structure";
        logger.info("Validating " + icName);
        ValidatorIC9 validatorIC9 = new ValidatorIC9(model);
        Map<Resource, Set<RDFNode>> structBySlice = validatorIC9.validate();
        String logMsg = " is associated with the following slice structures: ";
        logValidationResult(icName, structBySlice, logMsg);
        return structBySlice;
    }

    /**
     * Validate IC-10 Slice dimensions complete: Every qb:Slice must have a
     * value for every dimension declared in its qb:sliceStructure.
     * @return a map of slices with a set of dimensions without values
     */
    public Map<Resource, Set<RDFNode>> validateIC10() {
        String icName = "Integrity Constraint 10: Slice Dimensions Complete";
        logger.info("Validating " + icName);
        ValidatorIC10 validatorIC10 = new ValidatorIC10(model);
        Map<Resource, Set<RDFNode>> dimBySliceWithoutVal = validatorIC10.validate();
        String logMsg = " does not have values for the following dimensions: ";
        logValidationResult(icName, dimBySliceWithoutVal, logMsg);
        return dimBySliceWithoutVal;
    }

    /**
     * Validate IC-11 All dimensions required: Every qb:Observation has a value
     * for each dimension declared in its associated qb:DataStructureDefinition.
     * Validate IC-12 No duplicate observations: No two qb:Observations in the
     * same qb:DataSet may have the same value for all dimensions.
     * @return a map of observations with dimensions without values or with
     * duplicate values.
     */
    public Map<Resource, Set<RDFNode>> validateIC11_12() {
        String icName11 = "Integrity Constraint 11: All Dimensions Required";
        String icName12 = "Integrity Constraint 12: No Duplicate Observations";
        logger.info("Validating " + icName11 + " & " + icName12);
        ValidatorIC11_12 validatorIC11_12 = new ValidatorIC11_12(model);
        Map<Resource, Set<RDFNode>> faultyObs = validatorIC11_12.validate();
        Set<Resource> duplicateObsSet = new HashSet<Resource>();
        for (Resource obs : faultyObs.keySet()) {
            if (faultyObs.get(obs).isEmpty()) duplicateObsSet.add(obs);
        }
        Map<Resource, Set<RDFNode>> dimSetByObsWithoutVal =
                new HashMap<Resource, Set<RDFNode>>(faultyObs);
        for (Resource obs : duplicateObsSet) {
            dimSetByObsWithoutVal.remove(obs);
        }
        String logMsg11 = " does not have values for the following dimensions: ";
        String logMsg12 = "The following observations has duplicated values: ";
        logValidationResult(icName11, dimSetByObsWithoutVal, logMsg11);
        logValidationResult(icName12, duplicateObsSet, logMsg12);
        return faultyObs;
    }


    /**
     * Validate IC-13 Required attributes: Every qb:Observation has a value for
     * each declared attribute that is marked as required.
     * @return a map of observations with attribute properties missing values
     */
    public Map<Resource, Set<RDFNode>> validateIC13() {
        String icName = "Integrity Constraint 13: Required Attributes";
        logger.info("Validating " + icName);
        ValidatorIC13 validatorIC13 = new ValidatorIC13(model);
        Map<Resource, Set<RDFNode>> obsWithoutAttribVal = validatorIC13.validate();
        String logMsg = " does not have values for the following required attributes: ";
        logValidationResult(icName, obsWithoutAttribVal, logMsg);
        return obsWithoutAttribVal;
    }


    /**
     * Validate IC-14 All measures present: In a qb:DataSet which does not use
     * a Measure dimension then each individual qb:Observation must have a
     * value for every declared measure.
     * @return a map of observations with a set of measures missing values.
     */
    public Map<Resource, Set<RDFNode>> validateIC14() {
        String icName = "Integrity Constraint 14: All Measures Present";
        logger.info("Validating " + icName);
        ValidatorIC14 validatorIC14 = new ValidatorIC14(model);
        Map<Resource, Set<RDFNode>> obsWithoutMeasureVal = validatorIC14.validate();
        String logMsg = " does not have values for the following declared measures: ";
        logValidationResult(icName, obsWithoutMeasureVal, logMsg);
        return obsWithoutMeasureVal;
    }



    /**
     * Validate IC-15 Measure dimension consistent: In a qb:DataSet which uses
     * a Measure dimension then each qb:Observation must have a value for the
     * measure corresponding to its given qb:measureType.
     * Validate IC-16 Single measure on measure dimension observation: In a
     * qb:DataSet which uses a Measure dimension then each qb:Observation must
     * only have a value for one measure (by IC-15 this will be the measure
     * corresponding to its qb:measureType).
     * @return a map of faulty observations with measures missing values
     */
    public Map<Resource, Set<RDFNode>> validateIC15_16() {
        String icName15 = "Integrity Constraint 15: Measure Dimension Consistent";
        String icName16 = "Integrity Constraint 16: Single Measure On Measure Dimension Observation";
        logger.info("Validating " + icName15 + " & " + icName16);
        ValidatorIC15_16 validatorIC15_16 = new ValidatorIC15_16(model);
        Map<Resource, Set<RDFNode>> obsWithFaultyMeasure = validatorIC15_16.validate();
        Map<Resource, Set<RDFNode>> obsWithoutMeasureVal =
                new HashMap<Resource, Set<RDFNode>>(obsWithFaultyMeasure);
        Map<Resource, Set<RDFNode>> obsWithMultipleMeasure =
                new HashMap<Resource, Set<RDFNode>>(obsWithFaultyMeasure);
        for (Resource obs : obsWithFaultyMeasure.keySet()) {
            if (obsWithFaultyMeasure.get(obs).size() == 1)
                obsWithMultipleMeasure.remove(obs);
            else obsWithoutMeasureVal.remove(obs);
        }
        String logMsg15 = " corresponds to a wrong measure or does not have a value on: ";
        String logMsg16 = " has the following multiple measures: ";
        logValidationResult(icName15, obsWithoutMeasureVal, logMsg15);
        logValidationResult(icName16, obsWithMultipleMeasure, logMsg16);
        return obsWithFaultyMeasure;
    }

    /**
     * Validate IC-17 All measures present in measures dimension cube: In a
     * qb:DataSet which uses a Measure dimension then if there is a Observation
     * for some combination of non-measure dimensions then there must be other
     * Observations with the same non-measure dimension values for each of the
     * declared measures.
     * @return a map of observations with amount of other observations with
     * same dimension values.
     */
    public Map<Resource, Integer> validateIC17() {
        String icName = "Integrity Constraint 17: All Measures Present In Measures Dimension Cube";
        logger.info("Validating " + icName);
        ValidatorIC17 validatorIC17 = new ValidatorIC17(model);
        Map<Resource, Integer> numObs2ByObs1 = validatorIC17.validate();
        String logMsg = " shares the same dimension values with the following number of observations";
        logValidationResult(icName, numObs2ByObs1, logMsg);
        return numObs2ByObs1;
    }

    /**
     * Validate IC-18 Consistent dataset links: If a qb:DataSet D has a
     * qb:slice S, and S has an qb:observation O, then the qb:dataSet
     * corresponding to O must be D.
     * @return a map of observations with correct datasets that should be
     * associated to.
     */
    public Map<Resource, Resource> validateIC18() {
        String icName = "Integrity Constraint 18: Consistent Dataset Links";
        logger.info("Validating " + icName);
        ValidatorIC18 validatorIC18 = new ValidatorIC18(model);
        Map<Resource, Resource> obsNotInDataset = validatorIC18.validate();
        String logMsg = " should be associated to the following dataset: ";
        logValidationResult(icName, obsNotInDataset, logMsg);
        return obsNotInDataset;
    }

    /**
     * Validate IC-19 Codes from code list: If a dimension property has a
     * qb:codeList, then the value of the dimension property on every
     * qb:Observation must be in the code list.
     * @return a map of values with a set of code lists not including the
     * values
     */
    public Map<RDFNode, Set<RDFNode>> validateIC19() {
        String icName = "Integrity Constraint 19: Codes From Code List";
        logger.info("Validating " + icName);
        ValidatorIC19 validatorIC19 = new ValidatorIC19(model);
        Map<RDFNode, Set<RDFNode>> valNotInCodeList = validatorIC19.validate();
        String logMsg = " is not included in the following code lists: ";
        logValidationResult(icName, valNotInCodeList, logMsg);
        return valNotInCodeList;
    }


    /**
     * Validate IC-20 Codes from hierarchy: If a dimension property has a
     * qb:HierarchicalCodeList with a non-blank qb:parentChildProperty then
     * the value of that dimension property on every qb:Observation must be
     * reachable from a root of the hierarchy using zero or more hops along
     * the qb:parentChildProperty links.
     * Validate IC-21 Codes from hierarchy (inverse): If a dimension property
     * has a qb:HierarchicalCodeList with an inverse qb:parentChildProperty
     * then the value of that dimension property on every qb:Observation must
     * be reachable from a root of the hierarchy using zero or more hops along
     * the inverse qb:parentChildProperty links.
     * @return a list of two maps containing values with code lists not
     * including corresponding values with any parent child property along both
     * direct and inverse paths
     */
    public List<Map<RDFNode, Set<RDFNode>>> validateIC20_21 () {
        String icName20 = "Integrity Constraint 20: Codes From Hierarchy";
        String icName21 = "Integrity Constraint 21: Codes From Hierarchy (Inverse)";
        logger.info("Validating " + icName20 + " & " +icName21);
        ValidatorIC20_21 validatorIC20_21 = new ValidatorIC20_21(model);
        List<Map<RDFNode, Set<RDFNode>>> valNotInCodeListByPcp =
                validatorIC20_21.validate();
        String logMsg20 = " is not connected to the following code lists along a direct path: ";
        String logMsg21 = " is not connected to the following code lists along an inverse path: ";
        logValidationResult(icName20, valNotInCodeListByPcp.get(0), logMsg20);
        logValidationResult(icName21, valNotInCodeListByPcp.get(1), logMsg21);
        return valNotInCodeListByPcp;
    }

    /**
     * Logs the results of a validation
     * @param icName name of the integrity constraint
     * @param set a set of objects violating the given constraint
     * @param msg a message describing validation conclusion
     * @param <T> an unknown type of the objects in the set
     */
    private <T> void logValidationResult (String icName, Set<T> set, String msg) {
        logger.debug(icName);
        logger.debug(new String(new char[icName.length()]).replace("\0", "-"));
        logger.debug("");
        if (set.isEmpty()) logger.debug("Pass.");
        else {
            logger.debug(msg);
            for (T obj : set) {
                logger.debug("    " + obj.toString());
            }
        }
        logger.debug("");
    }

    /**
     * Logs the results of a validation
     * @param icName name of the integrity constraint
     * @param map a map of resources with properties or values violating the
     *            constraint
     * @param msg a message describing validation conclusion
     * @param <K> an unknown type of keys in the map
     * @param <V> an unknown type of values in the map
     */
    private <K, V> void logValidationResult (String icName,
                            Map<K, V> map, String msg) {
        logger.debug(icName);
        logger.debug(new String(new char[icName.length()]).replace("\0", "-"));
        logger.debug("");
        if (map.isEmpty()) logger.debug("Pass.");
        else {
            for (K key : map.keySet()) {
                logger.debug(key.toString() + msg);
                V value = map.get(key);
                // a value could be a set of properties or objects violating constraint
                // for the subject represented by a key
                if (value instanceof Set) {
                    for (Object obj : (Set) value) {
                        logger.debug("    " + obj.toString());
                    }
                }
                else
                    logger.debug("    " + value.toString());
            }
        }
        logger.debug("");
    }
}
