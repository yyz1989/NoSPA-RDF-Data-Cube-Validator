package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC1 extends ValidatorBase {
    public ValidatorIC1(Model model) {
        super(model);
    }

    /**
     * Validate IC-1 Unique DataSet: Every qb:Observation has exactly one
     * associated qb:DataSet.
     * @return a map of observations with multiple datasets
     */
    public Map<Resource, Set<RDFNode>> validate() {
        Map<Resource, Set<RDFNode>> datasetByObs =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> obsSet = model.listSubjectsWithProperty(
                RDF_type, QB_Observation).toSet();
        for (Resource obs : obsSet) {
            Set<RDFNode> datasetSet = model.listObjectsOfProperty(obs, QB_dataSet).toSet();
            if (datasetSet.size() != 1) {
                datasetByObs.put(obs, datasetSet);
            }
        }
        return datasetByObs;
    }
}
