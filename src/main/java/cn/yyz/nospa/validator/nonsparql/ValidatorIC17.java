package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC17 extends ValidatorBase {
    public ValidatorIC17(Model model) {
        super(model);
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
    public Map<Resource, Integer> validate() {
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
        return numObs2ByObs1;
    }

    /**
     * This function is a subtask to check observations with same dimension
     * property structure as each observation given in the set
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
}
