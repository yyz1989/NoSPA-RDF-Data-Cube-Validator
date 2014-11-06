package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC13 extends ValidatorBase {
    public ValidatorIC13(Model model) {
        super(model);
    }

    /**
     * Validate IC-13 Required attributes: Every qb:Observation has a value for
     * each declared attribute that is marked as required.
     * @return a map of observations with attribute properties missing values
     */
    public Map<Resource, Set<RDFNode>> validate() {
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
        return obsWithoutAttribVal;
    }

    /**
     * This function is a subtask to check the values of a set of attribute
     * properties for a set of observations.
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
}
