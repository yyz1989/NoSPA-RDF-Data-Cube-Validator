package cn.yyz.rdf.validator;

import com.hp.hpl.jena.update.UpdateAction;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by yyz on 9/26/14.
 */
public class Main {
    public static void main(String[] args) {
        Validator validator = new Validator("test.ttl", "TTL");
        long t1 = System.currentTimeMillis();
        validator.normalizeBySparql();
        long t2 = System.currentTimeMillis();
        validator.normalizePhase1();
        validator.normalizePhase2();
        long t3 = System.currentTimeMillis();
        System.out.println(t2 - t1);
        System.out.println(t3 - t2);
        //validator.output();
    }
}
