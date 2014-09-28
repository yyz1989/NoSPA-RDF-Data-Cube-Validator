package cn.yyz.rdf.validator;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;

import javax.swing.plaf.nimbus.State;
import javax.xml.soap.Node;
import java.io.InputStream;
import java.util.*;

/**
 * Created by yyz on 9/26/14.
 */
public class Validator {
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
    private static final Property QB_AttributeProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "AttributeProperty");
    private static final Property QB_attribute = ResourceFactory.createProperty(
            PREFIX_CUBE + "attribute");
    private static final Property QB_componentAttachment = ResourceFactory.createProperty(
            PREFIX_CUBE + "componentAttachment");
    private static final Property QB_structure = ResourceFactory.createProperty(
            PREFIX_CUBE + "structure");
    private static final Property QB_DataStructureDefinition =
            ResourceFactory.createProperty(PREFIX_CUBE + "DataStructureDefinition");
    private static final Property QB_codeList = ResourceFactory.createProperty(
            PREFIX_CUBE + "codeList");
    private static final Property RDFS_range = ResourceFactory.createProperty(
            PREFIX_RDFS + "range");
    private static final Property SKOS_Concept = ResourceFactory.createProperty(
            PREFIX_SKOS + "Concept");

    private Model model;
    private Map<Resource, Set<Resource>> dimensionMap =
            new HashMap<Resource, Set<Resource>>();

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

    public void checkConstraint(String constraint) {
        checkConstraint(IntegrityConstraint.valueOf(constraint));
    }

    public void checkConstraint(IntegrityConstraint constraint) {
        Query query = QueryFactory.create(constraint.getValue());
        QueryExecution qe = QueryExecutionFactory.create(query, model);
        ResultSet resultSet = qe.execSelect();
        List<String> variables = resultSet.getResultVars();
        while (resultSet.hasNext()) {
            for (String var : variables) {
                QuerySolution querySolution = resultSet.next();
                System.out.print(var + ": " + querySolution.get(var) + "    ");
            }
            System.out.println();
        }
        qe.close();
    }

    public void checkIC1() {
        ResIterator observationIterator = model.listSubjectsWithProperty(
                RDF_type, QB_Observation);
        while (observationIterator.hasNext()) {
            Resource observation = observationIterator.nextResource();
            NodeIterator dataSetIterator = model.listObjectsOfProperty(
                    observation, QB_dataSet);
            Set dataSetSet = dataSetIterator.toSet();
            if (dataSetSet.size() != 1) {
                System.out.println(observation + ": " + dataSetSet);
            }
        }

    }

    public void checkIC2() {
        ResIterator dataSetIterator = model.listSubjectsWithProperty(
                RDF_type, QB_DataSet);
        while (dataSetIterator.hasNext()) {
            Resource dataSet = dataSetIterator.nextResource();
            NodeIterator dsdIterator = model.listObjectsOfProperty(dataSet, QB_structure);
            Set dsdSet = dsdIterator.toSet();
            if (dsdSet.size() != 1) {
                System.out.println(dataSet + ": " + dsdSet);
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

    public void checkIC4() {
        ResIterator dimensionIterator = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty);
        while (dimensionIterator.hasNext()) {
            Resource dimension = dimensionIterator.nextResource();
            NodeIterator rangeIterator = model.listObjectsOfProperty(dimension, RDFS_range);
            if (!rangeIterator.hasNext()) {
                System.out.println(dimension);
            }
        }
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
            if (!codeListIterator.hasNext()) {
                System.out.println(dimension);
            }
        }
    }

    public void checkIC6() {
        ResIterator resIterator = model.listResourcesWithProperty(RDF_type, QB_DataSet);
        while (resIterator.hasNext()) {
            Resource dataSet = resIterator.nextResource();
            dimensionMap.put(dataSet, new HashSet<Resource>());
        }

        StmtIterator dimensionIterator = model.listStatements(null, QB_dimension, (RDFNode) null);
        while (dimensionIterator.hasNext()) {
            Statement dimensionStatement = dimensionIterator.nextStatement();
            Resource component = dimensionStatement.getSubject();
            Resource dimension = dimensionStatement.getObject().asResource();

            ResIterator componentIterator = model.listResourcesWithProperty(QB_component, component);
            while (componentIterator.hasNext()) {
                Resource dataSet = componentIterator.nextResource();
                Set dimensions = dimensionMap.get(dataSet);
                if (dimensions == null) dimensions = new HashSet<Resource>();
                dimensions.add(dimension);
                dimensionMap.put(dataSet, dimensions);
            }
        }

        for (Resource key : dimensionMap.keySet()) {
            System.out.println(key.toString() + ": " + dimensionMap.get(key).toString());
        }
    }

    public void checkIC12() {
        ResIterator resIterator = model.listResourcesWithProperty(RDF_type, QB_Observation);
        while (resIterator.hasNext()) {
            Resource subject = resIterator.nextResource();
            model.listStatements(subject, QB_dataSet, (RDFNode) null);
        }
    }
}
