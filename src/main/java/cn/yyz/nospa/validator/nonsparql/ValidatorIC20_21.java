package cn.yyz.nospa.validator.nonsparql;

import com.hp.hpl.jena.rdf.model.*;

import java.util.*;

/**
 * Created by yyz on 11/4/14.
 */
public class ValidatorIC20_21 extends ValidatorBase {
    public ValidatorIC20_21(Model model) {
        super(model);
    }

    /**
     * Validate IC-20 Codes from hierarchy: If a dimension property has a
     * qb:HierarchicalCodeList with a non-blank qb:parentChildProperty then
     * the value of that dimension property on every qb:Observation must be
     * reachable from a root of the hierarchy using zero or more hops along
     * the qb:parentChildProperty links.
     * Validate IC-21 Codes from hierarchy (inverse): If a dimension property
     * has a qb:HierarchicalCodeList with an inverse qb:parentChildProperty
     * then the value of that dimension property on every qb:Observation must
     * be reachable from a root of the hierarchy using zero or more hops along
     * the inverse qb:parentChildProperty links.
     * @return a list of two maps containing values with code lists not
     * including corresponding values with any parent child property along both
     * direct and inverse paths
     */
    public List<Map<RDFNode, Set<RDFNode>>> validate() {
        Map<RDFNode, Set<RDFNode>> valNotInCodeListByDirPcp =
                new HashMap<RDFNode, Set<RDFNode>>();
        Map<RDFNode, Set<RDFNode>> valNotInCodeListByInvPcp =
                new HashMap<RDFNode, Set<RDFNode>>();
        List<Map<RDFNode, Set<RDFNode>>> valNotInCodeListByPcp =
                new ArrayList<Map<RDFNode, Set<RDFNode>>>();
        Map<Resource, Map<String, Set<Property>>> pcpByCodeList = getPcpByCodeList();
        Set<Resource> codeListWithDefSet = model.listResourcesWithProperty(RDF_type,
                QB_HierarchicalCodeList).toSet();
        Map<Resource, Set<? extends RDFNode>> dimByDataset = searchByPathVisit(null,
                Arrays.asList(QB_structure, QB_component, QB_componentProperty), null);
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_DimensionProperty);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_codeList));
        for (Resource dataset : dimByDataset.keySet()) {
            Map<Property, Set<RDFNode>> codeListByDim = new HashMap<Property, Set<RDFNode>>();
            Set<Resource> obsSet = model.listSubjectsWithProperty(QB_dataSet, dataset).toSet();
            Set<? extends RDFNode> dimSet = dimByDataset.get(dataset);
            dimSet.retainAll(objBySubAndProp.keySet());
            for (RDFNode dim : dimSet) {
                Set<RDFNode> codeListSet = objBySubAndProp.get(dim.asResource()).get(QB_codeList);
                codeListSet.retainAll(codeListWithDefSet);
                Property dimAsProp = ResourceFactory.createProperty(dim.asResource().getURI());
                if (!codeListSet.isEmpty()) codeListByDim.put(dimAsProp, codeListSet);
            }
            valNotInCodeListByDirPcp.putAll(
                    obsSetPcpCheck("DIRECT", obsSet, codeListByDim, pcpByCodeList));
            valNotInCodeListByInvPcp.putAll(
                    obsSetPcpCheck("INVERSE", obsSet, codeListByDim, pcpByCodeList));
        }
        valNotInCodeListByPcp.add(valNotInCodeListByDirPcp);
        valNotInCodeListByPcp.add(valNotInCodeListByInvPcp);
        return valNotInCodeListByPcp;
    }

    /**
     * This function is a subtask to check if the dimension values of a set of
     * observations are connected to their corresponding code list through a
     * path of parent child properties
     * @param direction indicates a direct or inverse link path
     * @param obsSet a set of observations
     * @param codeListByDim a map of dimensions with corresponding code lists
     * @param pcpByCodeList a map of code lists with corresponding parent child
     *                      properties
     * @return a map of values with a set of code lists not including
     * corresponding values
     */
    public Map<RDFNode, Set<RDFNode>> obsSetPcpCheck (String direction,
                                                      Set<Resource> obsSet, Map<Property, Set<RDFNode>> codeListByDim,
                                                      Map<Resource, Map<String, Set<Property>>> pcpByCodeList) {
        Map<RDFNode, Set<RDFNode>> valNotInCodeList = new HashMap<RDFNode, Set<RDFNode>>();
        for (Resource obs : obsSet) {
            Map<RDFNode, Set<RDFNode>> codeListByVal =
                    valNotInCodeListCheck(direction, obs, codeListByDim, pcpByCodeList);
            for (RDFNode val : codeListByVal.keySet()) {
                Set<RDFNode> codeListForOneObs = codeListByVal.get(val);
                if (valNotInCodeList.containsKey(val)) {
                    Set<RDFNode> codeListForObsSet = valNotInCodeList.get(val);
                    codeListForObsSet.addAll(codeListForOneObs);
                }
                else valNotInCodeList.put(val, codeListForOneObs);
            }
        }
        return valNotInCodeList;
    }

    /**
     * This function is a subtask to check if the dimension values of an
     * observation are connected to code lists through a path of parent child
     * properties
     * @param direction indicates a direct or inverse link path
     * @param obs an observation
     * @param codeListByDim a map of dimensions with corresponding code lists
     * @param pcpByCodeList a map of code lists with corresponding parent child
     *                      properties
     * @return a map of values with a set of code lists not including
     * corresponding values
     */
    public Map<RDFNode, Set<RDFNode>> valNotInCodeListCheck(String direction, Resource obs,
                                                            Map<Property, Set<RDFNode>> codeListByDim,
                                                            Map<Resource, Map<String, Set<Property>>> pcpByCodeList) {
        Map<RDFNode, Set<RDFNode>> valNotInCodeList = new HashMap<RDFNode, Set<RDFNode>>();
        for (Property dim : codeListByDim.keySet()) {
            Set<RDFNode> codeListSet = codeListByDim.get(dim);
            Set<RDFNode> valueSet = model.listObjectsOfProperty(obs, dim).toSet();
            if (valueSet.size() != 1) continue;
            RDFNode value = valueSet.iterator().next();
            boolean isConnected = false;
            for (RDFNode codeList : codeListSet) {
                Resource codeListAsRes = codeList.asResource();
                Set<Property> pcpSet = pcpByCodeList.get(codeListAsRes).get(direction);
                isConnected = connectedByPcp(direction, codeListAsRes, pcpSet, value);
                if (isConnected) break;
            }
            if (!isConnected) valNotInCodeList.put(value, codeListSet);
        }
        return valNotInCodeList;
    }

    /**
     * This function is a subtask to check if a code list is connected to a
     * value through one of parent child properties in the given set
     * @param direction indicates a direct or inverse link path
     * @param codeList a code list
     * @param pcpSet a set of candidate parent child properties connecting the
     *               code list to the value
     * @param value the dimension value of an observation
     * @return a boolean value indicating whether there is a connection or not
     */
    public boolean connectedByPcp (String direction, Resource codeList,
                                   Set<Property> pcpSet, RDFNode value) {
        boolean isConnected = false;
        if (pcpSet.isEmpty()) {
            isConnected = connectedByPropList(codeList, Arrays.asList(QB_hierarchyRoot), value);
            return isConnected;
        }
        for (Property pcp : pcpSet) {
            if (direction.equals("DIRECT"))
                isConnected = connectedByRepeatedProp(codeList, Arrays.asList(QB_hierarchyRoot),
                        pcp, value, true);
            else
                isConnected = connectedByRepeatedProp(codeList, Arrays.asList(QB_hierarchyRoot),
                        pcp, value, false);
            if (isConnected) break;
        }
        return isConnected;
    }

    /**
     * This function is a subtask to get sets of candidate parent child
     * properties for the corresponding code lists
     * @return a map of code lists with corresponding parent child properties
     */
    public Map<Resource, Map<String, Set<Property>>> getPcpByCodeList () {
        Map<Resource, Map<String, Set<Property>>> pcpByCodeList =
                new HashMap<Resource, Map<String, Set<Property>>>();
        Map<Property, RDFNode> objByProp = new HashMap<Property, RDFNode>();
        objByProp.put(RDF_type, QB_HierarchicalCodeList);
        Map<Resource, Map<Property, Set<RDFNode>>> objBySubAndProp =
                searchByMultipleProperty(null, objByProp, Arrays.asList(QB_parentChildProperty));
        for (Resource codeList : objBySubAndProp.keySet()) {
            Map<String, Set<Property>> pcpByDirect = new HashMap<String, Set<Property>>();
            Set<Property> dirPcpSet = new HashSet<Property>();
            Set<Property> invPcpSet = new HashSet<Property>();
            Set<RDFNode> pcpNodeSet =
                    objBySubAndProp.get(codeList).get(QB_parentChildProperty);
            for (RDFNode pcp : pcpNodeSet) {
                if (pcp.isURIResource())
                    dirPcpSet.add(ResourceFactory.createProperty(pcp.asResource().getURI()));
                else if (pcp.isAnon()) {
                    NodeIterator invPcpIter =
                            model.listObjectsOfProperty(pcp.asResource(), OWL_inverseOf);
                    invPcpSet.addAll(nodeToProperty(invPcpIter.toSet()));
                }
            }
            pcpByDirect.put("DIRECT", dirPcpSet);
            pcpByDirect.put("INVERSE", invPcpSet);
            pcpByCodeList.put(codeList, pcpByDirect);
        }
        return pcpByCodeList;
    }
}
