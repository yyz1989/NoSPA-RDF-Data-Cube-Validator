package cn.yyz.nospa.validator;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.*;

/**
 * An RDF Data Cube Validator
 * Created by Yang Yuanzhe on 9/26/14.
 */
public class Validator {
    private Logger logger = LoggerFactory.getLogger(Validator.class);
    private Model model;
    private Map<Resource, Set<Resource>> dimensionByDataset =
            new HashMap<Resource, Set<Resource>>();
    private Map<Resource, Map<Resource, Set<Resource>>> valueByDatasetAndDim =
            new HashMap<Resource, Map<Resource, Set<Resource>>>();
    private Map<Resource, Set<RDFNode>> dsdByDataset = new HashMap<Resource, Set<RDFNode>>();

    /**
     * Constructor of a validator
     * @param filename complete path of the cube file to be validated
     * @param format RDF serialization format of the cube file
     */
    public Validator(String filename, String format) {
        logger.debug("RDF Cube Validation Result");
        logger.debug("==========================");
        logger.debug("");
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
    public void normalizeBySparql() {
        logger.info("Normalizing cube with SPARQL queries ...");
        String queryString1 = NormalizationAlgorithm.PHASE1.getValue();
        String queryString2 = NormalizationAlgorithm.PHASE2.getValue();
        UpdateAction.parseExecute(queryString1, model);
        UpdateAction.parseExecute(queryString2, model);
    }

    /**
     * This function normalizes an abbreviated Data Cube at phase 1 for type
     * and property closure. It ensures that rdf:type assertions on instances
     * of qb:Observation and qb:Slice may be omitted in an abbreviated Data
     * Cube. They also simplify the second set of update operations by
     * expanding the sub properties of qb:componentProperty (specifically
     * qb:dimension, qb:measure and qb:attribute).
     */
    public void normalizePhase1() {
        // Phase 1: Type and property closure
        logger.info("Normalizing cube at phase 1 ...");

        Set<RDFNode> obsSet = model.listObjectsOfProperty(QB_observation).toSet();
        for (RDFNode obs : obsSet) {
            if (obs.isResource())
                model.add(obs.asResource(), RDF_type, QB_Observation);
        }

        StmtIterator stmtIter = model.listStatements(null, QB_dataSet, (RDFNode) null);
        while (stmtIter.hasNext()) {
            Statement statement = stmtIter.nextStatement();
            RDFNode obj = statement.getObject();
            if (obj.isResource())
                model.add(obj.asResource(), RDF_type, QB_DataSet);
            model.add(statement.getSubject(), RDF_type, QB_Observation);
        }

        Set<RDFNode> sliceSet = model.listObjectsOfProperty(QB_slice).toSet();
        for (RDFNode slice : sliceSet) {
            model.add(slice.asResource(), RDF_type, QB_Slice);
        }

        stmtIter = model.listStatements(null, QB_dimension, (RDFNode) null);
        while (stmtIter.hasNext()) {
            Statement statement = stmtIter.nextStatement();
            RDFNode obj = statement.getObject();
            if (obj.isResource())
                model.add(obj.asResource(), RDF_type, QB_DimensionProperty);
            model.add(statement.getSubject(), QB_componentProperty, statement.getObject());
        }

        stmtIter = model.listStatements(null, QB_measure, (RDFNode) null);
        while (stmtIter.hasNext()) {
            Statement statement = stmtIter.nextStatement();
            RDFNode obj = statement.getObject();
            if (obj.isResource())
                model.add(obj.asResource(), RDF_type, QB_MeasureProperty);
            model.add(statement.getSubject(), QB_componentProperty, statement.getObject());
        }

        stmtIter = model.listStatements(null, QB_attribute, (RDFNode) null);
        while (stmtIter.hasNext()) {
            Statement statement = stmtIter.nextStatement();
            RDFNode obj = statement.getObject();
            if (obj.isResource())
                model.add(obj.asResource(), RDF_type, QB_AttributeProperty);
            model.add(statement.getSubject(), QB_componentProperty, statement.getObject());
        }
    }

    /**
     * This function normalizes an abbreviated Data Cube at phase 2. It checks
     * the components of the data structure definition of the data set for
     * declared attachment levels. For each of the possible attachments levels
     * it looks for ocurrences of that component to be pushed down to the
     * corresponding observations.
     */
    public void normalizePhase2() {
        logger.info("Normalizing cube at phase 2 ...");
        pushDownDatasetAttachments();
        pushDownSliceAttachments();
        pushDownDimValOnSlice();
    }

    /**
     * Push down Dataset attachments.
     */
    private void pushDownDatasetAttachments() {
        Map<Resource, Set<? extends RDFNode>> specSetByDataset = searchByPathVisit(null,
                Arrays.asList(QB_structure, QB_component), null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(QB_componentAttachment, QB_DataSet);
        Map<Resource, Map<Property, Set<RDFNode>>> compBySpec = searchByMultipleProperty(null,
                objByProp, Arrays.asList(QB_componentProperty));
        for (Resource dataset : specSetByDataset.keySet()) {
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            Set<? extends RDFNode> specSet = specSetByDataset.get(dataset);
            specSet.retainAll(compBySpec.keySet());
            Set<RDFNode> compSet = new HashSet<RDFNode>();
            for (RDFNode spec : specSet) {
                compSet.addAll(compBySpec.get(spec.asResource()).get(QB_componentProperty));
            }
            Map<Property, Set<RDFNode>> valueSetByComp = new HashMap<Property, Set<RDFNode>>();
            for (RDFNode comp : compSet) {
                if (comp.isURIResource()) {
                    Property compAsProp =
                            ResourceFactory.createProperty(comp.asResource().getURI());
                    valueSetByComp.put(compAsProp,
                            model.listObjectsOfProperty(dataset, compAsProp).toSet());
                }
            }
            insertValueToObs(obsSet, valueSetByComp);
        }
    }

    /**
     * Push dwon Slice Attachments.
     */
    private void pushDownSliceAttachments() {
        Map<Resource, Set<? extends RDFNode>> specSetByDataset = searchByPathVisit(null,
                Arrays.asList(QB_structure, QB_component), null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(QB_componentAttachment, QB_Slice);
        Map<Resource, Map<Property, Set<RDFNode>>> compBySpec = searchByMultipleProperty(null,
                objByProp, Arrays.asList(QB_componentProperty));
        for (Resource dataset : specSetByDataset.keySet()) {
            Set<? extends RDFNode> specSet = specSetByDataset.get(dataset);
            specSet.retainAll(compBySpec.keySet());
            Set<RDFNode> compSet = new HashSet<RDFNode>();
            for (RDFNode spec : specSet) {
                compSet.addAll(compBySpec.get(spec.asResource()).get(QB_componentProperty));
            }
            NodeIterator sliceIter = model.listObjectsOfProperty(dataset, QB_slice);
            Set<Resource> sliceSet = nodeToResource(sliceIter.toSet());
            for (Resource slice : sliceSet) {
                NodeIterator resIter = model.listObjectsOfProperty(slice, QB_observation);
                Set<Resource> obsSet = nodeToResource(resIter.toSet());
                Map<Property, Set<RDFNode>> valueSetByComp = new HashMap<Property, Set<RDFNode>>();
                for (RDFNode comp : compSet) {
                    if (comp.isURIResource()) {
                        Property compAsProp =
                                ResourceFactory.createProperty(comp.asResource().getURI());
                        valueSetByComp.put(compAsProp,
                                model.listObjectsOfProperty(slice, compAsProp).toSet());
                    }
                }
                insertValueToObs(obsSet, valueSetByComp);
            }
        }
    }

    /**
     * Push down dimension values on slices.
     */
    private void pushDownDimValOnSlice () {
        Map<Resource, Set<? extends RDFNode>> specSetByDataset = searchByPathVisit(null,
                Arrays.asList(QB_structure, QB_component), null);
        Map<Resource, Set<? extends RDFNode>> compBySpec = searchByPathVisit(null,
                Arrays.asList(QB_componentProperty), null);
        Set<Resource> compWithDef = model.listResourcesWithProperty(RDF_type,
                QB_DimensionProperty).toSet();
        for (Resource dataset : specSetByDataset.keySet()) {
            Set<? extends RDFNode> specSet = specSetByDataset.get(dataset);
            specSet.retainAll(compBySpec.keySet());
            Set<RDFNode> compSet = new HashSet<RDFNode>();
            for (RDFNode spec : specSet) {
                compSet.addAll(compBySpec.get(spec.asResource()));
            }
            compSet.retainAll(compWithDef);
            NodeIterator sliceIter = model.listObjectsOfProperty(dataset, QB_slice);
            Set<Resource> sliceSet = nodeToResource(sliceIter.toSet());
            for (Resource slice : sliceSet) {
                NodeIterator resIter = model.listObjectsOfProperty(slice, QB_observation);
                Set<Resource> obsSet = nodeToResource(resIter.toSet());
                Map<Property, Set<RDFNode>> valueSetByComp = new HashMap<Property, Set<RDFNode>>();
                for (RDFNode comp : compSet) {
                    if (comp.isURIResource()) {
                        Property compAsProp =
                                ResourceFactory.createProperty(comp.asResource().getURI());
                        valueSetByComp.put(compAsProp,
                                model.listObjectsOfProperty(slice, compAsProp).toSet());
                    }
                }
                insertValueToObs(obsSet, valueSetByComp);
            }
        }
    }

    /**
     * Insert statements to the model for the given observations.
     * @param obsSet a set of observations to be associated with new statements
     * @param objSetByProp a map containing the properties and corresponding
     *                     object values to be associated to the observations
     */
    private void insertValueToObs(Set<Resource> obsSet, Map<Property, Set<RDFNode>> objSetByProp) {
        for (Property prop : objSetByProp.keySet()) {
            Set<RDFNode> objSet = objSetByProp.get(prop);
            for (Resource obs : obsSet) {
                for (RDFNode obj : objSet) {
                    model.add(obs, prop, obj);
                }
            }
        }
    }

    /**
     * A shortcut function to excute all constraint validations.
     */
    public void checkICAll() {
        logger.info("Validating all constraints ...");
        checkIC1();
        checkIC2();
        checkIC3();
        checkIC4();
        checkIC5();
        checkIC6();
        checkIC7();
        checkIC8();
        checkIC9();
        checkIC10();
        checkIC11_12();
        checkIC13();
        checkIC14();
        checkIC15_16();
        checkIC17();
        checkIC18();
        checkIC19();
        checkIC20_21();
    }

    /**
     * Temporarily deprecated.
     */
    public void precomputeCubeStructure() {
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DataSet);
        List<Property> propertySet = Collections.singletonList(QB_structure);
        Map<Resource, Map<Property, Set<RDFNode>>> dsdByDatasetAndStructure =
                searchByMultipleProperty(null, objByProp, propertySet);
        for (Resource dataset : dsdByDatasetAndStructure.keySet()) {
            Map<Property, Set<RDFNode>> dsdByStructure =
                    dsdByDatasetAndStructure.get(dataset);
            Set<RDFNode> dsdSet = dsdByStructure.get(QB_structure);
            if (dsdSet != null) dsdByDataset.put(dataset, dsdSet);
        }
    }

    /**
     * The function to validate constraints by SPARQL queries
     * @param constraint name of constraint (e.g., IC1)
     */
    public void checkICBySparql(String constraint) {
        checkICBySparql(IntegrityConstraint.valueOf(constraint));
    }

    /**
     * The function to validate constraints by SPARQL queries
     * @param constraint constraint defined in an enum class
     */
    public void checkICBySparql(IntegrityConstraint constraint) {
        String prefix = IntegrityConstraint.PREFIX.getValue();
        Query query = QueryFactory.create(prefix + constraint.getValue());
        QueryExecution qe = QueryExecutionFactory.create(query, model);
        ResultSet resultSet = qe.execSelect();
        List<String> variables = resultSet.getResultVars();
        while (resultSet.hasNext()) {
            QuerySolution querySolution = resultSet.next();
            for (String var : variables) {
                System.out.print(var + ": " + querySolution.get(var).toString() + "    ");
            }
            System.out.println();
        }
        qe.close();
    }

    /**
     * Validate IC-1 Unique DataSet: Every qb:Observation has exactly one
     * associated qb:DataSet.
     * @return a map of observations with multiple datasets
     */
    public Map<Resource, Set<RDFNode>> checkIC1() {
        String icName = "Integrity Constraint 1: Unique DataSet";
        logger.info("Validating " + icName);
        Map<Resource, Set<RDFNode>> datasetByObs =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> obsSet = model.listSubjectsWithProperty(
                RDF_type, QB_Observation).toSet();
        for (Resource obs : obsSet) {
            Set<RDFNode> datasetSet = model.listObjectsOfProperty(obs, QB_dataSet).toSet();
            if (datasetSet.size() != 1) {
                datasetByObs.put(obs, datasetSet);
            }
        }
        String logMsg = " is associated to the following datasets: ";
        logValidationResult(icName, datasetByObs, logMsg);
        return datasetByObs;
    }

    /**
     * Validate IC-2 Unique DSD: Every qb:DataSet has exactly one associated
     * qb:DataStructureDefinition.
     * @return a map of datasets with multiple dsds
     */
    public Map<Resource, Set<RDFNode>> checkIC2() {
        String icName = "Integrity Constraint 2: Unique DSD";
        logger.info("Validating " + icName);
        Map<Resource, Set<RDFNode>> dsdByDataset =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> datasetSet = model.listSubjectsWithProperty(
            RDF_type, QB_DataSet).toSet();
        for (Resource dataset : datasetSet) {
            Set<RDFNode> dsdSet = model.listObjectsOfProperty(dataset, QB_structure).toSet();
            if (dsdSet.size() != 1) {
                dsdByDataset.put(dataset, dsdSet);
            }
        }
        String logMsg = " is associated to the following DSDs: ";
        logValidationResult(icName, dsdByDataset, logMsg);
        return dsdByDataset;
    }

    /**
     * Validate IC-3 DSD includes measure: Every qb:DataStructureDefinition
     * must include at least one declared measure.
     * @return a set of DSDs without at least one declared measure
     */
    public Set<Resource> checkIC3() {
        String icName = "Integrity Constraint 3: DSD Includes Measure";
        logger.info("Validating " + icName);
        Set<Resource> dsdWithoutMeasure = new HashSet<Resource>();
        Set<Resource> dsdSet = model.listSubjectsWithProperty(RDF_type,
                QB_DataStructureDefinition).toSet();
        for (Resource dsd : dsdSet) {
            Property[] properties = {QB_component, QB_componentProperty, RDF_type};
            Map<Resource, Set<? extends RDFNode>> dsdPropertyMap = searchByPathVisit(dsd,
                    Arrays.asList(properties), QB_MeasureProperty);
            if (dsdPropertyMap.get(dsd).isEmpty()) dsdWithoutMeasure.add(dsd);
        }
        String logMsg = "The following DSDs do not include at least one declared measure: ";
        logValidationResult(icName, dsdWithoutMeasure, logMsg);
        return dsdWithoutMeasure;
    }

    /**
     * Validate IC-4 Dimensions have range: Every dimension declared in a
     * qb:DataStructureDefinition must have a declared rdfs:range.
     * @return a set of dimensions without a declared rdfs:range
     */
    public Set<Resource> checkIC4() {
        String icName = "Integrity Constraint 4: Dimensions Have Range";
        logger.info("Validating " + icName);
        Set<Resource> dimSet = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty).toSet();
        Set<Resource> dimWithRangeSet = model.listSubjectsWithProperty(
                RDFS_range).toSet();
        Set<Resource> dimWithoutRangeSet = new HashSet<Resource>(dimSet);
        dimWithoutRangeSet.removeAll(dimWithRangeSet);
        String logMsg = "The following dimensions do not have a declared rdfs:range: ";
        logValidationResult(icName, dimWithoutRangeSet, logMsg);
        return dimWithoutRangeSet;
    }

    /**
     * Validate IC-5 Concept dimensions have code lists: Every dimension with
     * range skos:Concept must have a qb:codeList.
     * @return a set of concept dimensions without code lists
     */
    public Set<Resource> checkIC5() {
        String icName = "Integrity Constraint 5: Concept Dimensions Have Code Lists";
        logger.info("Validating " + icName);
        Set<Resource> dimWithoutCodeList = new HashSet<Resource>();
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DimensionProperty);
        objByProp.put(RDFS_range, SKOS_Concept);
        Set<Resource> dimSet = searchByMultipleProperty(null, objByProp);
        for (Resource dimension : dimSet) {
            NodeIterator codelistIterator = model.listObjectsOfProperty(dimension, QB_codeList);
            if (!codelistIterator.hasNext()) dimWithoutCodeList.add(dimension);
        }
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
    public Set<RDFNode> checkIC6() {
        String icName = "Integrity Constraint 6: Only Attributes May Be Optional";
        logger.info("Validating " + icName);
        Set<RDFNode> compPropSet = new HashSet<RDFNode>();
        Map<Property, RDFNode> objyByProp = new HashMap<Property, RDFNode>();
        objyByProp.put(QB_componentRequired, LITERAL_FALSE);
        Map<Resource, Map<Property, Set<RDFNode>>> compPropByCompSpec = searchByMultipleProperty(
                null, objyByProp, Arrays.asList(QB_componentProperty));
        Set<RDFNode> compSpecSet = model.listObjectsOfProperty(QB_component).toSet();
        compSpecSet.retainAll(compPropByCompSpec.keySet());
        for (RDFNode compSpec : compSpecSet) {
            Resource compSpecAsRes = compSpec.asResource();
            compPropSet.addAll(compPropByCompSpec
                        .get(compSpecAsRes).get(QB_componentProperty));
        }
        Set<Resource> AttribWithDefSet = model.listSubjectsWithProperty(
                RDF_type, QB_AttributeProperty).toSet();
        compPropSet.removeAll(AttribWithDefSet);
        String logMsg = "The following component properties are not delared as attributes: ";
        logValidationResult(icName, compPropSet, logMsg);
        return compPropSet;
    }

    /**
     * Validate IC-7 Slice Keys must be declared: Every qb:SliceKey must be
     * associated with a qb:DataStructureDefinition.
     * @return a set of Slice Keys not associated with DSDs
     */
    public Set<Resource> checkIC7() {
        String icName = "Integrity Constraint 7: Slice Keys Must Be Declared";
        logger.info("Validating " + icName);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DataStructureDefinition);
        Map<Resource, Map<Property, Set<RDFNode>>> sliceKeyByDSD =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_sliceKey));
        Set<Resource> sliceKeySet = model.listSubjectsWithProperty(RDF_type, QB_SliceKey).toSet();
        for (Resource dsd : sliceKeyByDSD.keySet()) {
            Set<RDFNode> sliceKeyInDSDSet = sliceKeyByDSD.get(dsd).get(QB_sliceKey);
            sliceKeySet.removeAll(sliceKeyInDSDSet);
        }
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
    public Set<RDFNode> checkIC8() {
        String icName = "Integrity Constraint 8: Slice Keys Consistent With DSD";
        logger.info("Validating " + icName);
        Set<RDFNode> compWithoutDSD = new HashSet<RDFNode>();
        List<Property> propPath = Arrays.asList(QB_component, QB_componentProperty);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_SliceKey);
        Set<RDFNode> propSet = new HashSet<RDFNode>();
        Map<Resource, Map<Property, Set<RDFNode>>> propBySliceKey =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_componentProperty));
        Map<Resource, Set<? extends RDFNode>> sliceKeyByDSD =
                searchByPathVisit(null, Arrays.asList(QB_sliceKey), null);
        for (Resource dsd : sliceKeyByDSD.keySet()) {
            Set<? extends RDFNode> sliceKeySet = sliceKeyByDSD.get(dsd);
            for (RDFNode sliceKey : sliceKeySet) {
                if (propBySliceKey.containsKey(sliceKey.asResource()))
                    propSet.addAll(propBySliceKey
                        .get(sliceKey.asResource()).get(QB_componentProperty));
            }
            for (RDFNode property : propSet) {
                if (!connectedByPropList(dsd, propPath, property))
                    compWithoutDSD.add(property);
            }
        }
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
    public Map<Resource, Set<RDFNode>> checkIC9() {
        String icName = "Integrity Constraint 9: Unique Slice Structure";
        logger.info("Validating " + icName);
        Map<Resource, Set<RDFNode>> structBySlice =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> sliceSet = model.listSubjectsWithProperty(RDF_type, QB_Slice).toSet();
        for (Resource slice : sliceSet) {
            Set<RDFNode> sliceStructSet = model.listObjectsOfProperty(slice,
                    QB_sliceStructure).toSet();
            if (sliceStructSet.size() != 1) structBySlice.put(slice,
                    sliceStructSet);
        }
        String logMsg = " is associated with the following slice structures: ";
        logValidationResult(icName, structBySlice, logMsg);
        return structBySlice;
    }

    /**
     * Validate IC-10 Slice dimensions complete: Every qb:Slice must have a
     * value for every dimension declared in its qb:sliceStructure.
     * @return a map of slices with a set of dimensions without values
     */
    public Map<Resource, Set<RDFNode>> checkIC10() {
        String icName = "Integrity Constraint 10: Slice Dimensions Complete";
        logger.info("Validating " + icName);
        Map<Resource, Set<RDFNode>> dimBySliceWithoutVal = new HashMap<Resource, Set<RDFNode>>();
        List<Property> propPath = Arrays.asList(QB_sliceStructure, QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> dimBySlice = searchByPathVisit(null, propPath, null);
        for (Resource slice : dimBySlice.keySet()) {
            Set<RDFNode> dimWithoutValSet = new HashSet<RDFNode>();
            for (RDFNode dim : dimBySlice.get(slice)) {
                Property dimAsProp = ResourceFactory.createProperty(dim.asResource().getURI());
                NodeIterator valIter = model.listObjectsOfProperty(slice, dimAsProp);
                if (!valIter.hasNext()) dimWithoutValSet.add(dim);
            }
            if (!dimWithoutValSet.isEmpty()) dimBySliceWithoutVal.put(slice, dimWithoutValSet);
        }
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
    public Map<Resource, Set<RDFNode>> checkIC11_12() {
        String icName11 = "Integrity Constraint 11: All Dimensions Required";
        String icName12 = "Integrity Constraint 12: No Duplicate Observations";
        logger.info("Validating " + icName11 + " & " + icName12);
        Map<Resource, Set<RDFNode>> faultyObs = new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> duplicateObsSet = new HashSet<Resource>();
        Map<Resource, Set<Resource>> obsByDataset =
                new HashMap<Resource, Set<Resource>>();
        List<Property> propPath = Arrays.asList(QB_structure,
                QB_component, QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> dimByDataset = searchByPathVisit(
                null, propPath, null);
        Set<Resource> dimWithDef = model.listResourcesWithProperty(RDF_type,
                QB_DimensionProperty).toSet();
        for (Resource dataset : dimByDataset.keySet()) {
            Set<? extends RDFNode> dimInDataset = dimByDataset.get(dataset);
            dimInDataset.retainAll(dimWithDef);
            dimByDataset.put(dataset, dimInDataset);

            obsByDataset.put(dataset,
                    model.listSubjectsWithProperty(QB_dataSet, dataset).toSet());
        }
        for (Resource dataset : obsByDataset.keySet()) {
            Set<Resource> obsSet = obsByDataset.get(dataset);
            Set<? extends RDFNode> dimSet = dimByDataset.get(dataset);
            faultyObs.putAll(dimValueCheck(obsSet, dimSet));
        }

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
     * This function is a subtask of function checkIC11_12 for checking the
     * values of a set of observations for a set of dimensions.
     * @param obsSet a set of observations
     * @param dimSet a set of dimension properties
     * @return a map of faulty observations with dimension property set missing
     * corresponding values. If the set is empty then the observation is
     * duplicated.
     */
    private Map<Resource, Set<RDFNode>> dimValueCheck (Set<Resource> obsSet,
                                                  Set<? extends RDFNode> dimSet) {
        Map<Resource, Set<RDFNode>> faultyObs = new HashMap<Resource, Set<RDFNode>>();
        Map<Resource, Set<RDFNode>> valueSetByObs = new HashMap<Resource, Set<RDFNode>>();
        Set<Property> dimAsPropSet = nodeToProperty(dimSet);
        for (Resource obs : obsSet) {
            Set<RDFNode> valueSet = new HashSet<RDFNode>();
            Set<RDFNode> dimWithoutValSet = new HashSet<RDFNode>();
            for (Property dim : dimAsPropSet) {
                NodeIterator valueIter = model.listObjectsOfProperty(obs, dim);
                if (!valueIter.hasNext()) dimWithoutValSet.add(dim);
                else valueSet.add(valueIter.next());
            }
            if (!dimWithoutValSet.isEmpty()) faultyObs.put(obs, dimWithoutValSet);
            else {
                if (valueSetByObs.containsValue(valueSet)) faultyObs.put(obs,
                        dimWithoutValSet);
                else valueSetByObs.put(obs, valueSet);
            }
        }
        return faultyObs;
    }

    /**
     * Validate IC-13 Required attributes: Every qb:Observation has a value for
     * each declared attribute that is marked as required.
     * @return a map of observations with attribute properties missing values
     */
    public Map<Resource, Set<RDFNode>> checkIC13() {
        String icName = "Integrity Constraint 13: Required Attributes";
        logger.info("Validating " + icName);
        Map<Resource, Set<RDFNode>> obsWithoutAttribVal =
                new HashMap<Resource, Set<RDFNode>>();
        List<Property> propPath = Arrays.asList(QB_structure, QB_component);
        Map<Resource, Set<? extends RDFNode>> compByDataset = searchByPathVisit(
                null, propPath, null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(QB_componentRequired, LITERAL_TRUE);
        Map<Resource, Map<Property, Set<RDFNode>>> attribByComp = searchByMultipleProperty(null,
                objByProp, Arrays.asList(QB_componentProperty));
        for (Resource dataset : compByDataset.keySet()) {
            Set<? extends RDFNode> compSet = compByDataset.get(dataset);
            Set<RDFNode> attribSet = new HashSet<RDFNode>();
            compSet.retainAll(attribByComp.keySet());
            for (RDFNode component : compSet) {
                attribSet.addAll(attribByComp.get(component.asResource())
                        .get(QB_componentProperty));
            }
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            obsWithoutAttribVal.putAll(attribValueCheck(obsSet, attribSet));
        }
        String logMsg = " does not have values for the following required attributes: ";
        logValidationResult(icName, obsWithoutAttribVal, logMsg);
        return obsWithoutAttribVal;
    }

    /**
     * This function is a subtask of function checkIC13 for checking the values
     * of a set of attribute properties for a set of observations.
     * @param obsSet a set of observations
     * @param attribSet a set of attribute properties
     * @return a map of observations with attribute properties missing values
     */
    private Map<Resource, Set<RDFNode>> attribValueCheck (Set<Resource> obsSet,
                                                     Set<RDFNode> attribSet) {
        Map<Resource, Set<RDFNode>> obsWithoutAttribVal =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Property> attribAsPropSet = nodeToProperty(attribSet);
        for (Resource obs : obsSet) {
            Set<RDFNode> attribPropWithoutValSet = new HashSet<RDFNode>();
            for (Property attribProp : attribAsPropSet) {
                if (!model.listObjectsOfProperty(obs, attribProp).hasNext())
                    attribPropWithoutValSet.add(attribProp);
            }
            if (!attribPropWithoutValSet.isEmpty())
                obsWithoutAttribVal.put(obs, attribPropWithoutValSet);
        }
        return obsWithoutAttribVal;
    }

    /**
     * Validate IC-14 All measures present: In a qb:DataSet which does not use
     * a Measure dimension then each individual qb:Observation must have a
     * value for every declared measure.
     * @return a map of observations with a set of measures missing values.
     */
    public Map<Resource, Set<RDFNode>> checkIC14() {
        String icName = "Integrity Constraint 14: All Measures Present";
        logger.info("Validating " + icName);
        Map<Resource, Set<RDFNode>> obsWithoutMeasureVal =
                new HashMap<Resource, Set<RDFNode>>();
        List<Property> propPath = Arrays.asList(QB_structure, QB_component, QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> compPropSetByDataset = searchByPathVisit(null,
                propPath, null);
        Set<Resource> measureSet = model.listSubjectsWithProperty(RDF_type,
                ResourceFactory.createProperty(QB_MeasureProperty.getURI())).toSet();
        for (Resource dataset : compPropSetByDataset.keySet()) {
            Set<? extends RDFNode> compPropSet = compPropSetByDataset.get(dataset);
            if (!compPropSet.contains(QB_measureType)) {
                compPropSet.retainAll(measureSet);
            }
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            obsWithoutMeasureVal.putAll(measureValueCheck(obsSet, compPropSet));
        }
        String logMsg = " does not have values for the following declared measures: ";
        logValidationResult(icName, obsWithoutMeasureVal, logMsg);
        return obsWithoutMeasureVal;
    }

    /**
     * This function is a subtask of checkIC14 for checking the values of a set
     * of measures for a set of observations.
     * @param obsSet a set of observations
     * @param measureSet a set of measures
     * @return a map of observations with a set of measures missing values
     */
    private Map<Resource, Set<RDFNode>> measureValueCheck (Set<Resource> obsSet,
                                                      Set<? extends RDFNode> measureSet) {
        Map<Resource, Set<RDFNode>> obsWithoutMeasureVal =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Property> measureAsPropSet = nodeToProperty(measureSet);
        for (Resource obs : obsSet) {
            Set<RDFNode> measureWithoutValSet = new HashSet<RDFNode>();
            for (Property measure : measureAsPropSet) {
                if (!model.listObjectsOfProperty(obs, measure).hasNext())
                    measureWithoutValSet.add(measure);
            }
            if (!measureWithoutValSet.isEmpty())
                obsWithoutMeasureVal.put(obs, measureWithoutValSet);
        }
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
    public Map<Resource, Set<RDFNode>> checkIC15_16() {
        String icName15 = "Integrity Constraint 15: Measure Dimension Consistent";
        String icName16 = "Integrity Constraint 16: Single Measure On Measure Dimension Observation";
        logger.info("Validating " + icName15 + " & " + icName16);
        Map<Resource, Set<RDFNode>> obsWithFaultyMeasure = new HashMap<Resource, Set<RDFNode>>();
        List<Property> propPath = Arrays.asList(QB_structure, QB_component,
                QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> compPropSetByDataset = searchByPathVisit(null,
                propPath, null);
        Set<Resource> measurePropSet = model.listSubjectsWithProperty(RDF_type,
                ResourceFactory.createProperty(QB_MeasureProperty.getURI())).toSet();
        for (Resource dataset : compPropSetByDataset.keySet()) {
            Set<? extends RDFNode> compPropSet = compPropSetByDataset.get(dataset);
            if (compPropSet.contains(QB_measureType)) {
                compPropSet.retainAll(measurePropSet);
                Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
                obsWithFaultyMeasure.putAll(measureTypeValueCheck(obsSet, compPropSet));
            }
        }

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
     * This function is a subtask of function checkIC15_16 for checking values
     * of a set of measures for a set of observations.
     * @param obsSet a set of observations
     * @param measureSet a set of measures
     * @return a map of faulty observations with measures missing values
     */
    private Map<Resource, Set<RDFNode>> measureTypeValueCheck (Set<Resource> obsSet,
                                                               Set<? extends RDFNode> measureSet) {
        Map<Resource, Set<RDFNode>> obsWithFaultyMeasure = new HashMap<Resource, Set<RDFNode>>();
        for (Resource obs : obsSet) {
            Set<RDFNode> measurePropInObs = model.listObjectsOfProperty(obs,
                    QB_measureType).toSet();
            if (measurePropInObs.size() !=1) {
                obsWithFaultyMeasure.put(obs, measurePropInObs);
            }
            else {
                Property measureProp = ResourceFactory.createProperty(
                        measurePropInObs.iterator().next().asResource().getURI());
                Set<RDFNode> measurePropValSet =
                        model.listObjectsOfProperty(obs, measureProp).toSet();
                if (!measureSet.contains(measureProp) || measurePropValSet.size() != 1)
                    obsWithFaultyMeasure.put(obs, measurePropInObs);
            }
        }
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
    public Map<Resource, Integer> checkIC17() {
        String icName = "Integrity Constraint 17: All Measures Present In Measures Dimension Cube";
        logger.info("Validating " + icName);
        Map<Resource, Integer> numObs2ByObs1 = new HashMap<Resource, Integer>();
        List<Property> propPath = Arrays.asList(QB_structure,
                QB_component, QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> compPropByDataset = searchByPathVisit(
                null, propPath, null);
        Set<Resource> measPropWithDef = model.listResourcesWithProperty(RDF_type,
                QB_MeasureProperty).toSet();
        Set<Resource> dimPropWithDef = model.listResourcesWithProperty(RDF_type,
                QB_DimensionProperty).toSet();
        Set<Resource> obsWithMeasure = model.listResourcesWithProperty(QB_measureType).toSet();
        for (Resource dataset : compPropByDataset.keySet()) {
            Set<? extends RDFNode> compPropSet = compPropByDataset.get(dataset);
            Set<? extends RDFNode> dimPropSet = new HashSet<RDFNode>(compPropSet);
            compPropSet.retainAll(measPropWithDef);
            int numOfMeasure = compPropSet.size();

            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            obsSet.retainAll(obsWithMeasure);
            if (obsSet.size() == 0) continue;

            dimPropSet.retainAll(dimPropWithDef);
            for (RDFNode dim : dimPropSet) {
                if (dim.equals(QB_measureType)) dimPropSet.remove(dim);
            }

            Map<Resource, Set<Resource>> unqualifiedObsPair =
                    unqualifiedObsPairCheck(obsSet, dimPropSet);
            int numOfObs1 = unqualifiedObsPair.keySet().size();
            for (Resource obs : unqualifiedObsPair.keySet()) {
                int numOfObs2 = unqualifiedObsPair.get(obs).size();
                if (numOfObs1 - numOfObs2 != numOfMeasure)
                    numObs2ByObs1.put(obs, numOfObs2);
            }
        }
        String logMsg = " shares the same dimension values with the following number of observations";
        logValidationResult(icName, numObs2ByObs1, logMsg);
        return numObs2ByObs1;
    }

    /**
     * This function is a subtask of checkIC17 for checking observations with
     * same dimension property structure as each observation given in the set
     * @param obsSet a set of observations
     * @param dimPropSet a set of dimension properties
     * @return a map of observations with a set of corresponding observations
     * with same dimension property structure
     */
    public Map<Resource, Set<Resource>> unqualifiedObsPairCheck (
            Set<Resource> obsSet,
            Set<? extends RDFNode> dimPropSet) {
        Set<Property> dimAsPropSet = nodeToProperty(dimPropSet);
        Map<Resource, Set<Resource>> unqualifiedObsPair =
                new HashMap<Resource, Set<Resource>>();
        for (Resource obs1 : obsSet) {
            Set<Resource> unqualifiedObsSet = new HashSet<Resource>();
            for (Resource obs2 : obsSet) {
                boolean isEqual = true;
                for (Property dim : dimAsPropSet) {
                    Set<RDFNode> valueSet1 = model.listObjectsOfProperty(obs1, dim).toSet();
                    Set<RDFNode> valueSet2 = model.listObjectsOfProperty(obs2, dim).toSet();
                    if (valueSet1.size() != 1 || valueSet2.size() != 1) continue;
                    RDFNode value1 = valueSet1.iterator().next();
                    RDFNode value2 = valueSet2.iterator().next();
                    if (!value1.equals(value2)) {
                        isEqual = false;
                        break;
                    }
                }
                if (!isEqual) unqualifiedObsSet.add(obs2);
            }
            unqualifiedObsPair.put(obs1, unqualifiedObsSet);
        }
        return unqualifiedObsPair;
    }

    /**
     * Validate IC-18 Consistent dataset links: If a qb:DataSet D has a
     * qb:slice S, and S has an qb:observation O, then the qb:dataSet
     * corresponding to O must be D.
     * @return a map of observations with correct datasets that should be
     * associated to.
     */
    public Map<Resource, Resource> checkIC18() {
        String icName = "Integrity Constraint 18: Consistent Dataset Links";
        logger.info("Validating " + icName);
        Map<Resource, Resource> obsNotInDataset = new HashMap<Resource, Resource>();
        Property[] properties = {QB_slice, QB_observation};
        Map<Resource, Set<? extends RDFNode>> obsByDataset = searchByPathVisit(null,
                Arrays.asList(properties), null);
        for (Resource dataset : obsByDataset.keySet()) {
            Set<? extends RDFNode> obsSet = obsByDataset.get(dataset);
            for (RDFNode obs : obsSet) {
                Resource obsAsRes = obs.asResource();
                if (!model.listStatements(obsAsRes, QB_dataSet, dataset).hasNext())
                    obsNotInDataset.put(obsAsRes, dataset);
            }
        }
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
    public Map<RDFNode, Set<RDFNode>> checkIC19() {
        String icName = "Integrity Constraint 19: Codes From Code List";
        logger.info("Validating " + icName);
        Map<RDFNode, Set<RDFNode>> valNotInCodeList = new HashMap<RDFNode, Set<RDFNode>>();
        Map<RDFNode, Set<? extends RDFNode>> conceptCLByDim =
                new HashMap<RDFNode, Set<? extends RDFNode>>();
        Map<RDFNode, Set<? extends RDFNode>> collectionCLByDim =
                new HashMap<RDFNode, Set<? extends RDFNode>>();
        Set<Resource> conceptCLWithDefSet = model.listSubjectsWithProperty(RDF_type,
                SKOS_ConceptScheme).toSet();
        Set<Resource> collectionCLWithDefSet = model.listSubjectsWithProperty(RDF_type,
                SKOS_Collection).toSet();
        Map<Resource, Set<? extends RDFNode>> dimByDataset = searchByPathVisit(null,
                Arrays.asList(QB_structure, QB_component, QB_componentProperty), null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DimensionProperty);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_codeList));
        for (Resource dataset : dimByDataset.keySet()) {
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            Set<? extends RDFNode> dimSet = dimByDataset.get(dataset);
            dimSet.retainAll(objBySubAndProp.keySet());
            for (RDFNode dim : dimSet) {
                Set<RDFNode> conceptCLSet = objBySubAndProp.get(dim.asResource()).get(QB_codeList);
                Set<RDFNode> collectionCLSet = new HashSet<RDFNode>(conceptCLSet);
                conceptCLSet.retainAll(conceptCLWithDefSet);
                collectionCLSet.retainAll(collectionCLWithDefSet);
                if (!conceptCLSet.isEmpty()) conceptCLByDim.put(dim, conceptCLSet);
                if (!collectionCLSet.isEmpty()) collectionCLByDim.put(dim, collectionCLSet);
            }
            valNotInCodeList.putAll(obsWithFaultyDimCheck(obsSet, conceptCLByDim,
                    collectionCLByDim));
        }
        String logMsg = " is not included in the following code lists: ";
        logValidationResult(icName, valNotInCodeList, logMsg);
        return valNotInCodeList;
    }

    /**
     * This function is a subtask of function checkIC19 to check if the
     * dimension values of a set of observations match the given code lists
     * @param obsSet a set of observations
     * @param conceptCLByDim a map of dimensions with corresponding code lists
     *                       of the ConceptScheme type
     * @param collectionCLByDim a map of dimensions with corresponding code
     *                          lists of the Collection type
     * @return a map of values with a set of code lists not including the
     * values
     */
    private Map<RDFNode, Set<RDFNode>> obsWithFaultyDimCheck (Set<Resource> obsSet,
            Map<RDFNode, Set<? extends RDFNode>> conceptCLByDim,
            Map<RDFNode, Set<? extends RDFNode>> collectionCLByDim) {
        Map<RDFNode, Set<RDFNode>> valNotInCodeList = new HashMap<RDFNode, Set<RDFNode>>();
        Set<Property> dimWithConcept = nodeToProperty(conceptCLByDim.keySet());
        Set<Property> dimWithCollection = nodeToProperty(collectionCLByDim.keySet());
        for (Resource obs : obsSet) {
            Map<RDFNode, Set<RDFNode>> valNotInConceptCL =
                    dimValueCheck(true, obs, dimWithConcept, conceptCLByDim);
            Map<RDFNode, Set<RDFNode>> valNotInCollectionCL =
                    dimValueCheck(false, obs, dimWithCollection, collectionCLByDim);
            for (RDFNode value : valNotInConceptCL.keySet()) {
                if (valNotInCodeList.containsKey(value)) {
                    Set<RDFNode> codeList = valNotInCodeList.get(value);
                    codeList.addAll(valNotInConceptCL.get(value));
                    valNotInCodeList.put(value, codeList);
                }
                else valNotInCodeList.put(value, valNotInConceptCL.get(value));
            }
            for (RDFNode value : valNotInCollectionCL.keySet()) {
                if (valNotInCodeList.containsKey(value)) {
                    Set<RDFNode> codeList = valNotInCodeList.get(value);
                    codeList.addAll(valNotInCollectionCL.get(value));
                    valNotInCodeList.put(value, codeList);
                }
                else valNotInCodeList.put(value, valNotInCollectionCL.get(value));
            }
        }
        return valNotInCodeList;
    }

    /**
     * This function is a subtask of function checkIC19 to check if the
     * dimension values of an observation matches one of the given code lists
     * @param isConceptList indicates the type of code list, true for Concept
     *                      Scheme and false for Collection.
     * @param obs an observation
     * @param dimAsPropSet a set of properties of the given observation
     * @param codeListByDim a set of candidate code lists for the given
     *                      properties
     * @return a map of values with a set of code lists not including the
     * values
     */
    private Map<RDFNode, Set<RDFNode>> dimValueCheck (boolean isConceptList,
                Resource obs, Set<Property> dimAsPropSet,
                Map<RDFNode, Set<? extends RDFNode>> codeListByDim) {
        Map<RDFNode, Set<RDFNode>> valNotInCodeList = new HashMap<RDFNode, Set<RDFNode>>();
        for (Property dimAsProp : dimAsPropSet) {
            Set<RDFNode> valueSet = model.listObjectsOfProperty(obs, dimAsProp).toSet();
            if (valueSet.size() == 1) {
                RDFNode value = valueSet.iterator().next();
                Set<RDFNode> codeList = new HashSet<RDFNode>();
                codeList.addAll(codeListByDim.get(dimAsProp));
                if (!value.isURIResource() || !connectedToCodeList(isConceptList,
                        value.asResource(), codeList)) {
                    if (valNotInCodeList.containsKey(value)) {
                        Set<RDFNode> cl = valNotInCodeList.get(value);
                        cl.addAll(codeList);
                        valNotInCodeList.put(value, cl);
                    } else {
                        valNotInCodeList.put(value, codeList);
                    }
                }
            }
        }
        return valNotInCodeList;
    }

    /**
     * This function is a subtask of function checkIC19 to check if the given
     * value is included in a code list.
     * @param isConceptList indicates the type of code list, true for Concept
     *                      Scheme and false for Collection.
     * @param value value of a dimension property
     * @param codeListSet a set of candidate code lists
     * @return a boolean value indicating if the value is included in a code
     * list
     */
    private boolean connectedToCodeList (boolean isConceptList, Resource value,
                                         Set<? extends RDFNode> codeListSet) {
        boolean isConnected = false;
        if (!model.listStatements(value, RDF_type, SKOS_Concept).hasNext())
            return false;
        for (RDFNode codelist : codeListSet) {
            if (isConceptList)
                isConnected = model.listStatements(value, SKOS_inScheme, codelist).hasNext();
            else
                isConnected = connectedByRepeatedProp(codelist.asResource(), SKOS_member, value);
            if (isConnected) break;
        }
        return isConnected;
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
     * @return a list of two maps containing observations with dimension values
     * not connected to hierarchical code lists, along both direct and inverse
     * links
     */
    public List<Map<Resource, Set<RDFNode>>> checkIC20_21 () {
        String icName20 = "Integrity Constraint 20: Codes From Hierarchy";
        String icName21 = "Integrity Constraint 21: Codes From Hierarchy (Inverse)";
        logger.info("Validating " + icName20 + " & " +icName21);
        Map<Resource, Set<RDFNode>> dimSetByObsNotConByDirPcp =
                new HashMap<Resource, Set<RDFNode>>();
        Map<Resource, Set<RDFNode>> dimSetByObsNotConByInvPcp =
                new HashMap<Resource, Set<RDFNode>>();
        List<Map<Resource, Set<RDFNode>>> dimSetByObsNotConByPcp =
                new ArrayList<Map<Resource, Set<RDFNode>>>();
        Map<Resource, Map<String, Set<Property>>> pcpByCodeList = getPcpByCodeList();
        Set<Resource> codeListWithDefSet = model.listResourcesWithProperty(RDF_type,
                QB_HierarchicalCodeList).toSet();
        Map<Resource, Set<? extends RDFNode>> dimByDataset = searchByPathVisit(null,
                Arrays.asList(QB_structure, QB_component, QB_componentProperty), null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DimensionProperty);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_codeList));
        for (Resource dataset : dimByDataset.keySet()) {
            Map<Property, Set<RDFNode>> codeListByDim = new HashMap<Property, Set<RDFNode>>();
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            Set<? extends RDFNode> dimSet = dimByDataset.get(dataset);
            dimSet.retainAll(objBySubAndProp.keySet());
            for (RDFNode dim : dimSet) {
                Set<RDFNode> codeListSet = objBySubAndProp.get(dim.asResource()).get(QB_codeList);
                codeListSet.retainAll(codeListWithDefSet);
                Property dimAsProp = ResourceFactory.createProperty(dim.asResource().getURI());
                if (!codeListSet.isEmpty()) codeListByDim.put(dimAsProp, codeListSet);
            }
            dimSetByObsNotConByDirPcp.putAll(
                    obsSetPcpCheck("DIRECT", obsSet, codeListByDim, pcpByCodeList));
            dimSetByObsNotConByInvPcp.putAll(
                    obsSetPcpCheck("INVERSE", obsSet, codeListByDim, pcpByCodeList));
        }
        dimSetByObsNotConByPcp.add(dimSetByObsNotConByDirPcp);
        dimSetByObsNotConByPcp.add(dimSetByObsNotConByInvPcp);
        String logMsg20 = " has values for the following dimensions not reachable along a direct link path: ";
        String logMsg21 = " has values for the following dimensions not reachable along an inverse link path: ";
        logValidationResult(icName20, dimSetByObsNotConByDirPcp, logMsg20);
        logValidationResult(icName21, dimSetByObsNotConByInvPcp, logMsg21);
        return dimSetByObsNotConByPcp;
    }

    /**
     * This function is a subtask of function checkIC20_21 for checking if the
     * dimension values of a set of observations are connected to their
     * corresponding code list through a parent child property
     * @param direction indicates a direct or inverse link path
     * @param obsSet a set of observations
     * @param codeListByDim a map of dimensions with corresponding code lists
     * @param pcpByCodeList a map of code lists with corresponding parent child
     *                      properties
     * @return a map of observations of which the dimension values are not
     * connected to corresponding code lists by any parent child property
     */
    public Map<Resource, Set<RDFNode>> obsSetPcpCheck (String direction,
                      Set<Resource> obsSet, Map<Property, Set<RDFNode>> codeListByDim,
                      Map<Resource, Map<String, Set<Property>>> pcpByCodeList) {
        Map<Resource, Set<RDFNode>> faultyDimSetByObs = new HashMap<Resource, Set<RDFNode>>();
        for (Resource obs : obsSet) {
            Set<RDFNode> faultyDimSet =
                    obsDimValNotConByPcpCheck(direction, obs, codeListByDim, pcpByCodeList);
            if (!faultyDimSet.isEmpty()) faultyDimSetByObs.put(obs, faultyDimSet);
        }
        return faultyDimSetByObs;
    }

    /**
     * This function is a subtask of function checkIC20_21 for checking if the
     * dimension values of an observation is conneted to a code list through a
     * parent child property
     * @param direction indicates a direct or inverse link path
     * @param obs an observation
     * @param codeListByDim a map of dimensions with corresponding code lists
     * @param pcpByCodeList a map of code lists with corresponding parent child
     *                      properties
     * @return a set of dimensions of which values are not connected to
     * corresponding code lists by any parent child property
     */
    public Set<RDFNode> obsDimValNotConByPcpCheck (String direction, Resource obs,
                          Map<Property, Set<RDFNode>> codeListByDim,
                          Map<Resource, Map<String, Set<Property>>> pcpByCodeList) {
        Set<RDFNode> dimNotConByPcp = new HashSet<RDFNode>();
        for (Property dim : codeListByDim.keySet()) {
            Set<RDFNode> codeListSet = codeListByDim.get(dim);
            Set<RDFNode> valueSet = model.listObjectsOfProperty(obs, dim).toSet();
            if (valueSet.size() != 1) continue;
            RDFNode value = valueSet.iterator().next();
            boolean isConnected = false;
            for (RDFNode codeList : codeListSet) {
                Map<String, Set<Property>> pcpByDirect = pcpByCodeList.get(codeList.asResource());
                System.out.println(pcpByDirect);
                isConnected = connectedByPcp(direction, codeList.asResource(),
                        pcpByDirect.get(direction), value);
                if (isConnected) break;
            }
            if (!isConnected) dimNotConByPcp.add(dim);
        }
        return dimNotConByPcp;
    }

    /**
     * This function is a subtask of function checkIC20_21 for checking if a
     * code list is connected to a value through one of parent child
     * properties in the given set
     * @param direction indicates a direct or inverse link path
     * @param codeList a code list
     * @param pcpSet a set of candidate parent child properties for the code
     *               lsit
     * @param value the dimension value of an observation
     * @return a boolean value indicating whether there is a connection or not
     */
    public boolean connectedByPcp (String direction, Resource codeList,
                                   Set<Property> pcpSet, RDFNode value) {
        boolean isConnected = false;
        for (Property pcp : pcpSet) {
            if (direction.equals("DIRECT"))
                isConnected = connectedByRepeatedProp(codeList, Arrays.asList(QB_hierarchyRoot),
                        pcp, value, true);
            else
                isConnected = connectedByRepeatedProp(codeList, Arrays.asList(QB_hierarchyRoot),
                        pcp, value, false);
            if (isConnected) break;
        }
        return isConnected;
    }

    /**
     * This function is a subtask of function checkIC20_21 for getting sets of
     * candidate parent child properties for the corresponding code lists
     * @return a map of code lists with corresponding parent child properties
     */
    public Map<Resource, Map<String, Set<Property>>> getPcpByCodeList () {
        Map<Resource, Map<String, Set<Property>>> pcpByCodeList =
                new HashMap<Resource, Map<String, Set<Property>>>();
        Map<String, Set<Property>> pcpByDirect = new HashMap<String, Set<Property>>();
        Set<Property> dirPcpSet = new HashSet<Property>();
        Set<Property> invPcpSet = new HashSet<Property>();
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_HierarchicalCodeList);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_parentChildProperty));
        for (Resource codeList : objBySubAndProp.keySet()) {
            Set<RDFNode> pcpNodeSet =
                    objBySubAndProp.get(codeList).get(QB_parentChildProperty);
            for (RDFNode pcp : pcpNodeSet) {
                if (pcp.isURIResource())
                    dirPcpSet.add(ResourceFactory.createProperty(pcp.asResource().getURI()));
                else if (pcp.isAnon()) {
                    NodeIterator invPcpIter =
                            model.listObjectsOfProperty(pcp.asResource(), OWL_inverseOf);
                    invPcpSet.addAll(nodeToProperty(invPcpIter.toSet()));
                }
            }
            pcpByDirect.put("DIRECT", dirPcpSet);
            pcpByDirect.put("INVERSE", invPcpSet);
            pcpByCodeList.put(codeList, pcpByDirect);
            dirPcpSet.clear();
            invPcpSet.clear();
            pcpByDirect.clear();
        }
        return pcpByCodeList;
    }

    /**
     * Checks if a subject is connected to an object through a list of
     * properties
     * @param subject an RDF resource
     * @param fixPropList a list of properties representing the property path
     * @param object a candidate value associated to the resource through the
     *               given property path
     * @return a boolean value indicating if they are connected
     */
    private boolean connectedByPropList(Resource subject,
                                        List<Property> fixPropList, RDFNode object) {
        boolean isConnected = false;
        Map<Resource, Set<? extends RDFNode>> objSetBySub = searchByPathVisit(subject,
                fixPropList, null);
        if (objSetBySub.containsKey(subject)) {
            if (objSetBySub.get(subject).contains(object)) isConnected = true;
        }
        return isConnected;
    }

    /**
     * Checks if a subject is connected to an object through a list of
     * properties and a repetitive property
     * @param subject an RDF resource
     * @param fixPropList a list of properties representing the property path
     * @param repProp a property that could be repeated for multiple times
     *                appending to the end of the property path
     * @param object a candidate value associated to the resource through the
     *               given property path
     * @param isDirect indicate the direction of the property path (direct or
     *                 inverse)
     * @return a boolean value indicating if they are connected
     */
    private boolean connectedByRepeatedProp(Resource subject, List<Property> fixPropList,
                                            Property repProp, RDFNode object,
                                            boolean isDirect) {
        boolean isConnected = false;
        Map<Resource, Set<? extends RDFNode>> objSetBySub = searchByPathVisit(subject,
                fixPropList, null);
        Set<? extends RDFNode> objectSet = objSetBySub.get(subject);
        for (RDFNode objOfPropPath : objectSet) {
            if (connectedByRepeatedProp(objOfPropPath.asResource(),
                    repProp, object, isDirect)) {
                isConnected = true;
                break;
            }
        }
        return isConnected;
    }

    /**
     * Checks if a subject is connected to an object through a repetitive
     * property
     * @param subject an RDF resource
     * @param repProp a property that could be repeated for multiple times
     *                appending to the end of the fix property list
     * @param object a candidate value associated to the resource through the
     *               given property path
     * @param isDirect indicate the direction of the property path (direct or
     *                 inverse)
     * @return a boolean value indicating if they are connected
     */
    private boolean connectedByRepeatedProp(Resource subject, Property repProp,
                                            RDFNode object, boolean isDirect) {
        if (isDirect) return connectedByRepeatedProp(subject, repProp, object);
        else return connectedByRepeatedProp(object.asResource(), repProp, subject);
    }

    /**
     * Checks if a subject is connected to an object through a repetitive
     * property
     * @param subject an RDF resource
     * @param repProp a property that could be repeated for multiple times
     *                appending to the end of the fix property list
     * @param object a candidate value associated to the resource through the
     *               given property path
     * @return a boolean value indicating if they are connected
     */
    private boolean connectedByRepeatedProp(Resource subject, Property repProp,
                                            RDFNode object) {
        boolean isConnected = false;
        Set<RDFNode> objectSet = searchObjectsOfProperty(Collections.singleton(subject),
                repProp);
        while (!objectSet.isEmpty()) {
            if (objectSet.contains(object)) {
                isConnected = true;
                break;
            }
            else objectSet = searchObjectsOfProperty(nodeToResource(objectSet), repProp);
        }
        return isConnected;
    }

    /**
     * Searches resources and their corresponding values connected by a
     * property path (e.g.,
     * ?obs qb:dataSet/qb:structure/qb:component/qb:componentProperty ?dim)
     * @param subject an RDF resource
     * @param propPath a list of properties representing the property path
     * @param object a candidate value associated to the resource through the
     *               given property path
     * @return a map of resources with corresponding values of the given
     * property path
     */
    private Map<Resource, Set<? extends RDFNode>> searchByPathVisit(
            Resource subject, List<Property> propPath, Resource object) {
        Map<Resource, Set<? extends RDFNode>> resultSet =
                new HashMap<Resource, Set<? extends RDFNode>>();
        if (propPath.size() == 0) return resultSet;
        Set<Resource> resourceSet = new HashSet<Resource>();
        Set<RDFNode> nodeSet = new HashSet<RDFNode>();

        // case: eg:obs1 qb:dataSet ?dataset
        if (subject != null) {
            NodeIterator objectIter = model.listObjectsOfProperty(subject,
                    propPath.get(0));
            while (objectIter.hasNext()) {
                resourceSet.add(objectIter.next().asResource());
            }
            for (int index = 1; index < propPath.size(); index++) {
                nodeSet = searchObjectsOfProperty(resourceSet, propPath.get(index));
            }
            if (object != null) nodeSet.retainAll(Collections.singleton(object));
            resultSet.put(subject, nodeSet);
        }

        // case: ?obs qb:dataSet eg:dataset1
        else if (subject == null && object !=null) {
            ResIterator subjectIter = model.listSubjectsWithProperty(propPath.get(0),
                    object);
            resourceSet = subjectIter.toSet();
            for (int index = 1; index < propPath.size(); index++) {
                resourceSet = searchSubjectsWithProperty(resourceSet, propPath.get(index));
            }
            resultSet.put(object, resourceSet);
        }

        // case: ?obs qb:dataSet ?dataset
        else if (subject == null && object == null) {
            ResIterator subjectIter = model.listSubjectsWithProperty(propPath.get(0));
            for (Resource sub : subjectIter.toSet()) {
                nodeSet = searchObjectsOfProperty(Collections.singleton(sub), propPath.get(0));
                for (int index = 1; index < propPath.size(); index++) {
                    nodeSet = searchObjectsOfProperty(nodeToResource(nodeSet), propPath.get(index));
                }
                resultSet.put(sub, nodeSet);
            }
        }
        return resultSet;
    }

    /**
     * Searches resources with multiple properties and corresponding values
     * (e.g.,
     * ?obs a qb:Observation
     *      qb:dataSet eg:ds1 )
     * @param subject an RDF resource
     * @param objByProp a map of properties with corresponding values
     * @return a set of qualified resources
     */
    private Set<Resource> searchByMultipleProperty(Resource subject,
                                                   Map<Property, RDFNode> objByProp) {
        Set<Resource> resultSet = new HashSet<Resource>();
        for (Property property : objByProp.keySet()) {
            if (objByProp.get(property) == null) objByProp.remove(property);
        }
        if (objByProp.size() == 0) return resultSet;
        Property seedKey = objByProp.keySet().iterator().next();
        RDFNode seedValue = objByProp.remove(seedKey);
        Set<Resource> subjectSet = model.listSubjectsWithProperty(seedKey, seedValue).toSet();
        for (Property property : objByProp.keySet()) {
            RDFNode object = objByProp.get(property);
            ResIterator subjectIter = model.listSubjectsWithProperty(property, object);
            subjectSet.retainAll(subjectIter.toSet());
        }

        if (subject != null) {
            if (subjectSet.contains(subject)) return Collections.singleton(subject);
            else return resultSet;
        }
        else return subjectSet;
    }

    /**
     * Searches resources and corresponding values with multiple properties
     * (e.g.,
     * ?obs a qb:Observation
     *      eg:dim1 eg:val1
     *      eg:dim2 eg:val2
     *      eg:dim3 ?val3
     *      eg:dim4 ?val4 )
     * @param subject an RDF resource
     * @param objByProp a map of properties with corresponding values
     * @param propWithoutVal a list of properties of which the values are
     *                           not given
     * @return a map of resources with a map of properties and corresponding
     * values
     */
    private Map<Resource, Map<Property, Set<RDFNode>>> searchByMultipleProperty(Resource subject,
            Map<Property, RDFNode> objByProp, List<Property> propWithoutVal) {
        Map<Resource, Map<Property, Set<RDFNode>>> resultSet =
                new HashMap<Resource, Map<Property, Set<RDFNode>>>();
        Set<Resource> subjectSet = searchByMultipleProperty(subject, objByProp);
        for (Resource resultSubject : subjectSet) {
            Map<Property, Set<RDFNode>> objectSetByProperty =
                    new HashMap<Property, Set<RDFNode>>();
            for (Property property : propWithoutVal) {
                Set<RDFNode> objectSet = searchObjectsOfProperty(
                        Collections.singleton(resultSubject), property);
                objectSetByProperty.put(property, objectSet);
            }
            resultSet.put(resultSubject, objectSetByProperty);
        }
        return resultSet;
    }

    /**
     * Searches objects of a property given a set of subjects
     * @param subjectSet a set of subjects
     * @param property a property
     * @return a set of objects
     */
    private Set<RDFNode> searchObjectsOfProperty(Set<Resource> subjectSet,
                                                  Property property) {
        Set<RDFNode> objectSet = new HashSet<RDFNode>();
        for (Resource subject : subjectSet) {
            NodeIterator objectIter = model.listObjectsOfProperty(subject, property);
            if (objectIter.hasNext()) objectSet.addAll(objectIter.toSet());
        }
        return objectSet;
    }

    /**
     * Searches subjects with a property given a set of objects
     * @param objectSet a set of objects
     * @param property a property
     * @return a set of subjects
     */
    private Set<Resource> searchSubjectsWithProperty(Set<? extends RDFNode> objectSet,
                                                     Property property) {
        Set<Resource> subjectSet = new HashSet<Resource>();
        for (RDFNode object : objectSet) {
            ResIterator subjectIter = model.listSubjectsWithProperty(property, object);
            if (subjectIter.hasNext()) subjectSet.addAll(subjectIter.toSet());
        }
        return subjectSet;
    }

    /**
     * Converts a set of RDFNode objects to a set of Resource objects
     * @param nodeSet a set of RDFNode objects
     * @return a set of Resource objects
     */
    private Set<Resource> nodeToResource (Set<? extends RDFNode> nodeSet) {
        Set<Resource> resourceSet = new HashSet<Resource>();
        for (RDFNode node : nodeSet) {
            if (node.isResource()) resourceSet.add(node.asResource());
        }
        return resourceSet;
    }

    /**
     * Converts a set of RDFNode objects to a set of Property objects
     * @param nodeSet a set of RDFNode objects
     * @return a set of Property objects
     */
    private Set<Property> nodeToProperty (Set<? extends RDFNode> nodeSet) {
        Set<Property> propSet = new HashSet<Property>();
        for (RDFNode node : nodeSet) {
            if (node.isURIResource()) propSet.add(ResourceFactory.createProperty(
                    node.asResource().getURI()));
        }
        return propSet;
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

    private static final String PREFIX_CUBE = "http://purl.org/linked-data/cube#";
    private static final String PREFIX_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String PREFIX_RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String PREFIX_SKOS = "http://www.w3.org/2004/02/skos/core#";
    private static final String PREFIX_OWL = "http://www.w3.org/2002/07/owl#";

    private static final Property RDF_type = ResourceFactory.createProperty(
            PREFIX_RDF + "type");
    private static final Property QB_observation = ResourceFactory.createProperty(
            PREFIX_CUBE + "observation");
    private static final Property QB_Observation = ResourceFactory.createProperty(
            PREFIX_CUBE + "Observation");
    private static final Property QB_dataSet = ResourceFactory.createProperty(
            PREFIX_CUBE + "dataSet");
    private static final Property QB_DataSet = ResourceFactory.createProperty(
            PREFIX_CUBE + "DataSet");
    private static final Property QB_slice = ResourceFactory.createProperty(
            PREFIX_CUBE + "slice");
    private static final Property QB_Slice = ResourceFactory.createProperty(
            PREFIX_CUBE + "Slice");
    private static final Property QB_sliceKey = ResourceFactory.createProperty(
            PREFIX_CUBE + "sliceKey");
    private static final Property QB_SliceKey = ResourceFactory.createProperty(
            PREFIX_CUBE + "SliceKey");
    private static final Property QB_sliceStructure = ResourceFactory.createProperty(
            PREFIX_CUBE + "sliceStructure");
    private static final Property QB_component = ResourceFactory.createProperty(
            PREFIX_CUBE + "component");
    private static final Property QB_componentProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "componentProperty");
    private static final Property QB_DimensionProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "DimensionProperty");
    private static final Property QB_dimension = ResourceFactory.createProperty(
            PREFIX_CUBE + "dimension");
    private static final Property QB_MeasureProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "MeasureProperty");
    private static final Property QB_measure = ResourceFactory.createProperty(
            PREFIX_CUBE + "measure");
    private static final Property QB_measureType = ResourceFactory.createProperty(
            PREFIX_CUBE + "measureType");
    private static final Property QB_AttributeProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "AttributeProperty");
    private static final Property QB_attribute = ResourceFactory.createProperty(
            PREFIX_CUBE + "attribute");
    private static final Property QB_componentAttachment = ResourceFactory.createProperty(
            PREFIX_CUBE + "componentAttachment");
    private static final Property QB_componentRequired = ResourceFactory.createProperty(
            PREFIX_CUBE + "componentRequired");
    private static final Property QB_structure = ResourceFactory.createProperty(
            PREFIX_CUBE + "structure");
    private static final Property QB_DataStructureDefinition =
            ResourceFactory.createProperty(PREFIX_CUBE + "DataStructureDefinition");
    private static final Property QB_codeList = ResourceFactory.createProperty(
            PREFIX_CUBE + "codeList");
    private static final Property QB_HierarchicalCodeList = ResourceFactory.createProperty(
            PREFIX_CUBE + "HierarchicalCodeList");
    private static final Property QB_hierarchyRoot = ResourceFactory.createProperty(
            PREFIX_CUBE + "hierarchyRoot");
    private static final Property QB_parentChildProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "parentChildProperty");
    private static final Property RDFS_range = ResourceFactory.createProperty(
            PREFIX_RDFS + "range");
    private static final Property SKOS_Concept = ResourceFactory.createProperty(
            PREFIX_SKOS + "Concept");
    private static final Property SKOS_ConceptScheme = ResourceFactory.createProperty(
            PREFIX_SKOS + "ConceptScheme");
    private static final Property SKOS_inScheme = ResourceFactory.createProperty(
            PREFIX_SKOS + "inScheme");
    private static final Property SKOS_Collection = ResourceFactory.createProperty(
            PREFIX_SKOS + "Collection");
    private static final Property SKOS_member = ResourceFactory.createProperty(
            PREFIX_SKOS + "member");
    private static final Property OWL_inverseOf = ResourceFactory.createProperty(
            PREFIX_OWL + "inverseOf");
    private static final Literal LITERAL_FALSE = ResourceFactory.createTypedLiteral(
            Boolean.FALSE);
    private static final Literal LITERAL_TRUE = ResourceFactory.createTypedLiteral(
            Boolean.TRUE);
}
