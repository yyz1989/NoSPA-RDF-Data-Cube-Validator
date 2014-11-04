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
public class ValidatorIC2 extends ValidatorBase {
    public ValidatorIC2 (Model model) {
        super(model);
    }

    /**
     * Validate IC-2 Unique DSD: Every qb:DataSet has exactly one associated
     * qb:DataStructureDefinition.
     * @return a map of datasets with multiple dsds
     */
    public Map<Resource, Set<RDFNode>> validate() {
        Map<Resource, Set<RDFNode>> dsdByDataset =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> datasetSet = model.listSubjectsWithProperty(
                RDF_type, QB_DataSet).toSet();
        for (Resource dataset : datasetSet) {
            Set<RDFNode> dsdSet = model.listObjectsOfProperty(dataset, QB_structure).toSet();
            if (dsdSet.size() != 1) {
                dsdByDataset.put(dataset, dsdSet);
            }
        }
        return dsdByDataset;
    }
}
