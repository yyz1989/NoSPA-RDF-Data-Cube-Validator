package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC19 extends ValidatorBase {
    public ValidatorIC19(Model model) {
        super(model);
    }

    /**
     * Validate IC-19 Codes from code list: If a dimension property has a
     * qb:codeList, then the value of the dimension property on every
     * qb:Observation must be in the code list.
     * @return a map of values with a set of code lists not including the
     * values
     */
    public Map<RDFNode, Set<RDFNode>> validate() {
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
        return valNotInCodeList;
    }

    /**
     * This function is a subtask to check if the dimension values of a set
     * of observations match the given code lists
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
     * This function is a subtask to check if the dimension values of an
     * observation matches one of the given code lists
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
     * This function is a subtask to check if the given value is included in a
     * code list.
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
}
