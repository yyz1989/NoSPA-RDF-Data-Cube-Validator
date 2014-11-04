package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC8 extends ValidatorBase {
    public ValidatorIC8(Model model) {
        super(model);
    }

    public Set<RDFNode> validate() {
        Set<RDFNode> compWithoutDSD = new HashSet<RDFNode>();
        List<Property> propPath = Arrays.asList(QB_component, QB_componentProperty);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_SliceKey);
        Set<RDFNode> propSet = new HashSet<RDFNode>();
        Map<Resource, Map<Property, Set<RDFNode>>> propBySliceKey =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_componentProperty));
        Map<Resource, Set<? extends RDFNode>> sliceKeyByDSD =
                searchByPathVisit(null, Arrays.asList(QB_sliceKey), null);
        for (Resource dsd : sliceKeyByDSD.keySet()) {
            Set<? extends RDFNode> sliceKeySet = sliceKeyByDSD.get(dsd);
            for (RDFNode sliceKey : sliceKeySet) {
                if (propBySliceKey.containsKey(sliceKey.asResource()))
                    propSet.addAll(propBySliceKey
                            .get(sliceKey.asResource()).get(QB_componentProperty));
            }
            for (RDFNode property : propSet) {
                if (!connectedByPropList(dsd, propPath, property))
                    compWithoutDSD.add(property);
            }
        }
        return compWithoutDSD;
    }
}
