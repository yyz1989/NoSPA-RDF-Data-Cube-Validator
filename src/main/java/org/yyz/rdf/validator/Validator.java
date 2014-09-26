package org.yyz.rdf.validator;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;

import java.io.InputStream;

/**
 * Created by yyz on 9/26/14.
 */
public class Validator {
    private static final String PREFIX_CUBE = "http://purl.org/linked-data/cube#";
    private static final String PREFIX_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

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

    private Model model;

    public Validator(String filename, String format) {
        model = ModelFactory.createDefaultModel();
        InputStream inputStream = FileManager.get().open(filename);
        if (inputStream == null) throw new IllegalArgumentException(
                "File " + filename + " not found");
        model.read(inputStream, null, format);
    }

    public void normalize() {
        // Phase 1: Type and property closure

        //
        NodeIterator nodeIterator = model.listObjectsOfProperty(QB_observation);
        while (nodeIterator.hasNext()) {
            model.add(nodeIterator.next().asResource(), RDF_type, QB_Observation);
        }

        StmtIterator stmtIterator = model.listStatements(null, QB_dataSet, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getObject().asResource(), RDF_type, QB_DataSet);
            model.add(statement.getResource(), RDF_type, QB_Observation);
        }

        nodeIterator = model.listObjectsOfProperty(QB_slice);
        while (nodeIterator.hasNext()) {
            model.add(nodeIterator.next().asResource(), RDF_type, QB_Slice);
        }

        stmtIterator = model.listStatements(null, QB_dimension, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getResource(), QB_componentProperty, statement.getObject());
            model.add(statement.getObject().asResource(), RDF_type, QB_DimensionProperty);
        }

        stmtIterator = model.listStatements(null, QB_measure, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getResource(), QB_componentProperty, statement.getObject());
            model.add(statement.getObject().asResource(), RDF_type, QB_MeasureProperty);
        }

        stmtIterator = model.listStatements(null, QB_attribute, (RDFNode) null);
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            model.add(statement.getResource(), QB_componentProperty, statement.getObject());
            model.add(statement.getObject().asResource(), RDF_type, QB_AttributeProperty);
        }

        // Phase 2: Push down attachment levels

        String queryString = "" +
                "PREFIX qb: <http://purl.org/linked-data/cube#>\n" +
                "\n" +
                "\n" +
                "SELECT ?obs ?comp ?value\n" +
                "WHERE {\n" +
                "    ?spec    qb:componentProperty ?comp ;\n" +
                "             qb:componentAttachment qb:DataSet .\n" +
                "    ?dataset qb:structure [qb:component ?spec];\n" +
                "             ?comp ?value .\n" +
                "    ?obs     qb:dataSet ?dataset.\n" +
                "}";

        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, model);

        ResultSet results = qe.execSelect();
        qe.close();

// Output query results
        ResultSetFormatter.out(System.out, results, query);

// Important - free up resources used running the query

    }

    public void checkIC12() {
        String queryString = "" +
                "PREFIX qb: <http://purl.org/linked-data/cube#>\n" +
                "ASK {\n" +
                "  FILTER( ?allEqual )\n" +
                "  {\n" +
                "    # For each pair of observations test if all the dimension values are the same\n" +
                "    SELECT (MIN(?equal) AS ?allEqual) WHERE {\n" +
                "        ?obs1 qb:dataSet ?dataset .\n" +
                "        ?obs2 qb:dataSet ?dataset .\n" +
                "        FILTER (?obs1 != ?obs2)\n" +
                "        ?dataset qb:structure/qb:component/qb:componentProperty ?dim .\n" +
                "        ?dim a qb:DimensionProperty .\n" +
                "        ?obs1 ?dim ?value1 .\n" +
                "        ?obs2 ?dim ?value2 .\n" +
                "        BIND( ?value1 = ?value2 AS ?equal)\n" +
                "    } GROUP BY ?obs1 ?obs2\n" +
                "  }\n" +
                "}";

        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, model);
        System.out.println(qe.execAsk());
        qe.close();


        //ResultSetFormatter.out(System.out, results, query);
    }
}
