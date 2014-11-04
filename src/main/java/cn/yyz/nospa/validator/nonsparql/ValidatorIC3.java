package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC3 extends ValidatorBase {
    public ValidatorIC3 (Model model) {
        super(model);
    }

    /**
     * Validate IC-3 DSD includes measure: Every qb:DataStructureDefinition
     * must include at least one declared measure.
     * @return a set of DSDs without at least one declared measure
     */
    public Set<Resource> validate() {
        Set<Resource> dsdWithoutMeasure = new HashSet<Resource>();
        Set<Resource> dsdSet = model.listSubjectsWithProperty(RDF_type,
                QB_DataStructureDefinition).toSet();
        Set<Resource> measurePropSet = model.listSubjectsWithProperty(RDF_type,
                QB_MeasureProperty).toSet();
        for (Resource dsd : dsdSet) {
            Map<Resource, Set<? extends RDFNode>> dsdPropertyMap = searchByPathVisit(dsd,
                    Arrays.asList(QB_component, QB_componentProperty), null);
            Set<? extends RDFNode> compPropSet = dsdPropertyMap.get(dsd);
            compPropSet.retainAll(measurePropSet);
            if (compPropSet.isEmpty()) dsdWithoutMeasure.add(dsd);
        }
        return dsdWithoutMeasure;
    }
}
