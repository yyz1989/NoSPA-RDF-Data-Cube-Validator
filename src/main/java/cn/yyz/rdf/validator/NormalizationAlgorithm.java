package cn.yyz.rdf.validator;

/**
 * Created by yyz on 9/26/14.
 */
public enum NormalizationAlgorithm {
    PHASE1(
            "PREFIX rdf:            <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                    "PREFIX qb:             <http://purl.org/linked-data/cube#>\n" +
                    "\n" +
                    "INSERT {\n" +
                    "    ?o rdf:type qb:Observation .\n" +
                    "} WHERE {\n" +
                    "    [] qb:observation ?o .\n" +
                    "};\n" +
                    "\n" +
                    "INSERT {\n" +
                    "    ?o  rdf:type qb:Observation .\n" +
                    "    ?ds rdf:type qb:DataSet .\n" +
                    "} WHERE {\n" +
                    "    ?o qb:dataSet ?ds .\n" +
                    "};\n" +
                    "\n" +
                    "INSERT {\n" +
                    "    ?s rdf:type qb:Slice .\n" +
                    "} WHERE {\n" +
                    "    [] qb:slice ?s.\n" +
                    "};\n" +
                    "\n" +
                    "INSERT {\n" +
                    "    ?cs qb:componentProperty ?p .\n" +
                    "    ?p  rdf:type qb:DimensionProperty .\n" +
                    "} WHERE {\n" +
                    "    ?cs qb:dimension ?p .\n" +
                    "};\n" +
                    "\n" +
                    "INSERT {\n" +
                    "    ?cs qb:componentProperty ?p .\n" +
                    "    ?p  rdf:type qb:MeasureProperty .\n" +
                    "} WHERE {\n" +
                    "    ?cs qb:measure ?p .\n" +
                    "};\n" +
                    "\n" +
                    "INSERT {\n" +
                    "    ?cs qb:componentProperty ?p .\n" +
                    "    ?p  rdf:type qb:AttributeProperty .\n" +
                    "} WHERE {\n" +
                    "    ?cs qb:attribute ?p .\n" +
                    "}"
    ),

    PHASE2(
            "PREFIX qb:             <http://purl.org/linked-data/cube#>\n" +
                    "\n" +
                    "# Dataset attachments\n" +
                    "INSERT {\n" +
                    "    ?obs  ?comp ?value\n" +
                    "} WHERE {\n" +
                    "    ?spec    qb:componentProperty ?comp ;\n" +
                    "             qb:componentAttachment qb:DataSet .\n" +
                    "    ?dataset qb:structure [qb:component ?spec];\n" +
                    "             ?comp ?value .\n" +
                    "    ?obs     qb:dataSet ?dataset.\n" +
                    "};\n" +
                    "\n" +
                    "# Slice attachments\n" +
                    "INSERT {\n" +
                    "    ?obs  ?comp ?value\n" +
                    "} WHERE {\n" +
                    "    ?spec    qb:componentProperty ?comp;\n" +
                    "             qb:componentAttachment qb:Slice .\n" +
                    "    ?dataset qb:structure [qb:component ?spec];\n" +
                    "             qb:slice ?slice .\n" +
                    "    ?slice ?comp ?value;\n" +
                    "           qb:observation ?obs .\n" +
                    "};\n" +
                    "\n" +
                    "# Dimension values on slices\n" +
                    "INSERT {\n" +
                    "    ?obs  ?comp ?value\n" +
                    "} WHERE {\n" +
                    "    ?spec    qb:componentProperty ?comp .\n" +
                    "    ?comp a  qb:DimensionProperty .\n" +
                    "    ?dataset qb:structure [qb:component ?spec];\n" +
                    "             qb:slice ?slice .\n" +
                    "    ?slice ?comp ?value;\n" +
                    "           qb:observation ?obs .\n" +
                    "}\n"
    );

    private String value;

    private NormalizationAlgorithm(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
