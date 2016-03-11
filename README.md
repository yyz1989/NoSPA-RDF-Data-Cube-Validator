NoSPA RDF Data Cube Validator
=============================

### Introduction

This is an RDF Data Cube Validator. Its significant difference from other existing validators is that it is not based on SPARQL queries, as its name "NoSPA". Jena library is used to manipulate RDF models. The official SPARQL queries for constraint checks are interpreted and parsed by this validator to search functions with nested statement listing functions provided by Jena and filters for different conditions. It has an outstanding performance because the entire process is executed in memory. I believe that it is valuable to sacrifice some memory for saving time.

Here are some references and knowledge background for this tool:
  * The official RDF data cube spec: [The RDF Data Cube Vocabulary](http://www.w3.org/TR/vocab-data-cube/)
  * Jena API: [Apache Jena](http://jena.apache.org/index.html)
  * The official SPARQL spec: [SPARQL 1.1 Query Language](http://www.w3.org/TR/sparql11-query/)

### Updates in the latest release 0.9.9

1.  Rewrote some functions to boost the performance on validating constraints 11 and 12, which occupies more than 99% computation time among all constraints. Now NoSPA is capable of handling data cube with million level observations.

2.  Added a progress monitor for the validation of 11 and 12.

### Requirements

JDK (>=5) and Maven if you want to compile by yourself

or 

JVM (>=5) if you want to execute a jar directly

### Installation

This tool is written in Java and managed by Maven so you can compile it easily by yourself. The first thing you need to do is ``git clone`` this repository.

*Updates: now the packaged jar files are already uploaded and can be found at the release page so you don't need to do it by yourself any more*

Then you need to do a ``mvn package`` at the root directory of this repository and find the jar file at ``NoSPA-RDF-Data-Cube-Validator/target/nospa-rdf-data-cube-validator-0.9.9.jar``. Note that in this case the library for Jena and Log4j is not included in this package.

In the case that you need to run this package independently, you will need to do a ``mvn package assembly:single`` at the root directory of this repository and find the jar file at ``NoSPA-RDF-Data-Cube-Validator/target/nospa-rdf-data-cube-validator-0.9.9-jar-with-dependencies.jar``, which includes all the required libraries to run it.

### Validation

Basically, there are 3 ways to use it:

1.  Use an IDE to hack into the code by yourself and run the Main class, without making any packages.

2.  In the case that you need to integrate it into your own project, you have to import the package ``rdf-data-cube-validator-0.9.9.jar``, create a new validator instance and call the functions to normalize and validate the cube.

    ``Validator validator = ValidatorFactory.createValidator("NOSPA", inputPath, inputFormat);``
    
    ``validator.normalize();``
    
    ``validator.validateAll();``

    The first argument for the createValidaotr method is the type of validator. Options are "NOSPA" and "SPARQL" since they are implemented in this software. The ``inputPath`` is the path of the cube file and ``inputFormat`` indicates the RDF format of the cube file such as RDF/XML, N3, TURTLE, N-TRIPLES, etc.

    You may also want to check constraints selectively, in that case you cannot use the ValidatorFactory because the two types of validator have different implementions to validate constraints individually and it is a bit difficulty to unify them with an interface. For example, validate with NoSPA validator:
    
    ``NospaValidator nospaValidator = new NospaValidator(inputPath, inputFormat);``
    
    ``nospaValidator.normalize();``
    
    ``nospaValidator.validateIC1();``
    
    ``nospaValidator.validateIC20_21();``
    
    Validate with SPARQL validator:
    
    ``SparqlValidator sparqlValidator = new SparqlValidator(inputPath, inputFormat);``
    
    ``sparqlValidator.normalize();``
    
    ``sparqlValidator.validate("IC1");``
    
    ``sparqlValidator.validateIC20_21("IC20");``
    
    You will know why there is such difference if you can take a look at the code. Maybe I will get better ideas to unify them in the future. Besides, please make sure that you have normalized the cube before checking constraints if it is in the abbreviated form. You don't need to normalize it if you are sure that it is in the normalized form.

    Note that the validation result of this tool will be recorded as logs so you need to turn on the logs for this package in the log configuration of your own project. Additionally you have to set a system property ``current.timestamp`` with the value of current time as part of the name of the validation result. Finally, the validation result can be found at ``${user.dir}/validation_result_${current.timestamp}.md``.

3.  In the case that you need to validate the cube file manually and independently, you need to run ``java -jar nospa-rdf-data-cube-validator-0.9.9-jar-with-dependencies.jar <cube-file.(xml|rdf|nt|n3|ttl)> <(nospa|sparql)>``, where the first argument is the file path of the cube to be validated and the second argument is the name of validator respectively. Currently only 5 RDF format are supported, as can be seen from the file extension name. The validator can be "nospa" power by this tool, or "sparql" which runs the official validation SPARQL queries against the cube with Jena ARQ.

### Performance

The constraint check IC-12, "No duplicate observations", is the most time-consuming procedure for the entire validation. The motivation of developing this tool is mainly to tackle this issue. 

Test file: a data cube containing 13970 observations  
Test environment: Ubuntu 14.04 with VMWare, 2 CPU cores of I5-2450M @ 2GHz, 2 GB memory, ordinary HHD

Time consumption for validating IC-12:  
  * Validation by SPARQL queries with Virtuoso: 1 hour 22 min  
  * Validation by SPARQL queries with Jena Parser: 58 min  
  * Validation by NoSPA: 10 sec

*Updates for the performance of the latest release:*

Test file: a 230MB cube file including 540K observations

Test environment: A Web server with 4 Intel(R) Xeon(R) CPUs E5-2630L 0 @ 2.00GHz and 8 GB memory

Time consumption: 52 sec

### Prospects

Due to lack of faluty datasets, my tests may not cover all cases. Please give me any feedback and suggestion if you are using this software so I can keep improving its quality.

I am still working on some minor changes related to the functionalities. I am planning to make a fair front end when it gets stabilized.
