package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC5 extends ValidatorBase {
    public ValidatorIC5(Model model) {
        super(model);
    }

    /**
     * Validate IC-5 Concept dimensions have code lists: Every dimension with
     * range skos:Concept must have a qb:codeList.
     * @return a set of concept dimensions without code lists
     */
    public Set<Resource> validate() {
        Set<Resource> dimWithoutCodeList = new HashSet<Resource>();
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DimensionProperty);
        objByProp.put(RDFS_range, SKOS_Concept);
        Set<Resource> dimSet = searchByMultipleProperty(null, objByProp);
        for (Resource dimension : dimSet) {
            NodeIterator codelistIterator = model.listObjectsOfProperty(dimension, QB_codeList);
            if (!codelistIterator.hasNext()) dimWithoutCodeList.add(dimension);
        }
        return dimWithoutCodeList;
    }
}
