package cn.yyz.nospa.validator;

/**
 * Created by yyz on 9/26/14.
 */
public enum IntegrityConstraint {
    PREFIX(
            "PREFIX qb: <http://purl.org/linked-data/cube#>\n" +
                    "PREFIX rdfs: <http://www.w3.org/2000/01/nospa-schema#>\n" +
                    "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n" +
                    "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                    "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
    ),

    IC1(
                    "SELECT ?obs " +
                    "WHERE {\n" +
                    "  {\n" +
                    "    # Check observation has a data set\n" +
                    "    ?obs a qb:Observation .\n" +
                    "    FILTER NOT EXISTS { ?obs qb:dataSet ?dataset1 . }\n" +
                    "  } UNION {\n" +
                    "    # Check has just one data set\n" +
                    "    ?obs a qb:Observation ;\n" +
                    "       qb:dataSet ?dataset1, ?dataset2 .\n" +
                    "    FILTER (?dataset1 != ?dataset2)\n" +
                    "  }\n" +
                    "}"
    ),

    IC2(
                    "SELECT ?dataset " +
                    "WHERE {\n" +
                    "  {\n" +
                    "    # Check dataset has a dsd\n" +
                    "    ?dataset a qb:DataSet .\n" +
                    "    FILTER NOT EXISTS { ?dataset qb:structure ?dsd . }\n" +
                    "  } UNION { \n" +
                    "    # Check has just one dsd\n" +
                    "    ?dataset a qb:DataSet ;\n" +
                    "       qb:structure ?dsd1, ?dsd2 .\n" +
                    "    FILTER (?dsd1 != ?dsd2)\n" +
                    "  }\n" +
                    "}"
    ),

    IC3(
                    "SELECT ?dsd " +
                    "WHERE {\n" +
                    "  ?dsd a qb:DataStructureDefinition .\n" +
                    "  FILTER NOT EXISTS { ?dsd qb:component [qb:componentProperty [a qb:MeasureProperty]] }\n" +
                    "}"
    ),

    IC4(
                    "SELECT ?dim " +
                    "WHERE {\n"+
                    "  ?dim a qb:DimensionProperty .\n"+
                    "  FILTER NOT EXISTS { ?dim rdfs:range [] }\n"+
                    "}"
    ),

    IC5(
                    "SELECT ?dim " +
                    "WHERE {\n" +
                    "  ?dim a qb:DimensionProperty ;\n" +
                    "       rdfs:range skos:Concept .\n" +
                    "  FILTER NOT EXISTS { ?dim qb:codeList [] }\n" +
                    "}"
    ),

    IC6(
                    "SELECT ?component " +
                    "WHERE {\n" +
                    "  ?dsd qb:component ?componentSpec .\n" +
                    "  ?componentSpec qb:componentRequired \"false\"^^xsd:boolean ;\n" +
                    "                 qb:componentProperty ?component .\n" +
                    "  FILTER NOT EXISTS { ?component a qb:AttributeProperty }\n" +
                    "}"
    ),

    IC7(
                    "SELECT ?sliceKey " +
                    "WHERE {\n" +
                    "    ?sliceKey a qb:SliceKey .\n" +
                    "    FILTER NOT EXISTS { [a qb:DataStructureDefinition] qb:sliceKey ?sliceKey }\n" +
                    "}"
    ),

    IC8(
                    "SELECT ?prop " +
                    "WHERE {\n" +
                    "  ?slicekey a qb:SliceKey;\n" +
                    "      qb:componentProperty ?prop .\n" +
                    "  ?dsd qb:sliceKey ?slicekey .\n" +
                    "  FILTER NOT EXISTS { ?dsd qb:component [qb:componentProperty ?prop] }\n" +
                    "}"
    ),

    IC9(
                    "SELECT ?slice " +
                    "WHERE {\n" +
                    "  {\n" +
                    "    # Slice has a key\n" +
                    "    ?slice a qb:Slice .\n" +
                    "    FILTER NOT EXISTS { ?slice qb:sliceStructure ?key }\n" +
                    "  } UNION {\n" +
                    "    # Slice has just one key\n" +
                    "    ?slice a qb:Slice ;\n" +
                    "           qb:sliceStructure ?key1, ?key2;\n" +
                    "    FILTER (?key1 != ?key2)\n" +
                    "  }\n" +
                    "}"
    ),

    IC10(
                    "SELECT ?slice ?dim " +
                    "WHERE {\n" +
                    "  ?slice qb:sliceStructure [qb:componentProperty ?dim] .\n" +
                    "  FILTER NOT EXISTS { ?slice ?dim [] }\n" +
                    "}"
    ),

    IC11(
                    "SELECT ?obs ?dim " +
                    "WHERE {\n" +
                    "    ?obs qb:dataSet/qb:structure/qb:component/qb:componentProperty ?dim .\n" +
                    "    ?dim a qb:DimensionProperty;\n" +
                    "    FILTER NOT EXISTS { ?obs ?dim [] }\n" +
                    "}"
    ),

    IC12(
                    "SELECT ?obs1 ?obs2 " +
                    "WHERE {\n" +
                    "  FILTER( ?allEqual )\n" +
                    "  {\n" +
                    "    # For each pair of observations test if all the dimension values are the same\n" +
                    "    SELECT (MIN(?equal) AS ?allEqual) WHERE {\n" +
                    "        ?obs1 qb:dataSet ?dataset .\n" +
                    "        ?obs2 qb:dataSet ?dataset .\n" +
                    "        FILTER (?obs1 != ?obs2)\n" +
                    "        ?dataset qb:structure/qb:component/qb:componentProperty ?dim .\n" +
                    "        ?dim a qb:DimensionProperty .\n" +
                    "        ?obs1 ?dim ?value1 .\n" +
                    "        ?obs2 ?dim ?value2 .\n" +
                    "        BIND( ?value1 = ?value2 AS ?equal)\n" +
                    "    } GROUP BY ?obs1 ?obs2\n" +
                    "  }\n" +
                    "}"
    ),

    IC13(
                    "SELECT ?obs ?attr " +
                    "WHERE {\n" +
                    "    ?obs qb:dataSet/qb:structure/qb:component ?component .\n" +
                    "    ?component qb:componentRequired \"true\"^^xsd:boolean ;\n" +
                    "               qb:componentProperty ?attr .\n" +
                    "    FILTER NOT EXISTS { ?obs ?attr [] }\n" +
                    "}"
    ),

    IC14(
                    "SELECT ?obs ?measure " +
                    "WHERE {\n" +
                    "    # Observation in a non-measureType cube\n" +
                    "    ?obs qb:dataSet/qb:structure ?dsd .\n" +
                    "    FILTER NOT EXISTS { ?dsd qb:component/qb:componentProperty qb:measureType }\n" +
                    "\n" +
                    "    # verify every measure is present\n" +
                    "    ?dsd qb:component/qb:componentProperty ?measure .\n" +
                    "    ?measure a qb:MeasureProperty;\n" +
                    "    FILTER NOT EXISTS { ?obs ?measure [] }\n" +
                    "}"
    ),

    IC15(
                    "SELECT ?obs ?measure " +
                    "WHERE {\n" +
                    "    # Observation in a measureType-cube\n" +
                    "    ?obs qb:dataSet/qb:structure ?dsd ;\n" +
                    "         qb:measureType ?measure .\n" +
                    "    ?dsd qb:component/qb:componentProperty qb:measureType .\n" +
                    "    # Must have value for its measureType\n" +
                    "    FILTER NOT EXISTS { ?obs ?measure [] }\n" +
                    "}"
    ),

    IC16(
                    "SELECT ?obs ?measure " +
                    "WHERE {\n" +
                    "    # Observation with measureType\n" +
                    "    ?obs qb:dataSet/qb:structure ?dsd ;\n" +
                    "         qb:measureType ?measure ;\n" +
                    "         ?omeasure [] .\n" +
                    "    # Any measure on the observation\n" +
                    "    ?dsd qb:component/qb:componentProperty qb:measureType ;\n" +
                    "         qb:component/qb:componentProperty ?omeasure .\n" +
                    "    ?omeasure a qb:MeasureProperty .\n" +
                    "    # Must be the same as the measureType\n" +
                    "    FILTER (?omeasure != ?measure)\n" +
                    "}"
    ),

    IC17(
                    "      # Count number of other measures found at each point \n" +
                    "      SELECT ?numMeasures (COUNT(?obs2) AS ?count) WHERE {\n" +
                    "          {\n" +
                    "              # Find the DSDs and check how many measures they have\n" +
                    "              SELECT ?dsd (COUNT(?m) AS ?numMeasures) WHERE {\n" +
                    "                  ?dsd qb:component/qb:componentProperty ?m.\n" +
                    "                  ?m a qb:MeasureProperty .\n" +
                    "              } GROUP BY ?dsd\n" +
                    "          }\n" +
                    "        \n" +
                    "          # Observation in measureType cube\n" +
                    "          ?obs1 qb:dataSet/qb:structure ?dsd;\n" +
                    "                qb:dataSet ?dataset ;\n" +
                    "                qb:measureType ?m1 .\n" +
                    "    \n" +
                    "          # Other observation at same dimension value\n" +
                    "          ?obs2 qb:dataSet ?dataset ;\n" +
                    "                qb:measureType ?m2 .\n" +
                    "          FILTER NOT EXISTS { \n" +
                    "              ?dsd qb:component/qb:componentProperty ?dim .\n" +
                    "              FILTER (?dim != qb:measureType)\n" +
                    "              ?dim a qb:DimensionProperty .\n" +
                    "              ?obs1 ?dim ?v1 . \n" +
                    "              ?obs2 ?dim ?v2. \n" +
                    "              FILTER (?v1 != ?v2)\n" +
                    "          }\n" +
                    "          \n" +
                    "      } GROUP BY ?obs1 ?numMeasures\n" +
                    "        HAVING (?count != ?numMeasures)\n"
    ),

    IC18(
                    "SELECT ?obs ?dataset " +
                    "WHERE {\n" +
                    "    ?dataset qb:slice       ?slice .\n" +
                    "    ?slice   qb:observation ?obs .\n" +
                    "    FILTER NOT EXISTS { ?obs qb:dataSet ?dataset . }\n" +
                    "}"
    ),

    IC19(
                    "SELECT ?obs ?dim " +
                    "WHERE {{\n" +
                    "    ?obs qb:dataSet/qb:structure/qb:component/qb:componentProperty ?dim .\n" +
                    "    ?dim a qb:DimensionProperty ;\n" +
                    "        qb:codeList ?list .\n" +
                    "    ?list a skos:ConceptScheme .\n" +
                    "    ?obs ?dim ?v .\n" +
                    "    FILTER NOT EXISTS { ?v a skos:Concept ; skos:inScheme ?list }\n" +
                    "} UNION {\n" +
                    "    ?obs qb:dataSet/qb:structure/qb:component/qb:componentProperty ?dim .\n" +
                    "    ?dim a qb:DimensionProperty ;\n" +
                    "        qb:codeList ?list .\n" +
                    "    ?list a skos:Collection .\n" +
                    "    ?obs ?dim ?v .\n" +
                    "    FILTER NOT EXISTS { ?v a skos:Concept . ?list skos:member+ ?v }\n" +
                    "}}"
    ),

    IC20_1(
                    "SELECT ?p WHERE {\n" +
                    "    ?hierarchy a qb:HierarchicalCodeList ;\n" +
                    "                 qb:parentChildProperty ?p .\n" +
                    "    FILTER ( isIRI(?p) )\n" +
                    "}"
    ),

    IC20_2(
                    "SELECT ?list ?v " +
                    "WHERE {\n" +
                    "    ?obs qb:dataSet/qb:structure/qb:component/qb:componentProperty ?dim .\n" +
                    "    ?dim a qb:DimensionProperty ;\n" +
                    "        qb:codeList ?list .\n" +
                    "    ?list a qb:HierarchicalCodeList .\n" +
                    "    ?obs ?dim ?v .\n" +
                    "    FILTER NOT EXISTS { ?list qb:hierarchyRoot/<$p>* ?v }\n" +
                    "}"
    ),

    IC21_1(
                    "SELECT ?p WHERE {\n" +
                    "    ?hierarchy a qb:HierarchicalCodeList;\n" +
                    "                 qb:parentChildProperty ?pcp .\n" +
                    "    FILTER( isBlank(?pcp) )\n" +
                    "    ?pcp  owl:inverseOf ?p .\n" +
                    "    FILTER( isIRI(?p) )\n" +
                    "}"
    ),

    IC21_2(
                    "SELECT ?list ?v " +
                    "WHERE {\n" +
                    "    ?obs qb:dataSet/qb:structure/qb:component/qb:componentProperty ?dim .\n" +
                    "    ?dim a qb:DimensionProperty ;\n" +
                    "         qb:codeList ?list .\n" +
                    "    ?list a qb:HierarchicalCodeList .\n" +
                    "    ?obs ?dim ?v .\n" +
                    "    FILTER NOT EXISTS { ?list qb:hierarchyRoot/(^<$p>)* ?v }\n" +
                    "}"
    );

    private String value;

    private IntegrityConstraint(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
