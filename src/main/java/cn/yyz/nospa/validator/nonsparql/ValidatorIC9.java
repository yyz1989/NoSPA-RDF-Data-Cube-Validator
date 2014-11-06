package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC9 extends ValidatorBase {
    public ValidatorIC9(Model model) {
        super(model);
    }

    /**
     * Validate IC-9 Unique slice structure: Each qb:Slice must have exactly
     * one associated qb:sliceStructure.
     * @return a map of slices with multiple slice structures
     */
    public Map<Resource, Set<RDFNode>> validate() {
        Map<Resource, Set<RDFNode>> structBySlice =
                new HashMap<Resource, Set<RDFNode>>();
        Set<Resource> sliceSet = model.listSubjectsWithProperty(RDF_type, QB_Slice).toSet();
        for (Resource slice : sliceSet) {
            Set<RDFNode> sliceStructSet = model.listObjectsOfProperty(slice,
                    QB_sliceStructure).toSet();
            if (sliceStructSet.size() != 1) structBySlice.put(slice,
                    sliceStructSet);
        }
        return structBySlice;
    }
}
