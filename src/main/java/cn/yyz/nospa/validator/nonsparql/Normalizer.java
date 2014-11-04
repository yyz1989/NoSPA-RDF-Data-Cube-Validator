package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class Normalizer extends ValidatorBase {
    public Normalizer(Model model) {
        super(model);
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
}
