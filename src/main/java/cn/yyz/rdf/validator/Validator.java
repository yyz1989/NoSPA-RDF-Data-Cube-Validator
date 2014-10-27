package cn.yyz.rdf.validator;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.RDF;
import org.apache.commons.codec.binary.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

/**
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
    public void output(String outputPath, String outputFormat) {
        model.write(System.out, outputFormat);
    }

    /**
     * This function normalizes an abbreviated Data Cube with SPARQL queries.
     */
    public void normalizeBySparql() {
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
        Map<Resource, Map<Property, Set<RDFNode>>> compBySpec = searchByChildProperty(null,
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
        Map<Resource, Map<Property, Set<RDFNode>>> compBySpec = searchByChildProperty(null,
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
                searchByChildProperty(null, objByProp, propertySet);
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
     * @return a map containing observations with multiple datasets
     */
    public Map<Resource, Set<RDFNode>> checkIC1() {
        String icName = "Integrity Constraint 1";
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
        String logMsg = " is associated to datasets: ";
        logValidationResult(icName, datasetByObs, logMsg);
        return datasetByObs;
    }

    public void checkIC2() {
        Map<Resource, Set<Resource>> dsdByDataset =
                new HashMap<Resource, Set<Resource>>();
        ResIterator datasetIter = model.listSubjectsWithProperty(
            RDF_type, QB_DataSet);
        while (datasetIter.hasNext()) {
            Resource dataset = datasetIter.nextResource();
            Set dsdSet = model.listObjectsOfProperty(dataset, QB_structure).toSet();
            if (dsdSet.size() != 1) {
                dsdByDataset.put(dataset, dsdSet);
            }
        }
        System.out.println(dsdByDataset);
    }

    public void checkIC3() {
        Set<Resource> dsdWithoutMeasure = new HashSet<Resource>();
        ResIterator dsdIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DataStructureDefinition);
        while (dsdIterator.hasNext()) {
            Resource dsd = dsdIterator.nextResource();
            Property[] properties = {QB_component, QB_componentProperty, RDF_type};
            Map<Resource, Set<? extends RDFNode>> dsdPropertyMap = searchByPathVisit(dsd,
                    Arrays.asList(properties), QB_MeasureProperty);
            if (dsdPropertyMap.get(dsd).isEmpty()) dsdWithoutMeasure.add(dsd);
        }
        System.out.println(dsdWithoutMeasure);
    }

    public void checkIC4() {
        ResIterator dimensionIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty);
        ResIterator dimensionWithRangeIterator = model.listSubjectsWithProperty(
                RDFS_range);
        Set<Resource> dimensionWithoutRange = dimensionIterator.toSet();
        dimensionWithoutRange.retainAll(dimensionWithRangeIterator.toSet());
        System.out.println(dimensionWithoutRange);
    }

    public void checkIC5() {
        Set<Resource> dimensionWithoutCodelist = new HashSet<Resource>();
        Map<Property, RDFNode> objectByProperty = new HashMap<Property, RDFNode>();
        objectByProperty.put(RDF_type, QB_DimensionProperty);
        objectByProperty.put(RDFS_range, SKOS_Concept);
        Set<Resource> dimensionSet = searchByChildProperty(null, objectByProperty);
        for (Resource dimension : dimensionSet) {
            NodeIterator codelistIterator = model.listObjectsOfProperty(dimension, QB_codeList);
            if (!codelistIterator.hasNext()) dimensionWithoutCodelist.add(dimension);
        }
        System.out.println(dimensionWithoutCodelist);
    }

    public void checkIC6() {
        Set<RDFNode> componentSet = new HashSet<RDFNode>();
        Map<Property, RDFNode> objectByProperty = new HashMap<Property, RDFNode>();
        objectByProperty.put(QB_componentRequired, LITERAL_FALSE);
        List<Property> propertyOnly = Collections.singletonList(QB_componentProperty);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp = searchByChildProperty(
                null, objectByProperty, propertyOnly);
        NodeIterator compSpecIter = model.listObjectsOfProperty(QB_component);
        while (compSpecIter.hasNext()) {
            RDFNode compSpecNode = compSpecIter.next();
            if (compSpecNode.isResource()) {
                Resource compSpecRes = compSpecNode.asResource();
                if (objBySubAndProp.containsKey(compSpecRes))
                    componentSet.addAll(objBySubAndProp
                            .get(compSpecRes).get(QB_componentProperty));
            }
        }
        ResIterator compDefIter = model.listSubjectsWithProperty(
                RDF_type, QB_AttributeProperty);
        componentSet.retainAll(compDefIter.toSet());
        System.out.println(componentSet);
    }

    public void checkIC7() {
        Map<Property, RDFNode> objectByProperty = new HashMap<Property, RDFNode>();
        objectByProperty.put(RDF_type, QB_DataStructureDefinition);
        List<Property> propertyOnly = Collections.singletonList(QB_sliceKey);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByChildProperty(null, objectByProperty, propertyOnly);
        Set<Resource> sliceKeySet = model.listSubjectsWithProperty(RDF_type, QB_SliceKey).toSet();
        for (Resource dsd : objBySubAndProp.keySet()) {
            Set<RDFNode> objectSet = objBySubAndProp.get(dsd).get(QB_sliceKey);
            sliceKeySet.removeAll(objectSet);
        }
        System.out.println(sliceKeySet);
    }

    public void checkIC8() {
        Set<RDFNode> compWithoutDSD = new HashSet<RDFNode>();
        List<Property> dsdToProp = Arrays.asList(QB_component, QB_componentProperty);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_SliceKey);
        List<Property> compProp = Collections.singletonList(QB_componentProperty);
        List<Property> sliceKeyProp = Collections.singletonList(QB_sliceKey);
        Set<RDFNode> propSet = new HashSet<RDFNode>();
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByChildProperty(null, objByProp, compProp);
        Map<Resource, Set<? extends RDFNode>> sliceKeyByDSD =
                searchByPathVisit(null, sliceKeyProp, null);
        for (Resource dsd : sliceKeyByDSD.keySet()) {
            Set<? extends RDFNode> sliceKeySet = sliceKeyByDSD.get(dsd);
            for (RDFNode sliceKey : sliceKeySet) {
                propSet.addAll(objBySubAndProp
                        .get(sliceKey.asResource()).get(QB_componentProperty));
            }
            for (RDFNode property : propSet) {
                if (!connectedByPropList(dsd, dsdToProp, property))
                    compWithoutDSD.add(property);
            }
        }
        System.out.println(compWithoutDSD);
    }

    public void checkIC9() {
        Map<Resource, Set<RDFNode>> structBySlice =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> sliceSet = model.listSubjectsWithProperty(RDF_type, QB_Slice).toSet();
        for (Resource slice : sliceSet) {
            Set<RDFNode> sliceStructSet = model.listObjectsOfProperty(slice,
                    QB_sliceStructure).toSet();
            if (sliceStructSet.size() != 1) structBySlice.put(slice,
                    sliceStructSet);
        }
        System.out.println(structBySlice);
    }

    public void checkIC10() {
        Map<Resource, RDFNode> dimBySliceWithoutVal = new HashMap<Resource, RDFNode>();
        List<Property> propPath = Arrays.asList(QB_sliceStructure, QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> dimBySlice = searchByPathVisit(null, propPath, null);
        for (Resource slice : dimBySlice.keySet()) {
            for (RDFNode dim : dimBySlice.get(slice)) {
                Property dimAsProp = ResourceFactory.createProperty(dim.asResource().getURI());
                NodeIterator valIter = model.listObjectsOfProperty(slice, dimAsProp);
                if (!valIter.hasNext()) dimBySliceWithoutVal.put(slice, dim);
            }
        }
        System.out.println(dimBySliceWithoutVal);
    }

    public void checkIC11_12() {
        Map<Resource, Set<RDFNode>> faultyObservation = new HashMap<Resource, Set<RDFNode>>();
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
            faultyObservation.putAll(dimValueCheck(obsSet, dimSet));
        }
        System.out.println(faultyObservation);
    }

    private Map<Resource, Set<RDFNode>> dimValueCheck (Set<Resource> obsSet,
                                                  Set<? extends RDFNode> dimSet) {
        Map<Resource, Set<RDFNode>> faultyObservation = new HashMap<Resource, Set<RDFNode>>();
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
            if (!dimWithoutValSet.isEmpty()) faultyObservation.put(obs, dimWithoutValSet);
            else {
                if (valueSetByObs.containsValue(valueSet)) faultyObservation.put(obs,
                        dimWithoutValSet);
                else valueSetByObs.put(obs, valueSet);
            }
        }
        return faultyObservation;
    }

    public void checkIC13() {
        Map<Resource, RDFNode> obsWithoutAttribVal = new HashMap<Resource, RDFNode>();
        List<Property> propPath = Arrays.asList(QB_structure, QB_component);
        Map<Resource, Set<? extends RDFNode>> compByDataset = searchByPathVisit(
                null, propPath, null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(QB_componentRequired, LITERAL_TRUE);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp = searchByChildProperty(null,
                objByProp, Arrays.asList(QB_componentProperty));
        for (Resource dataset : compByDataset.keySet()) {
            Set<? extends RDFNode> compSet = compByDataset.get(dataset);
            Set<RDFNode> attribSet = new HashSet<RDFNode>();
            compSet.retainAll(objBySubAndProp.keySet());
            for (RDFNode component : compSet) {
                attribSet.addAll(objBySubAndProp.get(component.asResource())
                        .get(QB_componentProperty));
            }
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            obsWithoutAttribVal.putAll(attribValueCheck(obsSet, attribSet));
        }
        System.out.println(obsWithoutAttribVal);
    }

    private Map<Resource, RDFNode> attribValueCheck (Set<Resource> obsSet,
                                                     Set<RDFNode> attribSet) {
        Map<Resource, RDFNode> obsWithoutAttribVal = new HashMap<Resource, RDFNode>();
        Set<Property> attribAsPropSet = nodeToProperty(attribSet);
        for (Resource obs : obsSet) {
            for (Property attribProp : attribAsPropSet) {
                if (!model.listObjectsOfProperty(obs, attribProp).hasNext())
                    obsWithoutAttribVal.put(obs, attribProp);
            }
        }
        return obsWithoutAttribVal;
    }

    public void checkIC14() {
        Map<Resource, RDFNode> obsWithoutMeasureVal = new HashMap<Resource, RDFNode>();
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
        System.out.println(obsWithoutMeasureVal);
    }

    private Map<Resource, RDFNode> measureValueCheck (Set<Resource> obsSet,
                                                      Set<? extends RDFNode> measureSet) {
        Map<Resource, RDFNode> obsWithoutMeasureVal = new HashMap<Resource, RDFNode>();
        Set<Property> measureAsPropSet = nodeToProperty(measureSet);
        for (Resource obs : obsSet) {
            for (Property measure : measureAsPropSet) {
                if (!model.listObjectsOfProperty(obs, measure).hasNext())
                    obsWithoutMeasureVal.put(obs, measure);
            }
        }
        return obsWithoutMeasureVal;
    }

    public void checkIC15_16() {
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
        System.out.println(obsWithFaultyMeasure);
    }

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

    public void checkIC17() {
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
        System.out.println(numObs2ByObs1);
    }

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

    public void checkIC18() {
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
        System.out.println(obsNotInDataset);
    }

    public void checkIC19() {
        Map<Resource, Set<RDFNode>> dimValIsNotCode = new HashMap<Resource, Set<RDFNode>>();
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
                searchByChildProperty(null, objByProp, Arrays.asList(QB_codeList));
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
            dimValIsNotCode.putAll(obsWithFaultyDimCheck(obsSet, conceptCLByDim,
                    collectionCLByDim));
        }
        System.out.println(dimValIsNotCode);
    }

    private Map<Resource, Set<RDFNode>> obsWithFaultyDimCheck (Set<Resource> obsSet,
            Map<RDFNode, Set<? extends RDFNode>> conceptCLByDim,
            Map<RDFNode, Set<? extends RDFNode>> collectionCLByDim) {
        Map<Resource, Set<RDFNode>> dimValIsNotCode = new HashMap<Resource, Set<RDFNode>>();
        Set<Property> dimWithConcept = nodeToProperty(conceptCLByDim.keySet());
        Set<Property> dimWithCollection = nodeToProperty(collectionCLByDim.keySet());
        for (Resource obs : obsSet) {
            Set<RDFNode> fautyDimSet = new HashSet<RDFNode>();
            fautyDimSet.addAll(dimValueCheck(true, obs, dimWithConcept, conceptCLByDim));
            fautyDimSet.addAll(dimValueCheck(false, obs, dimWithCollection, collectionCLByDim));
            if (fautyDimSet.size() != 0) dimValIsNotCode.put(obs, fautyDimSet);
        }
        return dimValIsNotCode;
    }

    private Set<RDFNode> dimValueCheck (boolean isConceptList, Resource obs, Set<Property> dimAsPropSet,
                         Map<RDFNode, Set<? extends RDFNode>> codeListByDim) {
        Set<RDFNode> fautyDimSet = new HashSet<RDFNode>();
        for (Property dimAsProp : dimAsPropSet) {
            Set<RDFNode> valueSet = model.listObjectsOfProperty(obs, dimAsProp).toSet();
            if (valueSet.size() == 1) {
                RDFNode value = valueSet.iterator().next();
                if (!value.isURIResource() || !connectedToCodeList(isConceptList,
                        value.asResource(), codeListByDim.get(dimAsProp)))
                    fautyDimSet.add(dimAsProp);
            }
        }
        return fautyDimSet;
    }

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

    public void checkIC20_21 () {
        Map<Resource, Set<RDFNode>> dimSetByObsNotConByDirPcp =
                new HashMap<Resource, Set<RDFNode>>();
        Map<Resource, Set<RDFNode>> dimSetByObsNotConByInvPcp =
                new HashMap<Resource, Set<RDFNode>>();
        Map<Resource, Map<String, Set<Property>>> pcpByCodeList = getPcpByCodeList();
        if (pcpByCodeList.isEmpty()) {
            return;
        }
        Set<Resource> codeListWithDefSet = model.listResourcesWithProperty(RDF_type,
                QB_HierarchicalCodeList).toSet();
        Map<Resource, Set<? extends RDFNode>> dimByDataset = searchByPathVisit(null,
                Arrays.asList(QB_structure, QB_component, QB_componentProperty), null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DimensionProperty);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByChildProperty(null, objByProp, Arrays.asList(QB_codeList));
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
        System.out.println(dimSetByObsNotConByDirPcp.size());
        System.out.println(dimSetByObsNotConByInvPcp.size());
    }

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

    public Map<Resource, Map<String, Set<Property>>> getPcpByCodeList () {
        Map<Resource, Map<String, Set<Property>>> pcpByCodeList =
                new HashMap<Resource, Map<String, Set<Property>>>();
        Map<String, Set<Property>> pcpByDirect = new HashMap<String, Set<Property>>();
        Set<Property> directPcpSet = new HashSet<Property>();
        Set<Property> inversePcpSet = new HashSet<Property>();
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_HierarchicalCodeList);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByChildProperty(null, objByProp, Arrays.asList(QB_parentChildProperty));
        for (Resource codeList : objBySubAndProp.keySet()) {
            Set<RDFNode> pcpNodeSet =
                    objBySubAndProp.get(codeList).get(QB_parentChildProperty);
            for (RDFNode pcp : pcpNodeSet) {
                if (pcp.isURIResource())
                    directPcpSet.add(ResourceFactory.createProperty(pcp.asResource().getURI()));
                else if (pcp.isAnon()) {
                    NodeIterator invPcpIter =
                            model.listObjectsOfProperty(pcp.asResource(), OWL_inverseOf);
                    inversePcpSet.addAll(nodeToProperty(invPcpIter.toSet()));
                }
            }
            pcpByDirect.put("DIRECT", directPcpSet);
            pcpByDirect.put("INVERSE", inversePcpSet);
            pcpByCodeList.put(codeList, pcpByDirect);
            directPcpSet.clear();
            inversePcpSet.clear();
            pcpByDirect.clear();
        }
        return pcpByCodeList;
    }

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

    private boolean connectedByRepeatedProp(Resource subject, List<Property> fixPropList,
                                            Property variableProp, RDFNode object,
                                            boolean isDirect) {
        boolean isConnected = false;
        Map<Resource, Set<? extends RDFNode>> objSetBySub = searchByPathVisit(subject,
                fixPropList, null);
        Set<? extends RDFNode> objectSet = objSetBySub.get(subject);
        for (RDFNode objOfPropPath : objectSet) {
            if (connectedByRepeatedProp(objOfPropPath.asResource(),
                    variableProp, object, isDirect)) {
                isConnected = true;
                break;
            }
        }
        return isConnected;
    }

    private boolean connectedByRepeatedProp(Resource subject, Property variableProp,
                                            RDFNode object, boolean isDirect) {
        if (isDirect) return connectedByRepeatedProp(subject, variableProp, object);
        else return connectedByRepeatedProp(object.asResource(), variableProp, subject);
    }

    private boolean connectedByRepeatedProp(Resource subject, Property variableProp,
                                            RDFNode object) {
        boolean isConnected = false;
        Set<RDFNode> objectSet = searchObjectsOfProperty(Collections.singleton(subject),
                variableProp);
        while (!objectSet.isEmpty()) {
            if (objectSet.contains(object)) {
                isConnected = true;
                break;
            }
            else objectSet = searchObjectsOfProperty(nodeToResource(objectSet), variableProp);
        }
        return isConnected;
    }

    private Map<Resource, Set<? extends RDFNode>> searchByPathVisit(
            Resource subject, List<Property> properties, Resource object) {
        Map<Resource, Set<? extends RDFNode>> resultSet =
                new HashMap<Resource, Set<? extends RDFNode>>();
        if (properties.size() == 0) return resultSet;
        Set<Resource> resourceSet = new HashSet<Resource>();
        Set<RDFNode> nodeSet = new HashSet<RDFNode>();

        // case: eg:obs1 qb:dataSet ?dataset
        if (subject != null) {
            NodeIterator objectIter = model.listObjectsOfProperty(subject,
                    properties.get(0));
            while (objectIter.hasNext()) {
                resourceSet.add(objectIter.next().asResource());
            }
            for (int index = 1; index < properties.size(); index++) {
                nodeSet = searchObjectsOfProperty(resourceSet, properties.get(index));
            }
            if (object != null) nodeSet.retainAll(Collections.singleton(object));
            resultSet.put(subject, nodeSet);
        }

        // case: ?obs qb:dataSet eg:dataset1
        else if (subject == null && object !=null) {
            ResIterator subjectIter = model.listSubjectsWithProperty(properties.get(0),
                    object);
            resourceSet = subjectIter.toSet();
            for (int index = 1; index < properties.size(); index++) {
                resourceSet = searchSubjectsWithProperty(resourceSet, properties.get(index));
            }
            resultSet.put(object, resourceSet);
        }

        // case: ?obs qb:dataSet ?dataset
        else if (subject == null && object == null) {
            ResIterator subjectIter = model.listSubjectsWithProperty(properties.get(0));
            for (Resource sub : subjectIter.toSet()) {
                nodeSet = searchObjectsOfProperty(Collections.singleton(sub), properties.get(0));
                for (int index = 1; index < properties.size(); index++) {
                    nodeSet = searchObjectsOfProperty(nodeToResource(nodeSet), properties.get(index));
                }
                resultSet.put(sub, nodeSet);
            }
        }
        return resultSet;
    }

    private Set<Resource> searchByChildProperty(Resource subject,
                                                Map<Property, RDFNode> objectByProperty) {
        Set<Resource> resultSet = new HashSet<Resource>();
        for (Property property : objectByProperty.keySet()) {
            if (objectByProperty.get(property) == null) objectByProperty.remove(property);
        }
        if (objectByProperty.size() == 0) return resultSet;
        Property seedKey = objectByProperty.keySet().iterator().next();
        RDFNode seedValue = objectByProperty.remove(seedKey);
        Set<Resource> subjectSet = model.listSubjectsWithProperty(seedKey, seedValue).toSet();
        for (Property property : objectByProperty.keySet()) {
            RDFNode object = objectByProperty.get(property);
            ResIterator subjectIter = model.listSubjectsWithProperty(property, object);
            subjectSet.retainAll(subjectIter.toSet());
        }

        if (subject != null) {
            if (subjectSet.contains(subject)) return Collections.singleton(subject);
            else return resultSet;
        }
        else return subjectSet;
    }

    private Map<Resource, Map<Property, Set<RDFNode>>> searchByChildProperty(Resource subject,
           Map<Property, RDFNode> objectByProperty, List<Property> propertyOnlyList) {
        Map<Resource, Map<Property, Set<RDFNode>>> resultSet =
                new HashMap<Resource, Map<Property, Set<RDFNode>>>();
        Set<Resource> subjectSet = searchByChildProperty(subject, objectByProperty);
        for (Resource resultSubject : subjectSet) {
            Map<Property, Set<RDFNode>> objectSetByProperty =
                    new HashMap<Property, Set<RDFNode>>();
            for (Property property : propertyOnlyList) {
                Set<RDFNode> objectSet = searchObjectsOfProperty(
                        Collections.singleton(resultSubject), property);
                objectSetByProperty.put(property, objectSet);
            }
            resultSet.put(resultSubject, objectSetByProperty);
        }
        return resultSet;
    }

    private Set<RDFNode> searchObjectsOfProperty(Set<Resource> subjectSet,
                                                  Property property) {
        Set<RDFNode> objectSet = new HashSet<RDFNode>();
        for (Resource subject : subjectSet) {
            NodeIterator objectIter = model.listObjectsOfProperty(subject, property);
            if (objectIter.hasNext()) objectSet.addAll(objectIter.toSet());
        }
        return objectSet;
    }

    private Set<Resource> searchSubjectsWithProperty(Set<? extends RDFNode> objectSet,
                                                     Property property) {
        Set<Resource> subjectSet = new HashSet<Resource>();
        for (RDFNode object : objectSet) {
            ResIterator subjectIter = model.listSubjectsWithProperty(property, object);
            if (subjectIter.hasNext()) subjectSet.addAll(subjectIter.toSet());
        }
        return subjectSet;
    }

    private Set<Resource> nodeToResource (Set<? extends RDFNode> nodeSet) {
        Set<Resource> resourceSet = new HashSet<Resource>();
        for (RDFNode node : nodeSet) {
            if (node.isResource()) resourceSet.add(node.asResource());
        }
        return resourceSet;
    }

    private Set<Property> nodeToProperty (Set<? extends RDFNode> nodeSet) {
        Set<Property> propSet = new HashSet<Property>();
        for (RDFNode node : nodeSet) {
            if (node.isURIResource()) propSet.add(ResourceFactory.createProperty(
                    node.asResource().getURI()));
        }
        return propSet;
    }

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

    private <K, V> void logValidationResult (String icName,
                            Map<K, Set<V>> map, String msg) {
        logger.debug(icName);
        logger.debug(new String(new char[icName.length()]).replace("\0", "-"));
        logger.debug("");
        if (map.isEmpty()) logger.debug("Pass.");
        else {
            for (K key : map.keySet()) {
                logger.debug(key.toString() + msg);
                for (V nodeInSet : map.get(key)) {
                    logger.debug("    " + nodeInSet.toString());
                }
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
