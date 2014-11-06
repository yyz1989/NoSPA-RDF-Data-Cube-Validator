package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC15_16 extends ValidatorBase {
    public ValidatorIC15_16(Model model) {
        super(model);
    }

    /**
     * Validate IC-15 Measure dimension consistent: In a qb:DataSet which uses
     * a Measure dimension then each qb:Observation must have a value for the
     * measure corresponding to its given qb:measureType.
     * Validate IC-16 Single measure on measure dimension observation: In a
     * qb:DataSet which uses a Measure dimension then each qb:Observation must
     * only have a value for one measure (by IC-15 this will be the measure
     * corresponding to its qb:measureType).
     * @return a map of faulty observations with measures missing values
     */
    public Map<Resource, Set<RDFNode>> validate() {
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
        return obsWithFaultyMeasure;
    }

    /**
     * This function is a subtask to check values of a set of measures for
     * a set of observations.
     * @param obsSet a set of observations
     * @param measureSet a set of measures
     * @return a map of faulty observations with measures missing values
     */
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
}
