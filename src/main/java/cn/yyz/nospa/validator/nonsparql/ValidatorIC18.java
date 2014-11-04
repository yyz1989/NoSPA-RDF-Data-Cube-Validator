package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC18 extends ValidatorBase {
    public ValidatorIC18(Model model) {
        super(model);
    }

    public Map<Resource, Resource> validate() {
        Map<Resource, Resource> obsNotInDataset = new HashMap<Resource, Resource>();
        Property[] properties = {QB_slice, QB_observation};
        Map<Resource, Set<? extends RDFNode>> obsByDataset = searchByPathVisit(null,
                Arrays.asList(properties), null);
        for (Resource dataset : obsByDataset.keySet()) {
            Set<? extends RDFNode> obsSet = obsByDataset.get(dataset);
            for (RDFNode obs : obsSet) {
                Resource obsAsRes = obs.asResource();
                if (!model.listStatements(obsAsRes, QB_dataSet, dataset).hasNext())
                    obsNotInDataset.put(obsAsRes, dataset);
            }
        }
        return obsNotInDataset;
    }
}
