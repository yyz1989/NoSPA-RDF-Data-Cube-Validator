package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC6 extends ValidatorBase {
    public ValidatorIC6(Model model) {
        super(model);
    }

    /**
     * Validate IC-6 Only attributes may be optional: The only components of a
     * qb:DataStructureDefinition that may be marked as optional, using
     * qb:componentRequired are attributes.
     * @return a set of component properties not declared as attributes
     */
    public Set<RDFNode> validate() {
        Set<RDFNode> compPropSet = new HashSet<RDFNode>();
        Map<Property, RDFNode> objyByProp = new HashMap<Property, RDFNode>();
        objyByProp.put(QB_componentRequired, LITERAL_FALSE);
        Map<Resource, Map<Property, Set<RDFNode>>> compPropByCompSpec = searchByMultipleProperty(
                null, objyByProp, Arrays.asList(QB_componentProperty));
        Set<RDFNode> compSpecSet = model.listObjectsOfProperty(QB_component).toSet();
        compSpecSet.retainAll(compPropByCompSpec.keySet());
        for (RDFNode compSpec : compSpecSet) {
            Resource compSpecAsRes = compSpec.asResource();
            compPropSet.addAll(compPropByCompSpec
                    .get(compSpecAsRes).get(QB_componentProperty));
        }
        Set<Resource> AttribWithDefSet = model.listSubjectsWithProperty(
                RDF_type, QB_AttributeProperty).toSet();
        compPropSet.removeAll(AttribWithDefSet);
        return compPropSet;
    }
}
