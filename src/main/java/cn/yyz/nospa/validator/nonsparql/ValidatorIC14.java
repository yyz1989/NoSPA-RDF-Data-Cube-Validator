package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC14 extends ValidatorBase {
    public ValidatorIC14(Model model) {
        super(model);
    }

    public Map<Resource, Set<RDFNode>> validate() {
        Map<Resource, Set<RDFNode>> obsWithoutMeasureVal =
                new HashMap<Resource, Set<RDFNode>>();
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
        return obsWithoutMeasureVal;
    }

    /**
     * This function is a subtask of checkIC14 for checking the values of a set
     * of measures for a set of observations.
     * @param obsSet a set of observations
     * @param measureSet a set of measures
     * @return a map of observations with a set of measures missing values
     */
    private Map<Resource, Set<RDFNode>> measureValueCheck (Set<Resource> obsSet,
                                                           Set<? extends RDFNode> measureSet) {
        Map<Resource, Set<RDFNode>> obsWithoutMeasureVal =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Property> measureAsPropSet = nodeToProperty(measureSet);
        for (Resource obs : obsSet) {
            Set<RDFNode> measureWithoutValSet = new HashSet<RDFNode>();
            for (Property measure : measureAsPropSet) {
                if (!model.listObjectsOfProperty(obs, measure).hasNext())
                    measureWithoutValSet.add(measure);
            }
            if (!measureWithoutValSet.isEmpty())
                obsWithoutMeasureVal.put(obs, measureWithoutValSet);
        }
        return obsWithoutMeasureVal;
    }
}
