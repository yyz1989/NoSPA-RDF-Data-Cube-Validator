package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC4 extends ValidatorBase {
    public ValidatorIC4(Model model) {
        super(model);
    }

    /**
     * Validate IC-4 Dimensions have range: Every dimension declared in a
     * qb:DataStructureDefinition must have a declared rdfs:range.
     * @return a set of dimensions without a declared rdfs:range
     */
    public Set<Resource> validate() {
        Set<Resource> dimSet = model.listSubjectsWithProperty(RDF_type,
                QB_DimensionProperty).toSet();
        Set<Resource> dimWithRangeSet = model.listSubjectsWithProperty(
                RDFS_range).toSet();
        Set<Resource> dimWithoutRangeSet = new HashSet<Resource>(dimSet);
        dimWithoutRangeSet.removeAll(dimWithRangeSet);
        return dimWithoutRangeSet;
    }
}
