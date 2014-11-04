package cn.yyz.nospa.validator.sparql;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.update.UpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by yyz on 11/4/14.
 */
public class SparqlValidator {
    private Model model;
    private Logger logger = LoggerFactory.getLogger(SparqlValidator.class);

    public SparqlValidator(Model model) {
        this.model = model;
    }

    /**
     * Normalizes an abbreviated Data Cube with SPARQL queries.
     */
    public void normalizeBySparql() {
        logger.info("Normalizing cube with SPARQL queries ...");
        String queryString1 = NormalizationAlgorithm.PHASE1.getValue();
        String queryString2 = NormalizationAlgorithm.PHASE2.getValue();
        UpdateAction.parseExecute(queryString1, model);
        UpdateAction.parseExecute(queryString2, model);
    }

    /**
     * The function to validate constraints by SPARQL queries
     * @param constraint name of constraint (e.g., IC1)
     */
    public void validateBySparql(String constraint) {
        validateBySparql(IntegrityConstraint.valueOf(constraint));
    }

    /**
     * The function to validate constraints by SPARQL queries
     * @param constraint constraint defined in an enum class
     */
    public void validateBySparql(IntegrityConstraint constraint) {
        String prefix = IntegrityConstraint.PREFIX.getValue();
        Query query = QueryFactory.create(prefix + constraint.getValue());
        QueryExecution qe = QueryExecutionFactory.create(query, model);
        ResultSet resultSet = qe.execSelect();
        List<String> variables = resultSet.getResultVars();
        while (resultSet.hasNext()) {
            QuerySolution querySolution = resultSet.next();
            for (String var : variables) {
                System.out.print(var + ": " + querySolution.get(var).toString() + "    ");
            }
            System.out.println();
        }
        qe.close();
    }
}
