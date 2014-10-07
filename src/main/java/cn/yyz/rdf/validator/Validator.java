package cn.yyz.rdf.validator;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;
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
    private Map<Resource, Set<Resource>> dsdByDataset = new HashMap<Resource, Set<Resource>>();

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
        Map<Property, Resource> objByProp = new HashMap<Property, Resource>();
        objByProp.put(RDF_type, QB_DataSet);
        List<Property> propertySet = Collections.singletonList(QB_structure);
        Map<Resource, Map<Property, Set<Resource>>> dsdByDatasetAndStructure =
                searchByChildProperty(null, objByProp, propertySet);
        for (Resource dataset : dsdByDatasetAndStructure.keySet()) {
            Map<Property, Set<Resource>> dsdByStructure =
                    dsdByDatasetAndStructure.get(dataset);
            Set<Resource> dsdSet = dsdByStructure.get(QB_structure);
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
        Map<Resource, Set<Resource>> datasetByObservation =
                new HashMap<Resource, Set<Resource>>();
        ResIterator observationIterator = model.listSubjectsWithProperty(
                RDF_type, QB_Observation);
        while (observationIterator.hasNext()) {
            Resource observation = observationIterator.nextResource();
            Set<Resource> datasetSet = searchObjectsOfProperty(
                    Collections.singleton(observation), QB_dataSet);
            if (datasetSet.size() != 1) {
                datasetByObservation.put(observation, datasetSet);
            }
        }
        System.out.println(datasetByObservation);
    }

    public void checkIC2() {
        ResIterator datasetIterator = model.listSubjectsWithProperty(
            RDF_type, QB_DataSet);
        while (datasetIterator.hasNext()) {
            Resource dataset = datasetIterator.nextResource();
            NodeIterator dsdIterator = model.listObjectsOfProperty(dataset, QB_structure);
            Set dsdSet = dsdIterator.toSet();
            if (dsdSet.size() != 1) {
                System.out.println(dataset + ": " + dsdSet);
            }
        }
    }

    public void checkIC3() {
        ResIterator dsdIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DataStructureDefinition);
        while (dsdIterator.hasNext()) {
            Set<Resource> measureSet = new HashSet<Resource>();
            Resource dsd = dsdIterator.nextResource();
            NodeIterator componentIterator = model.listObjectsOfProperty(dsd, QB_component);
            while (componentIterator.hasNext()) {
                Resource component = componentIterator.next().asResource();
                NodeIterator measureIterator = model.listObjectsOfProperty(
                        component, QB_measure);
                while (measureIterator.hasNext()) {
                    Resource measure = measureIterator.next().asResource();
                    StmtIterator measurePropertyIterator = model.listStatements(
                            measure, RDF_type, QB_MeasureProperty);
                    if (measurePropertyIterator.hasNext()) measureSet.add(measure);
                }
            }
            if (measureSet.size() == 0) {
                System.out.println(dsd);
            }
        }

    }

    public void checkIC3_2() {
        ResIterator dsdIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DataStructureDefinition);
        while (dsdIterator.hasNext()) {
            Resource dsd = dsdIterator.nextResource();
            Property[] properties = {QB_component, QB_componentProperty, RDF_type};
            Map<Resource, Set<Resource>> dsdPropertyMap = searchByPathVisit(dsd,
                    Arrays.asList(properties), QB_MeasureProperty);
            if (dsdPropertyMap.get(dsd).isEmpty()) System.out.println(dsd);
        }
    }

    public void checkIC4() {
        ResIterator dimensionIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty);
        while (dimensionIterator.hasNext()) {
            Resource dimension = dimensionIterator.nextResource();
            NodeIterator rangeIterator = model.listObjectsOfProperty(dimension, RDFS_range);
            if (rangeIterator.hasNext()) {
                System.out.println(dimension);
            }
        }
    }

    public void checkIC4_2() {
        ResIterator dimensionIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty);
        ResIterator dimensionWithRangeIterator = model.listSubjectsWithProperty(
                RDFS_range);
        Set<Resource> dimensionWithoutRange = dimensionIterator.toSet();
        dimensionWithoutRange.retainAll(dimensionWithRangeIterator.toSet());
        System.out.println(dimensionWithoutRange);
    }

    public void checkIC5() {
        ResIterator dimensionIterator1 = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty);
        ResIterator dimensionIterator2 = model.listSubjectsWithProperty(RDFS_range,
                SKOS_Concept);
        Set<Resource> dimesnsionSet = dimensionIterator1.toSet();
        dimesnsionSet.retainAll(dimensionIterator2.toSet());

        for(Resource dimension : dimesnsionSet) {
            NodeIterator codeListIterator = model.listObjectsOfProperty(dimension,
                    QB_codeList);
            if (codeListIterator.hasNext()) {
                System.out.println(dimension);
            }
        }
    }

    public void checkIC6() {
        ResIterator componentSpecIterator1 = model.listSubjectsWithProperty(
                QB_componentRequired, LITERAL_FALSE);
        ResIterator componentSpecIterator2 = model.listSubjectsWithProperty(
                QB_componentProperty, (RDFNode) null);
        NodeIterator componentSpecIterator3 = model.listObjectsOfProperty(
                QB_component);
        Set<Resource> componentSpecSet = new HashSet<Resource>();
        for (RDFNode node : componentSpecIterator3.toSet()) {
            componentSpecSet.add(node.asResource());
        }

        componentSpecSet.retainAll(componentSpecIterator1.toSet());
        componentSpecSet.retainAll(componentSpecIterator2.toSet());

        for (Resource componentSpec : componentSpecSet) {
            NodeIterator componentIterator = model.listObjectsOfProperty(
                    componentSpec, QB_componentProperty);
            while (componentIterator.hasNext()) {
                Resource component = componentIterator.next().asResource();
                StmtIterator componentStmtIterator = model.listStatements(
                        component, RDF_type, QB_AttributeProperty);
                if (componentIterator.hasNext()) {
                    System.out.print(component);
                }
            }
        }

    }

    public void checkIC7() {
        ResIterator sliceKeyIterator = model.listSubjectsWithProperty(RDF_type,
                QB_SliceKey);
        Set<RDFNode> sliceKeyInDSDNodeSet = new HashSet<RDFNode>();
        ResIterator dsdIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DataStructureDefinition);
        while (dsdIterator.hasNext()) {
            Resource dsd = dsdIterator.nextResource();
            NodeIterator sliceKeyInDSDIterator = model.listObjectsOfProperty(dsd,
                    QB_sliceKey);
            sliceKeyInDSDNodeSet.addAll(sliceKeyInDSDIterator.toList());
        }
        Set<Resource> sliceKeyInDSDResourceSet = new HashSet<Resource>();
        for(RDFNode node : sliceKeyInDSDNodeSet) {
            sliceKeyInDSDResourceSet.add(node.asResource());
        }
        Set<Resource> sliceKeyNotInDSDSet = sliceKeyIterator.toSet();
        sliceKeyNotInDSDSet.removeAll(sliceKeyInDSDResourceSet);
        System.out.println(sliceKeyNotInDSDSet);
    }

    public void checkIC8() {
        Set<Resource> componentPropertyNotInDSDSet = new HashSet<Resource>();
        StmtIterator sliceKeyInDSDIterator = model.listStatements(null, QB_sliceKey,
                (RDFNode) null);
        while (sliceKeyInDSDIterator.hasNext()) {
            Statement statement = sliceKeyInDSDIterator.nextStatement();
            Resource sliceKey = statement.getResource();
            StmtIterator sliceKeyDefIterator = model.listStatements(sliceKey,
                    RDF_type, QB_SliceKey);
            if (sliceKeyDefIterator.hasNext()) {
                NodeIterator componentPropertyIterator = model.listObjectsOfProperty(
                        sliceKey, QB_componentProperty);
                while (componentPropertyIterator.hasNext()) {
                    RDFNode componentProperty = componentPropertyIterator.next();
                    ResIterator componentPropertyDefIterator =
                            model.listSubjectsWithProperty(QB_componentProperty,
                                    componentProperty);
                    NodeIterator componentDefIterator = model.listObjectsOfProperty(
                            statement.getSubject(), QB_component);
                    Set<Resource> componentSet = new HashSet<Resource>();
                    while (componentDefIterator.hasNext()) {
                        componentSet.add(componentDefIterator.next().asResource());
                    }
                    componentSet.retainAll(componentPropertyDefIterator.toSet());
                    if (componentSet.isEmpty()) {
                        componentPropertyNotInDSDSet.add(componentProperty.asResource());
                    }
                }
            }
        }
        System.out.println(componentPropertyNotInDSDSet);
    }

    public void checkIC9() {
        Map<Resource, Set<RDFNode>> sliceStructureMap =
                new HashMap<Resource, Set<RDFNode>>();
        ResIterator sliceIterator = model.listSubjectsWithProperty(RDF_type, QB_Slice);
        while (sliceIterator.hasNext()) {
            Resource slice = sliceIterator.nextResource();
            NodeIterator sliceStructureIterator = model.listObjectsOfProperty(slice,
                    QB_sliceStructure);
            Set<RDFNode> sliceStructureSet = sliceStructureIterator.toSet();
            if (sliceStructureSet.size() != 1) sliceStructureMap.put(slice,
                    sliceStructureSet);
        }
        System.out.println(sliceStructureMap);
    }

    public void checkIC10() {
        Map<Resource, Resource> sliceWithoutValue = new HashMap<Resource, Resource>();
        StmtIterator sliceDefIterator = model.listStatements(null,
                QB_sliceStructure, (RDFNode) null);
        while (sliceDefIterator.hasNext()) {
            Statement sliceStatement = sliceDefIterator.nextStatement();
            Resource slice = sliceStatement.getSubject();
            NodeIterator dimensionIterator = model.listObjectsOfProperty(
                    sliceStatement.getObject().asResource(), QB_componentProperty);
            while (dimensionIterator.hasNext()) {
                Resource dimension = dimensionIterator.next().asResource();
                Property dimAsProperty = ResourceFactory.createProperty(
                        dimension.getURI());
                NodeIterator dimensionValueIterator = model.listObjectsOfProperty(
                        slice, dimAsProperty);
                if (!dimensionValueIterator.hasNext()) sliceWithoutValue.put(slice,
                        dimension);
            }
        }
        System.out.println(sliceWithoutValue);
    }

    public void checkIC11_12() {
        ResIterator dimensionIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty);
        Set<Resource> dimensionSet = dimensionIterator.toSet();
        ResIterator datasetIterator = model.listResourcesWithProperty(RDF_type, QB_DataSet);
        while (datasetIterator.hasNext()) {
            Resource dataset = datasetIterator.nextResource();
            dimensionByDataset.put(dataset, new HashSet<Resource>());
            NodeIterator dsdIterator = model.listObjectsOfProperty(dataset, QB_structure);
            while (dsdIterator.hasNext()) {
                Resource dsd = dsdIterator.next().asResource();
                Set<Resource> componentPropertySet = new HashSet<Resource>();
                NodeIterator componentIterator = model.listObjectsOfProperty(dsd,
                        QB_component);
                while (componentIterator.hasNext()) {
                    Resource component = componentIterator.next().asResource();
                    NodeIterator componentPropertyIterator = model.listObjectsOfProperty(
                            component, QB_componentProperty);
                    while (componentPropertyIterator.hasNext()) {
                        componentPropertySet.add(componentPropertyIterator
                                .next().asResource());
                    }
                }
                componentPropertySet.retainAll(dimensionSet);
                dimensionByDataset.put(dataset, componentPropertySet);
            }
        }

        Map<Resource, Resource> obsValueNotExistMap = new HashMap<Resource, Resource>();
        Set<Resource> duplicateObservationSet = new HashSet<Resource>();
        for (Resource dataset : dimensionByDataset.keySet()) {
            Set<Resource> dimensions = dimensionByDataset.get(dataset);
            ResIterator observationIterator = model.listSubjectsWithProperty(QB_dataSet,
                    dataset);
            Map<Resource, Set<Resource>> observationValueMap =
                    new HashMap<Resource, Set<Resource>>();
            while (observationIterator.hasNext()) {
                Resource observation = observationIterator.nextResource();
                Set<Resource> values = new HashSet<Resource>();
                for (Resource dimension : dimensions) {
                    Property dimAsProperty = ResourceFactory.createProperty(
                            dimension.getURI());
                    NodeIterator valueIterator = model.listObjectsOfProperty(observation,
                            dimAsProperty);
                    if (!valueIterator.hasNext()) obsValueNotExistMap.put(observation,
                            dimension);
                    else values.add(valueIterator.next().asResource());
                }
                if (observationValueMap.containsValue(values))
                    duplicateObservationSet.add(observation);
                else observationValueMap.put(observation, values);
            }
            valueByDatasetAndDim.put(dataset, observationValueMap);
        }
        System.out.println(obsValueNotExistMap);
        System.out.println(duplicateObservationSet);
    }

    public void checkIC13() {
        Map<Resource, Resource> obsAttributeWithoutValue =
                new HashMap<Resource, Resource>();
        Map<Resource, Set<Resource>> datasetAttributeMap =
                new HashMap<Resource, Set<Resource>>();
        ResIterator datasetIterator = model.listSubjectsWithProperty(
                RDF_type, QB_DataSet);
        while (datasetIterator.hasNext()) {
            Resource dataset = datasetIterator.nextResource();
            Set<Resource> componentSet = new HashSet<Resource>();
            NodeIterator dsdIterator = model.listObjectsOfProperty(dataset, QB_structure);
            while (dsdIterator.hasNext()) {
                NodeIterator componentInDSDIterator = model.listObjectsOfProperty(
                        dsdIterator.next().asResource(), QB_component);
                while (componentInDSDIterator.hasNext()) {
                    componentSet.add(componentInDSDIterator.next().asResource());
                }
            }

            ResIterator componentRequiredIterator = model.listSubjectsWithProperty(
                    QB_componentRequired, LITERAL_TRUE);
            componentSet.retainAll(componentRequiredIterator.toSet());
            if (!componentSet.isEmpty()) {
                Set<Resource> attributeSet = new HashSet<Resource>();
                for (Resource component : componentSet) {
                    NodeIterator attributeIterator = model.listObjectsOfProperty(
                            component, QB_componentProperty);
                    while (attributeIterator.hasNext()) {
                        attributeSet.add(attributeIterator.next().asResource());
                    }
                }
                datasetAttributeMap.put(dataset, attributeSet);
            }
        }
        for (Resource dataset : datasetAttributeMap.keySet()) {
            Set<Resource> attributeSet = datasetAttributeMap.get(dataset);
            ResIterator observationIterator = model.listSubjectsWithProperty(
                    QB_dataSet, dataset);
            while (observationIterator.hasNext()) {
                Resource obeservation = observationIterator.nextResource();
                for (Resource attribute : attributeSet) {
                    Property attributeAsProperty = ResourceFactory.createProperty(
                            attribute.getURI());
                    NodeIterator valueIterator = model.listObjectsOfProperty(obeservation,
                            attributeAsProperty);
                    if (!valueIterator.hasNext()) obsAttributeWithoutValue.put(
                            obeservation, attribute);
                }
            }
        }
        System.out.println(obsAttributeWithoutValue);
    }

    public void checkIC13_2() {
        Map<Resource, Resource> obsAttributeWithoutValue =
                new HashMap<Resource, Resource>();
        Map<Resource, Set<Resource>> datasetAttributeMap =
                new HashMap<Resource, Set<Resource>>();
        ResIterator datasetIterator = model.listSubjectsWithProperty(
                RDF_type, QB_DataSet);
        while (datasetIterator.hasNext()) {
            Resource dataset = datasetIterator.nextResource();
            Property[] properties = {QB_structure, QB_component};
            Map<Resource, Set<Resource>> componentMap = searchByPathVisit(dataset,
                    Arrays.asList(properties), null);
            Set<Resource> componentSet = componentMap.get(dataset);
            ResIterator componentRequiredIterator = model.listSubjectsWithProperty(
                    QB_componentRequired, LITERAL_TRUE);
            componentSet.retainAll(componentRequiredIterator.toSet());
            if (!componentSet.isEmpty()) {
                Set<Resource> attributeSet = new HashSet<Resource>();
                for (Resource component : componentSet) {
                    NodeIterator attributeIterator = model.listObjectsOfProperty(
                            component, QB_componentProperty);
                    while (attributeIterator.hasNext()) {
                        attributeSet.add(attributeIterator.next().asResource());
                    }
                }
                datasetAttributeMap.put(dataset, attributeSet);
            }
        }

        for (Resource dataset : datasetAttributeMap.keySet()) {
            Set<Resource> attributeSet = datasetAttributeMap.get(dataset);
            ResIterator observationIterator = model.listSubjectsWithProperty(
                    QB_dataSet, dataset);
            while (observationIterator.hasNext()) {
                Resource obeservation = observationIterator.nextResource();
                for (Resource attribute : attributeSet) {
                    Property attributeAsProperty = ResourceFactory.createProperty(
                            attribute.getURI());
                    NodeIterator valueIterator = model.listObjectsOfProperty(obeservation,
                            attributeAsProperty);
                    if (!valueIterator.hasNext()) obsAttributeWithoutValue.put(
                            obeservation, attribute);
                }
            }
        }
        System.out.println(obsAttributeWithoutValue);
    }

    public void checkIC14() {
        Map<Resource, Resource> obsWithoutMeasureValue = new HashMap<Resource, Resource>();
        ResIterator datasetIterator = model.listSubjectsWithProperty(RDF_type, QB_DataSet);
        ResIterator measureIterator = model.listSubjectsWithProperty(RDF_type, QB_MeasureProperty);
        Property[] properties = {QB_structure, QB_component, QB_componentProperty};
        Map<Resource, Set<Resource>> measureTypeDataset = searchByPathVisit(null,
                Arrays.asList(properties), QB_measureType);
        Set<Resource> datasetWithoutMeasure = datasetIterator.toSet();
        datasetWithoutMeasure.removeAll(measureTypeDataset.get(QB_measureType));

        for (Resource dataset : datasetWithoutMeasure) {
            Map<Resource, Set<Resource>> datasetMeasure = searchByPathVisit(dataset,
                    Arrays.asList(properties), null);
            Set<Resource> measureSet = measureIterator.toSet();
            measureSet.retainAll(datasetMeasure.get(dataset));
            Set<Property> measureAsPropertySet = new HashSet<Property>();
            for (Resource measure : measureSet) {
                measureAsPropertySet.add(ResourceFactory.createProperty(measure.getURI()));
            }
            ResIterator observationIterator = model.listSubjectsWithProperty(QB_dataSet, dataset);
            while (observationIterator.hasNext()) {
                Resource observation = observationIterator.nextResource();
                for (Property measure : measureAsPropertySet) {
                    NodeIterator measureValueIterator = model.listObjectsOfProperty(observation,
                            measure);
                    if (!measureValueIterator.hasNext()) obsWithoutMeasureValue.put(observation,
                            measure);
                }
            }
        }
        System.out.println(obsWithoutMeasureValue);
    }

    public void checkIC15() {
        Map<Resource, Resource> obsWithoutMeasureValue = new HashMap<Resource, Resource>();
        ResIterator datasetDefIterator = model.listSubjectsWithProperty(RDF_type, QB_DataSet);
        Property[] properties = {QB_structure, QB_component, QB_componentProperty};
        Map<Resource, Set<Resource>> measureTypeDataset = searchByPathVisit(null,
                Arrays.asList(properties), QB_measureType);
        Set<Resource> datasetWithMeasure = datasetDefIterator.toSet();
        datasetWithMeasure.retainAll(measureTypeDataset.get(QB_measureType));
        for (Resource dataset : datasetWithMeasure) {
            ResIterator observationIterator = model.listSubjectsWithProperty(QB_dataSet, dataset);
            while (observationIterator.hasNext()) {
                Resource observation = observationIterator.nextResource();
                NodeIterator measureIterator = model.listObjectsOfProperty(observation,
                        QB_measureType);
                while (measureIterator.hasNext()) {
                    Resource measure = measureIterator.next().asResource();
                    NodeIterator measureValueIterator = model.listObjectsOfProperty(
                            observation, ResourceFactory.createProperty(measure.getURI()));
                    if (!measureValueIterator.hasNext()) obsWithoutMeasureValue.put(observation,
                            measure);
                }
            }
        }
        System.out.println(obsWithoutMeasureValue);
    }

    public void checkIC18() {
        Map<Resource, Resource> obsNotInDataset = new HashMap<Resource, Resource>();
        Property[] properties = {QB_slice, QB_observation};
        Map<Resource, Set<Resource>> datasetObservation = searchByPathVisit(null,
                Arrays.asList(properties), null);
        for (Resource key : datasetObservation.keySet()) {
            Set<Resource> observationSet = datasetObservation.get(key);
            for (Resource observation : observationSet) {
                StmtIterator obsDefIterator = model.listStatements(observation, QB_dataSet, key);
                if (!obsDefIterator.hasNext()) obsNotInDataset.put(observation, key);
            }
        }
        System.out.println(obsNotInDataset);
    }

    public void checkIC19() {
        Map<Resource, Resource> dimensionByObservation = new HashMap<Resource, Resource>();
        Map<Property, Resource> schemeCodelistByDimension = new HashMap<Property, Resource>();
        Map<Property, Resource> collectionCodelistByDimension = new HashMap<Property, Resource>();
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
                    StmtIterator codelistIsSchemeDef = model.listStatements(codelist,
                            RDF_type, SKOS_ConceptScheme);
                    StmtIterator codelistIsCollectionDef = model.listStatements(codelist,
                            RDF_type, SKOS_Collection);
                    if (codelistIsSchemeDef.hasNext())
                        schemeCodelistByDimension.put(dimAsProperty, codelist);
                    else if (codelistIsCollectionDef.hasNext())
                        collectionCodelistByDimension.put(dimAsProperty, codelist);
                }
            }

            Set<Resource> observationSet = model.listSubjectsWithProperty(QB_dataSet,
                    dataset).toSet();
            for (Resource observation : observationSet) {
                for (Property dimension : schemeCodelistByDimension.keySet()) {
                    NodeIterator dimValue = model.listObjectsOfProperty(observation,
                            dimension);
                    if (dimValue.hasNext()) {
                        Resource value = dimValue.next().asResource();
                        StmtIterator valueTypeDef = model.listStatements(value,
                                RDF_type, SKOS_Concept);
                        StmtIterator valueInScheme = model.listStatements(value,
                                SKOS_inScheme, schemeCodelistByDimension.get(dimension));
                        if (!valueTypeDef.hasNext() || !valueInScheme.hasNext())
                            dimensionByObservation.put(observation, dimension.asResource());
                    }
                }
                for (Property dimension : collectionCodelistByDimension.keySet()) {
                    NodeIterator dimValue = model.listObjectsOfProperty(observation,
                            dimension);
                    if (dimValue.hasNext()) {
                        Resource value = dimValue.next().asResource();
                        StmtIterator valueTypeDef = model.listStatements(value,
                                RDF_type, SKOS_Concept);
                        ResIterator codelistHasValue = model.listSubjectsWithProperty(
                                SKOS_member, value);
                        Resource codelist = collectionCodelistByDimension.get(dimension);
                        boolean valueInCodelist = false;
                        while (codelistHasValue.hasNext()) {
                            Resource subWithPropMember = codelistHasValue.nextResource();
                            if (subWithPropMember.equals(codelist)) {
                                valueInCodelist = true;
                                break;
                            }
                            else codelistHasValue = model.listSubjectsWithProperty(
                                    SKOS_member, subWithPropMember);
                        }
                        if (!valueTypeDef.hasNext() || valueInCodelist)
                            dimensionByObservation.put(observation, dimension.asResource());
                    }
                }
            }
        }
        System.out.println(dimensionByObservation);
    }

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
                RDFNode property = hierarchyPropertyIterator.next().asResource();
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

    public void checkIC21() {

    }

    private Map<Resource, Set<Resource>> searchByPathVisit(
            Resource subject, List<Property> properties, Resource object) {
        Map<Resource, Set<Resource>> resultSet = new HashMap<Resource, Set<Resource>>();
        if (properties.size() == 0) return resultSet;

        // case: eg:obs1 qb:dataSet ?dataset
        if (subject != null) {
            NodeIterator objectIterator = model.listObjectsOfProperty(subject,
                    properties.get(0));
            Set<Resource> nodeSet = new HashSet<Resource>();
            while (objectIterator.hasNext()) {
                nodeSet.add(objectIterator.next().asResource());
            }
            for (int index = 1; index < properties.size(); index++) {
                nodeSet = searchObjectsOfProperty(nodeSet, properties.get(index));
            }
            if (object != null) nodeSet.retainAll(Collections.singleton(object));
            resultSet.put(subject, nodeSet);
        }

        // case: ?obs qb:dataSet eg:dataset1
        else if (subject == null && object !=null) {
            ResIterator subjectIterator = model.listSubjectsWithProperty(properties.get(0),
                    object);
            Set<Resource> nodeSet = subjectIterator.toSet();
            for (int index = 1; index < properties.size(); index++) {
                nodeSet = searchSubjectsWithProperty(nodeSet, properties.get(index));
            }
            resultSet.put(object, nodeSet);
        }

        // case: ?obs qb:dataSet ?dataset
        else if (subject == null && object == null) {
            ResIterator subjectIterator = model.listSubjectsWithProperty(properties.get(0));
            for (Resource sub : subjectIterator.toSet()) {
                NodeIterator objectIterator = model.listObjectsOfProperty(sub,
                        properties.get(0));
                Set<Resource> nodeSet = new HashSet<Resource>();
                while (objectIterator.hasNext()) {
                    nodeSet.add(objectIterator.next().asResource());
                }
                for (int index = 1; index < properties.size(); index++) {
                    nodeSet = searchObjectsOfProperty(nodeSet, properties.get(index));
                }
                resultSet.put(sub, nodeSet);
            }
        }
        return resultSet;
    }

    private Map<Resource, Map<Property, Set<Resource>>> searchByChildProperty(Resource subject,
           Map<Property, Resource> objectByProperty, List<Property> propertyOnlyList) {
        Map<Resource, Map<Property, Set<Resource>>> resultSet =
                new HashMap<Resource, Map<Property, Set<Resource>>>();
        for (Property property : objectByProperty.keySet()) {
            if (objectByProperty.get(property) == null) objectByProperty.remove(property);
        }
        if (objectByProperty.size() == 0) return resultSet;
        Property seedKey = objectByProperty.keySet().iterator().next();
        Resource seedValue = objectByProperty.remove(seedKey);
        Set<Resource> subjectSet = model.listSubjectsWithProperty(seedKey, seedValue).toSet();
        for (Property property : objectByProperty.keySet()) {
            Resource object = objectByProperty.get(property);
            ResIterator subjectIterator = model.listSubjectsWithProperty(property, object);
            subjectSet.retainAll(subjectIterator.toSet());
        }

        if (subject != null && !subjectSet.contains(subject)) return resultSet;

        if (propertyOnlyList.size() == 0) {
            for (Resource resultSubject : subjectSet) {
                resultSet.put(resultSubject, null);
            }
            return resultSet;
        }
        else {
            for (Resource resultSubject : subjectSet) {
                Map<Property, Set<Resource>> objectSetByProperty =
                        new HashMap<Property, Set<Resource>>();
                for (Property property : propertyOnlyList) {
                    Set<Resource> objectSet = searchObjectsOfProperty(
                            Collections.singleton(resultSubject), property);
                    objectSetByProperty.put(property, objectSet);
                }
                resultSet.put(resultSubject, objectSetByProperty);
            }
            return resultSet;
        }
    }

    private Set<Resource> searchObjectsOfProperty(Set<Resource> subjects,
                                                  Property property) {
        Set<Resource> objects = new HashSet<Resource>();
        for (Resource subject : subjects) {
            NodeIterator objectIterator = model.listObjectsOfProperty(subject, property);
            while (objectIterator.hasNext()) {
                objects.add(objectIterator.next().asResource());
            }
        }
        return objects;
    }

    private Set<Resource> searchSubjectsWithProperty(Set<Resource> objects,
                                                     Property property) {
        Set<Resource> subjects = new HashSet<Resource>();
        for (Resource object : objects) {
            ResIterator subjectIterator = model.listSubjectsWithProperty(property, object);
            if (subjectIterator.hasNext()) subjects.addAll(subjectIterator.toSet());
        }
        return subjects;
    }

    private static final String PREFIX_CUBE = "http://purl.org/linked-data/cube#";
    private static final String PREFIX_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String PREFIX_RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String PREFIX_SKOS = "http://www.w3.org/2004/02/skos/core#";

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
    private static final Literal LITERAL_FALSE = ResourceFactory.createTypedLiteral(
            Boolean.FALSE);
    private static final Literal LITERAL_TRUE = ResourceFactory.createTypedLiteral(
            Boolean.TRUE);
}
