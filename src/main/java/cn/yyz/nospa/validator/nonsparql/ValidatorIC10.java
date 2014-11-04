package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC10 extends ValidatorBase {
    public ValidatorIC10(Model model) {
        super(model);
    }

    public Map<Resource, Set<RDFNode>> validate() {
        Map<Resource, Set<RDFNode>> dimBySliceWithoutVal = new HashMap<Resource, Set<RDFNode>>();
        List<Property> propPath = Arrays.asList(QB_sliceStructure, QB_componentProperty);
        Map<Resource, Set<? extends RDFNode>> dimBySlice = searchByPathVisit(null, propPath, null);
        for (Resource slice : dimBySlice.keySet()) {
            Set<RDFNode> dimWithoutValSet = new HashSet<RDFNode>();
            for (RDFNode dim : dimBySlice.get(slice)) {
                Property dimAsProp = ResourceFactory.createProperty(dim.asResource().getURI());
                NodeIterator valIter = model.listObjectsOfProperty(slice, dimAsProp);
                if (!valIter.hasNext()) dimWithoutValSet.add(dim);
            }
            if (!dimWithoutValSet.isEmpty()) dimBySliceWithoutVal.put(slice, dimWithoutValSet);
        }
        return dimBySliceWithoutVal;
    }
}
