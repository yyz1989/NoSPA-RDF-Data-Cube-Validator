package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * A base class with commonly used utility functions and static variables for
 * all validators.
 * Created by yyz on 11/4/14.
 */
public class ValidatorBase {
    protected Model model;

    /**
     * The constructor of a validator base
     * @param model the constructor takes an RDF model as parameter
     */
    public ValidatorBase(Model model) {
        this.model = model;
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
    protected Map<Resource, Set<? extends RDFNode>> searchByPathVisit(
            Resource subject, List<Property> propPath, RDFNode object) {
        Map<Resource, Set<? extends RDFNode>> resultSet =
                new HashMap<Resource, Set<? extends RDFNode>>();
        if (propPath.size() == 0) return resultSet;

        // case: eg:obs1 qb:dataSet ?dataset
        if (subject != null) {
            Set<RDFNode> nodeSet = model.listObjectsOfProperty(subject, propPath.get(0)).toSet();
            for (int index = 1; index < propPath.size(); index++) {
                nodeSet = searchObjectsOfProperty(nodeToResource(nodeSet), propPath.get(index));
            }
            if (object != null) nodeSet.retainAll(Collections.singleton(object));
            resultSet.put(subject, nodeSet);
        }

        // case: ?obs qb:dataSet eg:dataset1
        else if (subject == null && object !=null) {
            Set<Resource> resSet = model.listSubjectsWithProperty(propPath.get(0),
                    object).toSet();
            for (int index = 1; index < propPath.size(); index++) {
                resSet = searchSubjectsWithProperty(resSet, propPath.get(index));
            }
            resultSet.put(object.asResource(), resSet);
        }

        // case: ?obs qb:dataSet ?dataset
        else if (subject == null && object == null) {
            Set<Resource> resSet = model.listSubjectsWithProperty(propPath.get(0),
                    object).toSet();
            for (Resource sub : resSet) {
                Set<RDFNode> nodeSet =
                        searchObjectsOfProperty(Collections.singleton(sub), propPath.get(0));
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
    protected Set<Resource> searchByMultipleProperty(Resource subject,
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
    protected Map<Resource, Map<Property, Set<RDFNode>>> searchByMultipleProperty(Resource subject,
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
    protected Set<RDFNode> searchObjectsOfProperty(Set<Resource> subjectSet,
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
    protected Set<Resource> searchSubjectsWithProperty(Set<? extends RDFNode> objectSet,
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
    protected Set<Resource> nodeToResource (Set<? extends RDFNode> nodeSet) {
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
    protected Set<Property> nodeToProperty (Set<? extends RDFNode> nodeSet) {
        Set<Property> propSet = new HashSet<Property>();
        for (RDFNode node : nodeSet) {
            if (node.isURIResource()) propSet.add(ResourceFactory.createProperty(
                    node.asResource().getURI()));
        }
        return propSet;
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
    protected boolean connectedByPropList(Resource subject,
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
    protected boolean connectedByRepeatedProp(Resource subject, List<Property> fixPropList,
                                            Property repProp, RDFNode object,
                                            boolean isDirect) {
        boolean isConnected = false;
        Map<Resource, Set<? extends RDFNode>> objSetBySub = searchByPathVisit(subject,
                fixPropList, null);
        Set<? extends RDFNode> objectSet = objSetBySub.get(subject);
        if (objectSet.contains(object)) return true;
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
    protected boolean connectedByRepeatedProp(Resource subject, Property repProp,
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
    protected boolean connectedByRepeatedProp(Resource subject, Property repProp,
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

    protected static final String PREFIX_CUBE = "http://purl.org/linked-data/cube#";
    protected static final String PREFIX_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    protected static final String PREFIX_RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    protected static final String PREFIX_SKOS = "http://www.w3.org/2004/02/skos/core#";
    protected static final String PREFIX_OWL = "http://www.w3.org/2002/07/owl#";

    protected static final Property RDF_type = ResourceFactory.createProperty(
            PREFIX_RDF + "type");
    protected static final Property QB_observation = ResourceFactory.createProperty(
            PREFIX_CUBE + "observation");
    protected static final Property QB_Observation = ResourceFactory.createProperty(
            PREFIX_CUBE + "Observation");
    protected static final Property QB_dataSet = ResourceFactory.createProperty(
            PREFIX_CUBE + "dataSet");
    protected static final Property QB_DataSet = ResourceFactory.createProperty(
            PREFIX_CUBE + "DataSet");
    protected static final Property QB_slice = ResourceFactory.createProperty(
            PREFIX_CUBE + "slice");
    protected static final Property QB_Slice = ResourceFactory.createProperty(
            PREFIX_CUBE + "Slice");
    protected static final Property QB_sliceKey = ResourceFactory.createProperty(
            PREFIX_CUBE + "sliceKey");
    protected static final Property QB_SliceKey = ResourceFactory.createProperty(
            PREFIX_CUBE + "SliceKey");
    protected static final Property QB_sliceStructure = ResourceFactory.createProperty(
            PREFIX_CUBE + "sliceStructure");
    protected static final Property QB_component = ResourceFactory.createProperty(
            PREFIX_CUBE + "component");
    protected static final Property QB_componentProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "componentProperty");
    protected static final Property QB_DimensionProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "DimensionProperty");
    protected static final Property QB_dimension = ResourceFactory.createProperty(
            PREFIX_CUBE + "dimension");
    protected static final Property QB_MeasureProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "MeasureProperty");
    protected static final Property QB_measure = ResourceFactory.createProperty(
            PREFIX_CUBE + "measure");
    protected static final Property QB_measureType = ResourceFactory.createProperty(
            PREFIX_CUBE + "measureType");
    protected static final Property QB_AttributeProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "AttributeProperty");
    protected static final Property QB_attribute = ResourceFactory.createProperty(
            PREFIX_CUBE + "attribute");
    protected static final Property QB_componentAttachment = ResourceFactory.createProperty(
            PREFIX_CUBE + "componentAttachment");
    protected static final Property QB_componentRequired = ResourceFactory.createProperty(
            PREFIX_CUBE + "componentRequired");
    protected static final Property QB_structure = ResourceFactory.createProperty(
            PREFIX_CUBE + "structure");
    protected static final Property QB_DataStructureDefinition =
            ResourceFactory.createProperty(PREFIX_CUBE + "DataStructureDefinition");
    protected static final Property QB_codeList = ResourceFactory.createProperty(
            PREFIX_CUBE + "codeList");
    protected static final Property QB_HierarchicalCodeList = ResourceFactory.createProperty(
            PREFIX_CUBE + "HierarchicalCodeList");
    protected static final Property QB_hierarchyRoot = ResourceFactory.createProperty(
            PREFIX_CUBE + "hierarchyRoot");
    protected static final Property QB_parentChildProperty = ResourceFactory.createProperty(
            PREFIX_CUBE + "parentChildProperty");
    protected static final Property RDFS_range = ResourceFactory.createProperty(
            PREFIX_RDFS + "range");
    protected static final Property SKOS_Concept = ResourceFactory.createProperty(
            PREFIX_SKOS + "Concept");
    protected static final Property SKOS_ConceptScheme = ResourceFactory.createProperty(
            PREFIX_SKOS + "ConceptScheme");
    protected static final Property SKOS_inScheme = ResourceFactory.createProperty(
            PREFIX_SKOS + "inScheme");
    protected static final Property SKOS_Collection = ResourceFactory.createProperty(
            PREFIX_SKOS + "Collection");
    protected static final Property SKOS_member = ResourceFactory.createProperty(
            PREFIX_SKOS + "member");
    protected static final Property OWL_inverseOf = ResourceFactory.createProperty(
            PREFIX_OWL + "inverseOf");
    protected static final Literal LITERAL_FALSE = ResourceFactory.createTypedLiteral(
            Boolean.FALSE);
    protected static final Literal LITERAL_TRUE = ResourceFactory.createTypedLiteral(
            Boolean.TRUE);
}
