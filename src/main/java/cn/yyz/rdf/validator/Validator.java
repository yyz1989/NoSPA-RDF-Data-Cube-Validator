package cn.yyz.rdf.validator;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.RDF;

import java.io.InputStream;
import java.util.*;

/**
 * Created by yyz on 9/26/14.
 */
public class Validator {
    private Model model;
    private Map<Resource, Set<Resource>> dimensionByDataset =
            new HashMap<Resource, Set<Resource>>();
    private Map<Resource, Map<Resource, Set<Resource>>> valueByDatasetAndDim =
            new HashMap<Resource, Map<Resource, Set<Resource>>>();
    private Map<Resource, Set<RDFNode>> dsdByDataset = new HashMap<Resource, Set<RDFNode>>();

    public Validator(String filename, String format) {
        model = ModelFactory.createDefaultModel();
        InputStream inputStream = FileManager.get().open(filename);
        if (inputStream == null) throw new IllegalArgumentException(
                "File " + filename + " not found");
        model.read(inputStream, null, format);
    }

    public void output() {
        model.write(System.out, "TURTLE");
    }

    public void normalize() {
        // Phase 1: Type and property closure
        NodeIterator nodeIterator = model.listObjectsOfProperty(QB_observation);
        while (nodeIterator.hasNext()) {
            model.add(nodeIterator.next().asResource(), RDF_type, QB_Observation);
        }

        StmtIterator stmtIterator = model.listStatements(null, QB_dataSet, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getObject().asResource(), RDF_type, QB_DataSet);
            model.add(statement.getSubject(), RDF_type, QB_Observation);
        }

        nodeIterator = model.listObjectsOfProperty(QB_slice);
        while (nodeIterator.hasNext()) {
            model.add(nodeIterator.next().asResource(), RDF_type, QB_Slice);
        }

        stmtIterator = model.listStatements(null, QB_dimension, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getSubject(), QB_componentProperty, statement.getObject());
            model.add(statement.getObject().asResource(), RDF_type, QB_DimensionProperty);
        }

        stmtIterator = model.listStatements(null, QB_measure, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getSubject(), QB_componentProperty, statement.getObject());
            model.add(statement.getObject().asResource(), RDF_type, QB_MeasureProperty);
        }

        stmtIterator = model.listStatements(null, QB_attribute, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getSubject(), QB_componentProperty, statement.getObject());
            model.add(statement.getObject().asResource(), RDF_type, QB_AttributeProperty);
        }

        // Phase 2: Push down attachment levels
        String queryString = NormalizationAlgorithm.PHASE2.getValue();
        UpdateAction.parseExecute(queryString, model);

    }

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

    public void checkConstraint(String constraint) {
        checkConstraint(IntegrityConstraint.valueOf(constraint));
    }

    public void checkConstraint(IntegrityConstraint constraint) {
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

    public void checkIC1() {
        Map<Resource, Set<RDFNode>> datasetByObservation =
                new HashMap<Resource, Set<RDFNode>>();
        ResIterator observationIter = model.listSubjectsWithProperty(
                RDF_type, QB_Observation);
        while (observationIter.hasNext()) {
            Resource observation = observationIter.nextResource();
            Set<RDFNode> datasetSet = searchObjectsOfProperty(
                    Collections.singleton(observation), QB_dataSet);
            if (datasetSet.size() != 1) {
                datasetByObservation.put(observation, datasetSet);
            }
        }
        System.out.println(datasetByObservation);
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
                objByProp, Collections.singletonList(QB_componentProperty));
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
                searchByChildProperty(null, objByProp, Collections.singletonList(QB_codeList));
        for (Resource dataset : dimByDataset.keySet()) {
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            Set<? extends RDFNode> dimSet = dimByDataset.get(dataset);
            dimSet.retainAll(objBySubAndProp.keySet());
            for (RDFNode dim : dimSet) {
                Set<RDFNode> conceptCLSet = objBySubAndProp.get(dim).get(QB_codeList);
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
        System.out.println(dimValIsNotCode.size());
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
/*
    public void checkIC20() {
        Map<Resource, Resource> dimensionByObservation = new HashMap<Resource, Resource>();
        Map<Property, Resource> codelistByDimension = new HashMap<Property, Resource>();
        Set<Property> propertySet = new HashSet<Property>();
        ResIterator hierarchyTypeDef = model.listSubjectsWithProperty(RDF_type,
                QB_HierarchicalCodeList);
        while (hierarchyTypeDef.hasNext()) {
            NodeIterator hierarchyPropertyIterator = model.listObjectsOfProperty(
                    hierarchyTypeDef.nextResource(), QB_parentChildProperty);
            while (hierarchyPropertyIterator.hasNext()) {
                RDFNode property = hierarchyPropertyIterator.next();
                if (property.isURIResource()) propertySet.add(
                        ResourceFactory.createProperty(property.asResource().getURI()));
            }
        }

        Set<Resource> hierarchicalCodelistSet = model.listSubjectsWithProperty(RDF_type,
                QB_HierarchicalCodeList).toSet();
        ResIterator datasetDefIterator = model.listSubjectsWithProperty(RDF_type, QB_DataSet);
        Property[] properties = {QB_structure, QB_component, QB_componentProperty};
        Map<Resource, Set<Resource>> dimensionByDataset = searchByPathVisit(null,
                Arrays.asList(properties), null);
        for (Resource dataset : dimensionByDataset.keySet()) {
            Set<Resource> dimensionSet = dimensionByDataset.get(dataset);
            for (Resource dimension : dimensionSet) {
                Property dimAsProperty = ResourceFactory.createProperty(dimension.getURI());
                StmtIterator dimDefIterator = model.listStatements(dimension, RDF_type,
                        QB_DimensionProperty);
                NodeIterator dimHasCodelistIterator = model.listObjectsOfProperty(dimension,
                        QB_codeList);
                if (dimDefIterator.hasNext() && dimHasCodelistIterator.hasNext()) {
                    Resource codelist = dimHasCodelistIterator.next().asResource();
                    if (hierarchicalCodelistSet.contains(codelist))
                        codelistByDimension.put(dimAsProperty, codelist);
                }
            }

            Set<Resource> observationSet = model.listSubjectsWithProperty(QB_dataSet,
                    dataset).toSet();
            for (Resource observation : observationSet) {
                for (Property dimension : codelistByDimension.keySet()) {
                    NodeIterator dimValue = model.listObjectsOfProperty(observation,
                            dimension);
                    if (dimValue.hasNext()) {
                        boolean valueInCodelist = false;
                        for (Property hierarchyProp : propertySet) {
                            NodeIterator hierarchyRoot = model.listObjectsOfProperty(
                                    codelistByDimension.get(dimension), QB_hierarchyRoot);
                            while (hierarchyRoot.hasNext()) {
                                Resource codelistRoot = hierarchyRoot.next().asResource();
                                if (codelistRoot.equals(dimValue)) {
                                    valueInCodelist = true;
                                    break;
                                }
                                else hierarchyRoot = model.listObjectsOfProperty(
                                        codelistRoot, hierarchyProp);
                            }
                            if (valueInCodelist) break;
                        }
                        if (valueInCodelist) {
                            dimensionByObservation.put(observation, dimension.asResource());
                        }
                    }
                }
            }
        }
        System.out.println(dimensionByObservation);
    }

    public void checkIC20_21() {
        Map<Resource, Resource> dimensionByObservation = new HashMap<Resource, Resource>();
        Map<Property, Resource> objByPropParams = new HashMap<Property, Resource>();
        objByPropParams.put(RDF_type, QB_HierarchicalCodeList.asResource());
        List<Property> propOnlyParams = Collections.singletonList(QB_codeList);
        Map<Resource, Map<Property, Set<Resource>>> childpropByCodelistAndProp =
                searchByChildProperty(null, objByPropParams, propOnlyParams);
        Set<Resource> propInDefSet = new HashSet<Resource>();
        Set<Property> propSet = new HashSet<Property>();
        Set<Property> inversePropSet = new HashSet<Property>();
        for (Resource codelist : childpropByCodelistAndProp.keySet()) {
            Map<Property, Set<Resource>> childPropByProp =
                    childpropByCodelistAndProp.get(codelist);
            propInDefSet.addAll(childPropByProp.get(QB_parentChildProperty));
        }
        for (Resource childProp : propInDefSet) {
            if (childProp.isAnon()) {
                NodeIterator inversePropIterator = model.listObjectsOfProperty(childProp,
                        OWL_inverseOf);
                while (inversePropIterator.hasNext()) {
                    Property inverseProp = ResourceFactory.createProperty(
                            inversePropIterator.next().asResource().getURI());
                    if (inverseProp.isURIResource()) inversePropSet.add(inverseProp);
                }
            }
            else if (childProp.isURIResource()) propSet.add(
                    ResourceFactory.createProperty(childProp.getURI()));
        }

        objByPropParams.clear();
        objByPropParams.put(RDF_type, QB_DimensionProperty);
        Set<Resource> hierarchicalCodelistSet = model.listSubjectsWithProperty(RDF_type,
                QB_HierarchicalCodeList).toSet();
        Map<Resource, Resource> codelistByDimension = new HashMap<Resource, Resource>();
        for (Resource codelist : hierarchicalCodelistSet) {
            objByPropParams.put(QB_codeList, codelist);
            Set<Resource> dimensionSet = searchByChildProperty(null, objByPropParams);
            for (Resource dimension : dimensionSet) {
                codelistByDimension.put(dimension, codelist);
            }
        }

        Property[] properties = {QB_structure, QB_component, QB_componentProperty};
        Map<Resource, Set<Resource>> dimensionByDataset = searchByPathVisit(null,
                Arrays.asList(properties), null);
        List<Property> fixPropList = Collections.singletonList(QB_hierarchyRoot);
        for (Resource dataset : dimensionByDataset.keySet()) {
            Set<Resource> observationSet = model.listSubjectsWithProperty(QB_dataSet,
                    dataset).toSet();
            Set<Resource> dimensionSet = dimensionByDataset.get(dataset);
            dimensionSet.retainAll(codelistByDimension.keySet());
            for (Resource dimension : dimensionSet) {
                Resource codelist = codelistByDimension.get(dimension);
                Property dimAsProperty = ResourceFactory.createProperty(dimension.getURI());
                for (Resource observation : observationSet) {
                    NodeIterator dimValue = model.listObjectsOfProperty(observation,
                            dimAsProperty);
                    if (dimValue.hasNext()) {
                        Resource value = dimValue.next().asResource();
                        boolean isConnected = false;
                        for (Property prop : propSet) {
                            if (connectedByRepeatedProp(codelist, fixPropList, prop,
                                    value, true)) {
                                isConnected = true;
                                break;
                            }
                        }
                        if (isConnected) continue;
                        for (Property invProp : inversePropSet) {
                            if (connectedByRepeatedProp(codelist, fixPropList, invProp,
                                    value, false)) {
                                isConnected = true;
                                break;
                            }
                        }
                        if (!isConnected) dimensionByObservation.put(observation, dimension);
                    }
                }
            }
        }
        System.out.println(dimensionByObservation);
    }
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
        else if (null == subject && object !=null) {
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
            PREFIX_SKOS + "inverseOf");
    private static final Literal LITERAL_FALSE = ResourceFactory.createTypedLiteral(
            Boolean.FALSE);
    private static final Literal LITERAL_TRUE = ResourceFactory.createTypedLiteral(
            Boolean.TRUE);
}
