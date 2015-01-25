package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC11_12 extends ValidatorBase {
    public ValidatorIC11_12(Model model) {
        super(model);
    }
    private Logger logger = LoggerFactory.getLogger(ValidatorIC11_12.class);
    /**
     * Validate IC-11 All dimensions required: Every qb:Observation has a value
     * for each dimension declared in its associated qb:DataStructureDefinition.
     * Validate IC-12 No duplicate observations: No two qb:Observations in the
     * same qb:DataSet may have the same value for all dimensions.
     * @return a map of observations with dimensions without values or with
     * duplicate values.
     */
    public Map<Resource, Set<RDFNode>> validate() {
        Map<Resource, Set<RDFNode>> faultyObs = new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> duplicateObsSet = new HashSet<Resource>();
        Map<Resource, Set<Resource>> obsByDataset =
                new HashMap<Resource, Set<Resource>>();
        List<Property> propPath = Arrays.asList(QB_structure,
                QB_component, QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> dimByDataset = searchByPathVisit(
                null, propPath, null);
        Set<Resource> dimWithDef = model.listResourcesWithProperty(RDF_type,
                QB_DimensionProperty).toSet();
        for (Resource dataset : dimByDataset.keySet()) {
            Set<? extends RDFNode> dimInDataset = dimByDataset.get(dataset);
            dimInDataset.retainAll(dimWithDef);
            dimByDataset.put(dataset, dimInDataset);
            obsByDataset.put(dataset,
                    model.listSubjectsWithProperty(QB_dataSet, dataset).toSet());
        }
        for (Resource dataset : obsByDataset.keySet()) {
            logger.info("    Validating dataset " + dataset.toString());
            Set<Resource> obsSet = obsByDataset.get(dataset);
            Set<? extends RDFNode> dimSet = dimByDataset.get(dataset);
            faultyObs.putAll(dimValueCheck(obsSet, dimSet));
        }
        return faultyObs;
    }

    /**
     * This function is a subtask to check the values of a set of
     * observations for a set of dimensions.
     * @param obsSet a set of observations
     * @param dimSet a set of dimension properties
     * @return a map of faulty observations with dimension property set missing
     * corresponding values. If the set is empty then the observation is
     * duplicated.
     */
    private Map<Resource, Set<RDFNode>> dimValueCheck (Set<Resource> obsSet,
                                                       Set<? extends RDFNode> dimSet) {
        int obsSize = obsSet.size();
        Map<Resource, Set<RDFNode>> faultyObs = new HashMap<Resource, Set<RDFNode>>();
        Map<Resource, Set<RDFNode>> valueSetByObs = new HashMap<Resource, Set<RDFNode>>(obsSize);
        Set<Property> dimAsPropSet = nodeToProperty(dimSet);
        int progress = 1;
        for (Resource obs : obsSet) {
            System.out.print("    Validating observation "+ progress + " of " + obsSize + "\r");
            Set<RDFNode> valueSet = new HashSet<RDFNode>();
            Set<RDFNode> dimWithoutValSet = new HashSet<RDFNode>();
            for (Property dim : dimAsPropSet) {
                NodeIterator valueIter = model.listObjectsOfProperty(obs, dim);
                if (!valueIter.hasNext()) dimWithoutValSet.add(dim);
                else valueSet.add(valueIter.next());
            }
            if (!dimWithoutValSet.isEmpty()) faultyObs.put(obs, dimWithoutValSet);
            else {
                if (valueSetByObs.containsValue(valueSet)) faultyObs.put(obs,
                        dimWithoutValSet);
                else valueSetByObs.put(obs, valueSet);
            }
            progress++;
        }
        return faultyObs;
    }

}
