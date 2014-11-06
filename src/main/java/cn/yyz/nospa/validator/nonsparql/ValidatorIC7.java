package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC7 extends ValidatorBase {
    public ValidatorIC7(Model model) {
        super(model);
    }

    /**
     * Validate IC-7 Slice Keys must be declared: Every qb:SliceKey must be
     * associated with a qb:DataStructureDefinition.
     * @return a set of Slice Keys not associated with DSDs
     */
    public Set<Resource> validate() {
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DataStructureDefinition);
        Map<Resource, Map<Property, Set<RDFNode>>> sliceKeyByDSD =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_sliceKey));
        Set<Resource> sliceKeySet = model.listSubjectsWithProperty(RDF_type, QB_SliceKey).toSet();
        for (Resource dsd : sliceKeyByDSD.keySet()) {
            Set<RDFNode> sliceKeyInDSDSet = sliceKeyByDSD.get(dsd).get(QB_sliceKey);
            sliceKeySet.removeAll(sliceKeyInDSDSet);
        }
        return sliceKeySet;
    }
}
